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
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.HQLParserAdapter;

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

/**
 * <p>Generates SQL from Repository methods.</p>
 *
 * <p>The `BaseRepositoryParser` class is responsible for parsing repository classes and generating
 * repository queries based on the provided methods. It has functionality to handle custom
 * query annotations, inferred queries through method naming, and metadata extraction for entities.</p>
 *
 * <p>This class focuses on analyzing repository structures, interpreting query definitions, and
 * handling query conversion logic while accounting for specific database dialects.</p>
 */
public class BaseRepositoryParser extends AbstractCompiler {
    protected static final Logger logger = LoggerFactory.getLogger(BaseRepositoryParser.class);

    public static final String JPA_REPOSITORY = "JpaRepository";
    public static final String SELECT_STAR = "SELECT * FROM ";
    protected static final Pattern CAMEL_TO_SNAKE_PATTERN = Pattern.compile("([a-z])([A-Z]+)");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\?");
    public static final String NATIVE_QUERY = "nativeQuery";
    /**
     * SQL dialect, at the moment oracle or postgresql as identified from the connection url
     */
    protected static DatabaseDialect dialect = DatabaseDialect.POSTGRESQL; // default dialect set to PostgreSQL

    /**
     * The JPA query converter for converting non-native queries to SQL
     */
    protected HQLParserAdapter parserAdapter;

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

    Evaluator eval;

    protected static final Pattern KEYWORDS_PATTERN = Pattern.compile(
            "get|findBy|findFirstBy|findTopBy|And|OrderBy|NotIn|In|Desc|IsNotNull|IsNull|Not|Containing|Like|Or|Between|LessThanEqual|GreaterThanEqual|GreaterThan|LessThan"
    );

    public BaseRepositoryParser() throws IOException {
        super();
        queries = new HashMap<>();
    }

