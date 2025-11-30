package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Function;
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

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.BasicConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseRepositoryQuery {
    /**
     * This is the list of parameters that are defined in the function signature
     */
    protected final List<QueryMethodParameter> methodParameters;

    /**
     * This is the list of arguments that are passed to the function when being
     * called.
     */
    protected final List<QueryMethodArgument> methodArguments;
    private final Pattern selectPattern = Pattern.compile("SELECT\\s+\\w+\\s+FROM\\s+(\\w+)\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE);
    @SuppressWarnings("java:S5852")
    private final Pattern newEntityPattern = Pattern.compile("new\\s+.*?\\s+from\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * The method declaration that represents the query on the JPARepository
     */
    Callable methodDeclaration;

    /**
     * The fully qualified name of the repository class that contains this query.
     * This is set during query creation to avoid AST traversal issues later.
     */
    private String repositoryClassName;

    /**
     * The type of the entity that is being queried.
     */
    protected Type entityType;

    /**
     * The table that the entity is mapped to.
     */
    private String primaryTable;

    /**
     * The jsqlparser statement that represents the query.
     *
     * When we are dealing with derived queries, this will be the statement that we
     * derived through
     * the intermediate HQL.
     * For an annotated native query this will be directly created from the query
     * text.
     * JPQL queries will first be parsed with the JQL parser to create an AST, then
     * converted to SQL syntax
     */
    private Statement statement;

    /**
     * The result of the conversion from HQL to SQL
     */
    private ConversionResult conversionResult;

    QueryType queryType;

    /**
     * The original query as it was passed to the repository method.
     */
    protected String originalQuery;

    public BaseRepositoryQuery() {
        methodParameters = new ArrayList<>();
        methodArguments = new ArrayList<>();
    }

    /**
     * Recursively convert field names in expressions to snake case
     *
     * @param expr to be converted
     * @return the converted expression
     */
    @SuppressWarnings("java:S3740")
    public static net.sf.jsqlparser.expression.Expression convertExpressionToSnakeCase(
            net.sf.jsqlparser.expression.Expression expr) {
        if (expr instanceof AndExpression andExpr) {
            andExpr.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(andExpr.getLeftExpression()));
            andExpr.setRightExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(andExpr.getRightExpression()));
        } else if (expr instanceof Between between) {
            between.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(between.getLeftExpression()));
            between.setBetweenExpressionStart(
                    BaseRepositoryQuery.convertExpressionToSnakeCase(between.getBetweenExpressionStart()));
            between.setBetweenExpressionEnd(
                    BaseRepositoryQuery.convertExpressionToSnakeCase(between.getBetweenExpressionEnd()));
        } else if (expr instanceof InExpression ine) {
            ine.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(ine.getLeftExpression()));
        } else if (expr instanceof IsNullExpression isNull) {
            isNull.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(isNull.getLeftExpression()));
        } else if (expr instanceof ParenthesedExpressionList pel) {
            for (int i = 0; i < pel.size(); i++) {
                pel.getExpressions().set(i, BaseRepositoryQuery
                        .convertExpressionToSnakeCase((net.sf.jsqlparser.expression.Expression) pel.get(i)));
            }
        } else if (expr instanceof CaseExpression ce) {
            convertCaseExpression(ce);
        } else if (expr instanceof WhenClause wh) {
            wh.setWhenExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(wh.getWhenExpression()));
        } else if (expr instanceof Function function) {
            // Handle function parameters
            if (function.getParameters() != null &&
                    function.getParameters().getExpressions() instanceof ExpressionList params) {
                for (int i = 0; i < params.size(); i++) {
                    params.getExpressions().set(i, BaseRepositoryQuery
                            .convertExpressionToSnakeCase((net.sf.jsqlparser.expression.Expression) params.get(i)));
                }
            }
        } else if (expr instanceof ComparisonOperator compare) {
            compare.setRightExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(compare.getRightExpression()));
            compare.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(compare.getLeftExpression()));
        } else if (expr instanceof BinaryExpression binaryExpr) {
            binaryExpr.setLeftExpression(
                    BaseRepositoryQuery.convertExpressionToSnakeCase(binaryExpr.getLeftExpression()));
            binaryExpr.setRightExpression(
                    BaseRepositoryQuery.convertExpressionToSnakeCase(binaryExpr.getRightExpression()));
        } else if (expr instanceof Column column) {
            column.setColumnName(BaseRepositoryParser.camelToSnake(column.getColumnName()));
        }
        return expr;
    }

    private static void convertCaseExpression(CaseExpression ce) {
        // Convert switch expression if present
        if (ce.getSwitchExpression() != null) {
            ce.setSwitchExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(ce.getSwitchExpression()));
        }

        // Convert WHEN clauses
        for (int i = 0; i < ce.getWhenClauses().size(); i++) {
            WhenClause when = ce.getWhenClauses().get(i);
            when.setWhenExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(when.getWhenExpression()));
            when.setThenExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(when.getThenExpression()));
        }

        // Convert ELSE expression if present
        if (ce.getElseExpression() != null) {
            ce.setElseExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(ce.getElseExpression()));
        }
    }

    public String getQuery() {
        if (statement == null) {
            return originalQuery != null ? originalQuery : "";
        }
        return statement.toString();
    }

    public void setMethodDeclaration(Callable methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
        if (methodDeclaration.isMethodDeclaration()) {
            NodeList<Parameter> parameters = methodDeclaration.asMethodDeclaration().getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                methodParameters
                        .add(new QueryMethodParameter(methodDeclaration.asMethodDeclaration().getParameter(i), i));
            }
        }
    }

    public List<QueryMethodParameter> getMethodParameters() {
        return methodParameters;
    }

    public List<QueryMethodArgument> getMethodArguments() {
        return methodArguments;
    }

    public void setEntityType(Type entityType) {
        this.entityType = entityType;
    }

    public void setPrimaryTable(String table) {
        this.primaryTable = table;
    }

    public void setQuery(String query) {
        this.originalQuery = query;
        query = cleanUp(query);
        try {
            this.statement = CCJSqlParserUtil.parse(query);
            if (entityType != null) {
                TypeWrapper entity = BaseRepositoryParser.findEntity(entityType);
                BasicConverter.convertFieldsToSnakeCase(statement, entity);
            }
        } catch (JSQLParserException e) {
            throw new AntikytheraException("Exception parsing SQL query: " + query, e);
        }
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    /**
     * Clean up method to be called before handing over to JSQL
     *
     * @param sql the SQL to be cleaned up.
     * @return the cleaned up sql as a string
     */
    String cleanUp(String sql) {
        /*
         * First up we replace the entity name with the table name
         */
        if (entityType != null && primaryTable != null) {
            sql = sql.replace(entityType.asClassOrInterfaceType().getNameAsString(), primaryTable);
        }

        /*
         * If a JPA query is using a projection via a DTO, we will have a new keyword
         * immediately after
         * the select. Since this is Hibernate syntax and not SQL, the JSQL parser does
         * not recognize it.
         * So lets remove everything starting at the NEW keyword and finishing at the
         * FROM keyword.
         * The constructor call will be replaced by the '*' character.
         *
         * A second newEntityPattern is SELECT t FROM EntityClassName t ...
         *
         * The first step is to Use a case-insensitive regex to find and replace the NEW
         * keyword
         * and the FROM keyword
         */
        Matcher matcher = newEntityPattern.matcher(sql);
        if (matcher.find()) {
            sql = matcher.replaceAll(" * from ");
        }

        // Remove '+' signs only when they have spaces and a quotation mark on either
        // side
        sql = sql.replaceAll("\"\\s*\\+\\s*\"", " ");

        // Remove quotation marks
        sql = sql.replace("\"", "");

        Matcher selectMatcher = selectPattern.matcher(sql);
        if (selectMatcher.find()) {
            sql = selectMatcher.replaceAll("SELECT * FROM $1 $2");
            sql = sql.replace(" as ", " ");
        }

        // Remove backslashes used for line continuation
        sql = sql.replace("\\", " ");

        return sql;
    }

    public Statement getStatement() {
        return statement;
    }

    protected String getColumnName(Column expr) {
        return BaseRepositoryParser.camelToSnake(expr.getColumnName());
    }

    public Callable getMethodDeclaration() {
        return methodDeclaration;
    }

    public Type getEntityType() {
        return entityType;
    }

    public String getMethodName() {
        return methodDeclaration.getNameAsString();
    }

    public String getClassname() {
        // First try to get from stored repository class name (avoids AST traversal
        // issues)
        if (repositoryClassName != null) {
            // Extract simple class name from fully qualified name
            int lastDotIndex = repositoryClassName.lastIndexOf('.');
            return lastDotIndex >= 0 ? repositoryClassName.substring(lastDotIndex + 1) : repositoryClassName;
        }

        // Fallback to AST traversal (original approach)
        return methodDeclaration.getCallableDeclaration().findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow()
                .getNameAsString();
    }

    public String getPrimaryTable() {
        return primaryTable;
    }

    public String getRepositoryClassName() {
        return repositoryClassName;
    }

    public void setRepositoryClassName(String repositoryClassName) {
        this.repositoryClassName = repositoryClassName;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public ConversionResult getConversionResult() {
        return conversionResult;
    }

    public void setConversionResult(ConversionResult conversionResult) {
        this.conversionResult = conversionResult;
    }

    /**
     * Sets the statement from a ConversionResult (for HQL queries that have been
     * converted to SQL).
     * This method parses the converted SQL and applies field name conversion.
     *
     * @param conversionResult The conversion result containing the native SQL
     * @throws AntikytheraException if parsing fails
     */
    public void setStatementFromConversionResult(ConversionResult conversionResult) {
        if (conversionResult == null || !conversionResult.isSuccessful()) {
            throw new AntikytheraException("Cannot set statement from failed or null conversion result");
        }

        String nativeSql = conversionResult.getNativeSql();
        if (nativeSql == null || nativeSql.trim().isEmpty()) {
            throw new AntikytheraException("Conversion result contains empty SQL");
        }

        try {
            // Parse the converted SQL (not the original HQL)
            this.statement = CCJSqlParserUtil.parse(nativeSql);
            TypeWrapper entity = BaseRepositoryParser.findEntity(entityType);
            // HQLToPostgreSQLConverter already handles field name conversion and join
            // processing,
            // so we only need minimal post-processing (projection normalization, etc.)
            // Skip join processing since joins are already in SQL format
            BasicConverter.convertFieldsToSnakeCase(statement, entity, true);
        } catch (JSQLParserException e) {
            throw new AntikytheraException("Exception parsing converted SQL query: " + nativeSql, e);
        }
    }

    /**
     * Sets the original query string (for HQL queries, this is the HQL; for others,
     * it's the SQL).
     * This is separate from setQuery() to allow storing HQL while using converted
     * SQL for the statement.
     *
     * @param query The original query string
     */
    public void setOriginalQuery(String query) {
        this.originalQuery = query;
    }

    public void setQueryType(QueryType qt) {
        this.queryType = qt;
    }
}
