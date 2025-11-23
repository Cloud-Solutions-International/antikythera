package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to convert Spring Data repository method-name keywords into SQL
 * fragments.
 * Extracted from {@link BaseRepositoryParser} to keep parsing and conversion
 * concerns separated.
 */
public final class MethodToSQLConverter {

    public static final String CONTAINING = "Containing";
    public static final String ENDING_WITH = "EndingWith";
    public static final String STARTING_WITH = "StartingWith";
    public static final String IGNORE_CASE = "IgnoreCase";
    public static final String AND = "And";
    public static final String OR = "Or";
    public static final String NOT = "Not";
    public static final String IN = "In";
    public static final String NOT_IN = "NotIn";
    public static final String LIKE = "Like";
    public static final String BETWEEN = "Between";
    public static final String GREATER_THAN = "GreaterThan";
    public static final String LESS_THAN = "LessThan";
    public static final String GREATER_THAN_EQUAL = "GreaterThanEqual";
    public static final String LESS_THAN_EQUAL = "LessThanEqual";
    public static final String BEFORE = "Before";
    public static final String AFTER = "After";
    public static final String IS_NULL = "IsNull";
    public static final String IS_NOT_NULL = "IsNotNull";
    public static final String TRUE = "True";
    public static final String FALSE = "False";
    public static final String DESC = "Desc";
    public static final String ASC = "Asc";
    public static final String IS = "Is";
    public static final String EQUAL = "Equals";
    public static final String ALL_IGNORE_CASE = "AllIgnoreCase";
    public static final String FIND_BY = "findBy";
    public static final String FIND_ALL = "findAll";
    public static final String FIND_ALL_BY_ID = "findAllById";
    public static final String COUNT_BY = "countBy";
    public static final String DELETE_BY = "deleteBy";
    public static final String EXISTS_BY = "existsBy";
    public static final String FIND_FIRST_BY = "findFirstBy";
    public static final String FIND_TOP_BY = "findTopBy";
    public static final String FIND_DISTINCT_BY = "findDistinctBy";
    public static final String READ_BY = "readBy";
    public static final String QUERY_BY = "queryBy";
    public static final String SEARCH_BY = "searchBy";
    public static final String STREAM_BY = "streamBy";
    public static final String REMOVE_BY = "removeBy";
    public static final String GET = "get";

    private static final List<String> QUERY_TYPES = List.of(
            READ_BY, QUERY_BY, SEARCH_BY, STREAM_BY, REMOVE_BY, GET, FIND_BY,
            FIND_FIRST_BY, FIND_TOP_BY, FIND_DISTINCT_BY, FIND_ALL, COUNT_BY, DELETE_BY, EXISTS_BY);

    private static final List<String> OPERATORS = List.of(
            AND, OR, BETWEEN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL, GREATER_THAN,
            LESS_THAN, BEFORE, AFTER, IS_NULL, IS_NOT_NULL, LIKE, NOT, IN,
            NOT_IN, TRUE, FALSE, CONTAINING, STARTING_WITH, ENDING_WITH);

    private static final List<String> MODIFIERS = List.of(
            BaseRepositoryParser.ORDER_BY, DESC, ASC, IS, EQUAL, IGNORE_CASE, ALL_IGNORE_CASE);

    private static final Pattern KEYWORDS_PATTERN;

    static {
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(QUERY_TYPES);
        allKeywords.addAll(OPERATORS);
        allKeywords.addAll(MODIFIERS);

        // Sort by length descending to match longest keywords first
        allKeywords.sort((a, b) -> b.length() - a.length());

        String pattern = String.join("|", allKeywords);
        KEYWORDS_PATTERN = Pattern.compile(pattern);
    }

    /**
     * Set of operators that do not require an equals sign to be appended.
     * These operators handle the SQL generation themselves or are syntactic sugar.
     */
    private static final Set<String> NO_EQUALS_OPERATORS = Set.of(
            BETWEEN, GREATER_THAN, LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL,
            IS_NOT_NULL, IS_NULL, LIKE, CONTAINING, IN, NOT_IN, NOT,
            STARTING_WITH, ENDING_WITH, BEFORE, AFTER, TRUE, FALSE, IGNORE_CASE);
    // Note: AND/OR are intentionally excluded so that a bare field before a logical operator
    // still receives an implicit "= ?" comparator (e.g., "...Where userId And tenantId..." -> "user_id = ? AND tenant_id = ?").

