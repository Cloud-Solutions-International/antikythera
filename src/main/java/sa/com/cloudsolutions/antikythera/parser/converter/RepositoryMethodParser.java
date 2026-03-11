package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight recursive-descent parser for Spring Data style repository method
 * names.
 *
 * <p>
 * The parser models the same broad shape as Spring Data Commons: a subject
 * (query verb/projection), a predicate split into OR parts and AND-connected
 * parts, and an optional static ORDER BY clause.
 * </p>
 */
public final class RepositoryMethodParser {
    private static final String BY = "By";
    private static final String ALL = "All";
    private static final String DISTINCT = "Distinct";
    private static final String FIRST = "First";
    private static final String TOP = "Top";
    private static final String ORDER_BY = BaseRepositoryParser.ORDER_BY;
    private static final String ALL_IGNORE_CASE = MethodToSQLConverter.ALL_IGNORE_CASE;
    private static final String IGNORE_CASE = MethodToSQLConverter.IGNORE_CASE;
    private static final String IGNORING_CASE = "IgnoringCase";
    private static final String AND = MethodToSQLConverter.AND;
    private static final String OR = MethodToSQLConverter.OR;
    private static final String ASC = MethodToSQLConverter.ASC;
    private static final String DESC = MethodToSQLConverter.DESC;

    private RepositoryMethodParser() {
    }

