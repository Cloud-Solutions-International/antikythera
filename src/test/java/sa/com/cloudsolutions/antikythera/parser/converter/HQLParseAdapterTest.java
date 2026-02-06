package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HQLParseAdapterTest extends TestHelper {
    private static final String USER_MODEL = "sa.com.cloudsolutions.antikythera.testhelper.model.User";
    private static final String VEHICAL_MODEL = "sa.com.cloudsolutions.antikythera.testhelper.model.Vehicle";
    private static HQLParserAdapter adapter;

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        EntityMappingResolver.reset();
        EntityMappingResolver.build();

        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        CompilationUnit cu = type.findCompilationUnit().orElseThrow();
        adapter = new HQLParserAdapter(cu, new TypeWrapper(type));
    }

    @ParameterizedTest
    @ValueSource(strings = { USER_MODEL, VEHICAL_MODEL })
    void testGetEntiyNameForEntity(String model) {
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(model).orElseThrow();
        CompilationUnit cu = type.findCompilationUnit().orElseThrow();
        adapter = new HQLParserAdapter(cu, new TypeWrapper(type));
        assertEquals(USER_MODEL, adapter.getEntiyNameForEntity(USER_MODEL));
        assertEquals(USER_MODEL, adapter.getEntiyNameForEntity("User"));
        assertEquals(VEHICAL_MODEL, adapter.getEntiyNameForEntity(VEHICAL_MODEL));
        assertNull(adapter.getEntiyNameForEntity("xx"));
    }

    static Stream<Arguments> spelPreprocessingProvider() {
        return Stream.of(
            Arguments.of(
                "SELECT u FROM User u WHERE u.id = :#{#userId}",
                ":#{#userId}",
                ":userId"
            ),
            Arguments.of(
                "SELECT u FROM User u WHERE u.id = :#{#searchModel.userId}",
                ":#{#searchModel.userId}",
                ":userId"
            ),
            Arguments.of(
                "SELECT u FROM User u WHERE u.id = :#{#searchModel.getUserId()}",
                ":#{#searchModel.getUserId()}",
                ":userId"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("spelPreprocessingProvider")
    void testPreprocessSpELExpressions(String query, String spelKey, String expectedMapping) {
        HQLParserAdapter.SpELPreprocessingResult result = adapter.preprocessSpELExpressions(query);
        assertEquals("SELECT u FROM User u WHERE u.id = :userId", result.preprocessedQuery);
        assertEquals(1, result.spelMapping.size());
        assertTrue(result.spelMapping.containsKey(spelKey));
        assertEquals(expectedMapping, result.spelMapping.get(spelKey));
    }

    @Test
    void testPreprocessSpELExpressions_MultipleSpELExpressions() {
        String query = "SELECT u FROM User u WHERE u.id = :#{#model.userId} AND u.name = :#{#model.userName}";
        HQLParserAdapter.SpELPreprocessingResult result = adapter.preprocessSpELExpressions(query);

        assertEquals("SELECT u FROM User u WHERE u.id = :userId AND u.name = :userName", result.preprocessedQuery);
        assertEquals(2, result.spelMapping.size());
        assertTrue(result.spelMapping.containsKey(":#{#model.userId}"));
        assertTrue(result.spelMapping.containsKey(":#{#model.userName}"));
    }

    @Test
    void testPreprocessSpELExpressions_ComplexQuery() {
        String query = "SELECT u FROM User u WHERE u.id = :#{#inPatientPhrSearchModel.admissionId} " +
                "AND u.patientId = :#{#inPatientPhrSearchModel.patientId} " +
                "AND ((:#{#inPatientPhrSearchModel.getPayerGroupId()} IS NULL) OR " +
                "(:#{#inPatientPhrSearchModel.getPayerGroupId()} IS NOT NULL))";
        HQLParserAdapter.SpELPreprocessingResult result = adapter.preprocessSpELExpressions(query);

        assertTrue(result.preprocessedQuery.contains(":admissionId"));
        assertTrue(result.preprocessedQuery.contains(":patientId"));
        assertTrue(result.preprocessedQuery.contains(":payerGroupId"));
        // Note: The same SpEL expression appears twice, but the mapping only stores
        // unique expressions
        // So we have 3 unique SpEL expressions: admissionId, patientId, and
        // getPayerGroupId()
        assertEquals(3, result.spelMapping.size());
        assertTrue(result.spelMapping.containsKey(":#{#inPatientPhrSearchModel.admissionId}"));
        assertTrue(result.spelMapping.containsKey(":#{#inPatientPhrSearchModel.patientId}"));
        assertTrue(result.spelMapping.containsKey(":#{#inPatientPhrSearchModel.getPayerGroupId()}"));
    }

    @Test
    void testPreprocessSpELExpressions_NoSpELExpressions() {
        String query = "SELECT u FROM User u WHERE u.id = :userId";
        HQLParserAdapter.SpELPreprocessingResult result = adapter.preprocessSpELExpressions(query);

        assertEquals(query, result.preprocessedQuery);
        assertEquals(0, result.spelMapping.size());
    }

    @Test
    void testExtractParameterName_SimpleProperty() {
        assertEquals("admissionId", adapter.extractParameterName("#inPatientPhrSearchModel.admissionId", 1));
        assertEquals("userId", adapter.extractParameterName("#model.userId", 1));
    }

    @Test
    void testExtractParameterName_MethodCall() {
        assertEquals("payerGroupId", adapter.extractParameterName("#inPatientPhrSearchModel.getPayerGroupId()", 1));
        assertEquals("userId", adapter.extractParameterName("#searchModel.getUserId()", 1));
    }

    @Test
    void testExtractParameterName_SimpleVariable() {
        assertEquals("userId", adapter.extractParameterName("#userId", 1));
        assertEquals("name", adapter.extractParameterName("#name", 1));
    }

    @Test
    void testExtractParameterName_Fallback() {
        // Test with invalid identifier - should fallback to generic name
        String result = adapter.extractParameterName("#123invalid", 5);
        assertEquals("spel_param_5", result);
    }

    @Test
    void testPostprocessSpELExpressions() {
        Map<String, String> reverseMapping = Map.of(
                ":userId", ":#{#userId}",
                ":userName", ":#{#userName}");

        String sql = "SELECT * FROM users WHERE id = :userId AND name = :userName";
        String result = adapter.postprocessSpELExpressions(sql, reverseMapping);

        assertEquals("SELECT * FROM users WHERE id = :#{#userId} AND name = :#{#userName}", result);
    }

    @Test
    void testPostprocessSpELExpressions_NoMappings() {
        String sql = "SELECT * FROM users WHERE id = :userId";
        String result = adapter.postprocessSpELExpressions(sql, Map.of());

        assertEquals(sql, result);
    }

    // ========== LIKE Wildcard Preprocessing Tests ==========

    @Test
    void testPreprocessLikeWildcards_BothWildcards() {
        String query = "SELECT c FROM Crop c WHERE c.variety LIKE %:searchTerm%";
        String result = adapter.preprocessLikeWildcards(query);

        assertEquals("SELECT c FROM Crop c WHERE c.variety LIKE CONCAT('%', :searchTerm, '%')", result);
    }

    @Test
    void testPreprocessLikeWildcards_PrefixWildcard() {
        String query = "SELECT c FROM Crop c WHERE c.variety LIKE %:searchTerm";
        String result = adapter.preprocessLikeWildcards(query);

        assertEquals("SELECT c FROM Crop c WHERE c.variety LIKE CONCAT('%', :searchTerm)", result);
    }

    @Test
    void testPreprocessLikeWildcards_SuffixWildcard() {
        String query = "SELECT c FROM Crop c WHERE c.variety LIKE :searchTerm%";
        String result = adapter.preprocessLikeWildcards(query);

        assertEquals("SELECT c FROM Crop c WHERE c.variety LIKE CONCAT(:searchTerm, '%')", result);
    }

    @Test
    void testPreprocessLikeWildcards_WithSpEL() {
        String query = "SELECT c FROM Crop c WHERE c.variety LIKE %:#{#search.term}%";
        String result = adapter.preprocessLikeWildcards(query);

        assertEquals("SELECT c FROM Crop c WHERE c.variety LIKE CONCAT('%', :#{#search.term}, '%')", result);
    }

    @Test
    void testPreprocessLikeWildcards_MultipleConditions() {
        String query = "SELECT f FROM Field f WHERE (f.fieldName LIKE %:term% OR f.soilType LIKE %:term%)";
        String result = adapter.preprocessLikeWildcards(query);

        assertEquals("SELECT f FROM Field f WHERE (f.fieldName LIKE CONCAT('%', :term, '%') OR f.soilType LIKE CONCAT('%', :term, '%'))", result);
    }

    @Test
    void testPreprocessLikeWildcards_NoWildcards() {
        String query = "SELECT c FROM Crop c WHERE c.variety LIKE :searchTerm";
        String result = adapter.preprocessLikeWildcards(query);

        // No change expected
        assertEquals(query, result);
    }

    @Test
    void testPreprocessLikeWildcards_CaseInsensitive() {
        String query = "SELECT c FROM Crop c WHERE c.variety like %:searchTerm%";
        String result = adapter.preprocessLikeWildcards(query);

        assertEquals("SELECT c FROM Crop c WHERE c.variety like CONCAT('%', :searchTerm, '%')", result);
    }

    @Test
    void testRemoveASFromConstructorExpressions_SimpleCase() {
        String query = "SELECT NEW com.example.DTO(SUM(amount) AS total, COUNT(*) AS count) FROM Order o";
        String result = adapter.removeASFromConstructorExpressions(query);

        assertEquals("SELECT NEW com.example.DTO(SUM(amount), COUNT(*)) FROM Order o", result);
    }

    @Test
    void testRemoveASFromConstructorExpressions_WithCAST() {
        // Should preserve CAST ... AS expressions
        String query = "SELECT NEW com.example.DTO(CAST(price AS DECIMAL) AS price, amount AS total) FROM Order o";
        String result = adapter.removeASFromConstructorExpressions(query);

        assertTrue(result.contains("CAST(price AS DECIMAL)"));
        assertFalse(result.contains("AS price"));
        assertFalse(result.contains("AS total"));
    }

    @Test
    void testRemoveASFromConstructorExpressions_NoConstructor() {
        String query = "SELECT u.name AS userName, u.email AS userEmail FROM User u";
        String result = adapter.removeASFromConstructorExpressions(query);

        // Should return unchanged since there's no SELECT NEW
        assertEquals(query, result);
    }

    @Test
    void testRemoveASFromConstructorExpressions_ComplexCase() {
        String query = "SELECT NEW com.example.DTO(" +
                "SUM(CASE WHEN status = 'ACTIVE' THEN amount ELSE 0 END) AS activeAmount, " +
                "SUM(CASE WHEN status = 'INACTIVE' THEN amount ELSE 0 END) AS inactiveAmount" +
                ") FROM Order o";
        String result = adapter.removeASFromConstructorExpressions(query);

        assertFalse(result.contains("AS activeAmount"));
        assertFalse(result.contains("AS inactiveAmount"));
        assertTrue(result.contains("SUM(CASE WHEN status = 'ACTIVE' THEN amount ELSE 0 END)"));
    }

    @Test
    void testRemoveASFromConstructorExpressions_MultipleAS() {
        String query = "SELECT NEW com.example.DTO(field1 AS alias1, field2 AS alias2, field3 AS alias3) FROM Entity e";
        String result = adapter.removeASFromConstructorExpressions(query);

        assertEquals("SELECT NEW com.example.DTO(field1, field2, field3) FROM Entity e", result);
    }

    @Test
    void testRemoveASFromConstructorExpressions_NestedCAST() {
        // This test case fails with the original regex because [^)]+ stops at the first
        // closing parenthesis
        String query = "SELECT NEW com.example.DTO(CAST(SUM(amount) AS DECIMAL) AS total) FROM Order o";
        String result = adapter.removeASFromConstructorExpressions(query);

        // Expected: AS total is removed, but CAST(SUM(amount) AS DECIMAL) is preserved
        String expected = "SELECT NEW com.example.DTO(CAST(SUM(amount) AS DECIMAL)) FROM Order o";
        assertEquals(expected, result);
    }

    @Test
    void testRemoveASFromConstructorExpressions_MultipleNestedCAST() {
        String query = "SELECT NEW com.example.DTO(CAST(SUM(amount) AS DECIMAL) AS total, CAST(COUNT(id) AS Long) AS count) FROM Order o";
        String result = adapter.removeASFromConstructorExpressions(query);

        String expected = "SELECT NEW com.example.DTO(CAST(SUM(amount) AS DECIMAL), CAST(COUNT(id) AS Long)) FROM Order o";
        assertEquals(expected, result);
    }

    // ========== Join Path Entity Resolution Tests ==========

    @Test
    void testConvertToNativeSQL_WithJoin() throws Exception {
        // Test basic join - User has @OneToMany to Vehicle
        String query = "SELECT u.username, v.manufacturer FROM User u JOIN u.vehicles v";
        ConversionResult result = adapter.convertToNativeSQL(query);

        assertNotNull(result);
        assertNotNull(result.getNativeSql());
        // The SQL should reference both tables
        assertTrue(result.getReferencedTables().contains("users"));
    }

    @Test
    void testConvertToNativeSQL_EntityResolutionThroughJoinPath() throws Exception {
        // This tests that Vehicle is resolved through the join path u.vehicles
        // even though Vehicle is not directly specified in the FROM clause
        String query = "SELECT v.manufacturer FROM User u JOIN u.vehicles v WHERE v.year > 2020";
        ConversionResult result = adapter.convertToNativeSQL(query);

        assertNotNull(result);
        assertNotNull(result.getNativeSql());
        // Should contain vehicle table reference
        String sql = result.getNativeSql().toLowerCase();
        assertTrue(sql.contains("vehicle") || sql.contains("vehicles"));
    }

    @Test
    void testConvertToNativeSQL_MultipleJoinLevels() throws Exception {
        // Test that entity resolution works for entities accessed through join chains
        // User -> Vehicle (through u.vehicles)
        String query = "SELECT u.username, v.color FROM User u " +
                "LEFT JOIN u.vehicles v " +
                "WHERE u.active = true AND v.year >= 2020";
        ConversionResult result = adapter.convertToNativeSQL(query);

        assertNotNull(result);
        assertNotNull(result.getNativeSql());
    }

    @Test
    void testConvertToNativeSQL_FieldsFromJoinedEntity() throws Exception {
        // Test that field references to joined entities work correctly
        String query = "SELECT u.firstName, u.lastName, v.manufacturer, v.color " +
                "FROM User u JOIN u.vehicles v";
        ConversionResult result = adapter.convertToNativeSQL(query);

        assertNotNull(result);
        String sql = result.getNativeSql();
        // Should have converted camelCase field names to snake_case columns
        assertTrue(sql.contains("first_name") || sql.contains("firstName"));
    }

    @Test
    void testConvertToNativeSQL_WithWhereOnJoinedEntity() throws Exception {
        String query = "SELECT u FROM User u JOIN u.vehicles v WHERE v.manufacturer = :make";
        ConversionResult result = adapter.convertToNativeSQL(query);

        assertNotNull(result);
        assertNotNull(result.getParameterMappings());
        // Should have the :make parameter
        assertTrue(result.getParameterMappings().stream()
                .anyMatch(p -> p.originalName().contains("make")));
    }

    @Test
    void testConvertToNativeSQL_CountWithJoin() throws Exception {
        String query = "SELECT COUNT(v) FROM User u JOIN u.vehicles v WHERE u.active = true";
        ConversionResult result = adapter.convertToNativeSQL(query);

        assertNotNull(result);
        assertNotNull(result.getNativeSql());
        assertTrue(result.getNativeSql().toUpperCase().contains("COUNT"));
    }
}
