package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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

import net.sf.jsqlparser.util.deparser.StatementDeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
public class RepositoryParser extends ClassProcessor{
    private static final Logger logger = LoggerFactory.getLogger(RepositoryParser.class);
    public static final String JPA_REPOSITORY = "JpaRepository";

    /**
     * The queries that were identified in this repository
     */
    private Map<String, RepositoryQuery> queries;
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
    private RepositoryQuery current;

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
        cu.accept(new Visitor(), null);

        var cls = cu.getTypes().get(0).asClassOrInterfaceDeclaration();
        var parents = cls.getExtendedTypes();
        if(!parents.isEmpty() && parents.get(0).toString().startsWith(JPA_REPOSITORY)) {
            Optional<NodeList<Type>> t = parents.get(0).getTypeArguments();
            if(t.isPresent()) {
                entityType = t.get().get(0);
                entityCu = findEntity(entityType);
                table = findTableName(entityCu);

            }
        }
    }

    /**
     * Execute all the queries that were identified.
     * This is usuefull only for visualization purposes.
     * @throws IOException
     * @throws SQLException
     */
    public void executeAllQueries() throws IOException, SQLException {
        for (var entry : queries.entrySet()) {
            ResultSet rs = executeQuery(entry.getKey(), entry.getValue());
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
     * Execute the query given in entry.value
     * @param method the name of the method that represents the query in the JPARepository interface
     * @param rql the actual query
     * @throws FileNotFoundException rasied by covertFieldsToSnakeCase
     * @return the result set if the query was executed successfully
     */
    public ResultSet executeQuery(String method, RepositoryQuery rql) throws FileNotFoundException {
        try {
            current = rql;

            RepositoryParser.createConnection();
            String query = rql.getQuery().replace(entityType.asClassOrInterfaceType().getNameAsString(), table);
            Select stmt = (Select) CCJSqlParserUtil.parse(cleanUp(query));

            convertFieldsToSnakeCase(stmt, entityCu);

            String sql = stmt.toString().replaceAll("\\?\\d+", "?");
            if(dialect.equals(ORACLE)) {
                sql = sql.replaceAll("(?i)true", "1")
                        .replaceAll("(?i)false", "0");
            }

            // if the sql contains more than 1 and clause we will delete '1' IN '1'
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
            logger.debug("{} : {}",method , sql);

            if(runQueries) {
                PreparedStatement prep = conn.prepareStatement(sql);

                for (int j = 0; j < countPlaceholders(sql); j++) {
                    prep.setLong(j + 1, 1);
                }

                if (prep.execute()) {
                    ResultSet rs = prep.getResultSet();
                    return rs;
                }
            }

        } catch (JSQLParserException | SQLException e) {
            logger.error("\tUnparsable: {}", rql.getQuery());
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
    private static String findTableName(CompilationUnit entityCu) {
        String table = null;
        if(entityCu != null) {
            for (var ann : entityCu.getTypes().get(0).getAnnotations()) {
                if (ann.getNameAsString().equals("Table")) {
                    if (ann.isNormalAnnotationExpr()) {
                        for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if (pair.getNameAsString().equals("name")) {
                                table = pair.getValue().toString();
                            }
                        }
                    } else {
                        table = ann.asSingleMemberAnnotationExpr().getMemberValue().toString();
                    }
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
     * @param entity a type representing the entity
     * @return a compilation unit
     * @throws FileNotFoundException if the entity cannot be found in the AUT
     */
    private CompilationUnit findEntity(Type entity) throws FileNotFoundException {
        String nameAsString = entity.asClassOrInterfaceType().resolve().describe();
        String fileName = basePath + File.separator + nameAsString.replaceAll("\\.","/") + SUFFIX;
        return javaParser.parse(
                new File(fileName)).getResult().orElseThrow(
                        () -> new IllegalStateException("Parse error")
        );
    }

    /**
     * Java field names need to be converted to snake case to match the table column.
     *
     * @param stmt   the sql statement
     * @param entity a compilation unit representing the entity.
     * @return
     * @throws FileNotFoundException
     */
    private void convertFieldsToSnakeCase(Statement stmt, CompilationUnit entity) throws FileNotFoundException {

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
                select.setWhere(convertExpressionToSnakeCase(select.getWhere(), true));
            }

            if (select.getGroupBy() != null) {
                GroupByElement group = select.getGroupBy();
                List<Expression> groupBy = group.getGroupByExpressions();
                for (int i = 0; i < groupBy.size(); i++) {
                    groupBy.set(i, convertExpressionToSnakeCase(groupBy.get(i), false));
                }
            }

            if (select.getOrderByElements() != null) {
                List<OrderByElement> orderBy = select.getOrderByElements();
                for (int i = 0; i < orderBy.size(); i++) {
                    orderBy.get(i).setExpression(convertExpressionToSnakeCase(orderBy.get(i).getExpression(), false));
                }
            }

            if (select.getHaving() != null) {
                select.setHaving(convertExpressionToSnakeCase(select.getHaving(), false));
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
    private void processJoins(CompilationUnit entity, PlainSelect select) throws FileNotFoundException {
        List<CompilationUnit> units = new ArrayList<>();
        units.add(entity);

        List<Join> joins = select.getJoins();
        if(joins != null) {
            for (int i = 0 ; i < joins.size() ; i++) {
                Join j = joins.get(i);
                if (j.getRightItem() instanceof ParenthesedSelect) {
                    convertFieldsToSnakeCase(((ParenthesedSelect) j.getRightItem()).getSelectBody(), entity);
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
     * We violate SRP by also cleaning up some of the fields in the where clause. What we
     * hope to achieve through repository query parsing is to identify suitable data for
     * our test cases. That means the more filters that we apply the less likely that we
     * will get any results from query execution. Then the thing to do is to remove the
     * conditions.
     *
     * @param expr to be converted
     * @return the converted expression
     */
    private Expression convertExpressionToSnakeCase(Expression expr, boolean where) {
        if (expr instanceof AndExpression) {
            AndExpression andExpr = (AndExpression) expr;
            andExpr.setLeftExpression(convertExpressionToSnakeCase(andExpr.getLeftExpression(), where));
            andExpr.setRightExpression(convertExpressionToSnakeCase(andExpr.getRightExpression(), where));
        }
        else if (expr instanceof Between) {
            Between between = (Between) expr;
            between.setLeftExpression(convertExpressionToSnakeCase(between.getLeftExpression(), where));
            Expression start = between.getBetweenExpressionStart();
            Expression end = between.getBetweenExpressionEnd();

            mapPlaceHolders(start, camelToSnake(between.getLeftExpression().toString()));
            mapPlaceHolders(end, camelToSnake(between.getLeftExpression().toString()));
            current.remove(camelToSnake(between.getLeftExpression().toString()));
            between.setBetweenExpressionStart(new LongValue("2"));
            between.setBetweenExpressionEnd(new LongValue("4"));
            between.setLeftExpression(new LongValue("3"));
        }
        else if (expr instanceof  InExpression) {
            InExpression ine = (InExpression) expr;
            Column col = (Column) ine.getLeftExpression();
            if(where &&
                    !("hospitalId".equals(col.getColumnName()) || "hospitalGroupId".equals(col.getColumnName()))) {
                mapPlaceHolders(ine.getRightExpression(), camelToSnake(col.toString()));
                current.remove(camelToSnake(ine.getLeftExpression().toString()));
                ine.setLeftExpression(new StringValue("1"));
                ExpressionList<Expression> rightExpression = new ExpressionList<>();

                rightExpression.add(new StringValue("1"));
                ine.setRightExpression(rightExpression);
            }
            else {
                ine.setLeftExpression(convertExpressionToSnakeCase(ine.getLeftExpression(), where));
            }
        }
        else if (expr instanceof IsNullExpression) {
            IsNullExpression isNull = (IsNullExpression) expr;
            isNull.setLeftExpression(convertExpressionToSnakeCase(isNull.getLeftExpression(), where));
        }
        else if (expr instanceof ParenthesedExpressionList) {
            ParenthesedExpressionList pel = (ParenthesedExpressionList) expr;
            for(int i = 0 ; i < pel.size() ; i++) {
                pel.getExpressions().set(i, convertExpressionToSnakeCase((Expression) pel.get(i), where));
            }
        }
        else if (expr instanceof CaseExpression) {
            CaseExpression ce = (CaseExpression) expr;
            for(int i = 0; i < ce.getWhenClauses().size(); i++) {
                WhenClause when = ce.getWhenClauses().get(i);
                when.setWhenExpression(convertExpressionToSnakeCase(when.getWhenExpression(), where));
                when.setThenExpression(convertExpressionToSnakeCase(when.getThenExpression(), where));
            }

        }
        else if (expr instanceof WhenClause) {
            WhenClause wh = (WhenClause) expr;
            wh.setWhenExpression(convertExpressionToSnakeCase(wh.getWhenExpression(), where));
        }
        else if (expr instanceof Function) {
            Function function = (Function) expr;
            ExpressionList params = (ExpressionList) function.getParameters().getExpressions();
            if(params != null) {
                for (int i = 0; i < params.size(); i++) {
                    params.getExpressions().set(i, convertExpressionToSnakeCase((Expression) params.get(i), where));
                }
            }
        }
        else if (expr instanceof ComparisonOperator) {
            // this will be the leaf for Most WHERE clauses and HAVING clauses
            ComparisonOperator compare = (ComparisonOperator) expr;

            Expression left = compare.getLeftExpression();
            Expression right = compare.getRightExpression();

            if(left instanceof Column && right instanceof JdbcParameter || right instanceof JdbcNamedParameter) {
                Column col = (Column) left;
                String name = camelToSnake(left.toString());

                mapPlaceHolders(right, name);

                if(col.getColumnName().equals("hospitalId")) {
                    compare.setRightExpression(new LongValue("59"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left, where));
                    return expr;
                }
                else if(col.getColumnName().equals("hospitalGroupId")) {
                    compare.setRightExpression(new LongValue("58"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left, where));
                    return expr;
                }
                else if (where){
                    // we have a comparison so this is vary likely to be a part of the where clause.
                    // our object is to run a query to identify likely data. So removing as many
                    // components from the where clause is the way to go

                    current.remove(name);
                    compare.setLeftExpression(new StringValue("1"));
                    compare.setRightExpression(new StringValue("1"));
                    return expr;
                }
            }

            // common fall through for everything
            compare.setRightExpression(convertExpressionToSnakeCase(right, where));
            compare.setLeftExpression(convertExpressionToSnakeCase(left, where));

        }
        else if (expr instanceof BinaryExpression) {
            BinaryExpression binaryExpr = (BinaryExpression) expr;
            binaryExpr.setLeftExpression(convertExpressionToSnakeCase(binaryExpr.getLeftExpression(), where));
            binaryExpr.setRightExpression(convertExpressionToSnakeCase(binaryExpr.getRightExpression(), where));
        } else if (expr instanceof Column) {
            Column column = (Column) expr;
            String columnName = column.getColumnName();

            String snakeCaseField = camelToSnake(columnName);
            column.setColumnName(snakeCaseField);
            return column;
        }
        return expr;
    }

    private void mapPlaceHolders(Expression right, String name) {
        if(right instanceof  JdbcParameter) {
            int pos = ((JdbcParameter) right).getIndex();
            RepositoryQuery.QueryMethodParameter params = current.getMethodParameters().get(pos - 1);
            params.placeHolderId.add(pos);
            params.setColumnName(name);

            System.out.println("Mapping " + name + " to " + params.parameter.getName());
        }
        else {
            String placeHolder = ((JdbcNamedParameter) right).getName();
            for(RepositoryQuery.QueryMethodParameter p : current.getMethodParameters()) {
                if(p.placeHolderName.equals(placeHolder)) {
                    p.setColumnName(name);
                    System.out.println("Mapping " + name + " to " + p.parameter.getName());
                    break;
                }
            }
        }
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

    /**
     * Visitor to iterate through the methods in the repository
     */
    class Visitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            String query = null;
            String methodName = n.getNameAsString();
            boolean nt = false;

            for (var ann : n.getAnnotations()) {
                if (ann.getNameAsString().equals("Query")) {
                    if (ann.isSingleMemberAnnotationExpr()) {
                        query = ann.asSingleMemberAnnotationExpr().getMemberValue().toString();
                    } else if (ann.isNormalAnnotationExpr()) {

                        for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if (pair.getNameAsString().equals("nativeQuery")) {
                                if (pair.getValue().toString().equals("true")) {
                                    nt = true;
                                }
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

            if(query != null) {
                queries.put(n.getNameAsString(), new RepositoryQuery(cleanUp(query), nt));
            }
            else {
                List<String> components = extractComponents(methodName);
                StringBuilder sql = new StringBuilder();
                boolean top = false;
                boolean ordering = false;
                for(int i= 0 ; i < components.size() ; i++) {
                    String component = components.get(i);
                    String tableName = findTableName(entityCu);
                    if(tableName != null){
                        switch (component) {
                            case "findBy":
                            case "get":
                                sql.append("SELECT * FROM ")
                                        .append(tableName.replace("\"", ""))
                                        .append(" WHERE ");

                                break;
                            case "findFirstBy":
                            case "findTopBy":
                                top = true;
                                sql.append("SELECT * FROM ")
                                        .append(tableName.replace("\"", ""))
                                        .append(" WHERE ");
                                break;

                            case "And":
                            case "Or":
                            case "Not":
                                sql.append(component).append(" ");
                                break;
                            case "Containing":
                            case "Like":
                                sql.append(" LIKE '%?%'");
                                break;
                            case "OrderBy":
                                ordering = true;
                                sql.append(" ORDER BY ");
                                break;
                            case "":
                                break;
                            default:
                                sql.append(camelToSnake(component));
                                if (!ordering) {
                                    if (i < components.size() - 1 && components.get(i + 1).equals("In")) {
                                        sql.append(" In  (?) ");
                                        i++;
                                    } else {
                                        sql.append(" = ? ");
                                    }
                                } else {
                                    sql.append(" ");
                                }

                        }
                    }
                    else {
                        logger.warn("Table name cannot be null");
                    }
                }
                if(top) {
                    if(dialect.equals(ORACLE)) {
                        sql.append(" FETCH FIRST 1 ROWS ONLY");
                    }
                    else {
                        sql.append(" LIMIT 1");
                    }
                }
                queries.put(n.getNameAsString(), new RepositoryQuery(sql.toString(), true));
            }
        }

        /**
         * Recursively search method names for sql components
         * @param methodName name of the method
         * @return a list of components
         */
        private  List<String> extractComponents(String methodName) {
            List<String> components = new ArrayList<>();
            String keywords = "get|findBy|findFirstBy|findTopBy|And|OrderBy|In|Desc|Not|Containing|Like|Or";
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
    }

    /**
     * CLean up method to be called before handing over to JSQL
     * @param sql
     * @return the cleaned up sql as a string
     */
    private String cleanUp(String sql) {
        // If a JPA query is using a projection via a DTO, we will have a new keyword immediately after
        // the select JSQL does not recognize this. So lets remove everything starting at the NEW keyword
        // and finishing at the FROM keyword. It will be replaced by  the '*' character.
        //
        // A second pattern is SELECT t FROM EntityClassName t ...

        // Use case-insensitive regex to find and replace the NEW keyword and the FROM keyword
        Pattern pattern = Pattern.compile("new\\s+.*?\\s+from\\s+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            sql = matcher.replaceAll(" * from ");
        }

        // Remove '+' signs only when they have spaces and a quotation mark on either side
        sql = sql.replaceAll("\"\\s*\\+\\s*\"", " ");

        // Remove quotation marks
        sql = sql.replace("\"", "");

        Pattern selectPattern = Pattern.compile("SELECT\\s+\\w+\\s+FROM\\s+(\\w+)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher selectMatcher = selectPattern.matcher(sql);
        if (selectMatcher.find()) {
            sql = selectMatcher.replaceAll("SELECT * FROM $1 $2");
            sql = sql.replace(" as "," ");
        }

        return sql;
    }

    public Map<String, RepositoryQuery> getQueries() {
        return queries;
    }

}

