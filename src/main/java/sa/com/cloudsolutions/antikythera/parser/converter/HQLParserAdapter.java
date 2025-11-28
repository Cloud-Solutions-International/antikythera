package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.raditha.hql.parser.HQLParser;
import com.raditha.hql.parser.ParseException;
import com.raditha.hql.model.MetaData;
import com.raditha.hql.converter.HQLToPostgreSQLConverter;
import com.raditha.hql.converter.ConversionException;
import com.raditha.hql.converter.JoinMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter that bridges antikythera's converter interface with raditha's
 * hql-parser.
 * Replaces the mock implementation in HibernateQueryConverter with real
 * ANTLR4-based parsing.
 */
@SuppressWarnings("java:S125")
public class HQLParserAdapter {

    private static final Logger logger = LoggerFactory.getLogger(HQLParserAdapter.class);

    /*
     * Pattern to match SpEL expressions: such as :#{#variableName}, or
     * :#{#object.property}
     * and also :#{#object.method()}
     */
    private static final Pattern SPEL_PATTERN = Pattern.compile(":#\\{#([^}]+?)\\}");
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile("(?i)(SELECT\\s+NEW\\s+[\\w.]+\\s*\\()",
            Pattern.DOTALL);
    // Pattern to find CAST keywords (used as a starting point, actual matching uses parenthesis balancing)
    private static final Pattern CAST_KEYWORD_PATTERN = Pattern.compile("(?i)CAST\\s*\\(");
    private static final Pattern AS_ALIAS_PATTERN = Pattern.compile("(?i)\\s+AS\\s+([^\\d\\W]\\w*)");
    private final CompilationUnit cu;
    private final HQLParser hqlParser;
    private final HQLToPostgreSQLConverter sqlConverter;
    private final Set<DatabaseDialect> supportedDialects;
    TypeWrapper entity;

    public HQLParserAdapter(CompilationUnit cu, TypeWrapper entity) {
        this.hqlParser = new HQLParser();
        this.sqlConverter = new HQLToPostgreSQLConverter();
        this.supportedDialects = EnumSet.of(DatabaseDialect.POSTGRESQL);
        this.cu = cu;
        this.entity = entity;
    }

    /**
     * Converts a JPA/HQL query to native SQL.
     *
     * @param jpaQuery The original JPA/HQL query string to convert
     * @return ConversionResult containing the native SQL and conversion metadata
     * @throws QueryConversionException if the conversion fails
     */
    public ConversionResult convertToNativeSQL(String jpaQuery) throws ParseException, ConversionException {
        // Preprocess: Replace SpEL expressions with simple named parameters
        SpELPreprocessingResult preprocessing = preprocessSpELExpressions(jpaQuery);
        String preprocessedQuery = preprocessing.preprocessedQuery;

        // Preprocess: Remove AS aliases from inside SELECT NEW constructor expressions
        preprocessedQuery = removeASFromConstructorExpressions(preprocessedQuery);

        MetaData analysis = hqlParser.analyze(preprocessedQuery);

        Set<String> referencedTables = registerMappings(analysis);
        String nativeSql = sqlConverter.convert(preprocessedQuery, analysis);

        // Postprocess: Restore SpEL expressions in the SQL output
        nativeSql = postprocessSpELExpressions(nativeSql, preprocessing.spelMapping);

        List<ParameterMapping> parameterMappings = extractParameterMappings(analysis, preprocessing.spelMapping);
        ConversionResult result = new ConversionResult(nativeSql, parameterMappings, referencedTables);
        result.setMetaData(analysis);
        return result;
    }

