package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.FromItem;

import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

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
    private String dialect;
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
    private RepositoryQuery currentQuery;

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
            currentQuery = rql;

            RepositoryParser.createConnection();
            String query = rql.getQuery();
            Select stmt = (Select) rql.getStatement();

            convertFieldsToSnakeCase(stmt, entityCu);

            String sql = stmt.toString().replaceAll("\\?\\d+", "?");
            if(dialect.equals(ORACLE)) {
                sql = sql.replaceAll("(?i)true", "1")
                        .replaceAll("(?i)false", "0");
            }

            // if the sql contains more than 1 AND clause we will delete '1' IN '1'
            Pattern pattern = Pattern.compile("\\bAND\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(query);
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

                for (int j = 0; j < countPlaceholders(sql); j++) {
                    prep.setLong(j + 1, 1);
                }

                if (prep.execute()) {
                    return prep.getResultSet();
                }
            }

        } catch (SQLException e) {
            logger.error("\tUnparsable JPA Repository query: {}", rql.getQuery());
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
    static String findTableName(CompilationUnit entityCu) {
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
    private CompilationUnit findEntity(Type entity) throws IOException {
        String nameAsString = entity.asClassOrInterfaceType().resolve().describe();
        ClassProcessor processor = new ClassProcessor();
        processor.compile(AbstractCompiler.classToPath(nameAsString));
        return processor.getCompilationUnit();
    }

    /**
     * Java field names need to be converted to snake case to match the table column.
     *
     * This method does not return anything but has a side effect. The changes will be made to the
     * select statement that is passed in.
     *
     * @param stmt   the sql statement
     * @param entity a compilation unit representing the entity.
     * @throws FileNotFoundException
     */
    void convertFieldsToSnakeCase(Statement stmt, CompilationUnit entity) throws IOException {

        if(stmt instanceof  Select) {
            PlainSelect select = ((Select) stmt).getPlainSelect();

            List<SelectItem<?>> items = select.getSelectItems();
            if(items.size() == 1 && items.get(0).toString().length() == 1) {
                // This is a select * query but because it's an HQL query it appears as SELECT t
                // replace select t with select *
                items.set(0, SelectItem.from(new AllColumns()));
            }
            else {
                // here we deal with general projections
                for (int i = 0; i < items.size(); i++) {
                    SelectItem<?> item = items.get(i);
                    String itemStr = item.toString();
                    String[] parts = itemStr.split("\\.");

                    if (itemStr.contains(".") && parts.length == 2) {
                        String field = parts[1];
                        String snakeCaseField = camelToSnake(field);
                        SelectItem<?> col = SelectItem.from(new Column(parts[0] + "." + snakeCaseField));
                        items.set(i, col);
                    }
                    else {
                        String snakeCaseField = camelToSnake(parts[0]);
                        SelectItem<?> col = SelectItem.from(new Column(snakeCaseField));
                        items.set(i, col);
                    }
                }
            }

            if (select.getWhere() != null) {
                select.setWhere(convertExpressionToSnakeCase(select.getWhere()));
            }

            if (select.getGroupBy() != null) {
                GroupByElement group = select.getGroupBy();
                List<Expression> groupBy = group.getGroupByExpressions();
                for (int i = 0; i < groupBy.size(); i++) {
                    groupBy.set(i, convertExpressionToSnakeCase(groupBy.get(i)));
                }
            }

            if (select.getOrderByElements() != null) {
                List<OrderByElement> orderBy = select.getOrderByElements();
                for (int i = 0; i < orderBy.size(); i++) {
                    orderBy.get(i).setExpression(convertExpressionToSnakeCase(orderBy.get(i).getExpression()));
                }
            }

            if (select.getHaving() != null) {
                select.setHaving(convertExpressionToSnakeCase(select.getHaving()));
            }
            processJoins(entity, select);
        }

    }

    /**
     * HQL joins use entity names instead of table name and column names.
     * We need to replace those with the proper table and column name syntax if we are to execut the
     * query through JDBC.
     * @param entity the primary table or view for the join
     * @param select the select statement
     * @throws FileNotFoundException if we are unable to find related entities.
     */
    private void processJoins(CompilationUnit entity, PlainSelect select) throws IOException {
        List<CompilationUnit> units = new ArrayList<>();
        units.add(entity);

        List<Join> joins = select.getJoins();
        if(joins != null) {
            for (int i = 0 ; i < joins.size() ; i++) {
                Join j = joins.get(i);
                if (j.getRightItem() instanceof ParenthesedSelect ps) {
                    convertFieldsToSnakeCase(ps.getSelectBody(), entity);
                } else {
                    FromItem a = j.getRightItem();
                    // the toString() of this will look something like p.dischargeNurseRequest n
                    // from this we need to extract the dischargeNurseRequest
                    String[] parts = a.toString().split("\\.");
                    if (parts.length == 2) {
                        CompilationUnit other = null;
                        // the join may happen against any of the tables that we have encountered so far
                        // hence the need to loop through here.
                        for(CompilationUnit unit : units) {
                            String field = parts[1].split(" ")[0];
                            var x = unit.getType(0).getFieldByName(field);
                            if(x.isPresent()) {
                                var member = x.get();
                                String lhs = null;
                                String rhs = null;

                                // find if there is a join column annotation, that will tell us the column names
                                // to map for the on clause.
                                for (var ann : member.getAnnotations()) {
                                    if (ann.getNameAsString().equals("JoinColumn")) {
                                        if (ann.isNormalAnnotationExpr()) {
                                            for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                                                if (pair.getNameAsString().equals("name")) {
                                                    lhs = camelToSnake(pair.getValue().toString());
                                                }
                                                if (pair.getNameAsString().equals("referencedColumnName")) {
                                                    rhs = camelToSnake(pair.getValue().toString());
                                                }
                                            }
                                        } else {
                                            lhs = camelToSnake(ann.asSingleMemberAnnotationExpr().getMemberValue().toString());
                                        }
                                        break;
                                    }
                                }

                                other = findEntity(member.getElementType());

                                String tableName = findTableName(other);
                                if(dialect.equals(ORACLE)) {
                                    tableName = tableName.replace("\"","");
                                }

                                var f = j.getFromItem();
                                if (f instanceof Table) {
                                    Table t = new Table(tableName);
                                    t.setAlias(f.getAlias());
                                    j.setFromItem(t);

                                }
                                if(lhs == null || rhs == null) {
                                    // lets roll with an implicit join for now
                                    // todo fix this by figuring out the join column from other annotations
                                    for(var column : other.getType(0).getFields()) {
                                        for(var ann : column.getAnnotations()) {
                                            if(ann.getNameAsString().equals("Id")) {
                                                lhs = camelToSnake(column.getVariable(0).getNameAsString());
                                                rhs = lhs;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if(lhs != null && rhs != null) {
                                    if (dialect.equals(ORACLE)) {
                                        lhs = lhs.replace("\"", "");
                                        rhs = rhs.replace("\"", "");
                                    }
                                    EqualsTo eq = new EqualsTo();
                                    eq.setLeftExpression(new Column(parts[0] + "." + lhs));
                                    eq.setRightExpression(new Column(parts[1].split(" ")[1] + "." + rhs));
                                    j.getOnExpressions().add(eq);
                                }

                            }
                        }
                        // if we have discovered a new entity add it to our collection for looking up
                        // join fields in the next one
                        if(other != null) {
                            units.add(other);
                        }
                    }
                }
            }
        }
    }

    /**
     * Recursively convert field names in expressions to snake case
     *
     * @param expr to be converted
     * @return the converted expression
     */
     Expression convertExpressionToSnakeCase(Expression expr) {
        if (expr instanceof AndExpression andExpr) {
            andExpr.setLeftExpression(convertExpressionToSnakeCase(andExpr.getLeftExpression()));
            andExpr.setRightExpression(convertExpressionToSnakeCase(andExpr.getRightExpression()));
        } else if (expr instanceof Between between) {
            between.setLeftExpression(convertExpressionToSnakeCase(between.getLeftExpression()));
            between.setBetweenExpressionStart(convertExpressionToSnakeCase(between.getBetweenExpressionStart()));
            between.setBetweenExpressionEnd(convertExpressionToSnakeCase(between.getBetweenExpressionEnd()));
        } else if (expr instanceof InExpression ine) {
            ine.setLeftExpression(convertExpressionToSnakeCase(ine.getLeftExpression()));
        } else if (expr instanceof IsNullExpression isNull) {
            isNull.setLeftExpression(convertExpressionToSnakeCase(isNull.getLeftExpression()));
        } else if (expr instanceof ParenthesedExpressionList pel) {
            for (int i = 0; i < pel.size(); i++) {
                pel.getExpressions().set(i, convertExpressionToSnakeCase((Expression) pel.get(i)));
            }
        } else if (expr instanceof CaseExpression ce) {
            for (int i = 0; i < ce.getWhenClauses().size(); i++) {
                WhenClause when = ce.getWhenClauses().get(i);
                when.setWhenExpression(convertExpressionToSnakeCase(when.getWhenExpression()));
                when.setThenExpression(convertExpressionToSnakeCase(when.getThenExpression()));
            }
        } else if (expr instanceof WhenClause wh) {
            wh.setWhenExpression(convertExpressionToSnakeCase(wh.getWhenExpression()));
        } else if (expr instanceof Function function) {
            ExpressionList params = (ExpressionList) function.getParameters().getExpressions();
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    params.getExpressions().set(i, convertExpressionToSnakeCase((Expression) params.get(i)));
                }
            }
        } else if (expr instanceof ComparisonOperator compare) {
            compare.setRightExpression(convertExpressionToSnakeCase(compare.getRightExpression()));
            compare.setLeftExpression(convertExpressionToSnakeCase(compare.getLeftExpression()));
        } else if (expr instanceof BinaryExpression binaryExpr) {
            binaryExpr.setLeftExpression(convertExpressionToSnakeCase(binaryExpr.getLeftExpression()));
            binaryExpr.setRightExpression(convertExpressionToSnakeCase(binaryExpr.getRightExpression()));
        } else if (expr instanceof Column column) {
            column.setColumnName(camelToSnake(column.getColumnName()));
        }
        return expr;
    }

    /**
     * Change the where clause so that the query is likely to be executed with success.
     *
     * By default when we get a query, we need to figure out what arguments in the where clause
     * will give a non empty result. That's one of the key challenges of API and Integration
     * testing.
     *
     * If we are able to run the query with a very limited where clause or a non existant where
     * clause we can then examine the result to figure out what values can actually be used to
     * @param expr the expression to be modified
     *
     */
    Expression setValuesInWhereClause(Expression expr) {
        if (expr instanceof Between between) {
            currentQuery.mapPlaceHolders(between.getBetweenExpressionStart(), camelToSnake(between.getLeftExpression().toString()));
            currentQuery.mapPlaceHolders(between.getBetweenExpressionEnd(), camelToSnake(between.getLeftExpression().toString()));
            currentQuery.remove(camelToSnake(between.getLeftExpression().toString()));
            between.setBetweenExpressionStart(new LongValue("2"));
            between.setBetweenExpressionEnd(new LongValue("4"));
            between.setLeftExpression(new LongValue("3"));
        } else if (expr instanceof InExpression ine) {
            Column col = (Column) ine.getLeftExpression();
            if (!("hospitalId".equals(col.getColumnName()) || "hospitalGroupId".equals(col.getColumnName()))) {
                currentQuery.mapPlaceHolders(ine.getRightExpression(), camelToSnake(col.toString()));
                currentQuery.remove(camelToSnake(ine.getLeftExpression().toString()));
                ine.setLeftExpression(new StringValue("1"));
                ExpressionList<Expression> rightExpression = new ExpressionList<>();
                rightExpression.add(new StringValue("1"));
                ine.setRightExpression(rightExpression);
            }
        } else if (expr instanceof ComparisonOperator compare) {
            Expression left = compare.getLeftExpression();
            Expression right = compare.getRightExpression();
            if (left instanceof Column col && (right instanceof JdbcParameter || right instanceof JdbcNamedParameter)) {

                String name = camelToSnake(left.toString());
                currentQuery.mapPlaceHolders(right, name);
                if (col.getColumnName().equals("hospitalId")) {
                    compare.setRightExpression(new LongValue("59"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left));
                    return expr;
                } else if (col.getColumnName().equals("hospitalGroupId")) {
                    compare.setRightExpression(new LongValue("58"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left));
                    return expr;
                } else  {
                    currentQuery.remove(name);
                    compare.setLeftExpression(new StringValue("1"));
                    compare.setRightExpression(new StringValue("1"));
                    return expr;
                }
            }
        }
        return expr;
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
        rql.setEntity(entityType);
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

    public RepositoryQuery getCurrentQuery() {
        return currentQuery;
    }

    public void setCurrentQuery(RepositoryQuery currentQuery) {
        this.currentQuery = currentQuery;
    }
}

