package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.WhenClause;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a query from a JPARepository
 */
public class RepositoryQuery {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryQuery.class);

    /**
     * Whether the query is native or not.
     * This is the value of the native flag to the @Query annotation.
     */
    boolean isNative;

    /**
     * The result set from the last execution of this query if any
     */
    private ResultSet resultSet;

    /**
     * This is the list of parameters that are defined in the function signature
     */
    private final List<QueryMethodParameter> methodParameters;
    /**
     * This is the list of arguments that are passed to the function when being called.
     */
    private final List<QueryMethodArgument> methodArguments;

    /**
     * The method declaration that represents the query on the JPARepository
     */
    MethodDeclaration methodDeclaration;

    Variable cachedResult;
    /**
     * The type of the entity that is being queried.
     */
    private Type entityType;
    /**
     * The table that the entity is mapped to.
     */
    private String table;
    /**
     * The SQL statement that represents the query.
     */
    private Statement statement;
    /**
     * The simplified statement where some arguments may have been removed.
     */
    private Statement simplifiedStatement;

    /**
     * The original query as it was passed to the repository method.
     */
    private String originalQuery;
    private ResultSet simplifiedResultSet;

    public RepositoryQuery() {
        methodParameters = new ArrayList<>();
        methodArguments = new ArrayList<>();
    }

    public boolean isNative() {
        return isNative;
    }

    public String getQuery() {
        return statement.toString();
    }

    public void setMethodDeclaration(MethodDeclaration methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
        for (int i = 0; i < methodDeclaration.getParameters().size(); i++) {
            methodParameters.add(new QueryMethodParameter(methodDeclaration.getParameter(i), i));
        }
    }

    public Variable getCachedResult() {
        return cachedResult;
    }

    public void setCachedResult(Variable cachedResult) {
        this.cachedResult = cachedResult;
    }

    /**
     * Mark that the column was actually not used in the query filters.
     * @param column
     */
    public void remove(String column, Expression right) {
        boolean matched = false;
        for (QueryMethodParameter p : methodParameters) {
            if (column.equals(p.getColumnName())) {
                p.setRemoved(true);
                matched = true;
                break;
            }
        }
        if (!matched) {
            if(right instanceof Between bw) {
                int a = ((JdbcParameter)bw.getBetweenExpressionStart()).getIndex();
                methodParameters.get(a - 1).setRemoved(true);
                int b = ((JdbcParameter)bw.getBetweenExpressionStart()).getIndex();
                methodParameters.get(b - 1).setRemoved(true);
            }
            else if(right instanceof JdbcParameter param) {
                methodParameters.get(param.getIndex() - 1).setRemoved(true);
            }
        }
    }

    public ResultSet getSimplifiedResultSet() {
        return simplifiedResultSet;
    }

    public ResultSet getResultSet() {
        return resultSet;
    }

    public void setResultSet(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public List<QueryMethodParameter> getMethodParameters() {
        return methodParameters;
    }
    public List<QueryMethodArgument> getMethodArguments() {
        return methodArguments;
    }

    public void setStatement(Statement statement) {
        this.statement = statement;
    }

    public Statement getSimplifiedStatement() {
        return simplifiedStatement;
    }

    public void mapPlaceHolders(net.sf.jsqlparser.expression.Expression right, String name) {
        if(right instanceof  JdbcParameter rhs) {
            int pos = rhs.getIndex();
            if (pos < getMethodParameters().size() -1) {
                QueryMethodParameter params = getMethodParameters().get(pos - 1);
                params.getPlaceHolderId().add(pos);
                params.setColumnName(name);
            }
        }
        else if (right instanceof JdbcNamedParameter rhs ){
            String placeHolder = rhs.getName();
            for(QueryMethodParameter p : getMethodParameters()) {
                if(p.getPlaceHolderName().equals(placeHolder)) {
                    p.setColumnName(name);
                    break;
                }
            }
        }
        else if (right instanceof ParenthesedExpressionList<?> rhs) {
            System.out.println(rhs + " not mapped");
        }
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

        if(stmt instanceof Select) {
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
                        String snakeCaseField = RepositoryParser.camelToSnake(field);
                        SelectItem<?> col = SelectItem.from(new Column(parts[0] + "." + snakeCaseField));
                        items.set(i, col);
                    }
                    else {
                        String snakeCaseField = RepositoryParser.camelToSnake(parts[0]);
                        SelectItem<?> col = SelectItem.from(new Column(snakeCaseField));
                        items.set(i, col);
                    }
                }
            }

            if (select.getWhere() != null) {
                select.setWhere(RepositoryQuery.convertExpressionToSnakeCase(select.getWhere()));
            }

            if (select.getGroupBy() != null) {
                GroupByElement group = select.getGroupBy();
                List<net.sf.jsqlparser.expression.Expression> groupBy = group.getGroupByExpressions();
                for (int i = 0; i < groupBy.size(); i++) {
                    groupBy.set(i, RepositoryQuery.convertExpressionToSnakeCase(groupBy.get(i)));
                }
            }

            if (select.getOrderByElements() != null) {
                List<OrderByElement> orderBy = select.getOrderByElements();
                for (int i = 0; i < orderBy.size(); i++) {
                    orderBy.get(i).setExpression(RepositoryQuery.convertExpressionToSnakeCase(orderBy.get(i).getExpression()));
                }
            }

            if (select.getHaving() != null) {
                select.setHaving(RepositoryQuery.convertExpressionToSnakeCase(select.getHaving()));
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
                                                    lhs = RepositoryParser.camelToSnake(pair.getValue().toString());
                                                }
                                                if (pair.getNameAsString().equals("referencedColumnName")) {
                                                    rhs = RepositoryParser.camelToSnake(pair.getValue().toString());
                                                }
                                            }
                                        } else {
                                            lhs = RepositoryParser.camelToSnake(ann.asSingleMemberAnnotationExpr().getMemberValue().toString());
                                        }
                                        break;
                                    }
                                }

                                other = RepositoryParser.findEntity(member.getElementType());

                                String tableName = RepositoryParser.findTableName(other);
                                if(RepositoryParser.isOracle()) {
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
                                                lhs = RepositoryParser.camelToSnake(column.getVariable(0).getNameAsString());
                                                rhs = lhs;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if(lhs != null && rhs != null) {
                                    if (RepositoryParser.isOracle()) {
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

    public void setEntityType(Type entityType) {
        this.entityType = entityType;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setQuery(String query) {
        this.originalQuery = query;
        query = cleanUp(query);
        try {
            this.statement = CCJSqlParserUtil.parse(query);
            this.simplifiedStatement = CCJSqlParserUtil.parse(query);

            convertFieldsToSnakeCase(simplifiedStatement, RepositoryParser.findEntity(entityType));
            convertFieldsToSnakeCase(statement, RepositoryParser.findEntity(entityType));

            if (simplifiedStatement instanceof PlainSelect ps) {
                simplifyWhereClause(ps.getWhere());
            }
        } catch (JSQLParserException e) {
            logger.debug("{} could not be parsed", query);
        } catch (IOException e) {
            logger.debug("{} entity could not be located", entityType);
        }
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    /**
     * Clean up method to be called before handing over to JSQL
     * @param sql the SQL to be cleaned up.
     * @return the cleaned up sql as a string
     */
    String cleanUp(String sql) {
        /*
         * First up we replace the entity name with the table name
         */
        if(entityType != null && table != null) {
            sql = sql.replace(entityType.asClassOrInterfaceType().getNameAsString(), table);
        }

        /*
         * If a JPA query is using a projection via a DTO, we will have a new keyword immediately after
         * the select. Since this is Hibernate syntax and not SQL, the JSQL parser does not recognize it.
         * So lets remove everything starting at the NEW keyword and finishing at the FROM keyword.
         * The constructor call will be replaced by  the '*' character.
         *
         * A second pattern is SELECT t FROM EntityClassName t ...
         *
         * The first step is to Use a case-insensitive regex to find and replace the NEW keyword
         * and the FROM keyword
         */
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

    public static String removeNumberedParams(String sql) {
        return sql.replaceAll("\\?\\d+", "?");
    }
    public Statement getStatement() {
        return statement;
    }

    public void setIsNative(boolean isNative) {
        this.isNative = isNative;
    }


    /**
     * Change the where clause so that the query is likely to be executed with success.
     *
     * By default, when we get a query, we need to figure out what arguments in the where clause
     * will give a non-empty result. That's one of the key challenges of API and Integration
     * testing.
     *
     * If we are able to run the query with a very limited where clause or a non existent where
     * clause we can then examine the result to figure out what values can actually be used to
     * @param expr the expression to be modified
     *
     */
    net.sf.jsqlparser.expression.Expression simplifyWhereClause(net.sf.jsqlparser.expression.Expression expr) {
        if (expr instanceof AndExpression andExpr) {
            andExpr.setLeftExpression(simplifyWhereClause(andExpr.getLeftExpression()));
            andExpr.setRightExpression(simplifyWhereClause(andExpr.getRightExpression()));
        }
        if (expr instanceof Between between) {
            mapPlaceHolders(between.getBetweenExpressionStart(), RepositoryParser.camelToSnake(between.getLeftExpression().toString()));
            mapPlaceHolders(between.getBetweenExpressionEnd(), RepositoryParser.camelToSnake(between.getLeftExpression().toString()));
            remove(RepositoryParser.camelToSnake(between.getLeftExpression().toString()), between);
            between.setBetweenExpressionStart(new LongValue("2"));
            between.setBetweenExpressionEnd(new LongValue("4"));
            between.setLeftExpression(new LongValue("3"));
        } else if (expr instanceof InExpression ine) {
            Column col = (Column) ine.getLeftExpression();
            if (!("hospitalId".equals(col.getColumnName()) || "hospitalGroupId".equals(col.getColumnName()))) {
                mapPlaceHolders(ine.getRightExpression(), RepositoryParser.camelToSnake(col.toString()));
                remove(RepositoryParser.camelToSnake(ine.getLeftExpression().toString()), ine);
                ine.setLeftExpression(new StringValue("1"));
                ExpressionList<net.sf.jsqlparser.expression.Expression> rightExpression = new ExpressionList<>();
                rightExpression.add(new StringValue("1"));
                ine.setRightExpression(rightExpression);
            }
        } else if (expr instanceof ComparisonOperator compare) {
            net.sf.jsqlparser.expression.Expression left = compare.getLeftExpression();
            net.sf.jsqlparser.expression.Expression right = compare.getRightExpression();
            if (left instanceof Column col && (right instanceof JdbcParameter || right instanceof JdbcNamedParameter)) {

                String name = RepositoryParser.camelToSnake(left.toString());
                mapPlaceHolders(right, name);
                if (col.getColumnName().equals("hospital_id")) {
                    compare.setRightExpression(new LongValue("59"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left));
                } else if (col.getColumnName().equals("hospital_group_id")) {
                    compare.setRightExpression(new LongValue("58"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left));
                } else  {
                    compare.setLeftExpression(new StringValue("1"));
                    compare.setRightExpression(new StringValue("1"));
                }
                remove(name, right);
                return expr;
            }
        }
        return expr;
    }


    /**
     * Recursively convert field names in expressions to snake case
     *
     * @param expr to be converted
     * @return the converted expression
     */
    public static net.sf.jsqlparser.expression.Expression convertExpressionToSnakeCase(net.sf.jsqlparser.expression.Expression expr) {
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
                pel.getExpressions().set(i, convertExpressionToSnakeCase((net.sf.jsqlparser.expression.Expression) pel.get(i)));
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
                    params.getExpressions().set(i, convertExpressionToSnakeCase((net.sf.jsqlparser.expression.Expression) params.get(i)));
                }
            }
        } else if (expr instanceof ComparisonOperator compare) {
            compare.setRightExpression(convertExpressionToSnakeCase(compare.getRightExpression()));
            compare.setLeftExpression(convertExpressionToSnakeCase(compare.getLeftExpression()));
        } else if (expr instanceof BinaryExpression binaryExpr) {
            binaryExpr.setLeftExpression(convertExpressionToSnakeCase(binaryExpr.getLeftExpression()));
            binaryExpr.setRightExpression(convertExpressionToSnakeCase(binaryExpr.getRightExpression()));
        } else if (expr instanceof Column column) {
            column.setColumnName(RepositoryParser.camelToSnake(column.getColumnName()));
        }
        return expr;
    }

    public void setSimplifedResultSet(ResultSet resultSet) {
        this.simplifiedResultSet = resultSet;
    }
}
