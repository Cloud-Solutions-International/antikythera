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
     * Extracts legacy flat components from a repository method name.
     *
     * <p>
     * The active parser is now {@link RepositoryMethodParser}. This method is kept as
     * a compatibility view for callers/tests that still consume the old token-list
     * format.
     * </p>
     *
     * @param methodName The method name to parse.
     * @return A list of components.
     */
    public static List<String> extractComponents(String methodName) {
        return RepositoryMethodParser.parse(methodName).toComponents();
    }

    /**
     * Extracts the numeric limit from a findTop&lt;N&gt;By or findFirst&lt;N&gt;By method name.
     * Returns 1 if no number is specified (e.g., findTopBy) or the method doesn't match.
     */
    public static int extractTopLimit(String methodName) {
        return RepositoryMethodParser.parse(methodName).getMaxResultsOrDefault(1);
    }

    /**
     * Builds SELECT/WHERE/ORDER BY clauses directly from the parsed method tree.
     * This is used by {@link sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser}
     * so derived query parsing no longer depends on the legacy token list model.
     *
     * @param parsedMethod the parsed repository method
     * @param sql the SQL builder to append to
     * @param tableName the primary table name
     * @return true if a TOP/FIRST subject was detected
     */
    public static boolean buildSelectAndWhereClauses(RepositoryMethodParser.ParsedMethod parsedMethod,
                                                     StringBuilder sql,
                                                     String tableName) {
        if (parsedMethod == null || !parsedMethod.isRecognized()) {
            return false;
        }

        String tableNameClean = tableName.replace("\"", "");
        RepositoryMethodParser.Subject subject = parsedMethod.subject();

        switch (subject.action()) {
            case SELECT -> {
                if (subject.distinct()) {
                    sql.append("SELECT DISTINCT * FROM ").append(tableNameClean);
                } else {
                    sql.append(BaseRepositoryParser.SELECT_STAR).append(tableNameClean);
                }
            }
            case COUNT -> sql.append("SELECT COUNT(*) FROM ").append(tableNameClean);
            case EXISTS -> sql.append("SELECT EXISTS (SELECT 1 FROM ").append(tableNameClean);
            case DELETE -> sql.append("DELETE FROM ").append(tableNameClean);
            case INSERT_DEFAULT -> sql.append("INSERT INTO ").append(tableNameClean).append(" DEFAULT VALUES");
            case UNKNOWN -> {
                return false;
            }
        }

        if (parsedMethod.hasPredicate()) {
            sql.append(" ").append(BaseRepositoryParser.WHERE).append(" ");
            appendPredicate(sql, parsedMethod.predicate());
        }

        if (parsedMethod.hasOrderBy() && subject.action() != RepositoryMethodParser.QueryAction.INSERT_DEFAULT) {
            sql.append(" ORDER BY ");
            appendOrderBy(sql, parsedMethod.orderBy());
        }

        if (subject.action() == RepositoryMethodParser.QueryAction.EXISTS) {
            sql.append(")");
        }

        return parsedMethod.isLimiting();
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

    private static void appendPredicate(StringBuilder sql, RepositoryMethodParser.Predicate predicate) {
        List<RepositoryMethodParser.OrPart> orParts = predicate.orParts();
        for (int i = 0; i < orParts.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }

            List<RepositoryMethodParser.PredicatePart> andParts = orParts.get(i).parts();
            for (int j = 0; j < andParts.size(); j++) {
                if (j > 0) {
                    sql.append(" AND ");
                }
                appendPredicatePart(sql, andParts.get(j));
            }
        }
    }

    private static void appendPredicatePart(StringBuilder sql, RepositoryMethodParser.PredicatePart part) {
        sql.append(resolveColumnName(part.property()));
        switch (part.type()) {
            case BETWEEN -> sql.append(" BETWEEN ? AND ? ");
            case IS_NOT_NULL -> sql.append(" IS NOT NULL ");
            case IS_NULL -> sql.append(" IS NULL ");
            case LESS_THAN, BEFORE -> sql.append(" < ? ");
            case LESS_THAN_EQUAL -> sql.append(" <= ? ");
            case GREATER_THAN, AFTER -> sql.append(" > ? ");
            case GREATER_THAN_EQUAL -> sql.append(" >= ? ");
            case NOT_LIKE, NOT_CONTAINING -> sql.append(" NOT LIKE ? ");
            case LIKE, CONTAINING, STARTING_WITH, ENDING_WITH -> sql.append(" LIKE ? ");
            case NOT_IN -> sql.append(" NOT IN (?) ");
            case IN -> sql.append(" IN (?) ");
            case TRUE -> sql.append(" = true ");
            case FALSE -> sql.append(" = false ");
            case NEGATING_SIMPLE_PROPERTY -> sql.append(" != ? ");
            case SIMPLE_PROPERTY -> sql.append(" = ? ");
        }
    }

    private static void appendOrderBy(StringBuilder sql, List<RepositoryMethodParser.SortOrder> orderBy) {
        for (int i = 0; i < orderBy.size(); i++) {
            RepositoryMethodParser.SortOrder sortOrder = orderBy.get(i);
            sql.append(resolveColumnName(sortOrder.property()));
            if (sortOrder.direction() != null) {
                sql.append(' ').append(sortOrder.direction().name());
            }
            if (i < orderBy.size() - 1) {
                sql.append(", ");
            } else {
                sql.append(' ');
            }
        }
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
            case IS_TRUE, TRUE -> sql.append(" = true ");
            case IS_FALSE, FALSE -> sql.append(" = false ");
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
        sql.append(resolveColumnName(component));
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
     * Resolves a field component to its SQL column name.
     * Handles Spring Data's underscore property traversal by using the
     * final property in the path.
     *
     * In Spring Data JPA, underscore followed by an uppercase letter in
     * method names indicates nested property access (e.g., Id_HospitalId
     * means id.hospitalId for an @EmbeddedId). The SQL column name comes
     * from the final property in the traversal path.
     *
     * @param component the field component from the method name
     * @return the resolved SQL column name in snake_case
     */
    static String resolveColumnName(String component) {
        int lastTraversal = -1;
        for (int i = 0; i < component.length() - 1; i++) {
            if (component.charAt(i) == '_' && Character.isUpperCase(component.charAt(i + 1))) {
                lastTraversal = i;
            }
        }
        if (lastTraversal >= 0) {
            return BaseRepositoryParser.camelToSnake(component.substring(lastTraversal + 1));
        }
        return BaseRepositoryParser.camelToSnake(component);
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
