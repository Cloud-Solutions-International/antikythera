package sa.com.cloudsolutions.antikythera.parser;

import net.sf.jsqlparser.JSQLParserException;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodParameter;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMappingResolver;
import com.github.javaparser.ast.body.MethodDeclaration;

import net.sf.jsqlparser.statement.select.Select;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.parser.converter.HQLParserAdapter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses JPA Repository subclasses to identify the queries that they execute.
 *
 * These queries can then be used to determine what kind of data need to be sent
 * to the controller for a valid response.
 */
public class RepositoryParser extends BaseRepositoryParser {
    protected static final Logger logger = LoggerFactory.getLogger(RepositoryParser.class);
    /**
     * The connection to the database established using the credentials in the configuration
     */
    private static Connection conn;
    /**
     * Whether queries should actually be executed or not.
     * As determined by the configurations
     */
    private static boolean runQueries;

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


    public RepositoryParser() throws IOException {
        super();

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
}

