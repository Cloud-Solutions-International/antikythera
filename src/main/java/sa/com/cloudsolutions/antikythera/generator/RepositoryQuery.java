package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;

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
    public void remove(String column) {
        for (QueryMethodParameter p : methodParameters) {
            if (p != null && p.columnName != null && p.columnName.equals(column)) {
                p.removed = true;
            }
        }
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
            RepositoryQuery.QueryMethodParameter params = getMethodParameters().get(pos - 1);
            params.getPlaceHolderId().add(pos);
            params.setColumnName(name);

            logger.debug("Mapping " + name + " to " + params.getParameter().getName());
        }
        else {
            String placeHolder = ((JdbcNamedParameter) right).getName();
            for(RepositoryQuery.QueryMethodParameter p : getMethodParameters()) {
                if(p.getPlaceHolderName().equals(placeHolder)) {
                    p.setColumnName(name);
                    logger.debug("Mapping " + name + " to " + p.getParameter().getName());
                    break;
                }
            }
        }
    }

    public void setEntity(Type entityType) {
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

        } catch (JSQLParserException e) {
            logger.debug("{} could not be parsed", query);
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
        if (expr instanceof Between between) {
            mapPlaceHolders(between.getBetweenExpressionStart(), RepositoryParser.camelToSnake(between.getLeftExpression().toString()));
            mapPlaceHolders(between.getBetweenExpressionEnd(), RepositoryParser.camelToSnake(between.getLeftExpression().toString()));
            remove(RepositoryParser.camelToSnake(between.getLeftExpression().toString()));
            between.setBetweenExpressionStart(new LongValue("2"));
            between.setBetweenExpressionEnd(new LongValue("4"));
            between.setLeftExpression(new LongValue("3"));
        } else if (expr instanceof InExpression ine) {
            Column col = (Column) ine.getLeftExpression();
            if (!("hospitalId".equals(col.getColumnName()) || "hospitalGroupId".equals(col.getColumnName()))) {
                mapPlaceHolders(ine.getRightExpression(), RepositoryParser.camelToSnake(col.toString()));
                remove(RepositoryParser.camelToSnake(ine.getLeftExpression().toString()));
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
                if (col.getColumnName().equals("hospitalId")) {
                    compare.setRightExpression(new LongValue("59"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left));
                    return expr;
                } else if (col.getColumnName().equals("hospitalGroupId")) {
                    compare.setRightExpression(new LongValue("58"));
                    compare.setLeftExpression(convertExpressionToSnakeCase(left));
                    return expr;
                } else  {
                    remove(name);
                    compare.setLeftExpression(new StringValue("1"));
                    compare.setRightExpression(new StringValue("1"));
                    return expr;
                }
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

    /**
     * Represents a parameter in the query method.
     *
     * Lets get the normenclature sorted out. Though arguments and parameters are used interchangeably
     * they have a subtle difference.
     *     An argument is the value that is passed to a function when it is called.
     *     A parameter refers to the variable that is used in the function declaration
     */
    public static class QueryMethodParameter {
        /**
         * The name of the argument as defined in the respository function
         */
        Parameter parameter;

        private String placeHolderName;

        /**
         * Typically column names in SQL are camel_cased.
         * Those mappings will be saved here.
         */
        private String columnName;

        /**
         * True if this column was removed from the WHERE clause or GROUP BY.
         */
        boolean removed;

        private List<Integer> placeHolderId;

        /**
         * The position at which this parameter appears in the function parameters list.
         */
        int paramIndex;

        public QueryMethodParameter(Parameter parameter, int index) {
            this.parameter = parameter;
            parameter.getAnnotationByName("Param").ifPresent(a ->
                    setPlaceHolderName(a.asStringLiteralExpr().asString())
            );
            setPlaceHolderId(new ArrayList<>());
            this.paramIndex = index;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        /**
         * The name of the jdbc named parameter.
         * These can typicall be identified in the JPARepository function by the @Param annotation.
         * If they are used along side custom queries, the query will have a place holder that starts
         * with the : character and matches the name of the parameter.
         * example:
         *     select u from User u where u.firstname = :firstname or u.lastname = :lastname
         *     User findByLastnameOrFirstname(@Param("lastname") String lastname,
         *                                  @Param("firstname") String firstname);
         */
        public String getPlaceHolderName() {
            return placeHolderName;
        }

        public void setPlaceHolderName(String placeHolderName) {
            this.placeHolderName = placeHolderName;
        }

        /**
         * Tracks usage of this column with numeric jdbc query parameters
         * Unlike named parameters, numeric parameters can appear in multiple places. THough the same
         * number can be used in two different places it's common to see programmers use two different ids.
         * These numbers will start counting from 1, in keeping with the usual jdbc convention.
         */
        public List<Integer> getPlaceHolderId() {
            return placeHolderId;
        }

        public void setPlaceHolderId(List<Integer> placeHolderId) {
            this.placeHolderId = placeHolderId;
        }

        public Parameter getParameter() {
            return parameter;
        }
    }

    /**
     * Represents a argument in a call to a query method
     */
    public static class QueryMethodArgument {
        /**
         * The name of the argument as defined in the respository function
         */
        private Expression argument;

        private int index;

        public QueryMethodArgument(Expression argument, int index) {
            this.argument = argument;
            this.index = index;
        }

        public Expression getArgument() {
            return argument;
        }
    }


}
