package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;

import java.util.List;
import java.util.Set;

/**
 * Utility to convert Spring Data repository method-name keywords into SQL
 * fragments.
 * Extracted from {@link BaseRepositoryParser} to keep parsing and conversion
 * concerns separated.
 */
public final class MethodToSQLConverter {

    /**
     * Set of operators that do not require an equals sign to be appended.
     * These operators handle the SQL generation themselves or are syntactic sugar.
     */
    private static final Set<String> NO_EQUALS_OPERATORS = Set.of(
            "Between", "GreaterThan", "LessThan", "LessThanEqual", "GreaterThanEqual",
            "IsNotNull", "IsNull", "Like", "Containing", "In", "NotIn", "Not", "Or", "And",
            "StartingWith", "EndingWith", "Before", "After", "True", "False", "IgnoreCase");

    private MethodToSQLConverter() {
    }

    /**
     * Builds SELECT and WHERE (and ORDER BY) clauses from parsed method-name
     * components.
     *
     * @param components The list of parsed method name components.
     * @param sql        The StringBuilder to append the SQL to.
     * @param tableName  The name of the table to query.
     * @return true if a TOP/FIRST (findFirstBy/findTopBy) was detected, false
     *         otherwise.
     */
    public static boolean buildSelectAndWhereClauses(List<String> components, StringBuilder sql, String tableName) {
        boolean top = false;
        boolean ordering = false;
        String tableNameClean = tableName.replace("\"", "");

        for (int i = 0; i < components.size(); i++) {
            String component = components.get(i);
            String next = (i < components.size() - 1) ? components.get(i + 1) : "";

            if (handleQueryType(component, next, sql, tableNameClean)) {
                if (component.startsWith("findFirst") || component.startsWith("findTop")) {
                    top = true;
                }
            } else if (handleOperator(component, next, sql)) {
                // Operator handled
            } else if (handleOrderBy(component, next, sql, ordering)) {
                if (component.equals(BaseRepositoryParser.ORDER_BY)) {
                    ordering = true;
                }
            } else {
                appendDefaultComponent(sql, component, next, ordering);
            }
        }
        return top;
    }

    /**
     * Handles the initial query type components (e.g., findAll, findBy, countBy).
     *
     * @param component The current component.
     * @param next      The next component.
     * @param sql       The StringBuilder to append SQL to.
     * @param tableName The table name.
     * @return true if the component was handled, false otherwise.
     */
    private static boolean handleQueryType(String component, String next, StringBuilder sql, String tableName) {
        switch (component) {
            case "findAll" -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName);
                if (!next.isEmpty() && !next.equals(BaseRepositoryParser.ORDER_BY)) {
                    sql.append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                }
                return true;
            }
            case "findAllById" -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName)
                        .append(" WHERE id IN (?)");
                return true;
            }
            case "findBy", "get", "readBy", "queryBy", "searchBy", "streamBy" -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case "countBy" -> {
                sql.append("SELECT COUNT(*) FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case "deleteBy", "removeBy" -> {
                sql.append("DELETE FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case "existsBy" -> {
                sql.append("SELECT EXISTS (SELECT 1 FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case "findFirstBy", "findTopBy" -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName);
                if (!next.equals(BaseRepositoryParser.ORDER_BY)) {
                    sql.append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                }
                return true;
            }
            case "findDistinctBy" -> {
                sql.append("SELECT DISTINCT * FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Handles logical operators and comparison keywords.
     *
     * @param component The current component.
     * @param next      The next component.
     * @param sql       The StringBuilder to append SQL to.
     * @return true if the component was handled, false otherwise.
     */
    private static boolean handleOperator(String component, String next, StringBuilder sql) {
        switch (component) {
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
                handleNotOperator(next, sql);
                return true;
            }
            case "Containing", "Like", "StartingWith", "EndingWith" -> sql.append(" LIKE ? ");
            case "Is", "Equals", "IgnoreCase" -> {
                // Syntactic sugar or handled elsewhere
                return true;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Handles the 'Not' operator logic, determining if it's a negation of the next
     * operator
     * or a standalone inequality.
     *
     * @param next The next component.
     * @param sql  The StringBuilder to append SQL to.
     */
    private static void handleNotOperator(String next, StringBuilder sql) {
        if (next.equals("Like") || next.equals("Containing") || next.equals("StartingWith")
                || next.equals("EndingWith") || next.equals("In")) {
            sql.append(" NOT");
        } else {
            sql.append(" != ? ");
        }
    }

    /**
     * Handles ORDER BY clauses.
     *
     * @param component The current component.
     * @param next      The next component.
     * @param sql       The StringBuilder to append SQL to.
     * @param ordering  Whether we are currently processing an ORDER BY clause.
     * @return true if the component was handled, false otherwise.
     */
    private static boolean handleOrderBy(String component, String next, StringBuilder sql, boolean ordering) {
        switch (component) {
            case BaseRepositoryParser.ORDER_BY -> {
                sql.append(" ORDER BY ");
                return true;
            }
            case "Desc" -> {
                if (ordering) {
                    sql.append("DESC");
                    appendCommaOrSpace(next, sql);
                    return true;
                }
                return false;
            }
            case "Asc" -> {
                if (ordering) {
                    sql.append("ASC");
                    appendCommaOrSpace(next, sql);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Appends a comma or space depending on whether there are more fields in the
     * ORDER BY clause.
     *
     * @param next The next component.
     * @param sql  The StringBuilder.
     */
    private static void appendCommaOrSpace(String next, StringBuilder sql) {
        if (!next.isEmpty() && !next.equals("Desc") && !next.equals("Asc")) {
            sql.append(", ");
        } else {
            sql.append(' ');
        }
    }

    /**
     * Appends a default component (field name) to the SQL.
     * Converts camelCase to snake_case and appends " = ?" if appropriate.
     *
     * @param sql       The StringBuilder.
     * @param component The component to append.
     * @param next      The next component.
     * @param ordering  Whether we are in an ORDER BY clause.
     */
    private static void appendDefaultComponent(StringBuilder sql, String component, String next, boolean ordering) {
        sql.append(BaseRepositoryParser.camelToSnake(component));
        if (!ordering) {
            if (shouldAppendEquals(next)) {
                sql.append(" = ? ");
            } else if (!next.equals("Not") && !next.equals("IgnoreCase")) {
                sql.append(' ');
            }
        } else {
            appendCommaOrSpace(next, sql);
        }
    }

    /**
     * Checks if " = ?" should be appended after a field name.
     *
     * @param next The next component.
     * @return true if equals should be appended, false otherwise.
     */
    private static boolean shouldAppendEquals(String next) {
        return next.isEmpty() || !NO_EQUALS_OPERATORS.contains(next);
    }
}
