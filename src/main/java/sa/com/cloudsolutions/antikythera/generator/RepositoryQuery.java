package sa.com.cloudsolutions.antikythera.generator;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;

import java.sql.ResultSet;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a query from a JPARepository
 */
public class RepositoryQuery extends BaseRepositoryQuery {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryQuery.class);

    /**
     * The result set from the last execution of this query if any
     */
    private ResultSet resultSet;
    /**
     * The result set from running the query with only predefined parameters.
     * When the default query is being executed the presence of many filters will typically cause
     * the result set to be empty. You are more likely to get a non-empty result when you have a
     * small number of filters. This simplifiedResultSet represents that.
     */
    private ResultSet simplifiedResultSet;

    /**
     * Running the same query repeatedly will slow things down and be wastefull, lets cache it.
     */
    Variable cachedResult;

    /**
     * The simplified statement where some arguments may have been removed.
     */
    private Statement simplifiedStatement;

    private boolean writeOps;

    public Variable getCachedResult() {
        return cachedResult;
    }

    /**
     * Mark that the column was actually not used in the query filters of the simplified query.
     * @param column the column name that was removed from the filters
     * @param right the right hand side of the original expression
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

    /**
     * Get the result set for the simplified query. This will often be non-empty.
     * @return the result set for the simplified query
     */
    public ResultSet getSimplifiedResultSet() {
        return simplifiedResultSet;
    }

    /**
     * Get the result set for the un tampered query
     * @return a JDBC result set which is likely to be empty in most situations.
     */
    public ResultSet getResultSet() {
        return resultSet;
    }

    public void setResultSet(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public boolean mapPlaceHolders(net.sf.jsqlparser.expression.Expression right, String name) {
        if(right instanceof  JdbcParameter rhs) {
            int pos = rhs.getIndex();
            if (pos <= getMethodParameters().size() ) {
                QueryMethodArgument arg = getMethodArguments().get(pos - 1);
                if (arg.getArgument().isLiteralExpr()) {
                    return false;
                }
                QueryMethodParameter params = getMethodParameters().get(pos - 1);
                params.getPlaceHolderId().add(pos);
                params.setColumnName(name);
                return true;
            }
        }
        else if (right instanceof JdbcNamedParameter rhs ){
            String placeHolder = rhs.getName();
            for(QueryMethodParameter p : getMethodParameters()) {
                if(p.getPlaceHolderName().equals(placeHolder)) {
                    p.setColumnName(name);
                    return true;
                }
            }
        }
        else if (right instanceof ParenthesedExpressionList<?> rhs) {
            System.out.println(rhs + " not mapped");
        }
        return false;
    }


    /**
     * Change the where clause so that the query is likely to be executed with success.
     *
     * By default, when we get a query, we need to figure out what arguments in the where clause
     * will give a non-empty result. That's one of the key challenges of API and Integration
     * testing.
     *
     * If we are able to run the query with a very limited where clause or a non-existent where
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
            String left = between.getLeftExpression().toString();
            if (mapPlaceHolders(between.getBetweenExpressionStart(), RepositoryParser.camelToSnake(left)) &&
                    mapPlaceHolders(between.getBetweenExpressionEnd(), RepositoryParser.camelToSnake(left))) {

                remove(RepositoryParser.camelToSnake(left), between);
                between.setBetweenExpressionStart(new LongValue("2"));
                between.setBetweenExpressionEnd(new LongValue("4"));
                between.setLeftExpression(new LongValue("3"));
            }
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
            return simplifyCompare(expr, compare);
        }
        return expr;
    }

    private Expression simplifyCompare(Expression expr, ComparisonOperator compare) {
        Expression left = compare.getLeftExpression();
        Expression right = compare.getRightExpression();
        if (left instanceof Column col && (right instanceof JdbcParameter || right instanceof JdbcNamedParameter)) {
            String name = getColumnName(col);
            if (mapPlaceHolders(right, name)) {
                Optional<Map> params = Settings.getProperty("database.parameters", Map.class);
                if (params.isPresent()) {

                    for (Object param : params.get().entrySet()) {
                        if (param instanceof Map.Entry<?, ?> entry && entry.getKey() instanceof String
                                && col.getColumnName().equals(entry.getKey().toString())) {
                            compare.setRightExpression(new LongValue(entry.getValue().toString()));
                            remove(name, right);
                            return expr;
                        }
                    }
                }
                compare.setLeftExpression(new StringValue("1"));
                compare.setRightExpression(new StringValue("1"));
                remove(compare.getRightExpression().toString(), right);

            }
        }
        return expr;
    }


    /**
     * Sets the result set for the simplified query.
     * It should already have advanced to the first row.
     * @param resultSet the result set for the simplified query (the one with minimal filters)
     */
    public void setSimplifedResultSet(ResultSet resultSet) {
        this.simplifiedResultSet = resultSet;
    }

    public void setWriteOps(boolean b) {
        this.writeOps = b;
    }

    public boolean isWriteOps() {
        return writeOps;
    }

    public Statement getSimplifiedStatement() {
        return simplifiedStatement;
    }

    public void buildSimplifiedQuery() throws JSQLParserException, AntikytheraException {
        this.simplifiedStatement = CCJSqlParserUtil.parse(cleanUp(this.originalQuery));
        TypeWrapper entity = RepositoryParser.findEntity(entityType);
        convertFieldsToSnakeCase(simplifiedStatement, entity);

        if (simplifiedStatement instanceof PlainSelect ps) {
            simplifyWhereClause(ps.getWhere());
        }
    }

}
