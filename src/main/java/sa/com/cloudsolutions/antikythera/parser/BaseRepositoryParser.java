package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.QueryType;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.HQLParserAdapter;
import sa.com.cloudsolutions.antikythera.parser.converter.MethodToSQLConverter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * Generates SQL from Repository methods.
 * </p>
 *
 * <p>
 * The `BaseRepositoryParser` class is responsible for parsing repository
 * classes and generating
 * repository queries based on the provided methods. It has functionality to
 * handle custom
 * query annotations, inferred queries through method naming, and metadata
 * extraction for entities.
 * </p>
 *
 * <p>
 * This class focuses on analyzing repository structures, interpreting query
 * definitions, and
 * handling query conversion logic while accounting for specific database
 * dialects.
 * </p>
 */
public class BaseRepositoryParser extends AbstractCompiler {
    public static final String JPA_REPOSITORY = "JpaRepository";
    public static final String SELECT_STAR = "SELECT * FROM ";
    public static final String NATIVE_QUERY = "nativeQuery";
    public static final String WHERE = "WHERE";
    protected static final Logger logger = LoggerFactory.getLogger(BaseRepositoryParser.class);
    // Pre-compiled patterns to avoid ReDoS and improve performance
    protected static final Pattern CAMEL_TO_SNAKE_PATTERN = Pattern.compile("([a-z])([A-Z]+)");
    private static final Pattern HQL_FROM_PATTERN = Pattern.compile("\\bFROM\\s+[A-Z][a-zA-Z0-9]+\\s+[a-z]\\b");
    private static final Pattern HQL_ALIAS_PATTERN = Pattern.compile("\\b[a-z]\\.[A-Z][a-zA-Z0-9]+");
    private static final Pattern SQL_COUNT_PATTERN = Pattern.compile("COUNT\\(\\*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_SUBQUERY_FROM_PATTERN = Pattern.compile("\\bFROM\\s*\\(\\s*SELECT",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_SNAKE_CASE_FROM_PATTERN = Pattern
            .compile("\\bfrom\\s+[a-z_][a-z0-9_]+\\s+[a-z]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_SNAKE_CASE_JOIN_PATTERN = Pattern
            .compile("\\bjoin\\s+[a-z_][a-z0-9_]+\\s+[a-z]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_QUOTED_FROM_PATTERN = Pattern.compile("\\bFROM\\s+\"[^\"]+\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_QUOTED_JOIN_PATTERN = Pattern.compile("\\bJOIN\\s+\"[^\"]+\"",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\?");
    public static final String ORDER_BY = "OrderBy";
    /**
     * SQL dialect, at the moment oracle or postgresql as identified from the
     * connection url
     */
    protected static DatabaseDialect dialect = DatabaseDialect.POSTGRESQL; // default dialect set to PostgreSQL
    /**
     * The JPA query converter for converting non-native queries to SQL
     */
    protected HQLParserAdapter parserAdapter;
    /**
     * The queries that were identified in this repository
     */
    protected Map<Callable, RepositoryQuery> queries = new HashMap<>();
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
    Evaluator eval;

    public BaseRepositoryParser() throws IOException {
        super();
        queries = new HashMap<>();
    }

    public static BaseRepositoryParser create(CompilationUnit cu) throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        parser.cu = cu;
        return parser;
    }

    /**
     * Find the table name from the hibernate entity.
     * Usually the entity will have an annotation giving the actual name of the
     * table.
     * <p>
     * This method is made static because when processing joins there are multiple
     * entities
     * and there by multiple table names involved.
     *
     * @param entity a TypeWrapper representing the entity
     * @return the table name as a string.
     */
    public static String findTableName(TypeWrapper entity) {
        String table = null;
        if (entity != null) {
            if (entity.getType() != null) {
                return getNameFromType(entity, table);
            } else if (entity.getClazz() != null) {
                Class<?> cls = entity.getClazz();
                for (Annotation ann : cls.getAnnotations()) {
                    if (ann instanceof javax.persistence.Table t) {
                        table = t.name();
                    }
                }
            }
        } else {
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
                } else if (!wrapper.getImport().getNameAsString().startsWith("java.util")) {
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
     * Converts the fields in an Entity to snake case which is the usual pattern for
     * columns
     *
     * @param str A camel cased variable
     * @return a snake cased variable
     */
    public static String camelToSnake(String str) {
        return CAMEL_TO_SNAKE_PATTERN.matcher(str).replaceAll("$1_$2").toLowerCase();
    }

    /**
     * Determines if a TypeWrapper represents a JPA repository interface.
     * Consolidates logic from QueryOptimizationChecker and HardDelete.
     *
     * @param typeWrapper the TypeWrapper to analyze
     * @return true if it's a JPA repository, false otherwise
     */
    public static boolean isJpaRepository(TypeWrapper typeWrapper) {
        if (typeWrapper == null) {
            return false;
        }

        // Check by fully qualified name first (most reliable)
        String fqn = typeWrapper.getFullyQualifiedName();
        if ("org.springframework.data.jpa.repository.JpaRepository".equals(fqn)) {
            return true;
        }

        // Check runtime class interfaces if available
        if (typeWrapper.getClazz() != null) {
            Class<?> clazz = typeWrapper.getClazz();
            for (Class<?> iface : clazz.getInterfaces()) {
                if (isRepositoryInterface(iface.getName())) {
                    return true;
                }
            }
        }
        return isJpaRepository(typeWrapper.getType());
    }

    public static boolean isJpaRepository(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration classOrInterface && classOrInterface.isInterface()) {

            // Check extended types
            for (ClassOrInterfaceType extendedType : classOrInterface.getExtendedTypes()) {
                String typeName = extendedType.getNameAsString();
                String fullTypeName = extendedType.toString();

                if (isRepositoryInterface(typeName) || isRepositoryInterface(fullTypeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRepositoryInterface(String interfaceName) {
        if (interfaceName == null) {
            return false;
        }
        // Exclude MongoDB repositories - they use a different query paradigm
        if (isMongoRepository(interfaceName)) {
            return false;
        }
        return interfaceName.contains(JPA_REPOSITORY) ||
                interfaceName.contains("CrudRepository") ||
                interfaceName.contains("PagingAndSortingRepository") ||
                interfaceName.contains("Repository") &&
                        (interfaceName.contains("org.springframework.data") || interfaceName.endsWith("Repository"));
    }

    /**
     * Checks if the interface name represents a MongoDB repository.
     * MongoDB repositories use a different query paradigm and should not be processed
     * for SQL/JPQL optimization.
     *
     * @param interfaceName the interface name to check
     * @return true if it's a MongoDB repository interface
     */
    private static boolean isMongoRepository(String interfaceName) {
        return interfaceName.contains("MongoRepository") ||
                interfaceName.contains("ReactiveMongoRepository");
    }

    public static DatabaseDialect getDialect() {
        return dialect;
    }

    protected static int countPlaceholders(String sql) { // restored as protected for external usage
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        int count = 0;
        while (matcher.find())
            count++;
        return count;
    }

    public RepositoryQuery getQueryFromRepositoryMethod(Callable repoMethod) {
        return queries.get(repoMethod);
    }

    /**
     * <p>
     * Extract a query from a given method declaration.
     * </p>
     *
     * <p>
     * The method declaration should be a part of a JPARepository interface.
     * It may or may not have an @Query annotation. If the annotation is
     * present), it will either be a native query (in which case the nativeQuery
     * attribute will be true, or it maybe an HQL.
     * </p>
     *
     * <p>
     * In the case of an HQL query, we will use the hql-parser library to
     * convert it into an SQL which in turn will be parsed with the JSQL parser.
     * However, that parsing takes place inside the BaseRepositoryQuery class
     * </p>
     *
     * @param methodDeclaration the method declaration to be processed
     */
    void queryFromMethodDeclaration(MethodDeclaration methodDeclaration) {
        try {
            Optional<AnnotationExpr> annotationExpr = methodDeclaration.getAnnotationByName("Query");
            Callable callable = new Callable(methodDeclaration, null);

            if (annotationExpr.isPresent()) {
                Map<String, Expression> attr = AbstractCompiler.extractAnnotationAttributes(annotationExpr.get());
                Expression value = attr.get("value");
                Expression nt = attr.get(NATIVE_QUERY);
                Variable v = eval.evaluateExpression(value);
                // Unescape the string to convert \\n to actual newlines (text blocks issue)
                String queryString = unescapeJavaString((String) v.getValue());

                boolean isNativeQuery = evaluateNativeQueryAttribute(nt, methodDeclaration);

                // Fallback: If query looks like SQL (has COUNT(*), FROM table_name pattern,
                // etc.) but nativeQuery wasn't detected, log a warning and treat as native SQL
                if (!isNativeQuery && looksLikeNativeSQL(queryString)) {
                    logger.warn("Query for method {} appears to be native SQL (contains COUNT(*) or SQL patterns) " +
                            "but nativeQuery attribute was not detected or evaluated to false. " +
                            "Treating as native SQL. If this is incorrect, ensure nativeQuery=true is set.",
                            methodDeclaration.getNameAsString());
                    isNativeQuery = true;
                }

                if (isNativeQuery) {
                    queries.put(callable, queryBuilder(queryString, QueryType.NATIVE_SQL, callable));
                } else {
                    queries.put(callable, queryBuilder(queryString, QueryType.HQL, callable));
                }
            } else {
                queries.put(callable, parseNonAnnotatedMethod(callable));
            }
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    /**
     * Checks if a query string looks like native SQL rather than HQL.
     * Native SQL typically has patterns like:
     * - COUNT(*) (not COUNT(e))
     * - FROM table_name (not FROM EntityName)
     * - Direct table/column names in snake_case or quoted identifiers
     * - Subqueries with FROM (SELECT ... FROM table_name)
     * 
     * This method is conservative - it only returns true for strong SQL indicators
     * to avoid incorrectly classifying HQL queries as SQL.
     * 
     * @param query the query string to check
     * @return true if the query appears to be native SQL
     */
    private boolean looksLikeNativeSQL(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        // First, check for HQL patterns - if present, it's definitely HQL
        // HQL uses entity.field syntax (e.g., "u.vehicles", "i.patientId")
        // SQL uses table.column syntax (e.g., "i.patient_id", "blinv_invoice_item.id")
        // HQL entity references often have CamelCase after the dot
        if (HQL_FROM_PATTERN.matcher(query).find()) {
            // Pattern: FROM EntityName alias - this is HQL
            return false;
        }
        if (HQL_ALIAS_PATTERN.matcher(query).find()) {
            // Pattern: alias.EntityField (CamelCase after dot) - this is HQL
            return false;
        }

        // Check for SQL-specific patterns that are unlikely in HQL
        // COUNT(*) is SQL syntax (HQL would be COUNT(e) or similar)
        if (SQL_COUNT_PATTERN.matcher(query).find()) {
            return true;
        }

        // Check for subquery pattern: FROM (SELECT ... FROM table_name)
        // This is a strong indicator of native SQL
        if (SQL_SUBQUERY_FROM_PATTERN.matcher(query).find()) {
            return true;
        }

        // Check for FROM table_name pattern with snake_case (strong SQL indicator)
        // Must have multiple snake_case patterns to be confident it's SQL
        // Pattern: FROM snake_case_table AND JOIN snake_case_table
        if (SQL_SNAKE_CASE_FROM_PATTERN.matcher(query).find() &&
                SQL_SNAKE_CASE_JOIN_PATTERN.matcher(query).find()) {
            // Both FROM and JOIN use snake_case - very likely SQL
            return true;
        }

        // Check for quoted table/column names (common in SQL, less common in HQL)
        return (SQL_QUOTED_FROM_PATTERN.matcher(query).find() ||
                SQL_QUOTED_JOIN_PATTERN.matcher(query).find());
    }

    /**
     * Evaluates the nativeQuery attribute from a Query annotation expression.
     * 
     * @param nativeQueryExpr   the Expression representing the nativeQuery
     *                          attribute
     * @param methodDeclaration the method declaration for logging purposes
     * @return true if the query is native SQL, false otherwise (defaults to false
     *         if evaluation fails)
     */
    private boolean evaluateNativeQueryAttribute(Expression nativeQueryExpr, MethodDeclaration methodDeclaration) {
        if (nativeQueryExpr == null) {
            return false;
        }

        try {
            Variable nativeQueryVar = eval.evaluateExpression(nativeQueryExpr);
            Object nativeQueryValue = nativeQueryVar.getValue();
            // Handle both Boolean objects and primitive booleans
            if (nativeQueryValue instanceof Boolean b) {
                return b;
            } else if (nativeQueryValue != null) {
                // Try to parse as boolean string
                return Boolean.parseBoolean(nativeQueryValue.toString());
            }
        } catch (Exception e) {
            logger.warn("Failed to evaluate nativeQuery attribute for method {}, defaulting to HQL: {}",
                    methodDeclaration.getNameAsString(), e.getMessage());
        }

        return false;
    }

    /**
     * Unescapes a Java string literal by converting escape sequences to actual
     * characters.
     * Handles:
     * - \\n (newline)
     * - \\t (tab)
     * - \\r (carriage return)
     * - \\" (quote)
     * - \\\\ (backslash)
     * - Line continuation: backslash at end of line (\ followed by newline) removes
     * the newline and leading whitespace on the next line
     * 
     * Note: For text blocks, Java processes line continuation backslashes during
     * compilation. However, if the value still contains them (e.g., from
     * JavaParser's
     * getValue()), this method will handle them.
     */
    private String unescapeJavaString(String str) {
        if (str == null) {
            return null;
        }

        // First, handle line continuation backslashes (text block syntax)
        // A backslash at the end of a line followed by newline removes the newline
        // and any leading whitespace on the next line, effectively joining the lines.
        // Pattern matches: backslash, optional trailing whitespace, newline(s),
        // optional leading whitespace
        // We replace with a single space to join the lines
        // This handles both \n and \r\n line endings
        str = str.replaceAll("\\\\\\s*[\\r\\n]+\\s*", " ");

        // Then handle other escape sequences
        // Note: Order matters - we handle line continuation first, then other escapes
        // to avoid double-processing
        return str.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * Parse a repository method that does not have a query annotation.
     * In these cases the naming convention of the method is used to infer the
     * query.
     *
     * @param md the method declaration
     * @return the RepositoryQuery instance that as parsed from the callable
     */
    @SuppressWarnings("java:S127")
    RepositoryQuery parseNonAnnotatedMethod(Callable md) {
        String methodName = md.getNameAsString();
        List<String> components = MethodToSQLConverter.extractComponents(methodName);

        // Validate that we have components to work with
        if (components.isEmpty()) {
            logger.warn("Method name '{}' did not produce any recognizable JPA query components", methodName);
            // Return a basic query as fallback
            return queryBuilder(SELECT_STAR + findTableName(entity), QueryType.DERIVED, md);
        }

        StringBuilder sql = new StringBuilder();
        String tableName = findTableName(entity);
        boolean top = false;
        boolean isExistsQuery = components.contains("existsBy") || components.contains("existsAllBy");
        if (tableName != null) {
            top = MethodToSQLConverter.buildSelectAndWhereClauses(components, sql, tableName);
        } else {
            logger.warn("Table name cannot be null for entity");
        }
        if (top) {
            applyTopLimit(sql);
        }

        // Close the EXISTS subquery if needed (closes both the inner SELECT and the EXISTS function)
        if (isExistsQuery) {
            sql.append(")");
        }

        String finalSql = numberPlaceholders(sql.toString());
        return queryBuilder(finalSql, QueryType.DERIVED, md);
    }

    /**
     * Apply dialect-specific top limit (FIRST/TOP semantics)
     */
    private void applyTopLimit(StringBuilder sql) {
        String built = sql.toString();
        String trimmedUpper = built.trim().toUpperCase();
        boolean trailingWhere = trimmedUpper.endsWith(WHERE);

        if (trailingWhere) {
            // Remove dangling WHERE before applying limit
            int idx = built.toUpperCase().lastIndexOf(WHERE);
            built = built.substring(0, idx).trim();
        }

        // For Oracle, we need a WHERE clause for ROWNUM to work
        // For PostgreSQL and others, LIMIT is appended after the query
        if (dialect == DatabaseDialect.ORACLE) {
            // Oracle needs WHERE clause for ROWNUM
            if (!built.toUpperCase().contains(WHERE)) {
                built = built + " WHERE ROWNUM = 1";
            } else {
                built = dialect.applyLimitClause(built, 1);
            }
        } else {
            // PostgreSQL and others use LIMIT which doesn't need WHERE
            built = dialect.applyLimitClause(built, 1);
        }

        sql.setLength(0);
        sql.append(built);
    }

    /**
     * Replace placeholders ? with numbered ?1, ?2
     */
    private String numberPlaceholders(String input) {
        StringBuilder result = new StringBuilder();
        int paramIndex = 1;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '?') {
                result.append('?').append(paramIndex++);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Build a repository query object
     *
     * @param query the query
     * @param qt    what is the type of query we are dealing with
     * @return a repository query instance.
     */
    RepositoryQuery queryBuilder(String query, QueryType qt, Callable md) {
        RepositoryQuery rql = new RepositoryQuery();
        rql.setMethodDeclaration(md);
        rql.setEntityType(entityType);
        rql.setPrimaryTable(table);
        rql.setRepositoryClassName(className);
        rql.setQueryType(qt);
        if (qt.equals(QueryType.HQL)) {
            try {
                // Trim query to remove leading/trailing whitespace from text blocks
                String trimmedQuery = query.trim();
                ConversionResult conversionResult = parserAdapter.convertToNativeSQL(trimmedQuery);
                rql.setConversionResult(conversionResult);
                // Store original HQL query for reference
                rql.setOriginalQuery(trimmedQuery);
                // Use converted SQL to set the statement instead of parsing HQL
                rql.setStatementFromConversionResult(conversionResult);
            } catch (Exception e) {
                logger.error("Failed to parse HQL query: {}", query);
                throw new AntikytheraException(e);
            }
        } else {
            // For DERIVED and NATIVE_SQL queries, set query as-is
            // Boolean transformations for Oracle are handled elsewhere (e.g., in
            // RepositoryParser.trueFalseCheck)
            rql.setQuery(query);
        }

        return rql;
    }

    /**
     * Gets all the repository queries that have been built.
     *
     * @return a collection of all RepositoryQuery objects
     */
    public Collection<RepositoryQuery> getAllQueries() {
        return queries.values();
    }

    public void buildQueries() {
        queries.clear();
        parserAdapter = new HQLParserAdapter(cu, entity);
        if (cu != null && entity != null) {
            cu.accept(new Visitor(), null);
        }
    }

    /**
     * Process the CompilationUnit to identify all the queries.
     */
    public void processTypes() {
        /*
         * this method is now being called multiple times for a repository, fix that
         */
        for (var tp : cu.getTypes()) {
            if (tp.isClassOrInterfaceDeclaration()) {
                var cls = tp.asClassOrInterfaceDeclaration();

                for (var parent : cls.getExtendedTypes()) {
                    if (isRepositoryInterface(parent.toString())) {
                        parent.getTypeArguments().ifPresent(t -> {
                            entityType = t.getFirst().orElseThrow();
                            entity = findEntity(entityType);
                            table = findTableName(entity);
                            eval = EvaluatorFactory.create(cls.getFullyQualifiedName().orElseThrow(), Evaluator.class);
                        });
                    }
                }
            }
        }
    }

    public TypeWrapper getEntity() {
        return entity;
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
}
