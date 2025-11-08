package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.Type;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Function;
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
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseRepositoryQuery {
    private static final Logger logger = LoggerFactory.getLogger(BaseRepositoryQuery.class);

    /**
     * This is the list of parameters that are defined in the function signature
     */
    protected final List<QueryMethodParameter> methodParameters;

    /**
     * This is the list of arguments that are passed to the function when being called.
     */
    protected final List<QueryMethodArgument> methodArguments;

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
     * When we are dealing with derived queries, this will be the statement that we derived through
     * the intermedia HQL.
     * For an annotated native query this will be directly created from the query test.
     * JPQL queries will first be parsed with the JQL parser to create an AST, then converted to SQL syntax
     */
    private Statement statement;

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

    protected static void generalProjections(List<SelectItem<?>> items) {
        for (int i = 0; i < items.size(); i++) {
            SelectItem<?> item = items.get(i);

            // Get the expression from the SelectItem
            if (item.getExpression() != null) {
                // Convert the expression using the existing convertExpressionToSnakeCase method
                net.sf.jsqlparser.expression.Expression convertedExpression =
                        BaseRepositoryQuery.convertExpressionToSnakeCase(item.getExpression());

                // Create a new SelectItem with the converted expression
                SelectItem<?> convertedItem = SelectItem.from(convertedExpression);

                // Preserve alias if it exists
                if (item.getAlias() != null) {
                    convertedItem.setAlias(item.getAlias());
                }

                items.set(i, convertedItem);
            } else {
                // Fallback to the old logic for simple cases
                String itemStr = item.toString();
                String[] parts = itemStr.split("\\.");

                if (itemStr.contains(".") && parts.length == 2 && !itemStr.contains("(")) {
                    String field = parts[1];
                    String snakeCaseField = BaseRepositoryParser.camelToSnake(field);
                    SelectItem<?> col = SelectItem.from(new Column(parts[0] + "." + snakeCaseField));
                    items.set(i, col);
                } else if (!itemStr.contains("(") && !itemStr.contains("*")) {
                    String snakeCaseField = BaseRepositoryParser.camelToSnake(parts[0]);
                    SelectItem<?> col = SelectItem.from(new Column(snakeCaseField));
                    items.set(i, col);
                }
                // If it contains functions or complex expressions, leave it as-is
            }
        }
    }

    public Optional<AnnotationExpr> getQueryAnnotation() {
        return methodDeclaration.getCallableDeclaration().getAnnotationByName("Query");
    }

    private static void processJoin(Join j, List<TypeWrapper> units) throws AntikytheraException {
        FromItem a = j.getRightItem();
        // the toString() of this will look something like p.dischargeNurseRequest n
        // from this we need to extract the dischargeNurseRequest
        String[] parts = a.toString().split("\\.");
        if (parts.length == 2) {
            TypeWrapper other = BaseRepositoryQuery.processJoin(j, units, parts);
            // if we have discovered a new entity add it to our collection for looking up
            // join fields in the next one
            if (other != null) {
                units.add(other);
            }
        }
    }

    protected static TypeWrapper processJoin(Join j, List<TypeWrapper> units, String[] parts) throws AntikytheraException {
        TypeWrapper other = null;
        // the join may happen against any of the tables that we have encountered so far
        // hence the need to loop through here.
        for (TypeWrapper unit : units) {
            String field = parts[1].split(" ")[0];
            Optional<FieldDeclaration> x = unit.getType().getFieldByName(field);
            if (x.isPresent()) {
                var member = x.get();
                String lhs = null;
                String rhs = null;

                // find if there is a join column annotation, that will tell us the column names
                // to map for the on clause.
                Optional<AnnotationExpr> annotationExpr =  member.getAnnotationByName("JoinColumn");

                if (annotationExpr.isPresent()) {
                    AnnotationExpr ann = annotationExpr.orElseThrow();
                    if (ann.isNormalAnnotationExpr()) {
                        for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if (pair.getNameAsString().equals("name")) {
                                lhs = BaseRepositoryParser.camelToSnake(pair.getValue().toString());
                            }
                            if (pair.getNameAsString().equals("referencedColumnName")) {
                                rhs = BaseRepositoryParser.camelToSnake(pair.getValue().toString());
                            }
                        }
                    } else {
                        lhs = BaseRepositoryParser.camelToSnake(ann.asSingleMemberAnnotationExpr().getMemberValue().toString());
                    }
                }


                other = RepositoryParser.findEntity(member.getElementType());

                String tableName = RepositoryParser.findTableName(other);
                if (tableName == null || other == null) {
                    throw new AntikytheraException("Could not find table name for " + member.getElementType());
                }
                if (RepositoryParser.isOracle()) {
                    tableName = tableName.replace("\"", "");
                }

                var f = j.getFromItem();
                if (f instanceof Table) {
                    Table t = new Table(tableName);
                    t.setAlias(f.getAlias());
                    j.setFromItem(t);

                }
                if (lhs == null || rhs == null) {
                    rhs = lhs = BaseRepositoryQuery.implicitJoin(other, lhs);
                }
                if (lhs != null && rhs != null) {
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
        return other;
    }

    private static String implicitJoin(TypeWrapper other, String lhs) {
        // lets roll with an implicit join for now
        // todo fix this by figuring out the join column from other annotations
        for (var column : other.getType().getFields()) {
            for (var ann : column.getAnnotations()) {
                if (ann.getNameAsString().equals("Id")) {
                    lhs = BaseRepositoryParser.camelToSnake(column.getVariable(0).getNameAsString());

                    break;
                }
            }
        }
        return lhs;
    }

    /**
     * Recursively convert field names in expressions to snake case
     *
     * @param expr to be converted
     * @return the converted expression
     */
    public static net.sf.jsqlparser.expression.Expression convertExpressionToSnakeCase(net.sf.jsqlparser.expression.Expression expr) {
        if (expr instanceof AndExpression andExpr) {
            andExpr.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(andExpr.getLeftExpression()));
            andExpr.setRightExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(andExpr.getRightExpression()));
        } else if (expr instanceof Between between) {
            between.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(between.getLeftExpression()));
            between.setBetweenExpressionStart(BaseRepositoryQuery.convertExpressionToSnakeCase(between.getBetweenExpressionStart()));
            between.setBetweenExpressionEnd(BaseRepositoryQuery.convertExpressionToSnakeCase(between.getBetweenExpressionEnd()));
        } else if (expr instanceof InExpression ine) {
            ine.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(ine.getLeftExpression()));
        } else if (expr instanceof IsNullExpression isNull) {
            isNull.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(isNull.getLeftExpression()));
        } else if (expr instanceof ParenthesedExpressionList pel) {
            for (int i = 0; i < pel.size(); i++) {
                pel.getExpressions().set(i, BaseRepositoryQuery.convertExpressionToSnakeCase((net.sf.jsqlparser.expression.Expression) pel.get(i)));
            }
        } else if (expr instanceof CaseExpression ce) {
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
        } else if (expr instanceof WhenClause wh) {
            wh.setWhenExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(wh.getWhenExpression()));
        } else if (expr instanceof Function function) {
            // Handle function parameters
            if (function.getParameters() != null &&
                    function.getParameters().getExpressions() instanceof ExpressionList params) {
                for (int i = 0; i < params.size(); i++) {
                    params.getExpressions().set(i, BaseRepositoryQuery.convertExpressionToSnakeCase((net.sf.jsqlparser.expression.Expression) params.get(i)));
                }
            }
        } else if (expr instanceof ComparisonOperator compare) {
            compare.setRightExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(compare.getRightExpression()));
            compare.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(compare.getLeftExpression()));
        } else if (expr instanceof BinaryExpression binaryExpr) {
            binaryExpr.setLeftExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(binaryExpr.getLeftExpression()));
            binaryExpr.setRightExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(binaryExpr.getRightExpression()));
        } else if (expr instanceof Column column) {
            column.setColumnName(BaseRepositoryParser.camelToSnake(column.getColumnName()));
        }
        return expr;
    }

    public String getQuery() {
        return statement.toString();
    }

    public void setMethodDeclaration(Callable methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
        if (methodDeclaration.isMethodDeclaration()) {
            NodeList<Parameter> parameters = methodDeclaration.asMethodDeclaration().getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                methodParameters.add(new QueryMethodParameter(methodDeclaration.asMethodDeclaration().getParameter(i), i));
            }
        }
    }

    public List<QueryMethodParameter> getMethodParameters() {
        return methodParameters;
    }

    public List<QueryMethodArgument> getMethodArguments() {
        return methodArguments;
    }

    /**
     * Java field names need to be converted to snake case to match the table column.
     * <p>
     * This method does not return anything but has a side effect. The changes will be made to the
     * select statement that is passed in.
     *
     * @param stmt   the sql statement
     * @param entity a compilation unit representing the entity.
     * @throws AntikytheraException if we are unable to find related entities.
     */
    void convertFieldsToSnakeCase(Statement stmt, TypeWrapper entity) throws AntikytheraException {

        if (stmt instanceof Select sel) {
            PlainSelect select = sel.getPlainSelect();

            List<SelectItem<?>> items = select.getSelectItems();
            if (items.size() == 1 && items.getFirst().toString().length() == 1) {
                // This is a select * query but because it's an HQL query it appears as SELECT t
                // replace select t with select *
                items.set(0, SelectItem.from(new AllColumns()));
            } else {
                BaseRepositoryQuery.generalProjections(items);
            }

            if (select.getWhere() != null) {
                select.setWhere(BaseRepositoryQuery.convertExpressionToSnakeCase(select.getWhere()));
            }

            if (select.getGroupBy() != null) {
                GroupByElement group = select.getGroupBy();
                List<net.sf.jsqlparser.expression.Expression> groupBy = group.getGroupByExpressions();
                for (int i = 0; i < groupBy.size(); i++) {
                    groupBy.set(i, BaseRepositoryQuery.convertExpressionToSnakeCase(groupBy.get(i)));
                }
            }

            if (select.getOrderByElements() != null) {
                List<OrderByElement> orderBy = select.getOrderByElements();
                for (OrderByElement orderByElement : orderBy) {
                    orderByElement.setExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(orderByElement.getExpression()));
                }
            }

            if (select.getHaving() != null) {
                select.setHaving(BaseRepositoryQuery.convertExpressionToSnakeCase(select.getHaving()));
            }
            processJoins(entity, select);
        }
    }

    /**
     * HQL joins use entity names instead of table name and column names.
     * We need to replace those with the proper table and column name syntax if we are to execut the
     * query through JDBC.
     *
     * @param entity the primary table or view for the join
     * @param select the select statement
     * @throws AntikytheraException if we are unable to find related entities.
     */
    private void processJoins(TypeWrapper entity, PlainSelect select) throws AntikytheraException {
        List<TypeWrapper> units = new ArrayList<>();
        units.add(entity);

        List<Join> joins = select.getJoins();
        if (joins != null) {
            for (Join j : joins) {
                if (j.getRightItem() instanceof ParenthesedSelect ps) {
                    convertFieldsToSnakeCase(ps.getSelectBody(), entity);
                } else {
                    BaseRepositoryQuery.processJoin(j, units);
                }
            }
        }
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
            TypeWrapper entity = RepositoryParser.findEntity(entityType);
            convertFieldsToSnakeCase(statement, entity);
        } catch (JSQLParserException e) {
            logger.debug("{} could not be parsed", query);
        } catch (AntikytheraException e) {
            logger.debug(e.getMessage());
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
            sql = sql.replace(" as ", " ");
        }

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
        // First try to get from stored repository class name (avoids AST traversal issues)
        if (repositoryClassName != null) {
            // Extract simple class name from fully qualified name
            int lastDotIndex = repositoryClassName.lastIndexOf('.');
            return lastDotIndex >= 0 ? repositoryClassName.substring(lastDotIndex + 1) : repositoryClassName;
        }

        // Fallback to AST traversal (original approach)
        return methodDeclaration.getCallableDeclaration().findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow().getNameAsString();
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
}
