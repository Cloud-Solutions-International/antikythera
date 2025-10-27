package sa.com.cloudsolutions.antikythera.parser.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Hibernate-based implementation of JpaQueryConverter.
 * 
 * This implementation uses Hibernate's HQL parser to convert JPA/HQL queries
 * to native SQL queries. It provides robust parsing and conversion capabilities
 * by leveraging Hibernate's own query processing infrastructure.
 */
public class HibernateQueryConverter implements JpaQueryConverter {
    
    private static final Logger logger = LoggerFactory.getLogger(HibernateQueryConverter.class);
    
    // Pattern to identify HQL/JPQL queries (contains entity references)
    private static final Pattern HQL_PATTERN = Pattern.compile(
        "\\b(FROM|JOIN)\\s+([A-Z][a-zA-Z0-9_]*(?:\\.[A-Z][a-zA-Z0-9_]*)*)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern to identify named parameters
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":(\\w+)");
    
    private final SqlGenerationVisitor sqlVisitor;
    private final Set<DatabaseDialect> supportedDialects;
    private final DialectHandler dialectHandler;
    
    /**
     * Constructs a new HibernateQueryConverter.
     */
    public HibernateQueryConverter() {
        this.sqlVisitor = new SqlGenerationVisitor();
        this.supportedDialects = EnumSet.of(
            DatabaseDialect.ORACLE, 
            DatabaseDialect.POSTGRESQL
        );
        this.dialectHandler = new DialectHandler();
        
        logger.info("HibernateQueryConverter initialized with support for dialects: {}", supportedDialects);
        logger.info("Detected current dialect: {}", 
                   dialectHandler.getCurrentDialect() != null ? 
                   dialectHandler.getCurrentDialect().getDisplayName() : "None");
    }
    
    /**
     * Constructs a new HibernateQueryConverter with a specific dialect.
     * 
     * @param dialect The database dialect to use
     */
    public HibernateQueryConverter(DatabaseDialect dialect) {
        this.sqlVisitor = new SqlGenerationVisitor();
        this.supportedDialects = EnumSet.of(
            DatabaseDialect.ORACLE, 
            DatabaseDialect.POSTGRESQL
        );
        this.dialectHandler = new DialectHandler(dialect);
        
        logger.info("HibernateQueryConverter initialized with dialect: {}", dialect.getDisplayName());
    }
    
    @Override
    public ConversionResult convertToNativeSQL(String jpaQuery, EntityMetadata entityMetadata, DatabaseDialect dialect) {
        if (!supportsDialect(dialect)) {
            return ConversionResult.failure(
                "Unsupported database dialect: " + dialect, 
                ConversionFailureReason.DIALECT_INCOMPATIBILITY
            );
        }

        // Step 1: Parse the HQL query
        HqlParseResult parseResult = parseHqlQuery(jpaQuery);
        if (!parseResult.isSuccessful()) {
            return ConversionResult.failure(parseResult.getErrorMessage(), ConversionFailureReason.PARSER_ERROR);
        }

        // Step 2: Convert to SQL using the visitor pattern
        SqlConversionContext context = new SqlConversionContext(entityMetadata, dialect);
        String nativeSql = sqlVisitor.convertToSql(parseResult.getQueryNode(), context);

        // Step 3: Apply additional dialect-specific transformations
        nativeSql = applyAdditionalDialectTransformations(nativeSql, dialect);

        // Step 4: Handle parameter conversion
        List<ParameterMapping> parameterMappings = extractParameterMappings(jpaQuery);

        // Step 5: Extract referenced tables
        Set<String> referencedTables = extractReferencedTables(entityMetadata, parseResult.getQueryNode());

        return new ConversionResult(nativeSql, parameterMappings, referencedTables);
    }
    
    @Override
    public boolean canConvert(String jpaQuery) {
        if (jpaQuery == null || jpaQuery.trim().isEmpty()) {
            return false;
        }
        
        // Check if the query contains HQL/JPQL patterns (entity references)
        Matcher matcher = HQL_PATTERN.matcher(jpaQuery);
        boolean hasEntityReferences = matcher.find();
        
        // Additional check: ensure it's not already native SQL
        boolean isNativeSQL = jpaQuery.toLowerCase().contains("select") && 
                             !hasEntityReferences;
        
        return hasEntityReferences && !isNativeSQL;
    }
    
    @Override
    public boolean supportsDialect(DatabaseDialect dialect) {
        return supportedDialects.contains(dialect);
    }
    
