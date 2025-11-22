package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;

import java.util.List;

/**
 * Utility to convert Spring Data repository method-name keywords into SQL
 * fragments.
 * Extracted from {@link BaseRepositoryParser} to keep parsing and conversion
 * concerns separated.
 */
public final class MethodToSQLConverter {
    private MethodToSQLConverter() {
    }

    /**
     * Builds SELECT and WHERE (and ORDER BY) clauses from parsed method-name
     * components.
     * Returns whether a TOP/FIRST (findFirstBy/findTopBy) was detected (so caller
     * can apply limit).
     */
    public static boolean buildSelectAndWhereClauses(List<String> components, StringBuilder sql, String tableName) {
        boolean top = false;
        boolean ordering = false;
        boolean distinct = false;
        String tableNameClean = tableName.replace("\"", "");

        for (int i = 0; i < components.size(); i++) {
            String component = components.get(i);
            String next = (i < components.size() - 1) ? components.get(i + 1) : "";
            String prev = (i > 0) ? components.get(i - 1) : "";

            switch (component) {
                case "findAll" -> {
                    if (next.isEmpty() || next.equals(BaseRepositoryParser.ORDER_BY)) {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableNameClean);
                    } else {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableNameClean).append(" ")
                                .append(BaseRepositoryParser.WHERE).append(" ");
                    }
                }
                case "findAllById" ->
                    sql.append(BaseRepositoryParser.SELECT_STAR).append(tableNameClean)
                            .append(" WHERE id IN (?)");
                case "findBy", "get", "readBy", "queryBy", "searchBy", "streamBy" ->
                    sql.append(BaseRepositoryParser.SELECT_STAR).append(tableNameClean)
                            .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "countBy" ->
                    sql.append("SELECT COUNT(*) FROM ").append(tableNameClean)
                            .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "deleteBy", "removeBy" ->
                    sql.append("DELETE FROM ").append(tableNameClean)
                            .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "existsBy" ->
                    sql.append("SELECT EXISTS (SELECT 1 FROM ").append(tableNameClean)
                            .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                case "findFirstBy", "findTopBy" -> {
                    top = true;
                    if (next.equals(BaseRepositoryParser.ORDER_BY)) {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableNameClean);
                    } else {
                        sql.append(BaseRepositoryParser.SELECT_STAR).append(tableNameClean)
                                .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                    }
                }
                case "findDistinctBy" -> {
                    distinct = true;
                    sql.append("SELECT DISTINCT * FROM ").append(tableNameClean)
                            .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                }
                case "In" -> sql.append(" IN (?) ");
                case "NotIn" -> sql.append(" NOT IN (?) ");
                case "Between" -> sql.append(" BETWEEN ? AND ? ");
                case "GreaterThan", "After" -> sql.append(" > ? ");
                case "LessThan", "Before" -> sql.append(" < ? ");
                case "GreaterThanEqual" -> sql.append(" >= ? ");
                case "LessThanEqual" -> sql.append(" <= ? ");
                case "IsNull" -> sql.append(" IS NULL ");
                case "IsNotNull" -> sql.append(" IS NOT NULL ");
                case "True" -> sql.append(" = true ");
                case "False" -> sql.append(" = false ");
                case "And", "Or" -> sql.append(" ").append(component.toUpperCase()).append(' ');
                case "Not" -> {
                    // "Not" is a prefix to the next operator or a standalone inequality
                    // If next is "In", "Like", "Null", etc., we handle it there or here?
                    // Actually, standard JPA: NotLike, NotIn, NotNull (handled as IsNotNull)
                    // If we see "Not" and next is "Like", we should output "NOT LIKE"
                    // If we see "Not" and next is "In", we should output "NOT IN" (but "NotIn" is
                    // usually parsed as one token if in keywords)
                    // If "Not" is standalone (e.g. findByNameNot), it means !=
                    if (next.equals("Like") || next.equals("Containing") || next.equals("StartingWith")
                            || next.equals("EndingWith")) {
                        sql.append(" NOT");
                    } else if (next.equals("In")) {
                        sql.append(" NOT");
                    } else {
                        sql.append(" != ? ");
                    }
                }
                case "Containing", "Like", "StartingWith", "EndingWith" -> sql.append(" LIKE ? ");
                case "Is", "Equals" -> {
                    /* Syntactic sugar, ignore */ }
                case "IgnoreCase" -> {
                    /*
                     * Handled in field processing or ignored for now as it requires wrapping column
                     */ }
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
            } else if (!next.equals("Not") && !next.equals("Is") && !next.equals("Equals")
                    && !next.equals("IgnoreCase")) {
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
                !next.equals("In") && !next.equals("NotIn") && !next.equals("Not") && !next.equals("Or") &&
                !next.equals("And") && !next.equals("StartingWith") && !next.equals("EndingWith") &&
                !next.equals("Before") && !next.equals("After") && !next.equals("True") && !next.equals("False") &&
                !next.equals("IgnoreCase"));
    }
}
