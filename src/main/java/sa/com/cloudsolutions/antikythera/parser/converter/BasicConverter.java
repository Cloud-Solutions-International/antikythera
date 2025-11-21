package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
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
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BasicConverter {

    private static final String ID_ANNOTATION = "Id";

    private BasicConverter () {
        // utility class
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
    public static void convertFieldsToSnakeCase(Statement stmt, TypeWrapper entity) throws AntikytheraException {
        if (!(stmt instanceof Select sel)) {
            return; // only Select statements supported
        }
        convertPlainSelectToSnakeCase(sel.getPlainSelect(), entity);
    }

    /**
     * Convert projections, clauses, and joins of a PlainSelect to snake case where applicable.
     * Keeps method small by delegating to focused helpers.
     */
    private static void convertPlainSelectToSnakeCase(PlainSelect select, TypeWrapper entity) throws AntikytheraException {
        if (select == null) return;
        normalizeProjection(select);
        convertWhere(select);
        convertGroupBy(select);
        convertOrderBy(select);
        convertHaving(select);
        processJoins(entity, select);
    }

    // --- Projection helpers -------------------------------------------------

    private static void normalizeProjection(PlainSelect select) {
        List<SelectItem<?>> items = select.getSelectItems();
        if (items == null || items.isEmpty()) {
            return; // nothing to do
        }
        if (shouldReplaceWithStar(select, items)) {
            items.set(0, SelectItem.from(new AllColumns()));
            return;
        }
        generalProjections(items); // fall back to field-by-field conversion
    }

    /**
     * Decide if single projection should become '*'. We consider any single select item
     * that equals the FROM alias (legacy heuristic kept for length==1).
     */
    private static boolean shouldReplaceWithStar(PlainSelect select, List<SelectItem<?>> items) {
        if (items.size() != 1) return false;
        String itemText = items.getFirst().toString().trim();
        String fromAlias = select.getFromItem() != null && select.getFromItem().getAlias() != null
                ? select.getFromItem().getAlias().getName() : null;
        if (fromAlias != null && fromAlias.equals(itemText)) return true;
        return itemText.length() == 1; // legacy fallback
    }

    private static void convertWhere(PlainSelect select) {
        if (select.getWhere() != null) {
            select.setWhere(BaseRepositoryQuery.convertExpressionToSnakeCase(select.getWhere()));
        }
    }

    private static void convertGroupBy(PlainSelect select) {
        GroupByElement group = select.getGroupBy();
        if (group != null && group.getGroupByExpressionList() != null) {
            var exprList = group.getGroupByExpressionList();
            for (int i = 0; i < exprList.size(); i++) {
                Object expr = exprList.get(i);
                if (expr instanceof net.sf.jsqlparser.expression.Expression expression) {
                    exprList.set(i, BaseRepositoryQuery.convertExpressionToSnakeCase(expression));
                }
            }
        }
    }

    private static void convertOrderBy(PlainSelect select) {
        List<OrderByElement> orderBy = select.getOrderByElements();
        if (orderBy != null) {
            for (OrderByElement obe : orderBy) {
                obe.setExpression(BaseRepositoryQuery.convertExpressionToSnakeCase(obe.getExpression()));
            }
        }
    }

    private static void convertHaving(PlainSelect select) {
        if (select.getHaving() != null) {
            select.setHaving(BaseRepositoryQuery.convertExpressionToSnakeCase(select.getHaving()));
        }
    }

    // --- Join processing ----------------------------------------------------

    private static void processJoins(TypeWrapper rootEntity, PlainSelect select) throws AntikytheraException {
        List<Join> joins = select.getJoins();
        if (joins == null || joins.isEmpty()) return;
        List<TypeWrapper> discovered = new ArrayList<>();
        discovered.add(rootEntity);
        for (Join j : joins) {
            var right = j.getRightItem();
            if (right instanceof ParenthesedSelect ps && ps.getSelect() instanceof Select innerSel) {
                // recurse into nested select body
                convertPlainSelectToSnakeCase(innerSel.getPlainSelect(), rootEntity);
                continue;
            }
            resolveAndRewriteJoin(j, discovered);
        }
    }

    private static void resolveAndRewriteJoin(Join join, List<TypeWrapper> known) throws AntikytheraException {
        String raw = join.getRightItem().toString();
        String[] dotParts = raw.split("\\.");
        if (dotParts.length != 2) {
            return; // not an HQL path style join (ignore)
        }
        String lhsAlias = dotParts[0];
        String fieldAndAlias = dotParts[1];
        String[] fieldParts = fieldAndAlias.split(" ");
        if (fieldParts.length != 2) return;
        String fieldName = fieldParts[0];
        String joinAlias = fieldParts[1];

        // Attempt resolution against each known entity
        for (TypeWrapper entity : known) {
            Optional<FieldDeclaration> fieldDeclOpt = entity.getType().getFieldByName(fieldName);
            if (fieldDeclOpt.isEmpty()) continue;
            FieldDeclaration fieldDecl = fieldDeclOpt.get();
            com.github.javaparser.ast.type.Type targetType = resolveTargetEntityType(fieldDecl);
            TypeWrapper targetWrapper = BaseRepositoryParser.findEntity(targetType);
            String tableName = BaseRepositoryParser.findTableName(targetWrapper);
            if (tableName == null) {
                throw new AntikytheraException("Unable to determine table name for join field '" + fieldName + "' of type " + targetType);
            }
            if (DatabaseDialect.ORACLE.equals(BaseRepositoryParser.getDialect())) tableName = tableName.replace("\"", "");

            // Replace right side with actual table + alias
            Table rightTable = new Table(tableName);
            rightTable.setAlias(new net.sf.jsqlparser.expression.Alias(joinAlias));
            join.setRightItem(rightTable);

            // Build ON expression if needed
            buildOnExpression(join, targetWrapper, lhsAlias, joinAlias, fieldDecl);
            known.add(targetWrapper);
            return; // done for this join
        }
    }

    /** Resolve generic collection element (List<X>) or direct type. */
    private static com.github.javaparser.ast.type.Type resolveTargetEntityType(FieldDeclaration fd) {
        com.github.javaparser.ast.type.Type resolved = fd.getElementType();
        if (resolved.isClassOrInterfaceType()) {
            var cit = resolved.asClassOrInterfaceType();
            if (cit.getTypeArguments().isPresent()) {
                var typeArgs = cit.getTypeArguments().orElseThrow();
                if (!typeArgs.isEmpty()) {
                    var firstArg = typeArgs.getFirst();
                    if (firstArg.isPresent()) {
                        resolved = firstArg.get();
                    }
                }
            }
        }
        return resolved;
    }

    /** Add implicit join ON clause based on @JoinColumn annotation or id field fallback. */
    private static void buildOnExpression(Join join, TypeWrapper target, String lhsAlias, String rhsAlias, FieldDeclaration fieldDecl) {
        String leftCol = extractJoinColumnName(fieldDecl);
        String rightCol = extractReferencedColumnName(fieldDecl);

        // Fallback to implicit id if missing pieces
        if (leftCol == null || rightCol == null) {
            String idCol = findIdColumn(target);
            leftCol = leftCol != null ? leftCol : idCol;
            rightCol = rightCol != null ? rightCol : idCol;
        }
        if (leftCol == null || rightCol == null) return; // cannot build ON

        addJoinCondition(join, lhsAlias, leftCol, rhsAlias, rightCol);
    }

    private static String extractJoinColumnName(FieldDeclaration fieldDecl) {
        Optional<AnnotationExpr> annOpt = fieldDecl.getAnnotationByName("JoinColumn");
        if (annOpt.isEmpty()) return null;

        AnnotationExpr ann = annOpt.get();
        if (ann.isNormalAnnotationExpr()) {
            for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("name")) {
                    return BaseRepositoryParser.camelToSnake(pair.getValue().toString());
                }
            }
        } else {
            return BaseRepositoryParser.camelToSnake(ann.asSingleMemberAnnotationExpr().getMemberValue().toString());
        }
        return null;
    }

    private static String extractReferencedColumnName(FieldDeclaration fieldDecl) {
        Optional<AnnotationExpr> annOpt = fieldDecl.getAnnotationByName("JoinColumn");
        if (annOpt.isEmpty()) return null;

        AnnotationExpr ann = annOpt.get();
        if (ann.isNormalAnnotationExpr()) {
            for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                if (pair.getNameAsString().equals("referencedColumnName")) {
                    return BaseRepositoryParser.camelToSnake(pair.getValue().toString());
                }
            }
        }
        return null;
    }

    private static void addJoinCondition(Join join, String lhsAlias, String leftCol, String rhsAlias, String rightCol) {
        if (DatabaseDialect.ORACLE.equals(BaseRepositoryParser.getDialect())) {
            leftCol = leftCol.replace("\"", "");
            rightCol = rightCol.replace("\"", "");
        }
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(lhsAlias + "." + leftCol));
        eq.setRightExpression(new Column(rhsAlias + "." + rightCol));
        join.getOnExpressions().add(eq);
    }

    private static String findIdColumn(TypeWrapper tw) {
        if (tw == null || tw.getType() == null) return null;
        for (var fd : tw.getType().getFields()) {
            if (fd.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(ID_ANNOTATION))) {
                return BaseRepositoryParser.camelToSnake(fd.getVariable(0).getNameAsString());
            }
        }
        return null;
    }

    // --- Field conversion helpers --------------------------------------------

    protected static void generalProjections(List<SelectItem<?>> items) {
        for (int i = 0; i < items.size(); i++) {
            SelectItem<?> item = items.get(i);
            if (item.getExpression() == null) {
                // simple string-based item
                String itemStr = item.toString();
                if (isConvertibleSimpleProjection(itemStr)) {
                    items.set(i, convertSimpleProjection(itemStr));
                }
                continue;
            }
            // expression-based item
            net.sf.jsqlparser.expression.Expression converted = BaseRepositoryQuery.convertExpressionToSnakeCase(item.getExpression());
            SelectItem<?> convertedItem = SelectItem.from(converted);
            if (item.getAlias() != null) {
                convertedItem.setAlias(item.getAlias());
            }
            items.set(i, convertedItem);
        }
    }

    private static boolean isConvertibleSimpleProjection(String itemStr) {
        return !itemStr.contains("(") && !itemStr.contains("*");
    }

    private static SelectItem<?> convertSimpleProjection(String itemStr) {
        String[] parts = itemStr.split("\\.");
        Column col;
        if (itemStr.contains(".") && parts.length == 2) {
            col = new Column(parts[0] + "." + BaseRepositoryParser.camelToSnake(parts[1]));
        } else {
            col = new Column(BaseRepositoryParser.camelToSnake(parts[0]));
        }
        return SelectItem.from(col);
    }


}