    private MethodToSQLConverter() {
    }

    /**
     * Extracts components from a method name based on JPA keywords.
     *
     * @param methodName The method name to parse.
     * @return A list of components.
     */
    public static List<String> extractComponents(String methodName) {
        List<String> components = new ArrayList<>();
        Matcher matcher = KEYWORDS_PATTERN.matcher(methodName);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String keyword = matcher.group();
            int end = matcher.end();

            // Special handling for short keywords that could be part of field names
            // If the keyword is followed by a lowercase letter, it's part of a field name
            // Examples: "Invoice" (In+voice), "Description" (Desc+ription), "Ordering"
            // (Or+dering)
            if (keyword.matches(IN + "|" + OR + "|" + NOT + "|" + ASC + "|" + DESC) && end < methodName.length()) {
                char nextChar = methodName.charAt(end);
                if (Character.isLowerCase(nextChar)) {
                    // Keyword is part of a field name, don't treat as keyword
                    continue;
                }
            }

            matcher.appendReplacement(sb, " " + keyword + " ");
        }
        matcher.appendTail(sb);

        String[] parts = sb.toString().split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                components.add(part);
            }
        }
        return components;
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
                if (component.startsWith(FIND_FIRST_BY) || component.startsWith(FIND_TOP_BY)) {
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
            case FIND_ALL -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName);
                if (!next.isEmpty() && !next.equals(BaseRepositoryParser.ORDER_BY)) {
                    sql.append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                }
                return true;
            }
            case FIND_ALL_BY_ID -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName)
                        .append(" WHERE id IN (?)");
                return true;
            }
            case FIND_BY, GET, READ_BY, QUERY_BY, SEARCH_BY, STREAM_BY -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case COUNT_BY -> {
                sql.append("SELECT COUNT(*) FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case DELETE_BY, REMOVE_BY -> {
                sql.append("DELETE FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case EXISTS_BY -> {
                sql.append("SELECT EXISTS (SELECT 1 FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case FIND_FIRST_BY, FIND_TOP_BY -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName);
                if (!next.equals(BaseRepositoryParser.ORDER_BY)) {
                    sql.append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                }
                return true;
            }
            case FIND_DISTINCT_BY -> {
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
            case IN -> sql.append(" IN (?) ");
            case NOT_IN -> sql.append(" NOT IN (?) ");
            case BETWEEN -> sql.append(" BETWEEN ? AND ? ");
            case GREATER_THAN, AFTER -> sql.append(" > ? ");
            case LESS_THAN, BEFORE -> sql.append(" < ? ");
            case GREATER_THAN_EQUAL -> sql.append(" >= ? ");
            case LESS_THAN_EQUAL -> sql.append(" <= ? ");
            case IS_NULL -> sql.append(" IS NULL ");
            case IS_NOT_NULL -> sql.append(" IS NOT NULL ");
            case TRUE -> sql.append(" = true ");
            case FALSE -> sql.append(" = false ");
            case AND, OR -> sql.append(" ").append(component.toUpperCase()).append(' ');
            case NOT -> {
                handleNotOperator(next, sql);
                return true;
            }
            case CONTAINING, LIKE, STARTING_WITH, ENDING_WITH -> sql.append(" LIKE ? ");
            case IS, EQUAL, IGNORE_CASE -> {
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
        if (next.equals(LIKE) || next.equals(CONTAINING) || next.equals(STARTING_WITH)
                || next.equals(ENDING_WITH) || next.equals(IN)) {
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
            case DESC -> {
                if (ordering) {
                    sql.append("DESC");
                    appendCommaOrSpace(next, sql);
                    return true;
                }
                return false;
            }
            case ASC -> {
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
        if (!next.isEmpty() && !next.equals(DESC) && !next.equals(ASC)) {
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
            } else if (!next.equals(NOT) && !next.equals(IGNORE_CASE)) {
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
