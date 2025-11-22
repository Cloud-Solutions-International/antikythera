package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;

import java.util.List;

/**
 * Utility to convert Spring Data repository method-name keywords into SQL fragments.
 * Extracted from {@link BaseRepositoryParser} to keep parsing and conversion concerns separated.
 */
public final class MethodToSQLConverter {
    private MethodToSQLConverter() {}

    /**
     * Builds SELECT and WHERE (and ORDER BY) clauses from parsed method-name components.
     * Returns whether a TOP/FIRST (findFirstBy/findTopBy) was detected (so caller can apply limit).
     */
    public static boolean buildSelectAndWhereClauses(List<String> components, StringBuilder sql, String tableName) {
        boolean top = false;
        boolean ordering = false;
        for (int i = 0; i < components.size(); i++) {
            String component = components.get(i);
            String next = (i < components.size() - 1) ? components.get(i + 1) : "";
            String prev = (i > 0) ? components.get(i - 1) : "";
            switch (component) {
                case "findAll" -> {
                    // findAll with no suffix means SELECT * FROM table with no WHERE
                    if (next.isEmpty() || next.equals(BaseRepositoryParser.ORDER_BY)) {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName.replace("\"", ""));
                    } else {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName.replace("\"", "")).append(" ")
                                .append(BaseRepositoryParser.WHERE).append(" ");
                    }
                }
                case "findAllById" ->
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName.replace("\"", ""))
                                .append(" WHERE id = ?");
                case "findBy", "get" ->
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName.replace("\"", ""))
                                .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "countBy" ->
                        sql.append("SELECT COUNT(*) FROM ").append(tableName.replace("\"", ""))
                                .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "deleteBy" ->
                        sql.append("DELETE FROM ").append(tableName.replace("\"", ""))
                                .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "existsBy" ->
                        sql.append("SELECT EXISTS (SELECT 1 FROM ").append(tableName.replace("\"", ""))
                                .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "findFirstBy", "findTopBy" -> {
                    top = true;
                    // Check if immediately followed by OrderBy (no WHERE clause)
                    if (next.equals(BaseRepositoryParser.ORDER_BY)) {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName.replace("\"", ""));
                    } else {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName.replace("\"", ""))
                                .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                    }
                }
                case "In" -> sql.append(" IN (?) ");
                case "NotIn" -> sql.append(" NOT IN (?) ");
                case "Between" -> sql.append(" BETWEEN ? AND ? ");
                case "GreaterThan" -> sql.append(" > ? ");
                case "LessThan" -> sql.append(" < ? ");
                case "GreaterThanEqual" -> sql.append(" >= ? ");
                case "LessThanEqual" -> sql.append(" <= ? ");
                case "IsNull" -> sql.append(" IS NULL ");
                case "IsNotNull" -> sql.append(" IS NOT NULL ");
                case "And", "Or" -> sql.append(" ").append(component.toUpperCase()).append(' ');
                case "Not" -> {
                    // Standalone negation operator: findByActiveNot means active != ?
                    if (!prev.equals("Is") && !next.equals("In")) {
                        sql.append(" != ? ");
                    }
                }
                case "Containing", "Like" -> sql.append(" LIKE ? ");
                case BaseRepositoryParser.ORDER_BY -> {
                    ordering = true;
                    sql.append(" ORDER BY ");
                }
                case "Desc" -> {
                    if (ordering) {
                        sql.append("DESC");
                        if (!next.isEmpty() && !next.equals("Desc") && !next.equals("Asc")) {
                            sql.append(", ");
                        } else {
                            sql.append(' ');
                        }
                    } else {
                        appendDefaultComponent(sql, component, next, ordering);
                    }
                }
                case "Asc" -> {
                    if (ordering) {
                        sql.append("ASC");
                        if (!next.isEmpty() && !next.equals("Desc") && !next.equals("Asc")) {
                            sql.append(", ");
                        } else {
                            sql.append(' ');
                        }
                    } else {
                        appendDefaultComponent(sql, component, next, ordering);
                    }
                }
                default -> appendDefaultComponent(sql, component, next, ordering);
            }
        }
        return top;
    }

    private static void appendDefaultComponent(StringBuilder sql, String component, String next, boolean ordering) {
        sql.append(BaseRepositoryParser.camelToSnake(component));
        if (!ordering) {
            if (shouldAppendEquals(next)) {
                sql.append(" = ? ");
            } else if (!next.equals("Not")) {
                sql.append(' ');
            }
        } else {
            if (next.equals("Desc") || next.equals("Asc")) {
                sql.append(' ');
            } else if (!next.isEmpty()) {
                sql.append(", ");
            } else {
                sql.append(' ');
            }
        }
    }

    private static boolean shouldAppendEquals(String next) {
        return next.isEmpty() || (!next.equals("Between") && !next.equals("GreaterThan") && !next.equals("LessThan") &&
                !next.equals("LessThanEqual") && !next.equals("IsNotNull") && !next.equals("Like") &&
                !next.equals("GreaterThanEqual") && !next.equals("IsNull") && !next.equals("Containing") &&
                !next.equals("In") && !next.equals("NotIn") && !next.equals("Not") && !next.equals("Or"));
    }
}
