package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMappingResolver;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMetadata;
import sa.com.cloudsolutions.antikythera.parser.converter.JpaQueryConverter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseRepositoryParser extends AbstractCompiler {
    protected static final Logger logger = LoggerFactory.getLogger(BaseRepositoryParser.class);

    public static final String JPA_REPOSITORY = "JpaRepository";
    public static final String SELECT_STAR = "SELECT * FROM ";
    protected static final Pattern CAMEL_TO_SNAKE_PATTERN = Pattern.compile("([a-z])([A-Z]+)");

    protected static final String ORACLE = "oracle";
    protected static final String POSTGRESQL = "PG";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\?");
    /**
     * SQL dialect, at the moment oracle or postgresql as identified from the connection url
     */
    protected static String dialect;
    /**
     * Cache for conversion results to avoid re-converting the same queries.
     * Key is generated from query string and entity metadata.
     */
    private final Map<String, ConversionResult> conversionCache = new HashMap<>();

    /**
     * The JPA query converter for converting non-native queries to SQL
     */
    protected JpaQueryConverter queryConverter;

    /**
     * Entity mapping resolver for extracting metadata from JPA annotations
     */
    protected EntityMappingResolver entityMappingResolver;

    /**
     * The compilation unit or class associated with this entity.
     */
    TypeWrapper entity;

    /**
     * The table name associated with the entity
     */
    String table;
    /**
     * The java parser type associated with the entity.
     */
    Type entityType;

    /**
     * The queries that were identified in this repository
     */
    protected Map<Callable, RepositoryQuery> queries = new HashMap<>();

    protected static final Pattern KEYWORDS_PATTERN = Pattern.compile(
            "get|findBy|findFirstBy|findTopBy|And|OrderBy|NotIn|In|Desc|IsNotNull|IsNull|Not|Containing|Like|Or|Between|LessThanEqual|GreaterThanEqual|GreaterThan|LessThan"
    );

    public BaseRepositoryParser() throws IOException {
        super();
    }

    public static BaseRepositoryParser create(CompilationUnit cu) throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        parser.cu = cu;
        return parser;
    }


    public RepositoryQuery getQueryFromRepositoryMethod(Callable repoMethod) {
        RepositoryQuery q = queries.get(repoMethod);
        if (q == null) {
            if (repoMethod.isMethodDeclaration()) {
                queryFromMethodDeclaration(repoMethod.asMethodDeclaration());
            }
            else {
                parseNonAnnotatedMethod(repoMethod);
            }
            q = queries.get(repoMethod);
        }
        return q;
    }

    void queryFromMethodDeclaration(MethodDeclaration n) {
        String query = null;
        boolean nt = false;
        AnnotationExpr ann = n.getAnnotationByName("Query").orElse(null);

        if (ann != null && ann.isSingleMemberAnnotationExpr()) {
            try {
                Evaluator eval = EvaluatorFactory.create(className, SpringEvaluator.class);
                Variable v = eval.evaluateExpression(
                        ann.asSingleMemberAnnotationExpr().getMemberValue()
                );
                query = v.getValue().toString();
            } catch (ReflectiveOperationException e) {
                throw new AntikytheraException(e);
            }
        } else if (ann != null && ann.isNormalAnnotationExpr()) {

            for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("nativeQuery") && pair.getValue().toString().equals("true")) {
                    nt = true;
                }
            }
            for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("value")) {
                    query = pair.getValue().toString();
                }
            }
        }
        Callable callable = new Callable(n, null);
        if (query != null) {
            queries.put(callable, queryBuilder(query, nt, callable));
        } else {
            parseNonAnnotatedMethod(callable);
        }
    }

    /**
     * Parse a repository method that does not have a query annotation.
     * In these cases the naming convention of the method is used to infer the query.
     * @param md the method declaration
     */
    void parseNonAnnotatedMethod(Callable md) {
        String methodName = md.getNameAsString();
        List<String> components = extractComponents(methodName);
        StringBuilder sql = new StringBuilder();
        boolean top = false;
        boolean ordering = false;
        String next = "";

        String tableName = findTableName(entity);
        if (tableName != null) {
            for (int i = 0; i < components.size(); i++) {
                String component = components.get(i);

                if (i < components.size() - 1) {
                    next = components.get(i + 1);
                } else {
                    next = "";
                }

                switch (component) {
                    case "findAll" -> sql.append(SELECT_STAR).append(tableName.replace("\"", ""));
                    case "findAllById" -> sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE id = ?");
                    case "findBy", "get" -> sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE ");
                    case "findFirstBy", "findTopBy" -> {
                        top = true;
                        sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE ");
                    }
                    case "Between" -> sql.append(" BETWEEN ? AND ? ");
                    case "GreaterThan" -> sql.append(" > ? ");
                    case "LessThan" -> sql.append(" < ? ");
                    case "GreaterThanEqual" -> sql.append(" >= ? ");
                    case "LessThanEqual" -> sql.append(" <= ? ");
                    case "IsNull" -> sql.append(" IS NULL ");
                    case "IsNotNull" -> sql.append(" IS NOT NULL ");
                    case "And", "Or", "Not" -> sql.append(component).append(" ");
                    case "Containing", "Like" -> sql.append(" LIKE ? ");
                    case "OrderBy" -> {
                        ordering = true;
                        sql.append(" ORDER BY ");
                    }
                    default -> {
                        sql.append(camelToSnake(component));
                        if (!ordering) {
                            if (next.equals("In")) {
                                sql.append(" In  (?) ");
                                i++;
                            } else if (next.equals("NotIn")) {
                                sql.append(" NOT In (?) ");
                                i++;
                            } else {
                                // Add = ? if next is empty (last component) or next is not a special operator
                                if (next.isEmpty() || (!next.equals("Between") && !next.equals("GreaterThan")
                                        && !next.equals("LessThan") && !next.equals("LessThanEqual")
                                        && !next.equals("IsNotNull") && !next.equals("Like")
                                        && !next.equals("GreaterThanEqual") && !next.equals("IsNull")
                                        && !next.equals("Containing"))) {
                                    sql.append(" = ? ");
                                }
                            }
                        } else {
                            sql.append(" ");
                        }
                    }
                }
            }
        } else {
            logger.warn("Table name cannot be null");
        }

        if (top) {
            if (ORACLE.equals(dialect)) {
                sql.append(" AND ROWNUM = 1");
            } else {
                sql.append(" LIMIT 1");
            }
        }

        StringBuilder result = new StringBuilder();
        for(int i = 0, j = 1 ; i < sql.length() ; i++) {
            char c = sql.charAt(i);
            if(c == '?') {
                result.append('?').append(j++);
            }
            else {
                result.append(c);
            }
        }
        queries.put(md, queryBuilder(result.toString(), true, md));
    }

    /**
     * Recursively search method names for sql components
     * @param methodName name of the method
     * @return a list of components
     */
    List<String> extractComponents(String methodName) {
        List<String> components = new ArrayList<>();
        Matcher matcher = KEYWORDS_PATTERN.matcher(methodName);

        // Add spaces around each keyword
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, " " + matcher.group() + " ");
        }
        matcher.appendTail(sb);

        // Split the modified method name by spaces
        String[] parts = sb.toString().split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                components.add(part);
            }
        }

        return components;
    }


    /**
     * Count the number of parameters to bind.
     *
     * @param sql the sql statement as a string in which we will count the number of placeholders
     * @return the number of placeholders. This can be 0
     */
    static int countPlaceholders(String sql) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static boolean isOracle() {
        return ORACLE.equals(dialect);
    }

    /**
     * Checks if query conversion is enabled in the configuration.
     *
     * @return true if query conversion is enabled, false otherwise
     */
    boolean isQueryConversionEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("enabled", "false").toString());
            }
        }
        return false; // Default to disabled
    }

    /**
     * Build a repository query object
     * @param query the query
     * @param isNative will be true if an annotation says a native query
     * @return a repository query instance.
     */
    RepositoryQuery queryBuilder(String query, boolean isNative, Callable md) {
        RepositoryQuery rql = new RepositoryQuery();
        rql.setMethodDeclaration(md);
        rql.setEntityType(entityType);
        rql.setPrimaryTable(table);
        rql.setRepositoryClassName(className);

        // Use the new converter for non-native queries if enabled
        if (!isNative && isQueryConversionEnabled()) {
            try {
                EntityMetadata entityMetadata = buildEntityMetadata();
                DatabaseDialect targetDialect = detectDatabaseDialect();

                // Check cache first
                String cacheKey = generateCacheKey(query, entityMetadata, targetDialect);
                ConversionResult result = getCachedConversionResult(cacheKey);

                if (result == null) {
                    // Not in cache, perform conversion
                    result = queryConverter.convertToNativeSQL(query, entityMetadata, targetDialect);

                    // Cache the result (both successful and failed results)
                    cacheConversionResult(cacheKey, result);
                } else {
                    logger.debug("Using cached conversion result for query");
                }

                if (result.isSuccessful()) {
                    logger.debug("Successfully converted JPA query to native SQL: {}", result.getNativeSql());
                    rql.setQuery(result.getNativeSql());
                    rql.setIsNative(true); // Mark as native after successful conversion
                } else {
                    logger.debug("Falling back to existing logic for query conversion failure");
                    rql.setQuery(query);
                    rql.setIsNative(isNative);
                }
            } catch (Exception e) {
                if (isConversionFailureLoggingEnabled()) {
                    logger.warn("Exception during query conversion: {}. Falling back to existing logic.", e.getMessage());
                }
                rql.setQuery(query);
                rql.setIsNative(isNative);
            }
        } else {
            // Use original query for native queries or when conversion is disabled
            rql.setQuery(query);
            rql.setIsNative(isNative);
        }

        return rql;
    }

    /**
     * Builds entity metadata for the current entity being processed.
     *
     * @return EntityMetadata containing mapping information for the entity
     */
    private EntityMetadata buildEntityMetadata() {
        if (entity != null && entity.getClazz() != null) {
            return entityMappingResolver.resolveEntityMetadata(entity.getClazz());
        }
        return EntityMetadata.empty(); // Return empty metadata if no entity context
    }

    /**
     * Detects the database dialect from the current configuration.
     *
     * @return DatabaseDialect enum value for the configured database
     */
    private DatabaseDialect detectDatabaseDialect() {
        if (ORACLE.equals(dialect)) {
            return DatabaseDialect.ORACLE;
        } else if (POSTGRESQL.equals(dialect)) {
            return DatabaseDialect.POSTGRESQL;
        }
        return DatabaseDialect.POSTGRESQL; // Default to PostgreSQL
    }


    /**
     * Generates a cache key for the given query and entity metadata.
     *
     * @param query The JPA query string
     * @param entityMetadata The entity metadata
     * @param dialect The database dialect
     * @return A unique cache key string
     */
    String generateCacheKey(String query, EntityMetadata entityMetadata, DatabaseDialect dialect) {
        // Create a simple hash-based key combining query, entity info, and dialect
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(query.trim().replaceAll("\\s+", " ")); // Normalize whitespace
        keyBuilder.append("|");
        keyBuilder.append(entityMetadata.hashCode());
        keyBuilder.append("|");
        keyBuilder.append(dialect.name());

        return String.valueOf(keyBuilder.toString().hashCode());
    }

    /**
     * Gets a cached conversion result if available.
     *
     * @param cacheKey The cache key
     * @return The cached ConversionResult, or null if not found
     */
    ConversionResult getCachedConversionResult(String cacheKey) {
        if (isCachingEnabled()) {
            return conversionCache.get(cacheKey);
        }
        return null;
    }

    /**
     * Caches a conversion result.
     *
     * @param cacheKey The cache key
     * @param result The conversion result to cache
     */
    void cacheConversionResult(String cacheKey, ConversionResult result) {
        if (isCachingEnabled()) {
            conversionCache.put(cacheKey, result);
            logger.debug("Cached conversion result for key: {}", cacheKey);
        }
    }

    /**
     * Clears the conversion cache. Useful for testing or when entity metadata changes.
     */
    public void clearConversionCache() {
        conversionCache.clear();
        logger.debug("Conversion cache cleared");
    }

    /**
     * Gets all the repository queries that have been built.
     * 
     * @return a collection of all RepositoryQuery objects
     */
    public Collection<RepositoryQuery> getAllQueries() {
        return queries.values();
    }


    /**
     * Checks if fallback to existing logic is enabled on conversion failure.
     *
     * @return true if fallback is enabled, false otherwise
     */
    boolean isFallbackOnFailureEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("fallback_on_failure", "true").toString());
            }
        }
        return true; // Default to enabled for safety
    }

    /**
     * Checks if conversion failure logging is enabled.
     *
     * @return true if logging conversion failures is enabled, false otherwise
     */
    boolean isConversionFailureLoggingEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("log_conversion_failures", "true").toString());
            }
        }
        return true; // Default to enabled
    }

    /**
     * Checks if conversion result caching is enabled.
     *
     * @return true if caching is enabled, false otherwise
     */
    boolean isCachingEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("cache_results", "true").toString());
            }
        }
        return true; // Default to enabled
    }


    /**
     * Find the table name from the hibernate entity.
     * Usually the entity will have an annotation giving the actual name of the table.
     *
     * This method is made static because when processing joins there are multiple entities
     * and there by multiple table names involved.
     *
     * @param entity a TypeWrapper representing the entity
     * @return the table name as a string.
     */
    public static String findTableName(TypeWrapper entity) {
        String table = null;
        if(entity != null) {
            if (entity.getType() != null) {
                return getNameFromType(entity, table);
            }
            else if (entity.getClazz() != null){
                Class<?> cls = entity.getClazz();
                for (Annotation ann : cls.getAnnotations()) {
                    if (ann instanceof javax.persistence.Table t) {
                        table = t.name();
                    }
                }
            }
        }
        else {
            logger.warn("Compilation unit is null");
        }
        return table;
    }

    static String getNameFromType(TypeWrapper entity, String table) {
        Optional<AnnotationExpr> ann = entity.getType().getAnnotationByName("Table");
        if (ann.isPresent()) {
            if (ann.get().isNormalAnnotationExpr()) {
                for (var pair : ann.get().asNormalAnnotationExpr().getPairs()) {
                    if (pair.getNameAsString().equals("name")) {
                        table = pair.getValue().toString().replace("\"", "");
                    }
                }
            } else {
                table = ann.get().asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
            }
            return table;
        } else {
            return camelToSnake(entity.getType().getNameAsString());
        }
    }

    /**
     * Find and parse the given entity.
     *
     * @param fd FieldDeclaration for which we need to find the compilation unit
     * @return a compilation unit
     */
    public static TypeWrapper findEntity(Type fd) {
        Optional<CompilationUnit> cu = fd.findCompilationUnit();
        if (cu.isPresent()) {
            for (ImportWrapper wrapper : AbstractCompiler.findImport(cu.get(), fd)) {
                if (wrapper.getType() != null) {
                    return new TypeWrapper(wrapper.getType());
                }
                else if(!wrapper.getImport().getNameAsString().startsWith("java.util")) {
                    try {
                        Class<?> cls = AbstractCompiler.loadClass(wrapper.getImport().getNameAsString());
                        return new TypeWrapper(cls);

                    } catch (ClassNotFoundException e) {
                        // can be ignored, we are trying to check for the existence of the class
                        logger.debug(e.getMessage());
                    }
                }
            }
            return new TypeWrapper(AbstractCompiler.findInSamePackage(cu.get(), fd).orElse(null));
        }
        return null;
    }

    /**
     * Converts the fields in an Entity to snake case which is the usual pattern for columns
     * @param str A camel cased variable
     * @return a snake cased variable
     */
    public static String camelToSnake(String str) {
        return CAMEL_TO_SNAKE_PATTERN.matcher(str).replaceAll("$1_$2").toLowerCase();
    }
    public void buildQueries() {
        if (cu != null && entity != null) {
            cu.accept(new Visitor(), null);
        }
    }

    /**
     * Visitor to iterate through the methods in the repository
     */
    class Visitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            queryFromMethodDeclaration(n);
        }
    }

    /**
     * Process the CompilationUnit to identify all the queries.
     */
    public void processTypes()  {
        for(var tp : cu.getTypes()) {
            if(tp.isClassOrInterfaceDeclaration()) {
                var cls = tp.asClassOrInterfaceDeclaration();

                for(var parent : cls.getExtendedTypes()) {
                    if (parent.toString().startsWith(JPA_REPOSITORY)) {

                        parent.getTypeArguments().ifPresent(t -> {
                            entityType = t.getFirst().orElseThrow();
                            entity = findEntity(entityType);
                            table = findTableName(entity);
                        });
                    }
                }
            }
        }
    }

}
