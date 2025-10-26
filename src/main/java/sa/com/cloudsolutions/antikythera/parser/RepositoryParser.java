package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.expr.AnnotationExpr;
import net.sf.jsqlparser.JSQLParserException;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodParameter;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.converter.JpaQueryConverter;
import sa.com.cloudsolutions.antikythera.parser.converter.HibernateQueryConverter;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMetadata;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMappingResolver;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import net.sf.jsqlparser.statement.select.Select;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses JPA Repository subclasses to identify the queries that they execute.
 *
 * These queries can then be used to determine what kind of data need to be sent
 * to the controller for a valid response.
 */
public class RepositoryParser extends AbstractCompiler {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryParser.class);
    public static final String JPA_REPOSITORY = "JpaRepository";
    public static final String SELECT_STAR = "SELECT * FROM ";
    private static final Pattern CAMEL_TO_SNAKE_PATTERN = Pattern.compile("([a-z])([A-Z]+)");

    /**
     * The queries that were identified in this repository
     */
    final Map<Callable, RepositoryQuery> queries;
    /**
     * The connection to the database established using the credentials in the configuration
     */
    private static Connection conn;
    /**
     * SQL dialect, at the moment oracle or postgresql as identified from the connection url
     */
    private static String dialect;
    private static final String ORACLE = "oracle";
    private static final String POSTGRESQL = "PG";
    /**
     * Whether queries should actually be executed or not.
     * As determined by the configurations
     */
    private static boolean runQueries;
    
    /**
     * The JPA query converter for converting non-native queries to SQL
     */
    private final JpaQueryConverter queryConverter;
    
    /**
     * Entity mapping resolver for extracting metadata from JPA annotations
     */
    private final EntityMappingResolver entityMappingResolver;

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
     * A query cache.
     * Since we execute the same lines of code repeatedly in order to generate tests to cover
     * different branches, we will end up executing the same query over and over again. This is
     * wasteful in terms of both time and money! So we will cache the result sets here.
     */
    private final Map<Callable, ResultSet> cache = new HashMap<>();

    /**
     * A cache for the simplified queries.
     */
    private final Map<MethodDeclaration, ResultSet> happyCache = new HashMap<>();
    
    /**
     * Cache for conversion results to avoid re-converting the same queries.
     * Key is generated from query string and entity metadata.
     */
    private final Map<String, ConversionResult> conversionCache = new HashMap<>();

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\?");

    public RepositoryParser() throws IOException {
        super();
        queries = new HashMap<>();
        
        // Initialize the query converter and entity mapping resolver
        this.entityMappingResolver = new EntityMappingResolver();
        this.queryConverter = new HibernateQueryConverter();

        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if(db != null) {
            runQueries = db.getOrDefault("run_queries", "false").toString().equals("true");
            Object urlObj = db.get("url");
            if (urlObj != null) {
                String url = urlObj.toString();
                if(url.contains(ORACLE)) {
                    dialect = ORACLE;
                }
                else {
                    dialect = POSTGRESQL;
                }
            }
        }
    }

    /**
     * Create a connection to the database.
     *
     * The connection will be shared among all instances of this class provided that the
     * runQueries configuration is switched on.
     *
     * @throws SQLException if the connection could not be established
     */
    static void createConnection() throws SQLException {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if(db != null && conn == null && runQueries) {
            Object urlObj = db.get("url");
            Object userObj = db.get("user");
            Object passwordObj = db.get("password");
            Object schemaObj = db.get("schema");
            
            if (urlObj != null && userObj != null && passwordObj != null) {
                String url = urlObj.toString();
                String user = userObj.toString();
                String password = passwordObj.toString();
                
                conn = DriverManager.getConnection(url, user, password);
                if (schemaObj != null) {
                    try (java.sql.Statement statement = conn.createStatement()) {
                        statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + schemaObj);
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, SQLException, JSQLParserException {
        if(args.length != 1) {
            logger.error("Please specify the path to a repository class");
        }
        else {
            Settings.loadConfigMap();
            RepositoryParser parser = new RepositoryParser();
            parser.compile(AbstractCompiler.classToPath(args[0]));
            parser.processTypes();
            parser.executeAllQueries();
        }
    }

    
    /**
     * Count the number of parameters to bind.
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

    /**
     * Execute all the queries that were identified.
     * This is useful only for visualization purposes.
     * @throws SQLException if the query cannot be executed
     */
    public void executeAllQueries() throws SQLException, JSQLParserException {
        for (var entry : queries.entrySet()) {
            ResultSet rs = executeQuery(entry.getKey());
            if (rs != null) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                for (int i = 1; i <= columnCount; i++) {
                    System.out.print(metaData.getColumnName(i) + "\t");
                }
                System.out.println();

                int i = 0;
                while (rs.next() && i < 10) {
                    for (int j = 1; j <= columnCount; j++) {
                        System.out.print(rs.getString(j) + "\t");
                    }
                    System.out.println();
                    i++;
                }
                rs.close();
            }
        }
    }

    /**
     * Execute the query represented by the method.
     * @param method the name of the method that represents the query in the JPARepository interface
     * @return the result set if the query was executed successfully
     */
    public ResultSet executeQuery(Callable method) throws SQLException, JSQLParserException {
        RepositoryQuery rql = queries.get(method);
        ResultSet rs = executeQuery(rql, method);
        rql.setResultSet(rs);
        cache.put(method, rs);
        return rs;
    }

    public ResultSet executeQuery(RepositoryQuery rql, Callable method) throws SQLException, JSQLParserException {
        if(method.isMethodDeclaration()) {
            return executeQuery(rql, method.asMethodDeclaration());
        }
        return null;
    }

    public ResultSet executeQuery(RepositoryQuery rql, MethodDeclaration method) throws SQLException, JSQLParserException {
        if(runQueries) {
            RepositoryParser.createConnection();

            Select stmt = (Select) rql.getStatement();
            String sql = beautify(stmt.toString());
            sql = trueFalseCheck(sql);

            int argumentCount = countPlaceholders(sql);

            if (argumentCount != 0 && rql.getSimplifiedResultSet() == null) {
                executeSimplifiedQuery(rql, method, argumentCount);
            }

            PreparedStatement prep = conn.prepareStatement(sql);
            for (int i = 0; i < argumentCount; i++) {
                QueryMethodArgument arg = rql.getMethodArguments().get(i);
                bindParameters(arg, prep, i);
            }

            if (prep.execute()) {
                return prep.getResultSet();
            }

        }
        return null;
    }

    /**
     * Executes the query by removing some of its placeholders
     * @param rql the repository query to be executed
     * @param method the method in the JPARepository
     * @param argumentCount the number of placeholders
     * @throws SQLException if the statement cannot be executed
     */
    void executeSimplifiedQuery(RepositoryQuery rql, MethodDeclaration method, int argumentCount) throws SQLException, JSQLParserException {
        rql.buildSimplifiedQuery();
        Select simplified = (Select) rql.getSimplifiedStatement();
        String simplifiedSql = trueFalseCheck(beautify(simplified.toString()));
        PreparedStatement prep = conn.prepareStatement(simplifiedSql);
        for (int i = 0, j= 0; i < argumentCount; i++) {
            QueryMethodArgument arg = rql.getMethodArguments().get(i);
            QueryMethodParameter p = rql.getMethodParameters().get(i);
            if (!p.isRemoved()) {
                bindParameters(arg, prep, j++);
            }
        }

        if (prep.execute()) {
            ResultSet resultSet = prep.getResultSet();
            if (resultSet.next()) {
                happyCache.put(method, resultSet);
                rql.setSimplifedResultSet(resultSet);
            }
        }

    }

    static void bindParameters(QueryMethodArgument arg, PreparedStatement prep, int i) throws SQLException {
        Class<?> clazz = arg.getVariable().getClazz();
        if (clazz == null) {
            prep.setNull(i + 1, java.sql.Types.NULL);

        }
        else {
            switch (clazz.getName()) {
                case "java.lang.Long" -> prep.setLong(i + 1, (Long) arg.getVariable().getValue());
                case "java.lang.String" -> prep.setString(i + 1, (String) arg.getVariable().getValue());
                case "java.lang.Integer" -> prep.setInt(i + 1, (Integer) arg.getVariable().getValue());
                case "java.lang.Boolean" -> prep.setBoolean(i + 1, (Boolean) arg.getVariable().getValue());
                default -> {
                    if (clazz.getName().contains("List")) {
                        List<?> list = (List<?>) arg.getVariable().getValue();
                        String arrayString = list.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(","));
                        prep.setString(i + 1, arrayString);
                    } else {
                        prep.setObject(i + 1, arg.getVariable().getValue());
                    }
                }
            }
        }
    }

    /**
     * Oracle has weird ideas about boolean
     * @param sql the sql statement
     * @return the sql statement modified so that oracle can understand it.
     */
    static String trueFalseCheck(String sql) {
        if(ORACLE.equals(dialect)) {
            sql = sql.replaceAll("(?i)true", "1")
                    .replaceAll("(?i)false", "0");
        }
        return sql;
    }

    private static final Pattern AND_PATTERN = Pattern.compile("\\bAND\\b", Pattern.CASE_INSENSITIVE);
    
    static String beautify(String sql) {
        sql = sql.replaceAll("\\?\\d+", "?");

        // if the sql contains more than 1 AND clause we will delete '1' IN '1'
        Matcher matcher = AND_PATTERN.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
            if(count == 3) {
                sql = sql.replaceAll("'1' IN '1' AND", "");
                break;
            }
        }
        return sql;
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
     * Build a repository query object
     * @param query the query
     * @param isNative will be true if an annotation says a native query
     * @return a repository query instance.
     */
    RepositoryQuery queryBuilder(String query, boolean isNative, Callable md) {
        RepositoryQuery rql = new RepositoryQuery();
        rql.setMethodDeclaration(md);
        rql.setEntityType(entityType);
        rql.setTable(table);
        
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
                    if (isConversionFailureLoggingEnabled()) {
                        logger.warn("Query conversion failed: {}. Failure reason: {}", 
                                   result.getErrorMessage(), result.getFailureReason());
                    }
                    
                    if (isFallbackOnFailureEnabled()) {
                        logger.debug("Falling back to existing logic for query conversion failure");
                        rql.setQuery(query);
                        rql.setIsNative(isNative);
                    } else {
                        // If fallback is disabled, we could throw an exception or handle differently
                        logger.error("Query conversion failed and fallback is disabled. Query: {}", query);
                        rql.setQuery(query);
                        rql.setIsNative(isNative);
                    }
                }
            } catch (Exception e) {
                if (isConversionFailureLoggingEnabled()) {
                    logger.warn("Exception during query conversion: {}. Falling back to existing logic.", e.getMessage());
                }
                
                if (isFallbackOnFailureEnabled()) {
                    rql.setQuery(query);
                    rql.setIsNative(isNative);
                } else {
                    logger.error("Exception during query conversion and fallback is disabled. Query: {}", query, e);
                    rql.setQuery(query);
                    rql.setIsNative(isNative);
                }
            }
        } else {
            // Use original query for native queries or when conversion is disabled
            rql.setQuery(query);
            rql.setIsNative(isNative);
        }
        
        return rql;
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

    private static final Pattern KEYWORDS_PATTERN = Pattern.compile(
        "get|findBy|findFirstBy|findTopBy|And|OrderBy|NotIn|In|Desc|IsNotNull|IsNull|Not|Containing|Like|Or|Between|LessThanEqual|GreaterThanEqual|GreaterThan|LessThan"
    );
    
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
}

