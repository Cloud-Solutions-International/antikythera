package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMappingResolver;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaseRepositoryParserTest {

    public static final String ANIMAL_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.Animal";
    public static final String DOG_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.Dog";
    public static final String USER_REPOSITORY = "sa.com.cloudsolutions.antikythera.testhelper.repository.UserRepository";

    @BeforeAll
    static void setUpAll() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        EntityMappingResolver.reset();
        EntityMappingResolver.build();
    }

    @Test
    void testFindTableName() {
        CompilationUnit animal = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
        assertEquals("animals", BaseRepositoryParser.findTableName(new TypeWrapper(animal.getType(0))));
        CompilationUnit dog = AntikytheraRunTime.getCompilationUnit(DOG_ENTITY);
        assertEquals("dog", BaseRepositoryParser.findTableName(new TypeWrapper(dog.getType(0))));
    }

    @Test
    void testCamelToSnake() {
        assertEquals("user_name", BaseRepositoryParser.camelToSnake("userName"));
        assertEquals("first_name", BaseRepositoryParser.camelToSnake("firstName"));
        assertEquals("id", BaseRepositoryParser.camelToSnake("id"));
        assertEquals("user_id", BaseRepositoryParser.camelToSnake("userID"));
        assertEquals("", BaseRepositoryParser.camelToSnake(""));
    }

    @Test
    void testCountPlaceholders() {
        assertEquals(0, BaseRepositoryParser.countPlaceholders("SELECT * FROM users"));
        assertEquals(1, BaseRepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ?"));
        assertEquals(2, BaseRepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ? AND name = ?"));
        assertEquals(3,
                BaseRepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ? AND name = ? AND age > ?"));
    }

    @Test
    void testIsJpaRepository() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        assertFalse(BaseRepositoryParser.isJpaRepository((TypeWrapper) null));
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(USER_REPOSITORY).orElseThrow();
        assertTrue(BaseRepositoryParser.isJpaRepository(type));
        assertTrue(BaseRepositoryParser.isJpaRepository(new TypeWrapper(type)));
    }

    @Test
    void testParseNonAnnotatedMethod() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Test method pattern parsing
        MethodDeclaration findByUsername = repoUnit.findAll(MethodDeclaration.class).getFirst();
        Callable callable = new Callable(findByUsername, null);

        // Test that the method doesn't throw an exception
        assertDoesNotThrow(() -> {
            RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
            assertNotNull(q);
            assertTrue(q.getQuery().contains("SELECT * FROM users"));
        });
    }

    @Test
    void testProcessTypes() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        assertNotNull(parser.entityType);
        assertEquals("User", parser.entityType.toString());
    }

    @Test
    void testFindEntity() {
        TypeWrapper result = BaseRepositoryParser.findEntity(StaticJavaParser.parseClassOrInterfaceType("List"));

        // The method should handle the case where entity is not found
        assertNull(result);
    }

    @Test
    void testGetQueryFromRepositoryMethodMethod() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        MethodDeclaration method = repoUnit.findAll(MethodDeclaration.class).get(0);
        Callable callable = new Callable(method, null);

        RepositoryQuery query = parser.getQueryFromRepositoryMethod(callable);
        assertNull(query);

        parser.buildQueries();
        RepositoryQuery query2 = parser.getQueryFromRepositoryMethod(callable);
        assertNotNull(query2);
    }

    @Test
    void testFindByFieldInMethod() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Test extractComponents for method with "In"
        List<String> components = parser.extractComponents("findByApprovalIdIn");
        assertEquals(List.of("findBy", "ApprovalId", "In"), components);

        // Create a mock method to test parsing
        String methodCode = "public interface TestRepo { List<User> findByApprovalIdIn(Collection<Long> approvalIds); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        // Test that parseNonAnnotatedMethod handles "In" correctly
        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("IN"), "Query should contain IN clause: " + sql);
        assertTrue(sql.contains("approval_id"), "Query should contain snake_case field: " + sql);
    }

    @Test
    void testOrderByWithDesc() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Test extractComponents for method with OrderBy and Desc
        List<String> components = parser.extractComponents("findByActiveOrderByCreatedDateDesc");
        assertEquals(List.of("findBy", "Active", "OrderBy", "CreatedDate", "Desc"), components);

        // Create a mock method to test parsing
        String methodCode = "public interface TestRepo { List<User> findByActiveOrderByCreatedDateDesc(Boolean active); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        // Test that parseNonAnnotatedMethod handles OrderBy with Desc correctly
        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("ORDER BY"), "Query should contain ORDER BY: " + sql);
        assertTrue(sql.contains("created_date"), "Query should contain snake_case field: " + sql);
        assertTrue(sql.contains("DESC"), "Query should contain DESC keyword: " + sql);
    }

    @Test
    void testOrderByWithAsc() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Create a mock method to test parsing with Asc
        String methodCode = "public interface TestRepo { List<User> findAllOrderByNameAsc(); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("ORDER BY"), "Query should contain ORDER BY: " + sql);
        assertTrue(sql.contains("ASC") || !sql.contains("DESC"), "Query should contain ASC or no DESC: " + sql);
    }

    @Test
    void testOrderByMultipleFields() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Test with multiple order by fields
        String methodCode = "public interface TestRepo { List<User> findAllOrderByLastNameAscFirstNameDesc(); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("ORDER BY"), "Query should contain ORDER BY: " + sql);
        assertTrue(sql.contains("last_name"), "Query should contain last_name: " + sql);
        assertTrue(sql.contains("first_name"), "Query should contain first_name: " + sql);
    }

    @Test
    void testNotOperator() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Test Not operator standalone (e.g., findByActiveNot should mean active != ?)
        List<String> components = parser.extractComponents("findByActiveNot");
        assertEquals(List.of("findBy", "Active", "Not"), components);
    }

    @Test
    void testFindFirstByWithNoWhereClause() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Test findFirst with no where clause (should still add LIMIT/ROWNUM)
        String methodCode = "public interface TestRepo { User findFirstByOrderByIdDesc(); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        // Should have either LIMIT 1 (PostgreSQL) or WHERE ROWNUM (Oracle)
        assertTrue(sql.contains("LIMIT 1") || sql.contains("ROWNUM"),
                "Query should contain LIMIT or ROWNUM: " + sql);
        assertTrue(sql.contains("ORDER BY"), "Query should contain ORDER BY: " + sql);
        assertTrue(sql.contains("DESC"), "Query should contain DESC: " + sql);
    }

    @Test
    void testCountByQuery() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Create a mock method to test countBy
        String methodCode = "public interface TestRepo { Long countByActive(Boolean active); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("SELECT COUNT(*)"), "Query should contain COUNT: " + sql);
        assertTrue(sql.contains("FROM users"), "Query should reference users table: " + sql);
        assertTrue(sql.contains("WHERE"), "Query should have WHERE clause: " + sql);
    }

    @Test
    void testDeleteByQuery() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Create a mock method to test deleteBy
        String methodCode = "public interface TestRepo { void deleteByActive(Boolean active); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("DELETE FROM"), "Query should be DELETE: " + sql);
        assertTrue(sql.contains("users"), "Query should reference users table: " + sql);
        assertTrue(sql.contains("WHERE"), "Query should have WHERE clause: " + sql);
    }

    @Test
    void testExistsByQuery() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Create a mock method to test existsBy
        String methodCode = "public interface TestRepo { boolean existsByUsername(String username); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("SELECT EXISTS"), "Query should use EXISTS: " + sql);
        assertTrue(sql.contains("SELECT *"), "Query should select 1 in subquery: " + sql);
        assertTrue(sql.contains("FROM users"), "Query should reference users table: " + sql);
        assertTrue(sql.contains("WHERE"), "Query should have WHERE clause: " + sql);
        // Verify the closing parenthesis
        int openParens = sql.length() - sql.replace("(", "").length();
        int closeParens = sql.length() - sql.replace(")", "").length();
        assertEquals(openParens, closeParens, "Parentheses should be balanced: " + sql);
    }

    @Test
    void testCountByWithMultipleConditions() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Create a mock method to test countBy with multiple conditions
        String methodCode = "public interface TestRepo { Long countByActiveAndAgeGreaterThan(Boolean active, Integer age); }";
        CompilationUnit cu = StaticJavaParser.parse(methodCode);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();
        Callable callable = new Callable(md, null);

        RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
        assertNotNull(q);
        String sql = q.getQuery();
        assertTrue(sql.contains("SELECT COUNT(*)"), "Query should contain COUNT: " + sql);
        assertTrue(sql.contains("AND"), "Query should have AND operator: " + sql);
        assertTrue(sql.contains(">"), "Query should have greater than operator: " + sql);
    }

    /**
     * Test the extractComponents method with various edge cases involving "In"
     * keyword.
     * The key distinction is:
     * - "In" followed by lowercase = part of field name (e.g., "Invoice",
     * "Invoiced")
     * - "In" followed by uppercase or end of string = SQL IN clause (e.g., "IdIn",
     * "CategoryIn")
     */
    @ParameterizedTest
    @CsvSource({
            // Edge case: "Invoice" field should not be split at "In"
            "findByInvoice, 'findBy,Invoice'",
            // Edge case: "InvoiceItemId" with SQL IN clause at the end
            "findByInvoiceItemIdIn, 'findBy,InvoiceItemId,In'",
            // Edge case: "CategoryIn" with SQL IN clause, followed by "And"
            "findByCategoryInAndStatus, 'findBy,Category,In,And,Status'",
            // Standard cases with SQL IN clause
            "findByIdIn, 'findBy,Id,In'",
            "findByNameIn, 'findBy,Name,In'",
            // Field name starting with "In"
            "findByIndustry, 'findBy,Industry'",
            "findByInternalCode, 'findBy,InternalCode'",
            // Multiple conditions
            "findByInvoiceAndStatus, 'findBy,Invoice,And,Status'",
            "findByInvoiceIdAndStatusIn, 'findBy,InvoiceId,And,Status,In'",
            // NotIn clause (should be matched as a single keyword)
            "findByStatusNotIn, 'findBy,Status,NotIn'",
            "findByInvoiceStatusNotIn, 'findBy,InvoiceStatus,NotIn'",
            // Complex case with ordering
            "findByInvoiceItemIdInOrderByCreatedDateDesc, 'findBy,InvoiceItemId,In,OrderBy,CreatedDate,Desc'",
    })
    void testExtractComponents(String methodName, String expectedComponentsStr) throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents(methodName);
        List<String> expected = List.of(expectedComponentsStr.split(","));

        assertEquals(expected, components,
                String.format("Method '%s' should be parsed as %s but got %s",
                        methodName, expected, components));
    }

    @Test
    void testExtractComponents_EmptyMethodName() throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents("");
        assertTrue(components.isEmpty(), "Empty method name should produce empty components");
    }

    @Test
    void testExtractComponents_OnlyKeyword() throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents("findAll");
        assertEquals(List.of("findAll"), components);
    }

    @Test
    void testExtractComponents_MultipleAndOr() throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents("findByInvoiceAndStatusOrCategory");
        assertEquals(List.of("findBy", "Invoice", "And", "Status", "Or", "Category"), components);
    }

    @Test
    void testExtractComponents_BetweenClause() throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents("findByInvoiceDateBetween");
        assertEquals(List.of("findBy", "InvoiceDate", "Between"), components);
    }

    @Test
    void testExtractComponents_GreaterThanLessThan() throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents("findByInvoiceAmountGreaterThanAndStatusIn");
        assertEquals(List.of("findBy", "InvoiceAmount", "GreaterThan", "And", "Status", "In"), components);
    }

    @Test
    void testExtractComponents_IsNullIsNotNull() throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents("findByInvoiceIsNullAndInternalCodeIsNotNull");
        assertEquals(List.of("findBy", "Invoice", "IsNull", "And", "InternalCode", "IsNotNull"), components);
    }

    @Test
    void testExtractComponents_LikeContaining() throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        List<String> components = parser.extractComponents("findByInvoiceNumberLikeAndDescriptionContaining");
        assertEquals(List.of("findBy", "InvoiceNumber", "Like", "And", "Description", "Containing"), components);
    }
}
