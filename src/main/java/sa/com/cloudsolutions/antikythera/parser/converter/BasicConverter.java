package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
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
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.BaseRepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BasicConverter {

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
    public static void convertFieldsToSnakeCase(Statement stmt, TypeWrapper entity) throws AntikytheraException {

        if (stmt instanceof Select sel) {
            PlainSelect select = sel.getPlainSelect();

            List<SelectItem<?>> items = select.getSelectItems();
            if (items.size() == 1 && items.getFirst().toString().length() == 1) {
                // This is a select * query but because it's an HQL query it appears as SELECT t
                // replace select t with select *
                items.set(0, SelectItem.from(new AllColumns()));
            } else {
                BasicConverter.generalProjections(items);
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
    private static void processJoins(TypeWrapper entity, PlainSelect select) throws AntikytheraException {
        List<TypeWrapper> units = new ArrayList<>();
        units.add(entity);

        List<Join> joins = select.getJoins();
        if (joins != null) {
            for (Join j : joins) {
                if (j.getRightItem() instanceof ParenthesedSelect ps) {
                    convertFieldsToSnakeCase(ps.getSelectBody(), entity);
                } else {
                    BasicConverter.processJoin(j, units);
                }
            }
        }
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

    private static void processJoin(Join j, List<TypeWrapper> units) throws AntikytheraException {
        FromItem a = j.getRightItem();
        // the toString() of this will look something like p.dischargeNurseRequest n
        // from this we need to extract the dischargeNurseRequest
        String[] parts = a.toString().split("\\.");
        if (parts.length == 2) {
            TypeWrapper other = BasicConverter.processJoin(j, units, parts);
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
                    rhs = lhs = BasicConverter.implicitJoin(other, lhs);
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


}