    public static ParsedMethod parse(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return ParsedMethod.unrecognized(methodName);
        }
        return new Parser(methodName).parse();
    }

    public record ParsedMethod(String source, Subject subject, Predicate predicate, List<SortOrder> orderBy) {
        static ParsedMethod unrecognized(String source) {
            return new ParsedMethod(source,
                    new Subject("", QueryAction.UNKNOWN, false, Optional.empty()),
                    Predicate.empty(),
                    List.of());
        }

        public boolean isRecognized() {
            return subject.action() != QueryAction.UNKNOWN;
        }

        public boolean hasPredicate() {
            return !predicate.orParts().isEmpty();
        }

        public boolean hasOrderBy() {
            return !orderBy.isEmpty();
        }

        public boolean isLimiting() {
            return subject.maxResults().isPresent();
        }

        public int getMaxResultsOrDefault(int defaultValue) {
            return subject.maxResults().orElse(defaultValue);
        }

        public List<String> toComponents() {
            if (!isRecognized()) {
                return List.of();
            }

            List<String> components = new ArrayList<>();
            if (!subject.token().isEmpty()) {
                components.add(subject.token());
            }

            for (int i = 0; i < predicate.orParts().size(); i++) {
                if (i > 0) {
                    components.add(OR);
                }

                OrPart orPart = predicate.orParts().get(i);
                for (int j = 0; j < orPart.parts().size(); j++) {
                    if (j > 0) {
                        components.add(AND);
                    }

                    PredicatePart part = orPart.parts().get(j);
                    components.add(part.property());
                    components.addAll(part.toLegacyOperatorTokens());
                    if (part.ignoreCase() == IgnoreCaseType.ALWAYS) {
                        components.add(IGNORE_CASE);
                    }
                }
            }

            if (predicate.allIgnoreCase()) {
                components.add(ALL_IGNORE_CASE);
            }

            if (!orderBy.isEmpty()) {
                components.add(ORDER_BY);
                for (SortOrder sortOrder : orderBy) {
                    components.add(sortOrder.property());
                    if (sortOrder.direction() != null) {
                        components.add(sortOrder.direction() == Direction.ASC ? ASC : DESC);
                    }
                }
            }

            return components;
        }
    }

    public record Subject(String token, QueryAction action, boolean distinct, Optional<Integer> maxResults) {
    }

    public record Predicate(List<OrPart> orParts, boolean allIgnoreCase) {
        static Predicate empty() {
            return new Predicate(List.of(), false);
        }
    }

    public record OrPart(List<PredicatePart> parts) {
    }

    public record PredicatePart(String property, PartType type, String matchedKeyword, IgnoreCaseType ignoreCase) {
        List<String> toLegacyOperatorTokens() {
            return switch (type) {
                case SIMPLE_PROPERTY -> matchedKeyword == null ? List.of() : List.of(matchedKeyword);
                case NEGATING_SIMPLE_PROPERTY -> List.of(
                        matchedKeyword != null && MethodToSQLConverter.IS_NOT.equals(matchedKeyword)
                                ? MethodToSQLConverter.IS_NOT
                                : MethodToSQLConverter.NOT);
                case NOT_LIKE -> List.of(MethodToSQLConverter.NOT, MethodToSQLConverter.LIKE);
                case NOT_CONTAINING -> List.of(MethodToSQLConverter.NOT, MethodToSQLConverter.CONTAINING);
                case NOT_IN -> List.of(MethodToSQLConverter.NOT_IN);
                case STARTING_WITH -> List.of(MethodToSQLConverter.STARTING_WITH);
                case ENDING_WITH -> List.of(MethodToSQLConverter.ENDING_WITH);
                case CONTAINING -> List.of(MethodToSQLConverter.CONTAINING);
                case LIKE -> List.of(MethodToSQLConverter.LIKE);
                case TRUE -> List.of(matchedKeyword != null ? matchedKeyword : MethodToSQLConverter.TRUE);
                case FALSE -> List.of(matchedKeyword != null ? matchedKeyword : MethodToSQLConverter.FALSE);
                case IS_NOT_NULL -> List.of(MethodToSQLConverter.IS_NOT_NULL);
                case IS_NULL -> List.of(MethodToSQLConverter.IS_NULL);
                case IN -> List.of(MethodToSQLConverter.IN);
                case BETWEEN -> List.of(MethodToSQLConverter.BETWEEN);
                case GREATER_THAN -> List.of(MethodToSQLConverter.GREATER_THAN);
                case GREATER_THAN_EQUAL -> List.of(MethodToSQLConverter.GREATER_THAN_EQUAL);
                case LESS_THAN -> List.of(MethodToSQLConverter.LESS_THAN);
                case LESS_THAN_EQUAL -> List.of(MethodToSQLConverter.LESS_THAN_EQUAL);
                case BEFORE -> List.of(MethodToSQLConverter.BEFORE);
                case AFTER -> List.of(MethodToSQLConverter.AFTER);
            };
        }
    }

    public record SortOrder(String property, Direction direction) {
    }

    public enum Direction {
        ASC,
        DESC
    }

    public enum IgnoreCaseType {
        NEVER,
        ALWAYS,
        WHEN_POSSIBLE
    }

    public enum QueryAction {
        SELECT,
        COUNT,
        EXISTS,
        DELETE,
        INSERT_DEFAULT,
        UNKNOWN
    }

    public enum PartType {
        BETWEEN(2, "IsBetween", MethodToSQLConverter.BETWEEN),
        IS_NOT_NULL(0, "IsNotNull", "NotNull"),
        IS_NULL(0, "IsNull", "Null"),
        LESS_THAN_EQUAL(1, "IsLessThanEqual", MethodToSQLConverter.LESS_THAN_EQUAL),
        LESS_THAN(1, "IsLessThan", MethodToSQLConverter.LESS_THAN),
        GREATER_THAN_EQUAL(1, "IsGreaterThanEqual", MethodToSQLConverter.GREATER_THAN_EQUAL),
        GREATER_THAN(1, "IsGreaterThan", MethodToSQLConverter.GREATER_THAN),
        BEFORE(1, "IsBefore", MethodToSQLConverter.BEFORE),
        AFTER(1, "IsAfter", MethodToSQLConverter.AFTER),
        NOT_LIKE(1, "IsNotLike", "NotLike"),
        LIKE(1, "IsLike", MethodToSQLConverter.LIKE),
        STARTING_WITH(1, "IsStartingWith", MethodToSQLConverter.STARTING_WITH, "StartsWith"),
        ENDING_WITH(1, "IsEndingWith", MethodToSQLConverter.ENDING_WITH, "EndsWith"),
        NOT_CONTAINING(1, "IsNotContaining", "NotContaining", "NotContains"),
        CONTAINING(1, "IsContaining", MethodToSQLConverter.CONTAINING, "Contains"),
        NOT_IN(1, "IsNotIn", MethodToSQLConverter.NOT_IN),
        IN(1, "IsIn", MethodToSQLConverter.IN),
        TRUE(0, MethodToSQLConverter.IS_TRUE, MethodToSQLConverter.TRUE),
        FALSE(0, MethodToSQLConverter.IS_FALSE, MethodToSQLConverter.FALSE),
        NEGATING_SIMPLE_PROPERTY(1, MethodToSQLConverter.IS_NOT, MethodToSQLConverter.NOT),
        SIMPLE_PROPERTY(1, MethodToSQLConverter.IS, MethodToSQLConverter.EQUAL);

        private static final List<PartType> DETECTION_ORDER = List.of(
                IS_NOT_NULL,
                IS_NULL,
                BETWEEN,
                LESS_THAN_EQUAL,
                LESS_THAN,
                GREATER_THAN_EQUAL,
                GREATER_THAN,
                BEFORE,
                AFTER,
                NOT_LIKE,
                LIKE,
                STARTING_WITH,
                ENDING_WITH,
                NOT_CONTAINING,
                CONTAINING,
                NOT_IN,
                IN,
                TRUE,
                FALSE,
                NEGATING_SIMPLE_PROPERTY,
                SIMPLE_PROPERTY);

        private final int argumentCount;
        private final List<String> keywords;

        PartType(int argumentCount, String... keywords) {
            this.argumentCount = argumentCount;
            this.keywords = List.of(keywords);
        }

        public int argumentCount() {
            return argumentCount;
        }

        OperatorMatch match(String rawPart) {
            for (String keyword : keywords) {
                if (rawPart.endsWith(keyword) && rawPart.length() > keyword.length()) {
                    return new OperatorMatch(this, keyword, rawPart.substring(0, rawPart.length() - keyword.length()));
                }
            }
            return null;
        }

        static OperatorMatch resolve(String rawPart) {
            for (PartType partType : DETECTION_ORDER) {
                OperatorMatch match = partType.match(rawPart);
                if (match != null) {
                    return match;
                }
            }
            return new OperatorMatch(SIMPLE_PROPERTY, null, rawPart);
        }
    }

    private record OperatorMatch(PartType type, String matchedKeyword, String property) {
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private ParsedMethod parse() {
            if (MethodToSQLConverter.FIND_ALL_BY_ID.equals(source)) {
                return new ParsedMethod(source,
                        new Subject(MethodToSQLConverter.FIND_ALL_BY_ID, QueryAction.SELECT, false, Optional.empty()),
                        Predicate.empty(),
                        List.of());
            }
            if (MethodToSQLConverter.DELETE_ALL_BY_ID.equals(source)) {
                return new ParsedMethod(source,
                        new Subject(MethodToSQLConverter.DELETE_ALL_BY_ID, QueryAction.DELETE, false, Optional.empty()),
                        Predicate.empty(),
                        List.of());
            }
            if (MethodToSQLConverter.DELETE_ALL_BY_ID_IN_BATCH.equals(source)) {
                return new ParsedMethod(source,
                        new Subject(MethodToSQLConverter.DELETE_ALL_BY_ID_IN_BATCH, QueryAction.DELETE, false, Optional.empty()),
                        Predicate.empty(),
                        List.of());
            }
            if (MethodToSQLConverter.GET_ONE.equals(source)) {
                return new ParsedMethod(source,
                        new Subject(MethodToSQLConverter.GET_ONE, QueryAction.SELECT, false, Optional.empty()),
                        Predicate.empty(),
                        List.of());
            }
            if (MethodToSQLConverter.SAVE_ALL.equals(source)
                    || MethodToSQLConverter.SAVE_AND_FLUSH.equals(source)
                    || MethodToSQLConverter.SAVE_ALL_AND_FLUSH.equals(source)) {
                return new ParsedMethod(source,
                        new Subject(source, QueryAction.INSERT_DEFAULT, false, Optional.empty()),
                        Predicate.empty(),
                        List.of());
            }

            Subject subject = parseSubject();
            if (subject.action() == QueryAction.UNKNOWN) {
                return ParsedMethod.unrecognized(source);
            }

            int orderByIndex = indexOfKeyword(index, ORDER_BY);
            int predicateEnd = orderByIndex >= 0 ? orderByIndex : source.length();
            Predicate predicate = PredicateParser.parse(source.substring(index, predicateEnd));
            index = predicateEnd;

            List<SortOrder> orderBy = List.of();
            if (startsWith(ORDER_BY)) {
                index += ORDER_BY.length();
                orderBy = OrderByParser.parse(source.substring(index));
            }

            return new ParsedMethod(source, subject, predicate, orderBy);
        }

        private Subject parseSubject() {
            if (consume(MethodToSQLConverter.SAVE)) {
                return new Subject(MethodToSQLConverter.SAVE, QueryAction.INSERT_DEFAULT, false, Optional.empty());
            }

            if (consume("find")) {
                return parseFindSubject();
            }
            if (consume("count")) {
                return parseSimpleSubject(MethodToSQLConverter.COUNT_BY, MethodToSQLConverter.COUNT_ALL_BY,
                        QueryAction.COUNT);
            }
            if (consume("exists")) {
                return parseSimpleSubject(MethodToSQLConverter.EXISTS_BY, MethodToSQLConverter.EXISTS_ALL_BY,
                        QueryAction.EXISTS);
            }
            if (consume("delete")) {
                return parseSimpleSubject(MethodToSQLConverter.DELETE_BY, MethodToSQLConverter.DELETE_ALL_BY,
                        QueryAction.DELETE);
            }
            if (consume("remove")) {
                return parseVerbSubject(MethodToSQLConverter.REMOVE_BY, QueryAction.DELETE);
            }
            if (consume("read")) {
                return parseVerbSubject(MethodToSQLConverter.READ_BY, QueryAction.SELECT);
            }
            if (consume("query")) {
                return parseVerbSubject(MethodToSQLConverter.QUERY_BY, QueryAction.SELECT);
            }
            if (consume("search")) {
                return parseVerbSubject(MethodToSQLConverter.SEARCH_BY, QueryAction.SELECT);
            }
            if (consume("stream")) {
                return parseVerbSubject(MethodToSQLConverter.STREAM_BY, QueryAction.SELECT);
            }
            if (consume("get")) {
                return parseVerbSubject(MethodToSQLConverter.GET, QueryAction.SELECT);
            }

            return new Subject("", QueryAction.UNKNOWN, false, Optional.empty());
        }

        private Subject parseFindSubject() {
            boolean distinct = consume(DISTINCT);
            LimitKind limitKind = LimitKind.NONE;
            int maxResults = 1;

            if (consume(FIRST)) {
                limitKind = LimitKind.FIRST;
                maxResults = parseOptionalNumber();
            } else if (consume(TOP)) {
                limitKind = LimitKind.TOP;
                maxResults = parseOptionalNumber();
            }

            if (consume(ALL)) {
                boolean hasPredicate = advancePastSubjectIfPresent();
                return new Subject(hasPredicate ? MethodToSQLConverter.FIND_ALL_BY : MethodToSQLConverter.FIND_ALL,
                        QueryAction.SELECT,
                        distinct,
                        Optional.empty());
            }

            if (consume(BY)) {
                return new Subject(selectToken(limitKind, distinct), QueryAction.SELECT, distinct, limit(limitKind, maxResults));
            }

            boolean hasPredicate = advancePastSubjectIfPresent();
            return new Subject(
                    hasPredicate ? selectToken(limitKind, distinct) : selectNoPredicateToken(limitKind, distinct),
                    QueryAction.SELECT,
                    distinct,
                    limit(limitKind, maxResults));
        }

        private Subject parseSimpleSubject(String token, String allToken, QueryAction action) {
            boolean all = consume(ALL);
            boolean hasPredicate;
            if (consume(BY)) {
                hasPredicate = true;
            } else {
                hasPredicate = advancePastSubjectIfPresent();
            }

            return new Subject(all && hasPredicate ? allToken : token, action, false, Optional.empty());
        }

        private Subject parseVerbSubject(String token, QueryAction action) {
            if (!consume(BY)) {
                advancePastSubjectIfPresent();
            }
            return new Subject(token, action, false, Optional.empty());
        }

        private String selectToken(LimitKind limitKind, boolean distinct) {
            if (distinct) {
                return MethodToSQLConverter.FIND_DISTINCT_BY;
            }
            return switch (limitKind) {
                case FIRST -> MethodToSQLConverter.FIND_FIRST_BY;
                case TOP -> MethodToSQLConverter.FIND_TOP_BY;
                case NONE -> MethodToSQLConverter.FIND_BY;
            };
        }

        private String selectNoPredicateToken(LimitKind limitKind, boolean distinct) {
            if (limitKind != LimitKind.NONE || distinct) {
                return selectToken(limitKind, distinct);
            }
            return MethodToSQLConverter.FIND_ALL;
        }

        private Optional<Integer> limit(LimitKind limitKind, int maxResults) {
            return limitKind == LimitKind.NONE ? Optional.empty() : Optional.of(maxResults);
        }

        private int parseOptionalNumber() {
            int start = index;
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (start == index) {
                return 1;
            }
            return Integer.parseInt(source.substring(start, index));
        }

        private boolean advancePastSubjectIfPresent() {
            int byIndex = indexOfKeyword(index, BY);
            int orderByIndex = indexOfKeyword(index, ORDER_BY);

            if (byIndex >= 0 && (orderByIndex < 0 || byIndex < orderByIndex)) {
                index = byIndex + BY.length();
                return true;
            }

            if (orderByIndex >= 0) {
                index = orderByIndex;
            } else {
                index = source.length();
            }
            return false;
        }

        private boolean consume(String keyword) {
            if (startsWith(keyword)) {
                index += keyword.length();
                return true;
            }
            return false;
        }

        private boolean startsWith(String keyword) {
            return source.startsWith(keyword, index);
        }

        private int indexOfKeyword(int startIndex, String keyword) {
            for (int i = startIndex; i <= source.length() - keyword.length(); i++) {
                if (source.startsWith(keyword, i) && hasBoundaryAfter(i + keyword.length())) {
                    return i;
                }
            }
            return -1;
        }

        private boolean hasBoundaryAfter(int endIndex) {
            return endIndex >= source.length() || !Character.isLowerCase(source.charAt(endIndex));
        }
    }

    private static final class PredicateParser {
        private String source;
        private int index;
        private boolean allIgnoreCase;

        private PredicateParser(String source) {
            this.source = source;
        }

        static Predicate parse(String source) {
            if (source == null || source.isEmpty()) {
                return Predicate.empty();
            }
            return new PredicateParser(source).parse();
        }

        private Predicate parse() {
            stripAllIgnoreCase();

            List<OrPart> orParts = new ArrayList<>();
            while (!isAtEnd()) {
                List<PredicatePart> andParts = new ArrayList<>();
                andParts.add(parsePart());
                while (consumeLogical(AND)) {
                    andParts.add(parsePart());
                }
                orParts.add(new OrPart(andParts));
                if (!consumeLogical(OR)) {
                    break;
                }
            }

            return new Predicate(orParts, allIgnoreCase);
        }

        private void stripAllIgnoreCase() {
            if (source.endsWith(ALL_IGNORE_CASE)) {
                source = source.substring(0, source.length() - ALL_IGNORE_CASE.length());
                allIgnoreCase = true;
            }
        }

        private PredicatePart parsePart() {
            String fragment = readUntilLogicalBoundary();
            IgnoreCaseType ignoreCase = IgnoreCaseType.NEVER;

            if (fragment.endsWith(IGNORE_CASE)) {
                fragment = fragment.substring(0, fragment.length() - IGNORE_CASE.length());
                ignoreCase = IgnoreCaseType.ALWAYS;
            } else if (fragment.endsWith(IGNORING_CASE)) {
                fragment = fragment.substring(0, fragment.length() - IGNORING_CASE.length());
                ignoreCase = IgnoreCaseType.ALWAYS;
            } else if (allIgnoreCase) {
                ignoreCase = IgnoreCaseType.WHEN_POSSIBLE;
            }

            OperatorMatch operatorMatch = PartType.resolve(fragment);
            return new PredicatePart(operatorMatch.property(), operatorMatch.type(), operatorMatch.matchedKeyword(), ignoreCase);
        }

        private String readUntilLogicalBoundary() {
            int start = index;
            while (!isAtEnd()) {
                if (startsWith(AND) && hasBoundaryAfter(index + AND.length())) {
                    break;
                }
                if (startsWith(OR) && hasBoundaryAfter(index + OR.length())) {
                    break;
                }
                index++;
            }
            return source.substring(start, index);
        }

        private boolean consumeLogical(String keyword) {
            if (startsWith(keyword) && hasBoundaryAfter(index + keyword.length())) {
                index += keyword.length();
                return true;
            }
            return false;
        }

        private boolean startsWith(String keyword) {
            return source.startsWith(keyword, index);
        }

        private boolean hasBoundaryAfter(int endIndex) {
            return endIndex >= source.length() || !Character.isLowerCase(source.charAt(endIndex));
        }

        private boolean isAtEnd() {
            return index >= source.length();
        }
    }

    private static final class OrderByParser {
        private final String source;
        private int index;

        private OrderByParser(String source) {
            this.source = source;
        }

        static List<SortOrder> parse(String source) {
            if (source == null || source.isEmpty()) {
                return List.of();
            }
            return new OrderByParser(source).parse();
        }

        private List<SortOrder> parse() {
            List<SortOrder> orders = new ArrayList<>();
            while (index < source.length()) {
                String property = readProperty();
                Direction direction = null;
                if (startsWith(ASC) && hasBoundaryAfter(index + ASC.length())) {
                    index += ASC.length();
                    direction = Direction.ASC;
                } else if (startsWith(DESC) && hasBoundaryAfter(index + DESC.length())) {
                    index += DESC.length();
                    direction = Direction.DESC;
                }

                orders.add(new SortOrder(property, direction));
            }
            return orders;
        }

        private String readProperty() {
            int start = index;
            while (index < source.length()) {
                if (startsWith(ASC) && hasBoundaryAfter(index + ASC.length())) {
                    break;
                }
                if (startsWith(DESC) && hasBoundaryAfter(index + DESC.length())) {
                    break;
                }
                index++;
            }
            return source.substring(start, index);
        }

        private boolean startsWith(String keyword) {
            return source.startsWith(keyword, index);
        }

        private boolean hasBoundaryAfter(int endIndex) {
            return endIndex >= source.length() || !Character.isLowerCase(source.charAt(endIndex));
        }
    }

    private enum LimitKind {
        NONE,
        FIRST,
        TOP
    }
}