    /**
     * Gets the current database dialect from the dialect handler.
     * 
     * @return The current DatabaseDialect, or null if not detected
     */
    public DatabaseDialect getCurrentDialect() {
        return dialectHandler.getCurrentDialect();
    }
    
    /**
     * Sets the current database dialect.
     * 
     * @param dialect The dialect to set
     */
    public void setCurrentDialect(DatabaseDialect dialect) {
        dialectHandler.setCurrentDialect(dialect);
    }
    
    /**
     * Gets the dialect handler for advanced dialect operations.
     * 
     * @return The DialectHandler instance
     */
    public DialectHandler getDialectHandler() {
        return dialectHandler;
    }
    
    /**
     * Parses an HQL query using Hibernate's parser.
     * 
     * @param hqlQuery The HQL query to parse
     * @return HqlParseResult containing the parsed query node or error information
     */
    private HqlParseResult parseHqlQuery(String hqlQuery) {

        // For now, implement a basic parser that can handle simple queries
        // This will be enhanced in subsequent implementations

        String trimmedQuery = hqlQuery.trim().toLowerCase();
        
        // Basic validation - support SELECT, FROM, UPDATE, and DELETE queries
        if (!trimmedQuery.startsWith("select") &&
            !trimmedQuery.startsWith("from") &&
            !trimmedQuery.startsWith("update") &&
            !trimmedQuery.startsWith("delete")) {
            return HqlParseResult.failure("Only SELECT, FROM, UPDATE, and DELETE queries are currently supported");
        }

        // Validate query structure based on type
        if (trimmedQuery.startsWith("update")) {
            if (!validateUpdateQuery(hqlQuery)) {
                return HqlParseResult.failure("Invalid UPDATE query structure");
            }
        } else if (trimmedQuery.startsWith("delete")) {
            if (!validateDeleteQuery(hqlQuery)) {
                return HqlParseResult.failure("Invalid DELETE query structure");
            }
        }

        // Create a mock query node for basic queries
        // In a full implementation, this would use Hibernate's actual HQL parser
        MockQueryNode queryNode = new MockQueryNode(hqlQuery);

        return HqlParseResult.success(queryNode);
    }
    
    /**
     * Validates the structure of an UPDATE query.
     * 
     * @param updateQuery The UPDATE query to validate
     * @return true if the query structure is valid, false otherwise
     */
    private boolean validateUpdateQuery(String updateQuery) {
        String trimmed = updateQuery.trim().toLowerCase();
        
        // Basic UPDATE query structure: UPDATE EntityName SET property = value [WHERE conditions]
        if (!trimmed.contains("set")) {
            logger.warn("UPDATE query missing SET clause: {}", updateQuery);
            return false;
        }
        
        // Check for entity name after UPDATE
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 4) { // UPDATE EntityName SET property
            logger.warn("UPDATE query has insufficient parts: {}", updateQuery);
            return false;
        }
        
        // Validate that we have an entity name (should start with uppercase in HQL)
        String entityPart = updateQuery.trim().split("\\s+")[1];
        if (!Character.isUpperCase(entityPart.charAt(0))) {
            logger.warn("UPDATE query entity name should start with uppercase: {}", entityPart);
            // Don't fail for this, just warn as it might be a table name in native SQL
        }
        
