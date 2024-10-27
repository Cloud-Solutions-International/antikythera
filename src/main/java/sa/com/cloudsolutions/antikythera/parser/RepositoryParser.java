package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
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

import java.io.FileNotFoundException;
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
 * Parses JPARespository subclasses to indentify the queries that they execute.
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
     * The java parser compilation unit associated with this entity.
     *
     * This is different from the 'cu' field in the AbstractClassProcessor. That field will
     * represent the repository interface while the entityCu will represent the entity that
     * is part of the type arguments
     */
    private CompilationUnit entityCu;
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
     * Since we execute the same lines of code repeatedly in order to generate testss to cover
     * different branches, we will end up executing the same query over and over again. This is
     * wasteful in terms of both time and money! So we will cache the result sets here.
     */
    private final Map<MethodDeclaration, ResultSet> cache = new HashMap<>();

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
     * @throws IOException
     */
    public void process() throws IOException {
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
                            entityCu = findEntity(entityType);
                            table = findTableName(entityCu);
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
     * @throws FileNotFoundException raised by covertFieldsToSnakeCase
     * @return the result set if the query was executed successfully
     */
    public ResultSet executeQuery(MethodDeclaration method) throws IOException {
        ResultSet cached = cache.get(method);
        if (cached != null) {
            return cached;
        }
        ResultSet rs = executeQuery(queries.get(method));
        cache.put(method, rs);
        return rs;
    }

    public ResultSet executeQuery(RepositoryQuery rql) throws IOException {
        try {
            RepositoryParser.createConnection();
            Select stmt = (Select) rql.getSimplifiedStatement();

            String sql = stmt.toString().replaceAll("\\?\\d+", "?");
            if(dialect.equals(ORACLE)) {
                sql = sql.replaceAll("(?i)true", "1")
                        .replaceAll("(?i)false", "0");
            }

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

            if(runQueries) {
                PreparedStatement prep = conn.prepareStatement(sql);

                for (int i = 0, j = countPlaceholders(sql); i < j ; i++) {
                    QueryMethodArgument arg = rql.getMethodArguments().get(i);
                    String name = arg.getVariable().getClazz().getName();
                    switch (name) {
                        case "Long" -> prep.setLong(i + 1, (Long) arg.getVariable().getValue());
                        case "String" -> prep.setString(i + 1, (String) arg.getVariable().getValue());
                        case "Integer" -> prep.setInt(i + 1, (Integer) arg.getVariable().getValue());
                        case "Boolean" -> prep.setBoolean(i + 1, (Boolean) arg.getVariable().getValue());
                    }
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

    /**
     * Find the table name from the hibernate entity.
     * Usually the entity will have an annotation giving the actual name of the table.
     *
     * This method is made static because when processing joins there are multiple entities
     * and there by multipe table names involved.
     *
     * @param entityCu a compilation unit representing the entity
     * @return the table name as a string.
     */
    public static String findTableName(CompilationUnit entityCu) {
        String table = null;
        if(entityCu != null) {
            Optional<AnnotationExpr> ann = entityCu.getTypes().get(0).getAnnotationByName("Table");
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
            }
            else {
                return camelToSnake(entityCu.getTypes().get(0).getNameAsString());
            }
        }
        else {
            logger.warn("Compilation unit is null");
        }
        return table;
    }

    /**
     * Find and parse the given entity.
     * @param entity a type representing the entity
     * @return a compilation unit
     * @throws FileNotFoundException if the entity cannot be found in the AUT
     */
    public static CompilationUnit findEntity(Type entity) throws IOException {
        String nameAsString = entity.asClassOrInterfaceType().resolve().describe();
        ClassProcessor processor = new ClassProcessor();
        processor.compile(AbstractCompiler.classToPath(nameAsString));
        return processor.getCompilationUnit();
    }

    /**
     * Converts the fields in an Entity to snake case whcih is the usual pattern for columns
     * @param str
     * @return
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
                queries.put(n, queryBuilder(query, nt));
            } else {
                parseNonAnnotatedMethod(n);
            }
        }
    }

    RepositoryQuery queryBuilder(String query, boolean isNative) {
        RepositoryQuery rql = new RepositoryQuery();
        rql.setIsNative(isNative);
        rql.setEntityType(entityType);
        rql.setTable(table);
        rql.setQuery(query);
        return rql;
    }

    void parseNonAnnotatedMethod(MethodDeclaration md) {
        String methodName = md.getNameAsString();
        List<String> components = extractComponents(methodName);
        StringBuilder sql = new StringBuilder();
        boolean top = false;
        boolean ordering = false;
        String next = "";

        for (int i = 0; i < components.size(); i++) {
            String component = components.get(i);
            String tableName = findTableName(entityCu);
            if (tableName != null) {
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
            } else {
                logger.warn("Table name cannot be null");
            }
        }
        if (top) {
            if (dialect.equals(ORACLE)) {
                sql.append(" AND ROWNUM = 1");
            } else {
                sql.append(" LIMIT 1");
            }
        }
        queries.put(md, queryBuilder(sql.toString(), true));
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

    public MethodDeclaration findMethodDeclaration(MethodCallExpr methodCall) {
        List<MethodDeclaration> methods = cu.getTypes().get(0).getMethodsByName(methodCall.getNameAsString());
        return findMethodDeclaration(methodCall, methods).orElse(null);
    }

    public static boolean isOracle() {
        return dialect.equals(ORACLE);
    }

}