    /**
     * Skips over a string literal starting at the given position.
     * Returns the position after the string literal, or the original position if not a string.
     */
    private int skipStringLiteral(String text, int pos) {
        if (pos >= text.length()) {
            return pos;
        }
        char quote = text.charAt(pos);
        if (quote != '\'' && quote != '"') {
            return pos;
        }
        
        for (int i = pos + 1; i < text.length(); i++) {
            if (text.charAt(i) == quote) {
                // Check for escaped quote (two quotes in a row)
                if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                    i++; // Skip escaped quote
                } else {
                    return i + 1; // Return position after closing quote
                }
            }
        }
        return text.length(); // Unterminated string
    }
    
    /**
     * Finds the position of the matching closing parenthesis, handling nested parens and strings.
     * Returns -1 if no matching paren is found.
     */
    private int findMatchingParen(String text, int openParenPos) {
        int depth = 1;
        
        for (int i = openParenPos + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            
            // Skip string literals completely
            if (c == '\'' || c == '"') {
                i = skipStringLiteral(text, i) - 1; // -1 because loop will increment
                continue;
            }
            
            // Track parentheses depth
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (--depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    /**
     * Finds all CAST expressions in the given text using safe parenthesis balancing.
     * Returns a list of [start, end] position pairs for each CAST expression.
     */
    private List<int[]> findCastExpressions(String text) {
        List<int[]> results = new ArrayList<>();
        Matcher matcher = CAST_KEYWORD_PATTERN.matcher(text);
        
        while (matcher.find()) {
            int start = matcher.start();
            int openParen = matcher.end() - 1;
            int closeParen = findMatchingParen(text, openParen);
            
            if (closeParen != -1) {
                String expr = text.substring(start, closeParen + 1);
                if (expr.toUpperCase().contains(" AS ")) {
                    results.add(new int[]{start, closeParen + 1});
                }
            }
        }
        return results;
    }

    /**
     * Removes AS aliases from inside SELECT NEW constructor expressions.
     * HQL doesn't allow AS aliases inside constructor argument lists.
     * Pattern: SELECT NEW ClassName(..., expr AS alias, ...) -> SELECT NEW
     * ClassName(..., expr, ...)
     * 
     * This handles cases like:
     * SELECT NEW DTO(SUM(...) AS discountAmount, ...) -> SELECT NEW DTO(SUM(...),
     * ...)
     */
    String removeASFromConstructorExpressions(String query) {
        // Pattern to match: SELECT NEW ... ( ... AS identifier ... )
        // Match SELECT NEW followed by qualified class name and opening parenthesis
        Matcher matcher = CONSTRUCTOR_PATTERN.matcher(query);
        if (!matcher.find()) {
            return query; // No constructor expression found
        }

        int openParenPos = matcher.end() - 1;
        int closeParenPos = findMatchingParen(query, openParenPos);
        
        if (closeParenPos == -1) {
            logger.warn("Could not find matching closing parenthesis for SELECT NEW constructor");
            return query;
        }

        // Extract the constructor argument list
        String beforeConstructor = query.substring(0, openParenPos + 1);
        String constructorArgs = query.substring(openParenPos + 1, closeParenPos);
        String afterConstructor = query.substring(closeParenPos);

        // Remove AS aliases from inside the constructor arguments
        // Pattern: AS followed by identifier
        // We need to be careful not to remove AS from CAST expressions
        // Strategy: Remove " AS identifier" but preserve "CAST(...AS type)"
        // We'll do this by first protecting CAST expressions, then removing AS, then
        // restoring CAST

        // Protect CAST expressions by replacing them with placeholders
        Map<String, String> placeholders = new HashMap<>();
        List<int[]> castPositions = findCastExpressions(constructorArgs);
        StringBuilder buffer = new StringBuilder(constructorArgs);
        
        // Replace from end to start to preserve indices
        for (int i = castPositions.size() - 1; i >= 0; i--) {
            int[] pos = castPositions.get(i);
            String castExpr = constructorArgs.substring(pos[0], pos[1]);
            String placeholder = "__CAST_" + i + "__";
            placeholders.put(placeholder, castExpr);
            buffer.replace(pos[0], pos[1], placeholder);
        }
        
        // Remove AS aliases (CAST expressions are now protected by placeholders)
        String cleanedArgs = AS_ALIAS_PATTERN.matcher(buffer.toString()).replaceAll("");
        
        // Restore CAST expressions and clean up extra commas
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            cleanedArgs = cleanedArgs.replace(entry.getKey(), entry.getValue());
        }
        cleanedArgs = cleanedArgs.replaceAll(",\\s*,", ",").replaceAll("^\\s*,|,\\s*$", "");

        logger.debug("Removed AS aliases from constructor expression. Original args length: {}, Cleaned: {}",
                constructorArgs.length(), cleanedArgs.length());

        return beforeConstructor + cleanedArgs + afterConstructor;
    }

    /**
     * Preprocesses SpEL expressions by replacing them with simple named parameters.
     * SpEL expressions like :#{#variableName} are replaced with :spel_param_N
     * 
     * @param query The original HQL query
     * @return PreprocessingResult containing the preprocessed query and mapping
     */
    SpELPreprocessingResult preprocessSpELExpressions(String query) {
        Map<String, String> spelMapping = new LinkedHashMap<>(); // original -> replacement
        Map<String, String> reverseMapping = new LinkedHashMap<>(); // replacement -> original
        StringBuffer result = new StringBuffer();
        Matcher matcher = SPEL_PATTERN.matcher(query);
        int paramIndex = 1;

        while (matcher.find()) {
            String originalSpel = matcher.group(0); // e.g., :#{#inPatientPhrSearchModel.admissionId}
            String spelContent = matcher.group(1); // e.g., #inPatientPhrSearchModel.admissionId

            // Create a simple parameter name
            // Extract a meaningful name from the SpEL content if possible
            String paramName = extractParameterName(spelContent, paramIndex);
            String replacement = ":" + paramName;

            spelMapping.put(originalSpel, replacement);
            reverseMapping.put(replacement, originalSpel);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            paramIndex++;
        }
        matcher.appendTail(result);

        logger.debug("Preprocessed {} SpEL expressions in query", spelMapping.size());
        return new SpELPreprocessingResult(result.toString(), spelMapping, reverseMapping);
    }

    /**
     * Extracts a meaningful parameter name from SpEL content.
     * For :#{#inPatientPhrSearchModel.admissionId}, extracts "admissionId"
     * For :#{#inPatientPhrSearchModel.getPayerGroupId()}, extracts "payerGroupId"
     */
    String extractParameterName(String spelContent, int index) {
        // Remove leading # if present
        if (spelContent.startsWith("#")) {
            spelContent = spelContent.substring(1);
        }

        // Try to extract the last meaningful identifier
        // For "inPatientPhrSearchModel.admissionId" -> "admissionId"
        // For "inPatientPhrSearchModel.getPayerGroupId()" -> "payerGroupId"
        String[] parts = spelContent.split("[\\.()]+");
        if (parts.length > 0) {
            String lastPart = parts[parts.length - 1];
            // Remove "get" prefix if present (e.g., "getPayerGroupId" -> "payerGroupId")
            if (lastPart.startsWith("get") && lastPart.length() > 3) {
                lastPart = Character.toLowerCase(lastPart.charAt(3)) + lastPart.substring(4);
            }
            // Use the last part if it's a valid identifier
            if (lastPart.matches("[^\\d\\W]\\w*")) {
                return lastPart;
            }
        }

        // Fallback to generic name
        return "spel_param_" + index;
    }

    /**
     * Postprocesses the SQL to restore SpEL expressions.
     * Replaces the simple parameters back with their original SpEL expressions.
     */
    String postprocessSpELExpressions(String sql, Map<String, String> reverseMapping) {
        String result = sql;
        // Replace in reverse order to avoid partial matches
        List<Map.Entry<String, String>> entries = new ArrayList<>(reverseMapping.entrySet());
        for (Map.Entry<String, String> entry : entries.reversed()) {
            String replacement = entry.getKey();
            String original = entry.getValue();
            result = result.replace(replacement, original);
        }
        return result;
    }

    /**
     * Result of SpEL preprocessing containing the preprocessed query and mappings.
     */
    static class SpELPreprocessingResult {
        final String preprocessedQuery;
        final Map<String, String> spelMapping; // original -> replacement
        final Map<String, String> reverseMapping; // replacement -> original

        SpELPreprocessingResult(String preprocessedQuery, Map<String, String> spelMapping,
                Map<String, String> reverseMapping) {
            this.preprocessedQuery = preprocessedQuery;
            this.spelMapping = spelMapping;
            this.reverseMapping = reverseMapping;
        }
    }

    /**
     * Checks if the converter supports the specified database dialect.
     *
     * @param dialect The database dialect to check
     * @return true if the dialect is supported, false otherwise
     */
    public boolean supportsDialect(DatabaseDialect dialect) {
        return supportedDialects.contains(dialect);
    }

    /**
     * Registers entity and field mappings from EntityMetadata into the hql-parser
     * converter.
     */
    private Set<String> registerMappings(MetaData analysis) {
        Set<String> tables = new HashSet<>();
        Map<String, Map<String, JoinMapping>> relationshipMetadata = new HashMap<>();

        for (String name : analysis.getEntityNames()) {
            TypeWrapper typeWrapper = AbstractCompiler.findType(cu, name);
            String fullName = null;
            if (typeWrapper == null) {
                fullName = getEntiyNameForEntity(name);
            } else {
                fullName = typeWrapper.getFullyQualifiedName();
            }

            EntityMetadata meta = EntityMappingResolver.getMapping().get(fullName);
            if (meta == null) {
                logger.warn("No metadata found for entity: {} (fullName: {})", name, fullName);
                continue;
            }

            tables.add(meta.tableName());
            sqlConverter.registerEntityMapping(name, meta.tableName());

            if (meta.propertyToColumnMap() != null) {
                for (var entry : meta.propertyToColumnMap().entrySet()) {
                    String propertyName = entry.getKey();
                    String columnName = entry.getValue();
                    sqlConverter.registerFieldMapping(
                            name,
                            propertyName,
                            columnName);
                }
            }

            // Collect relationship mappings for implicit join ON clause generation
            if (meta.relationshipMap() != null && !meta.relationshipMap().isEmpty()) {
                Map<String, JoinMapping> propertyMappings = new HashMap<>();
                for (var entry : meta.relationshipMap().entrySet()) {
                    propertyMappings.put(entry.getKey(), entry.getValue());
                    // Also register the joined entity's table mapping
                    JoinMapping joinMapping = entry.getValue();
                    sqlConverter.registerEntityMapping(
                            joinMapping.targetEntity(),
                            joinMapping.targetTable());
                    logger.debug("Registered join entity mapping: {} -> {}",
                            joinMapping.targetEntity(), joinMapping.targetTable());
                }
                // Use entity name as it appears in HQL (not FQN) as the key
                relationshipMetadata.put(name, propertyMappings);
            }
        }

        // Pass relationship metadata to converter for implicit join ON clause
        // generation
        sqlConverter.setRelationshipMetadata(relationshipMetadata);

        return tables;
    }

    String getEntiyNameForEntity(String name) {
        if (name.equals(entity.getName()) || name.equals(entity.getFullyQualifiedName())) {
            return entity.getFullyQualifiedName();
        }
        Optional<String> n = EntityMappingResolver.getFullNamesForEntity(name).stream().findFirst();
        if (n.isPresent()) {
            return n.stream().findFirst().orElseThrow();
        }

        if (entity.getClazz() == null) {
            for (FieldDeclaration f : entity.getType().getFields()) {
                for (TypeWrapper tw : AbstractCompiler.findTypesInVariable(f.getVariable(0))) {
                    if (tw.getFullyQualifiedName().equals(name) || tw.getName().equals(name)) {
                        return tw.getFullyQualifiedName();
                    }
                }
            }
        } else if (entity.getClazz().getName().equals(name)) {
            return entity.getFullyQualifiedName();
        }
        return null;
    }

    /**
     * Extracts parameter mappings from the query analysis.
     * If SpEL mapping is provided, maps the preprocessed parameter names back to
     * original SpEL expressions.
     */
    private List<ParameterMapping> extractParameterMappings(MetaData analysis,
            Map<String, String> spelMapping) {
        List<ParameterMapping> mappings = new ArrayList<>();

        // hql-parser provides parameter names from the preprocessed query
        Set<String> parameters = analysis.getParameters();

        // Build reverse lookup: replacement parameter -> original SpEL
        Map<String, String> replacementToOriginal = new HashMap<>();
        if (spelMapping != null) {
            for (Map.Entry<String, String> entry : spelMapping.entrySet()) {
                String original = entry.getKey(); // e.g., :#{#inPatientPhrSearchModel.admissionId}
                String replacement = entry.getValue(); // e.g., :admissionId
                // Remove the leading colon for lookup
                String replacementKey = replacement.startsWith(":") ? replacement.substring(1) : replacement;
                replacementToOriginal.put(replacementKey, original);
            }
        }

        int position = 1;
        for (String paramName : parameters) {
            // Check if this parameter was a SpEL expression replacement
            String originalName = replacementToOriginal.getOrDefault(paramName, ":" + paramName);

            // ParameterMapping requires: originalName, position, type, columnName
            ParameterMapping mapping = new ParameterMapping(originalName, position++, Object.class, null);
            mappings.add(mapping);
        }

        return mappings;
    }
}
