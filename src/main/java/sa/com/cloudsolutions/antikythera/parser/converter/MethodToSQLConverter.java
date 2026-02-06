package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    public static final String IS_TRUE = "IsTrue";
    public static final String IS_FALSE = "IsFalse";
    public static final String DESC = "Desc";
    public static final String ASC = "Asc";
    public static final String IS = "Is";
    public static final String IS_NOT = "IsNot";
    public static final String EQUAL = "Equals";
    public static final String ALL_IGNORE_CASE = "AllIgnoreCase";
    public static final String FIND_BY = "findBy";
    public static final String FIND_ALL = "findAll";
    public static final String FIND_ALL_BY = "findAllBy";
    public static final String FIND_ALL_BY_ID = "findAllById";
    public static final String COUNT_BY = "countBy";
    public static final String COUNT_ALL_BY = "countAllBy";
    public static final String DELETE_BY = "deleteBy";
    public static final String DELETE_ALL_BY = "deleteAllBy";
    public static final String EXISTS_BY = "existsBy";
    public static final String EXISTS_ALL_BY = "existsAllBy";
    public static final String FIND_FIRST_BY = "findFirstBy";
    public static final String FIND_TOP_BY = "findTopBy";
    public static final String FIND_DISTINCT_BY = "findDistinctBy";
    public static final String READ_BY = "readBy";
    public static final String QUERY_BY = "queryBy";
    public static final String SEARCH_BY = "searchBy";
    public static final String STREAM_BY = "streamBy";
    public static final String REMOVE_BY = "removeBy";
    public static final String GET = "get";
    public static final String SAVE = "save";

    private static final List<String> QUERY_TYPES = List.of(
            READ_BY, QUERY_BY, SEARCH_BY, STREAM_BY, REMOVE_BY, GET, SAVE, FIND_BY,
            FIND_FIRST_BY, FIND_TOP_BY, FIND_DISTINCT_BY, FIND_ALL, FIND_ALL_BY, FIND_ALL_BY_ID,
            COUNT_BY, COUNT_ALL_BY, DELETE_BY, DELETE_ALL_BY, EXISTS_BY, EXISTS_ALL_BY);

    private static final List<String> OPERATORS = List.of(
            AND, OR, BETWEEN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL, GREATER_THAN,
            LESS_THAN, BEFORE, AFTER, IS_NULL, IS_NOT_NULL, IS_NOT, LIKE, NOT, IN,
            NOT_IN, IS_TRUE, IS_FALSE, TRUE, FALSE, IS, EQUAL, CONTAINING, STARTING_WITH, ENDING_WITH);

    private static final List<String> MODIFIERS = List.of(
            BaseRepositoryParser.ORDER_BY, IGNORE_CASE, ALL_IGNORE_CASE);

    /**
     * Keywords that are only valid in ORDER BY context.
     * These should NOT be matched as keywords when parsing field names.
     */
    private static final List<String> ORDER_BY_ONLY_KEYWORDS = List.of(DESC, ASC);

    /**
     * Set of operators that do not require an equals sign to be appended.
     * These operators handle the SQL generation themselves or are syntactic sugar.
     */
    private static final Set<String> NO_EQUALS_OPERATORS = Set.of(
            BETWEEN, GREATER_THAN, LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL,
            IS_NOT_NULL, IS_NULL, IS_NOT, LIKE, CONTAINING, IN, NOT_IN, NOT,
            STARTING_WITH, ENDING_WITH, BEFORE, AFTER, IS_TRUE, IS_FALSE, TRUE, FALSE);
    // Note: AND/OR are intentionally excluded so that a bare field before a logical
    // operator still receives an implicit "= ?" comparator.
    // Note: IGNORE_CASE is NOT in this set - it should still get "= ?" appended,
    // with case-insensitive comparison handled by the database or application layer.

    private MethodToSQLConverter() {
    }

    /**
     * Extracts components from a method name based on JPA keywords.
     *
     * Handles edge cases where:
     * - Field names start with lowercase letters (e.g., eApprovalStatus)
     * - Keywords are followed by lowercase letters (part of field names)
     * - Short keywords (In, Or, Not, Asc, Desc) that could be part of field names
     * - Desc/Asc only recognized after OrderBy (not in "Description", "Ascending", etc.)
     *
     * @param methodName The method name to parse.
     * @return A list of components.
     */
    public static List<String> extractComponents(String methodName) {
        // Normalize 'find...By' subjects to 'findBy' to align with Spring Data
        // semantics
        methodName = normalizeFindSubject(methodName);

        List<String> components = new ArrayList<>();
        List<String> keywords = getKeywordsByLengthDesc();
        int i = 0;
        boolean inOrderByContext = false;

        while (i < methodName.length()) {
            // 1) Try to match a known keyword at the current index
            String matchedKeyword = tryMatchKeyword(methodName, i, keywords, inOrderByContext);
            if (matchedKeyword != null) {
                components.add(matchedKeyword);
                i += matchedKeyword.length();
                // Track when we enter ORDER BY context
                if (BaseRepositoryParser.ORDER_BY.equals(matchedKeyword)) {
                    inOrderByContext = true;
                }
                continue;
            }

            // 2) Otherwise scan a field token until the next valid keyword boundary
            ScanResult scan = scanField(methodName, i, keywords, inOrderByContext);
            i = scan.nextIndex;
            if (!scan.fieldToken.isEmpty()) {
                components.add(scan.fieldToken);
            }
        }

        return components;
    }

    // Attempts to match a keyword at position 'index' using the same boundary rules
    // as {@link #findNextKeyword(String, int, List, boolean)}.
    private static String tryMatchKeyword(String methodName, int index, List<String> keywords, boolean inOrderByContext) {
        return findNextKeyword(methodName, index, keywords, inOrderByContext);
    }

    // Scans characters from 'start' until a valid keyword boundary is reached,
    // returning the token and new index.
    private static ScanResult scanField(String methodName, int start, List<String> keywords, boolean inOrderByContext) {
        StringBuilder fieldName = new StringBuilder();
        int i = start;

        while (i < methodName.length()) {
            String nextKeyword = findNextKeyword(methodName, i, keywords, inOrderByContext);
            if (nextKeyword != null) {
                break;
            }
            fieldName.append(methodName.charAt(i));
            i++;
        }
        return new ScanResult(fieldName.toString(), i);
    }

    // Returns a keyword that starts at 'index' and forms a valid boundary per rules
    // used previously; otherwise null.
    private static String findNextKeyword(String methodName, int index, List<String> keywords, boolean inOrderByContext) {
        for (String keyword : keywords) {
            if (!methodName.startsWith(keyword, index)) {
                continue;
            }

            int keywordEnd = index + keyword.length();

            // DESC/ASC are only keywords in ORDER BY context
            if (ORDER_BY_ONLY_KEYWORDS.contains(keyword)) {
                if (inOrderByContext && isGeneralOperatorBoundary(methodName, keywordEnd)) {
                    return keyword;
                }
                continue; // Skip DESC/ASC when not in ORDER BY context
            }

            if (isAlwaysKeyword(keyword)) {
                return keyword;
            }

            if (isValidOperatorBoundary(methodName, keyword, keywordEnd)) {
                return keyword;
            }
        }
        return null;
    }

    private static boolean isValidOperatorBoundary(String methodName, String keyword, int keywordEnd) {
        if (isLogicalOperator(keyword)) {
            return hasFollowingUppercase(methodName, keywordEnd);
        }

        // Syntactic sugar keywords (Is, Equals) should only be matched when followed by:
        // 1. Another operator (True, False, Null, NotNull, Not, And, Or)
        // 2. The end of the string
        // NOT when followed by what looks like a field name (e.g., "IsActive" -> "Is" should not match)
        if (isSyntacticSugar(keyword)) {
            return isSyntacticSugarValidPosition(methodName, keywordEnd);
        }

        return isGeneralOperatorBoundary(methodName, keywordEnd);
    }

    /**
     * Checks if a syntactic sugar keyword (Is, Equals) is at a valid position.
     * Valid positions are:
     * - At end of string (e.g., "findByStatusIs" - though unusual)
     * - Followed by known suffixes: True, False, Null, NotNull, Not, And, Or
     * This prevents "Is" from matching at the start of field names like "IsActive"
     */
    private static boolean isSyntacticSugarValidPosition(String methodName, int keywordEnd) {
        if (keywordEnd >= methodName.length()) {
            return true; // At end of string
        }

        // Check if followed by known suffixes that make sense after "Is"
        String remainder = methodName.substring(keywordEnd);
        return remainder.startsWith("True") || remainder.startsWith("False") ||
               remainder.startsWith("Null") || remainder.startsWith("NotNull") ||
               remainder.startsWith("Not") || // covers IsNot, IsNotNull, IsNotIn, etc.
               remainder.startsWith("And") || remainder.startsWith("Or");
    }

    private static boolean hasFollowingUppercase(String methodName, int keywordEnd) {
        return keywordEnd < methodName.length() && Character.isUpperCase(methodName.charAt(keywordEnd));
    }

    private static boolean isSyntacticSugarFollowedByBooleanOp(String methodName, int keywordEnd) {
        return hasFollowingUppercase(methodName, keywordEnd)
                && isFollowedByFieldWithBooleanOp(methodName, keywordEnd);
    }

    private static boolean isGeneralOperatorBoundary(String methodName, int keywordEnd) {
        return keywordEnd >= methodName.length() || !Character.isLowerCase(methodName.charAt(keywordEnd));
    }

    private static boolean isAlwaysKeyword(String keyword) {
        return QUERY_TYPES.contains(keyword) || MODIFIERS.contains(keyword);
    }

    private static boolean isLogicalOperator(String keyword) {
        return AND.equals(keyword) || OR.equals(keyword);
    }

    private static boolean isSyntacticSugar(String keyword) {
        return IS.equals(keyword) || EQUAL.equals(keyword);
    }

    /**
     * Checks if the position is followed by a field name that ends with a boolean
     * operator.
     * For example, in "IsActiveIsTrue", at position 0, this returns true because
     * "Active" is followed by "IsTrue".
     */
    private static boolean isFollowedByFieldWithBooleanOp(String methodName, int startPos) {
        // Scan ahead to find the next keyword
        int pos = startPos;
        while (pos < methodName.length() && Character.isLetter(methodName.charAt(pos))) {
            // Check if we're at the start of a boolean operator
            if (methodName.startsWith(IS_TRUE, pos) || methodName.startsWith(IS_FALSE, pos) ||
                    methodName.startsWith(IS_NULL, pos) || methodName.startsWith(IS_NOT_NULL, pos)) {
                // There's a field name between startPos and pos, followed by a boolean operator
                return pos > startPos;
            }
            pos++;
        }
        return false;
    }

    // Small holder for scan results
    private record ScanResult(String fieldToken, int nextIndex) {
    }

    /**
     * Get all keywords sorted by length descending for longest match first
     */
    private static List<String> getKeywordsByLengthDesc() {
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(QUERY_TYPES);
        allKeywords.addAll(OPERATORS);
        allKeywords.addAll(MODIFIERS);
        allKeywords.addAll(ORDER_BY_ONLY_KEYWORDS);

        // Sort by length descending to match longest keywords first
        allKeywords.sort((a, b) -> b.length() - a.length());
        return allKeywords;
    }

    /**
     * Normalize method names that include a subject between the verb and the "By"
     * keyword.
     * For example, convert "findUserByEmail" to "findByEmail" so downstream parsing
     * recognizes the query type correctly. This mirrors Spring Data semantics where
     * the
     * part between the verb and "By" is the (optional) subject and does not affect
     * the
     * predicate parsing.
     *
     * Only applies to names starting with the verb "find" to keep the change
     * conservative.
     */
    private static String normalizeFindSubject(String methodName) {
        if (!methodName.startsWith("find")) {
            return methodName;
        }
        // Do not normalize when the method already uses a recognized find* query type
        if (methodName.startsWith(FIND_BY)
                || methodName.startsWith(FIND_FIRST_BY)
                || methodName.startsWith(FIND_TOP_BY)
                || methodName.startsWith(FIND_DISTINCT_BY)
                || methodName.startsWith(FIND_ALL_BY_ID)
                || methodName.startsWith(FIND_ALL_BY)
                || methodName.startsWith(FIND_ALL)) {
            return methodName;
        }
        // If the only occurrence of "By" is the one in "OrderBy", do not normalize.
        int byIdx = methodName.indexOf("By", 4); // look for the first "By" after "find"
        if (byIdx > 0) {
            // If this By is part of OrderBy, skip normalization to preserve ordering clause
            int orderStart = byIdx - "Order".length();
            if (orderStart >= 0) {
                String maybeOrder = methodName.substring(orderStart, byIdx);
                if ("Order".equals(maybeOrder)) {
                    return methodName;
                }
            }
            // Convert things like findSomethingByXxxYyy... -> findByXxxYyy...
            return FIND_BY + methodName.substring(byIdx + 2);
        }
        return methodName;
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
            case FIND_BY, FIND_ALL_BY, GET, READ_BY, QUERY_BY, SEARCH_BY, STREAM_BY -> {
                sql.append(BaseRepositoryParser.SELECT_STAR).append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case COUNT_BY, COUNT_ALL_BY -> {
                sql.append("SELECT COUNT(*) FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case DELETE_BY, DELETE_ALL_BY, REMOVE_BY -> {
                sql.append("DELETE FROM ").append(tableName)
                        .append(" ").append(BaseRepositoryParser.WHERE).append(" ");
                return true;
            }
            case EXISTS_BY, EXISTS_ALL_BY -> {
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
            case SAVE -> {
                // Very primitive INSERT support for save()
                // We don't attempt to list columns or placeholders; rely on DEFAULT VALUES
                sql.append("INSERT INTO ").append(tableName).append(" DEFAULT VALUES");
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
            case IS_NOT -> sql.append(" != ? ");
            case IS_TRUE -> sql.append(" = true ");
            case IS_FALSE -> sql.append(" = false ");
            case TRUE -> sql.append(" = true ");
            case FALSE -> sql.append(" = false ");
            case AND, OR -> sql.append(" ").append(component.toUpperCase()).append(' ');
            case NOT -> {
                handleNotOperator(next, sql);
                return true;
            }
            case CONTAINING, LIKE, STARTING_WITH, ENDING_WITH -> sql.append(" LIKE ? ");
            case IS, EQUAL, IGNORE_CASE, ALL_IGNORE_CASE -> {
                // Syntactic sugar or modifiers handled elsewhere
                // These don't produce SQL fragments themselves
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
    @SuppressWarnings("java:S1066")
    private static void appendDefaultComponent(StringBuilder sql, String component, String next, boolean ordering) {
        sql.append(BaseRepositoryParser.camelToSnake(component));
        if (!ordering) {
            if (shouldAppendEquals(next)) {
                sql.append(" = ? ");
            } else if (!next.equals(NOT) && !next.equals(IGNORE_CASE) && !next.equals(IS_TRUE)
                    && !next.equals(IS_FALSE)) {
                // Do not append a space if the next component is LIKE/CONTAINING/STARTING_WITH/ENDING_WITH
                if (!(next.equals(LIKE) || next.equals(CONTAINING) || next.equals(STARTING_WITH) || next.equals(ENDING_WITH))) {
                    sql.append(' ');
                }
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
