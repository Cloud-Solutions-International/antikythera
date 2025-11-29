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
        convertFieldsToSnakeCase(stmt, entity, false);
    }

    /**
     * Java field names need to be converted to snake case to match the table column.
     * <p>
     * This method does not return anything but has a side effect. The changes will be made to the
     * select statement that is passed in.
     *
     * @param stmt            the sql statement
     * @param entity           a compilation unit representing the entity.
     * @param skipJoinProcessing if true, skip join processing (for HQL queries already converted by HQLToPostgreSQLConverter)
     * @throws AntikytheraException if we are unable to find related entities.
     */
    public static void convertFieldsToSnakeCase(Statement stmt, TypeWrapper entity, boolean skipJoinProcessing) throws AntikytheraException {
        if (!(stmt instanceof Select sel)) {
            return; // only Select statements supported
        }
        convertPlainSelectToSnakeCase(sel.getPlainSelect(), entity, skipJoinProcessing);
    }

    /**
     * Convert projections, clauses, and joins of a PlainSelect to snake case where applicable.
     * Keeps method small by delegating to focused helpers.
     *
     * @param select            the PlainSelect to convert
     * @param entity            the root entity
     * @param skipJoinProcessing if true, skip join processing (for HQL queries already converted)
     */
    private static void convertPlainSelectToSnakeCase(PlainSelect select, TypeWrapper entity, boolean skipJoinProcessing) throws AntikytheraException {
        if (select == null) return;
        normalizeProjection(select);
        convertWhere(select);
        convertGroupBy(select);
        convertOrderBy(select);
        convertHaving(select);
        // Only process joins if not already converted by HQLToPostgreSQLConverter
        // HQL queries have joins already converted to SQL, so skip join processing
        if (!skipJoinProcessing) {
            processJoins(entity, select, skipJoinProcessing);
        }
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

    private static void processJoins(TypeWrapper rootEntity, PlainSelect select, boolean skipJoinProcessing) throws AntikytheraException {
        List<Join> joins = select.getJoins();
        if (joins == null || joins.isEmpty()) return;
        List<TypeWrapper> discovered = new ArrayList<>();
        discovered.add(rootEntity);
        for (Join j : joins) {
            var right = j.getRightItem();
            if (right instanceof ParenthesedSelect ps && ps.getSelect() instanceof Select innerSel) {
                // recurse into nested select body
                convertPlainSelectToSnakeCase(innerSel.getPlainSelect(), rootEntity, skipJoinProcessing);
                continue;
            }
            resolveAndRewriteJoin(j, discovered);
        }
    }

    private static void resolveAndRewriteJoin(Join join, List<TypeWrapper> known) throws AntikytheraException {
        String raw = join.getRightItem().toString();

        // 1) Short-circuit if the right item already looks like a concrete SQL table reference
        if (isAlreadySqlRightItem(join, raw)) {
            return;
        }

        // 2) Parse expected HQL path form inline: "<lhsAlias>.<fieldName> <joinAlias>"
        String[] dotParts = raw.split("\\.", 2);
        if (dotParts.length != 2) return; // not an HQL-style path
        String lhsAlias = dotParts[0].trim();
        String[] fieldAlias = dotParts[1].trim().split("\\s+");
        if (lhsAlias.isEmpty() || fieldAlias.length < 2) return;
        String fieldName = fieldAlias[0].trim();
        String joinAlias = fieldAlias[1].trim();
        if (fieldName.isEmpty() || joinAlias.isEmpty()) return;

        // 3) Attempt resolution against each known entity
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
            if (DatabaseDialect.ORACLE.equals(BaseRepositoryParser.getDialect())) {
                tableName = tableName.replace("\"", "");
            }

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

    /**
     * Heuristic to determine if the right item is already a concrete SQL table (and not an HQL path)
     */
    private static boolean isAlreadySqlRightItem(Join join, String raw) {
        if (!(join.getRightItem() instanceof Table table)) {
            return false; // Not a Table; could be a Subselect or other — let caller handle
        }
        // If schema explicitly set, it's a schema-qualified table => already SQL
        if (table.getSchemaName() != null && !table.getSchemaName().isEmpty()) {
            return true;
        }
        // No dot means it's a simple table name => already SQL
        if (!raw.contains(".")) {
            return true;
        }
        // If it contains a dot but schema isn't set, distinguish schema.table vs alias.field
        String[] dotParts = raw.split("\\.", 2);
        if (dotParts.length == 2) {
            String firstPart = dotParts[0].trim();
            // 4+ chars: likely schema (e.g., "public", "dbo", "my_schema"). Treat as SQL.
            if (firstPart.length() >= 4) {
                return true;
            }
        }
        return false;
    }


    /**
     * Resolve generic collection element (List<X>) or direct type.
     */
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