    public static BaseRepositoryParser create(CompilationUnit cu) throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        parser.cu = cu;
        return parser;
    }


    public RepositoryQuery getQueryFromRepositoryMethod(Callable repoMethod) {
        return queries.get(repoMethod);
    }

    /**
     * <p>Extract a query from a given method declaration.</p>
     *
     * <p>The method declaration should be a part of a JPARepository interface.
     * It may or may not have an @Query annotation. If the annotation is
     * present), it will either be a native query (in which case the nativeQuery
     * attribute will be true, or it maybe an HQL.</p>
     *
     * <p>In the case of an HQL query, we will use the hql-parser library to
     * convert it into an SQL which in turn will be parsed with the JSQL parser.
     * However, that parsing takes place inside the BaseRepositoryQuery class</p>
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

                if (nt != null && eval.evaluateExpression(nt).getValue().equals(true)) {
                    queries.put(callable, queryBuilder(v.getValue().toString(), QueryType.NATIVE_SQL, callable));
                }
                else {
                    queries.put(callable, queryBuilder(v.getValue().toString(), QueryType.HQL , callable));
                }
            }
            else {
                queries.put(callable, parseNonAnnotatedMethod(callable));
            }
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    /**
     * Parse a repository method that does not have a query annotation.
     * In these cases the naming convention of the method is used to infer the query.
     *
     * @param md the method declaration
     * @return the RepositoryQuery instance that as parsed from the callable
     */
    @SuppressWarnings("java:S127")
    RepositoryQuery parseNonAnnotatedMethod(Callable md) {
        String methodName = md.getNameAsString();
        List<String> components = extractComponents(methodName);
        StringBuilder sql = new StringBuilder();
        String tableName = findTableName(entity);
        boolean top = false;
        if (tableName != null) {
            top = buildSelectAndWhereClauses(components, sql, tableName);
        } else {
            logger.warn("Table name cannot be null");
        }
        if (top) {
            applyTopLimit(sql);
        }
        String finalSql = numberPlaceholders(sql.toString());
        return queryBuilder(finalSql, QueryType.DERIVED, md);
    }

    /** Refactored: returns whether a TOP/FIRST was detected */
    private boolean buildSelectAndWhereClauses(List<String> components, StringBuilder sql, String tableName) {
        boolean top = false;
        boolean ordering = false;
        for (int i = 0; i < components.size(); i++) {
            String component = components.get(i);
            String next = (i < components.size() - 1) ? components.get(i + 1) : "";
            switch (component) {
                case "findAll" -> sql.append(SELECT_STAR).append(tableName.replace("\"", ""));
                case "findAllById" -> sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE id = ?");
                case "findBy", "get" -> sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE ");
                case "findFirstBy", "findTopBy" -> { top = true; sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE "); }
                case "Between" -> sql.append(" BETWEEN ? AND ? ");
                case "GreaterThan" -> sql.append(" > ? ");
                case "LessThan" -> sql.append(" < ? ");
                case "GreaterThanEqual" -> sql.append(" >= ? ");
                case "LessThanEqual" -> sql.append(" <= ? ");
                case "IsNull" -> sql.append(" IS NULL ");
                case "IsNotNull" -> sql.append(" IS NOT NULL ");
                case "And", "Or", "Not" -> sql.append(component).append(' ');
                case "Containing", "Like" -> sql.append(" LIKE ? ");
                case "OrderBy" -> { ordering = true; sql.append(" ORDER BY "); }
                default -> appendDefaultComponent(sql, component, next, ordering);
            }
        }
        return top;
    }

    /** Updated default component handler to accept ordering flag */
    private void appendDefaultComponent(StringBuilder sql, String component, String next, boolean ordering) {
        sql.append(camelToSnake(component));
        if (!ordering) {
            if (next.equals("In")) {
                sql.append(" In  (?) ");
            } else if (next.equals("NotIn")) {
                sql.append(" NOT In (?) ");
            } else if (shouldAppendEquals(next)) {
                sql.append(" = ? ");
            } else {
                sql.append(' ');
            }
        } else {
            sql.append(' ');
        }
    }

    private boolean shouldAppendEquals(String next) {
        return next.isEmpty() || (!next.equals("Between") && !next.equals("GreaterThan") && !next.equals("LessThan") &&
                !next.equals("LessThanEqual") && !next.equals("IsNotNull") && !next.equals("Like") &&
                !next.equals("GreaterThanEqual") && !next.equals("IsNull") && !next.equals("Containing"));
    }

    /** Apply dialect-specific top limit (FIRST/TOP semantics) */
    private void applyTopLimit(StringBuilder sql) {
        String built = sql.toString();
        String trimmedUpper = built.trim().toUpperCase();
        boolean trailingWhere = trimmedUpper.endsWith("WHERE");
        if (dialect == DatabaseDialect.ORACLE) {
            if (trailingWhere) {
                int idx = built.toUpperCase().lastIndexOf("WHERE");
                built = built.substring(0, idx) + "WHERE ROWNUM = 1";
            } else {
                built = dialect.applyLimitClause(built, 1);
            }
        } else { // PostgreSQL (default) and others that use LIMIT
            if (trailingWhere) {
                // remove dangling WHERE before applying limit
                built = built.substring(0, built.toUpperCase().lastIndexOf("WHERE"));
                built = built.trim();
            }
            built = dialect.applyLimitClause(built, 1);
        }
        sql.setLength(0);
        sql.append(built);
    }

    /** Replace placeholders ? with numbered ?1, ?2 */
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
     * @param query the query
     * @param qt what is the type of query we are dealing with
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
                rql.setConversionResult(parserAdapter.convertToNativeSQL(query));
                rql.setQuery(query);
            } catch (Exception e) {
                logger.error(query);
                throw new AntikytheraException(e);
            }
        } else {
            // For DERIVED and NATIVE_SQL queries, set query as-is
            // Boolean transformations for Oracle are handled elsewhere (e.g., in RepositoryParser.trueFalseCheck)
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
        queries.clear();
        parserAdapter = new HQLParserAdapter(cu, entity);
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
        /*
         * this method is now being called multiple times for a repository, fix that
         */
        for(var tp : cu.getTypes()) {
            if(tp.isClassOrInterfaceDeclaration()) {
                var cls = tp.asClassOrInterfaceDeclaration();

                for(var parent : cls.getExtendedTypes()) {
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
        return interfaceName != null && (
                interfaceName.contains(JPA_REPOSITORY) ||
                        interfaceName.contains("CrudRepository") ||
                        interfaceName.contains("PagingAndSortingRepository") ||
                        interfaceName.contains("Repository") &&
                                (interfaceName.contains("org.springframework.data") || interfaceName.endsWith("Repository"))
        );
    }

    protected List<String> extractComponents(String methodName) {
        List<String> components = new ArrayList<>();
        Matcher matcher = KEYWORDS_PATTERN.matcher(methodName);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, " " + matcher.group() + " ");
        }
        matcher.appendTail(sb);
        String[] parts = sb.toString().split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) components.add(part);
        }
        return components;
    }

    public static DatabaseDialect getDialect() { return dialect; }

    protected static int countPlaceholders(String sql) { // restored as protected for external usage
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}
