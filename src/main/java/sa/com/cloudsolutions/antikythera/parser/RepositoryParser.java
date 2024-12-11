package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodParameter;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import net.sf.jsqlparser.statement.select.Select;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.io.IOException;
import java.lang.reflect.Method;
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

/**
 * Parses JPARespository subclasses to identify the queries that they execute.
 *
 * These queries can then be used to determine what kind of data need to be sent
 * to the controller for a valid response.
 */
public class RepositoryParser extends ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryParser.class);
    public static final String JPA_REPOSITORY = "JpaRepository";
    public static final String SELECT_STAR = "SELECT * FROM ";

    /**
     * The queries that were identified in this repository
     */
    private final Map<MethodDeclaration, RepositoryQuery> queries;
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
     * The compilation unit or class associated with this entity.
     */
    private TypeWrapper entity;

    /**
     * The table name associated with the entity
     */
    private String table;
    /**
     * The java parser type associated with the entity.
     */
    private Type entityType;

    /**
     * A query cache.
     * Since we execute the same lines of code repeatedly in order to generate tests to cover
     * different branches, we will end up executing the same query over and over again. This is
     * wasteful in terms of both time and money! So we will cache the result sets here.
     */
    private final Map<MethodDeclaration, ResultSet> cache = new HashMap<>();

    /**
     * A cache for the simplified queries.
     */
    private final Map<MethodDeclaration, ResultSet> happyCache = new HashMap<>();

    public RepositoryParser() throws IOException {
        super();
        queries = new HashMap<>();

        Map<String, Object> db = (Map<String, Object>) Settings.getProperty("database");
        if(db != null) {
            runQueries = db.getOrDefault("run_queries", "false").toString().equals("true");
            String url = db.get("url").toString();
            if(url.contains(ORACLE)) {
                dialect = ORACLE;
            }
            else {
                dialect = POSTGRESQL;
            }
        }
    }

    /**
     * Create a connection to the database.
     *
     * The connection will be shared among all instances of this class. Even if the
     * credentials are provide the connection is setup only if the runQueries
     * setting is true.
     *
     * @throws SQLException
     */
    private static void createConnection() throws SQLException {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty("database");
        if(db != null && conn == null && runQueries) {
            String url = db.get("url").toString();

            conn = DriverManager.getConnection(url, db.get("user").toString(), db.get("password").toString());
            try (java.sql.Statement statement = conn.createStatement()) {
                statement.execute("ALTER SESSION SET CURRENT_SCHEMA = " + db.get("schema").toString());
            }
        }
    }

    public static void main(String[] args) throws IOException, SQLException {
        if(args.length != 1) {
            logger.error("Please specifiy the path to a repository class");
        }
        else {
            Settings.loadConfigMap();
            RepositoryParser parser = new RepositoryParser();
            parser.compile(AbstractCompiler.classToPath(args[0]));
            parser.process();
            parser.executeAllQueries();
        }
    }

    /**
     * Count the number of parameters to bind.
     * @param sql an sql statement as a string
     * @return the number of place holder can be 0
     */
    private static int countPlaceholders(String sql) {
        Pattern pattern = Pattern.compile("\\?");
        Matcher matcher = pattern.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Process the CompilationUnit to identify the queries.
     */
    public void process()  {
        for(var tp : cu.getTypes()) {
            if(tp.isClassOrInterfaceDeclaration()) {
                var cls = tp.asClassOrInterfaceDeclaration();
                boolean found = false;

                for(var parent : cls.getExtendedTypes()) {
                    if (parent.toString().startsWith(JPA_REPOSITORY)) {
                        found = true;
                        Optional<NodeList<Type>> t = parent.getTypeArguments();
                        if (t.isPresent()) {
                            entityType = t.get().get(0);
                            entity = findEntity(entityType);
                            table = findTableName(entity);
                        }
                        break;
                    }
                }
                if(found) {
                    tackOn(cls);
                    cu.accept(new Visitor(), null);
                }
            }
        }
    }

    private void tackOn(ClassOrInterfaceDeclaration cls) {
        for(var parent : cls.getExtendedTypes()) {
            String fullName = AbstractCompiler.findFullyQualifiedName(cu, parent.getNameAsString());
            if (fullName != null) {
                CompilationUnit p = AntikytheraRunTime.getCompilationUnit(fullName);
                if(p == null) {
                    try {
                        Class<?> interfaceClass = Class.forName(fullName);
                        Method[] methods = interfaceClass.getMethods();

                        for (Method method : methods) {
                            // Extract method information
                            String methodName = method.getName();
                            Class<?> returnType = method.getReturnType();
                            java.lang.reflect.Parameter[] params = method.getParameters();
                            // Create a MethodDeclaration node using JavaParser
                            MethodDeclaration methodDeclaration = new MethodDeclaration();
                            methodDeclaration.setName(methodName);
                            methodDeclaration.setType(returnType.getCanonicalName());

                            // Add parameters to the method declaration
                            for (java.lang.reflect.Parameter param : params) {
                                Parameter parameter = new Parameter();
                                parameter.setType(param.getType());
                                parameter.setName(param.getName());
                                methodDeclaration.addParameter(parameter);
                            }
                            cls.addMember(methodDeclaration);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        }
    }

    /**
     * Execute all the queries that were identified.
     * This is useful only for visualization purposes.
     * @throws IOException
     * @throws SQLException
     */
    public void executeAllQueries() throws IOException, SQLException {
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
    public ResultSet executeQuery(MethodDeclaration method) {
        ResultSet cached = cache.get(method);
        if (cached != null) {
            return cached;
        }
        RepositoryQuery rql = queries.get(method);
        ResultSet rs = executeQuery(rql, method);
        rql.setResultSet(rs);
        cache.put(method, rs);
        return rs;
    }

    public ResultSet executeQuery(RepositoryQuery rql, MethodDeclaration method)  {
        try {
            if(runQueries) {
                RepositoryParser.createConnection();
                
                Select stmt = (Select) rql.getStatement();
                String sql = beautify(stmt.toString());
                sql = trueFalseCheck(sql);

                int argumentCount = countPlaceholders(sql);

                if (argumentCount != 0) {
                    // we will run the query the simplified query as well.
                    Select simplified = (Select) rql.getSimplifiedStatement();
                    String simplifiedSql = trueFalseCheck(beautify(simplified.toString()));
                    PreparedStatement prep = conn.prepareStatement(simplifiedSql);
                    for (int i = 0; i < argumentCount ; i++) {
                        QueryMethodArgument arg = rql.getMethodArguments().get(i);
                        QueryMethodParameter p = rql.getMethodParameters().get(i);
                        if(!p.isRemoved()) {
                            bindParameters(arg, prep, i);
                        }
                    }

                    if (prep.execute()) {
                        happyCache.put(method, prep.getResultSet());
                        rql.setSimplifedResultSet(prep.getResultSet());
                    }
                }

                PreparedStatement prep = conn.prepareStatement(sql);

                for (int i = 0; i < argumentCount ; i++) {
                    QueryMethodArgument arg = rql.getMethodArguments().get(i);
                    bindParameters(arg, prep, i);
                }

                if (prep.execute()) {
                    return prep.getResultSet();
                }
            }

        } catch (SQLException e) {
            logger.error(rql.getQuery());
        }
        return null;
    }

    private static void bindParameters(QueryMethodArgument arg, PreparedStatement prep, int i) throws SQLException {
        Class<?> clazz = arg.getVariable().getClazz();
        if (clazz == null) {
            prep.setNull(i + 1, java.sql.Types.NULL);

        }
        else {
            String name = clazz.getName();
            switch (name) {
                case "java.lang.Long" -> prep.setLong(i + 1, (Long) arg.getVariable().getValue());
                case "java.lang.String" -> prep.setString(i + 1, (String) arg.getVariable().getValue());
                case "java.lang.Integer" -> prep.setInt(i + 1, (Integer) arg.getVariable().getValue());
                case "java.lang.Boolean" -> prep.setBoolean(i + 1, (Boolean) arg.getVariable().getValue());
            }
        }
    }

    /**
     * Oracle has wierd ideas about boolean
     * @param sql
     * @return
     */
    private static String trueFalseCheck(String sql) {
        if(dialect.equals(ORACLE)) {
            sql = sql.replaceAll("(?i)true", "1")
                    .replaceAll("(?i)false", "0");
        }
        return sql;
    }

    private static String beautify(String sql) {
        sql = sql.replaceAll("\\?\\d+", "?");

        // if the sql contains more than 1 AND clause we will delete '1' IN '1'
        Pattern pattern = Pattern.compile("\\bAND\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
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
     * and there by multipe table names involved.
     *
     * @param entity a TypeWrapper representing the entity
     * @return the table name as a string.
     */
    public static String findTableName(TypeWrapper entity) {
        String table = null;
        if(entity != null) {
            if (entity.getType() != null) {
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
        }
        else {
            logger.warn("Compilation unit is null");
        }
        return table;
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
                        AbstractCompiler.loadClass(wrapper.getImport().getNameAsString());
                        return new TypeWrapper(
                            Class.forName(wrapper.getImport().getNameAsString())
                        );
                    } catch (ClassNotFoundException e) {
                        // can be ignored, we are trying to check for the existence of the class
                    }
                }
            }
            return new TypeWrapper(AbstractCompiler.findInSamePackage(cu.get(), fd));
        }
        return null;
    }

    /**
     * Converts the fields in an Entity to snake case which is the usual pattern for columns
     * @param str A camel cased variable
     * @return a snake cased variable
     */
    public static String camelToSnake(String str) {
        if(str.equalsIgnoreCase("patientpomr")) {
            return str;
        }
        return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    public RepositoryQuery get(MethodDeclaration repoMethod) {
        return queries.get(repoMethod);
    }

    /**
     * Visitor to iterate through the methods in the repository
     */
    class Visitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            String query = null;
            boolean nt = false;

            for (var ann : n.getAnnotations()) {
                if (ann.getNameAsString().equals("Query")) {
                    if (ann.isSingleMemberAnnotationExpr()) {
                        try {
                            Evaluator eval = new Evaluator(className);
                            Variable v = eval.evaluateExpression(
                                    ann.asSingleMemberAnnotationExpr().getMemberValue()
                            );
                            query = v.getValue().toString();
                        } catch (AntikytheraException|ReflectiveOperationException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (ann.isNormalAnnotationExpr()) {

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
                    break;
                }
            }

            if (query != null) {
                queries.put(n, queryBuilder(query, nt, n));
            } else {
                parseNonAnnotatedMethod(n);
            }
        }
    }

    /**
     * Build a repository query object
     * @param query
     * @param isNative
     * @return
     */
    RepositoryQuery queryBuilder(String query, boolean isNative, MethodDeclaration md) {
        RepositoryQuery rql = new RepositoryQuery();
        rql.setMethodDeclaration(md);
        rql.setIsNative(isNative);
        rql.setEntityType(entityType);
        rql.setTable(table);
        rql.setQuery(query);
        return rql;
    }

    /**
     * Parse a repository method that does not have a query annotation.
     * In these cases the naming convention of the method is used to infer the query.
     * @param md
     */
    void parseNonAnnotatedMethod(MethodDeclaration md) {
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
                                if (!next.isEmpty() && !next.equals("Between") && !next.equals("GreaterThan")
                                        && !next.equals("LessThan") && !next.equals("LessThanEqual")
                                        && !next.equals("IsNotNull") && !next.equals("Like")
                                        && !next.equals("GreaterThanEqual") && !next.equals("IsNull")) {
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
            if (dialect.equals(ORACLE)) {
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
    private List<String> extractComponents(String methodName) {
        List<String> components = new ArrayList<>();
        String keywords = "get|findBy|findFirstBy|findTopBy|And|OrderBy|NotIn|In|Desc|IsNotNull|IsNull|Not|Containing|Like|Or|Between|LessThanEqual|GreaterThanEqual|GreaterThan|LessThan";
        Pattern pattern = Pattern.compile(keywords);
        Matcher matcher = pattern.matcher(methodName);

        // Add spaces around each keyword
        StringBuffer sb = new StringBuffer();
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
     * Find the method declaration that corresponds to a method call.
     * @param methodCall the method call being executed
     * @return the MethodDeclaration from the repository interface.
     */
    public MethodDeclaration findMethodDeclaration(MethodCallExpr methodCall) {
        return findMethodDeclaration(methodCall, cu.getTypes().get(0)).orElse(null);
    }

    public static boolean isOracle() {
        return dialect.equals(ORACLE);
    }

}