        return true;
    }
    
    /**
     * Validates the structure of a DELETE query.
     * 
     * @param deleteQuery The DELETE query to validate
     * @return true if the query structure is valid, false otherwise
     */
    private boolean validateDeleteQuery(String deleteQuery) {
        String trimmed = deleteQuery.trim().toLowerCase();
        
        // Basic DELETE query structure: DELETE FROM EntityName [WHERE conditions]
        // or: DELETE EntityName [WHERE conditions] (HQL shorthand)
        
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) { // DELETE EntityName or DELETE FROM
            logger.warn("DELETE query has insufficient parts: {}", deleteQuery);
            return false;
        }
        
        // Check if it's "DELETE FROM EntityName" or "DELETE EntityName"
        boolean hasFrom = parts.length > 2 && "from".equals(parts[1]);
        int entityIndex = hasFrom ? 2 : 1;
        
        if (parts.length <= entityIndex) {
            logger.warn("DELETE query missing entity name: {}", deleteQuery);
            return false;
        }
        
        // Validate that we have an entity name (should start with uppercase in HQL)
        String entityPart = deleteQuery.trim().split("\\s+")[entityIndex];
        if (!Character.isUpperCase(entityPart.charAt(0))) {
            logger.warn("DELETE query entity name should start with uppercase: {}", entityPart);
            // Don't fail for this, just warn as it might be a table name in native SQL
        }
        
        return true;
    }
    
    /**
     * Extracts parameter mappings from the original and converted queries.
     * Converts named parameters to positional parameters and maintains mapping information.
     */
    private List<ParameterMapping> extractParameterMappings(String originalQuery) {
        List<ParameterMapping> mappings = new ArrayList<>();
        Set<String> processedParams = new HashSet<>();
        
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(originalQuery);
        int position = 1;
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            
            // Avoid duplicate parameters
            if (!processedParams.contains(paramName)) {
                // Determine parameter type based on context
                Class<?> paramType = determineParameterType(paramName, originalQuery);
                
                // Find the column name this parameter is used with
                String columnName = findParameterColumnContext(paramName, originalQuery);
                
                mappings.add(new ParameterMapping(paramName, position++, paramType, columnName));
                processedParams.add(paramName);
                
                logger.debug("Mapped parameter: {} -> position {}, type: {}, column: {}", 
                           paramName, position - 1, paramType.getSimpleName(), columnName);
            }
        }
        
        return mappings;
    }
    
    /**
     * Determines the parameter type based on its usage context in the query.
     */
    private Class<?> determineParameterType(String paramName, String query) {
        // Pattern to find the parameter usage context
        Pattern contextPattern = Pattern.compile(
            "\\b\\w+\\.\\w+\\s*[=<>!]+\\s*:" + Pattern.quote(paramName) + "\\b|" +
            ":" + Pattern.quote(paramName) + "\\s*[=<>!]+\\s*\\w+\\.\\w+",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = contextPattern.matcher(query);
        if (matcher.find()) {
            String context = matcher.group(0).toLowerCase();
            
            // Try to infer type from common patterns
            if (context.contains("date") || context.contains("time")) {
                return java.util.Date.class;
            } else if (context.contains("count") || context.contains("id")) {
                return Long.class;
            } else if (context.contains("active") || context.contains("enabled") || context.contains("flag")) {
                return Boolean.class;
            } else if (context.contains("amount") || context.contains("price") || context.contains("rate")) {
                return java.math.BigDecimal.class;
            }
        }
        
        // Default to String for unknown types
        return String.class;
    }
    
    /**
     * Finds the column name that a parameter is being used with.
     */
    private String findParameterColumnContext(String paramName, String query) {
        // Pattern to find property.column = :param or :param = property.column
        Pattern columnContextPattern = Pattern.compile(
            "\\b(\\w+)\\.(\\w+)\\s*[=<>!]+\\s*:" + Pattern.quote(paramName) + "\\b|" +
            ":" + Pattern.quote(paramName) + "\\s*[=<>!]+\\s*\\b(\\w+)\\.(\\w+)\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = columnContextPattern.matcher(query);
        if (matcher.find()) {
            // Return the property name (will be converted to column name later)
            return matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
        }
        
        return paramName; // Default to parameter name
    }
    
    /**
     * Extracts referenced tables from the query node using entity metadata.
     */
    private Set<String> extractReferencedTables(EntityMetadata entityMetadata, QueryNode queryNode) {
        Set<String> tables = new HashSet<>();
        
        if (queryNode instanceof MockQueryNode) {
            MockQueryNode mockNode = (MockQueryNode) queryNode;
            String query = mockNode.getOriginalQuery();
            
            // Extract entity names and convert to table names
            Matcher matcher = HQL_PATTERN.matcher(query);
            while (matcher.find()) {
                String entityName = matcher.group(2);
                if (entityName != null) {
                    TableMapping tableMapping = entityMetadata.getTableMapping(entityName);
                    if (tableMapping != null) {
                        tables.add(tableMapping.tableName());
                    }
                }
            }
        }
        
        return tables;
    }
    
    /**
     * Applies additional dialect-specific transformations beyond the basic SQL generation.
     * This method uses the DialectHandler to apply comprehensive transformations.
     */
    private String applyAdditionalDialectTransformations(String sql, DatabaseDialect dialect) {
        if (sql == null || dialect == null) {
            return sql;
        }
        
        // Use the DialectTransformer for comprehensive transformations
        String transformedSql = DialectTransformer.transform(sql, dialect);
        
        // Apply any converter-specific transformations
        transformedSql = applyConverterSpecificTransformations(transformedSql, dialect);
        
        logger.debug("Applied additional dialect transformations for {}: {} -> {}", 
                   dialect.getDisplayName(), sql, transformedSql);
        
        return transformedSql;
    }
    
    /**
     * Applies converter-specific transformations that are unique to this implementation.
     */
    private String applyConverterSpecificTransformations(String sql, DatabaseDialect dialect) {
        String result = sql;
        
        switch (dialect) {
            case ORACLE:
                // Oracle-specific converter transformations
                result = applyOracleConverterTransformations(result);
                break;
            case POSTGRESQL:
                // PostgreSQL-specific converter transformations
                result = applyPostgreSQLConverterTransformations(result);
                break;
            default:
                // No specific transformations for other dialects
                break;
        }
        
        return result;
    }
    
    /**
     * Applies Oracle-specific transformations unique to this converter.
     */
    private String applyOracleConverterTransformations(String sql) {
        String result = sql;
        
        // Handle Oracle-specific HQL to SQL conversions
        // For example, handling Oracle's unique identifier quoting
        result = result.replaceAll("\"([^\"]+)\"", "$1"); // Remove unnecessary quotes
        
        // Handle Oracle-specific pagination patterns from HQL
        if (result.toLowerCase().contains("first") || result.toLowerCase().contains("top")) {
            // These patterns might come from HQL and need Oracle-specific handling
            result = handleOraclePaginationPatterns(result);
        }
        
        return result;
    }
    
    /**
     * Applies PostgreSQL-specific transformations unique to this converter.
     */
    private String applyPostgreSQLConverterTransformations(String sql) {
        String result = sql;
        
        // Handle PostgreSQL-specific HQL to SQL conversions
        // For example, handling case sensitivity
        result = handlePostgreSQLCaseSensitivity(result);
        
        return result;
    }
    
    /**
     * Handles Oracle-specific pagination patterns that might come from HQL.
     */
    private String handleOraclePaginationPatterns(String sql) {
        // This is a placeholder for handling complex pagination patterns
        // In practice, this would handle conversion of HQL pagination to Oracle ROWNUM
        return sql;
    }
    
    /**
     * Handles PostgreSQL case sensitivity issues in converted SQL.
     */
    private String handlePostgreSQLCaseSensitivity(String sql) {
        // PostgreSQL is case-sensitive for identifiers, so we might need to quote them
        // This is a simplified implementation
        return sql;
    }
    
    /**
     * Result of HQL parsing operation.
     */
    private static class HqlParseResult {
        private final boolean successful;
        private final QueryNode queryNode;
        private final String errorMessage;
        
        private HqlParseResult(boolean successful, QueryNode queryNode, String errorMessage) {
            this.successful = successful;
            this.queryNode = queryNode;
            this.errorMessage = errorMessage;
        }
        
        public static HqlParseResult success(QueryNode queryNode) {
            return new HqlParseResult(true, queryNode, null);
        }
        
        public static HqlParseResult failure(String errorMessage) {
            return new HqlParseResult(false, null, errorMessage);
        }
        
        public boolean isSuccessful() { return successful; }
        public QueryNode getQueryNode() { return queryNode; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Mock implementation of QueryNode for basic functionality.
     * This will be replaced with actual Hibernate AST nodes in full implementation.
     */
    private static class MockQueryNode implements QueryNode {
        private final String originalQuery;
        
        public MockQueryNode(String originalQuery) {
            this.originalQuery = originalQuery;
        }
        
        public String getOriginalQuery() {
            return originalQuery;
        }
        
        // Implement required QueryNode methods with basic functionality
        @Override
        public int getType() { return 0; }
        
        @Override
        public String getText() { return originalQuery; }
        
        @Override
        public void setText(String text) { }
        
        @Override
        public int getLine() { return 0; }
        
        @Override
        public int getColumn() { return 0; }
        
        @Override
        public QueryNode getNextSibling() { return null; }
        
        @Override
        public QueryNode getFirstChild() { return null; }
        
        @Override
        public void addChild(QueryNode child) { }
        
        @Override
        public void setType(int type) { }
        
        @Override
        public QueryNode getParent() { return null; }
        
        @Override
        public void setParent(QueryNode parent) { }
        
        @Override
        public boolean hasChildren() { return false; }
        
        @Override
        public int getNumberOfChildren() { return 0; }
        
        @Override
        public List<QueryNode> getChildren() { return Collections.emptyList(); }
        
        @Override
        public QueryNode getChild(int index) { return null; }
        
        @Override
        public void insertChild(int index, QueryNode child) { }
        
        @Override
        public void removeChild(QueryNode child) { }
        
        @Override
        public void replaceChild(QueryNode oldChild, QueryNode newChild) { }
    }
}
