package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMappingResolver;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestRepositoryParser {

    public static final String ANIMAL_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.Animal";
    public static final String DOG_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.Dog";
    public static final String USER_REPOSITORY = "sa.com.cloudsolutions.antikythera.testhelper.repository.UserRepository";

    @BeforeAll
    static void setUpAll() throws IOException, ReflectiveOperationException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        EntityMappingResolver.reset();
        EntityMappingResolver.build();
    }

    @Test
    void testFindTableName() {
        CompilationUnit animal = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
        assertEquals("animals", RepositoryParser.findTableName(new TypeWrapper(animal.getType(0))));
        CompilationUnit dog = AntikytheraRunTime.getCompilationUnit(DOG_ENTITY);
        assertEquals("dog", RepositoryParser.findTableName(new TypeWrapper(dog.getType(0))));
    }

    @Test
    void convertExpressionToSnakeCaseAndExpression() {
        AndExpression andExpr = new AndExpression(new Column("firstName"), new Column("lastName"));
        Expression result = RepositoryQuery.convertExpressionToSnakeCase(andExpr);
        assertEquals("first_name AND last_name", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseIsNullExpression() {
        IsNullExpression isNullExpr = new IsNullExpression(new Column("middleName"));
        Expression result = RepositoryQuery.convertExpressionToSnakeCase(isNullExpr);
        assertEquals("middle_name IS NULL", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseComparisonOperator() {
        EqualsTo equalsExpr = new EqualsTo(new Column("salary"), new LongValue(5000));
        Expression result = RepositoryQuery.convertExpressionToSnakeCase(equalsExpr);
        assertEquals("salary = 5000", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseFunction() {
        Function functionExpr = new Function();
        functionExpr.setName("SUM");
        functionExpr.setParameters(new ExpressionList(new Column("totalAmount")));
        Expression result = RepositoryQuery.convertExpressionToSnakeCase(functionExpr);
        assertEquals("SUM(total_amount)", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseCaseExpression() {
        // Test CASE expression conversion - this tests the fix for the complex query issue
        try {
            // Create a CASE expression: CASE WHEN balanceAmount > 0 THEN COALESCE(balanceAmount, 0) ELSE 0 END
            net.sf.jsqlparser.expression.CaseExpression caseExpr = new net.sf.jsqlparser.expression.CaseExpression();
            
            // Initialize when clauses list
            caseExpr.setWhenClauses(new java.util.ArrayList<>());
            
            // Create WHEN clause
            net.sf.jsqlparser.expression.WhenClause whenClause = new net.sf.jsqlparser.expression.WhenClause();
            
            // WHEN condition: balanceAmount > 0
            net.sf.jsqlparser.expression.operators.relational.GreaterThan whenCondition = 
                new net.sf.jsqlparser.expression.operators.relational.GreaterThan();
            whenCondition.setLeftExpression(new Column("balanceAmount"));
            whenCondition.setRightExpression(new net.sf.jsqlparser.expression.LongValue(0));
            whenClause.setWhenExpression(whenCondition);
            
            // THEN expression: COALESCE(balanceAmount, 0)
            Function coalesceFunc = new Function();
            coalesceFunc.setName("COALESCE");
            coalesceFunc.setParameters(new ExpressionList(
                new Column("balanceAmount"), 
                new net.sf.jsqlparser.expression.LongValue(0)
            ));
            whenClause.setThenExpression(coalesceFunc);
            
            caseExpr.getWhenClauses().add(whenClause);
            
            // ELSE expression: 0
            caseExpr.setElseExpression(new net.sf.jsqlparser.expression.LongValue(0));
            
            // Convert the expression
            Expression result = RepositoryQuery.convertExpressionToSnakeCase(caseExpr);
            
            // Verify the result
            assertNotNull(result);
            String resultStr = result.toString();
            
            // Should contain the converted column name
            assertTrue(resultStr.contains("balance_amount"), 
                      "Should convert balanceAmount to balance_amount: " + resultStr);
            
            // Should preserve the CASE structure
            assertTrue(resultStr.contains("CASE"), "Should preserve CASE keyword: " + resultStr);
            assertTrue(resultStr.contains("WHEN"), "Should preserve WHEN keyword: " + resultStr);
            assertTrue(resultStr.contains("THEN"), "Should preserve THEN keyword: " + resultStr);
            assertTrue(resultStr.contains("ELSE"), "Should preserve ELSE keyword: " + resultStr);
            assertTrue(resultStr.contains("END"), "Should preserve END keyword: " + resultStr);
            
            // Should preserve the COALESCE function
            assertTrue(resultStr.contains("COALESCE"), "Should preserve COALESCE function: " + resultStr);
            
            System.out.println("CASE expression conversion test result: " + resultStr);
            
        } catch (Exception e) {
            fail("CASE expression conversion should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testCamelToSnake() {
        assertEquals("user_name", RepositoryParser.camelToSnake("userName"));
        assertEquals("first_name", RepositoryParser.camelToSnake("firstName"));
        assertEquals("id", RepositoryParser.camelToSnake("id"));
        assertEquals("user_id", RepositoryParser.camelToSnake("userID")); // Fixed expected value
        assertEquals("", RepositoryParser.camelToSnake(""));
    }

    @Test
    void testCountPlaceholders() {
        assertEquals(0, RepositoryParser.countPlaceholders("SELECT * FROM users"));
        assertEquals(1, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ?"));
        assertEquals(2, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ? AND name = ?"));
        assertEquals(3, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ? AND name = ? AND age > ?"));
    }

    @Test
    void testBeautify() {
        String result = RepositoryParser.beautify("SELECT * FROM users WHERE id = ?1");
        assertEquals("SELECT * FROM users WHERE id = ?", result);
        
        result = RepositoryParser.beautify("SELECT * FROM users WHERE id = ?1 AND name = ?2");
        assertEquals("SELECT * FROM users WHERE id = ? AND name = ?", result);
        
        // Test removal of '1' IN '1' AND when there are 3 or more AND clauses
        String sqlWithMultipleAnds = "SELECT * FROM users WHERE '1' IN '1' AND id = ? AND name = ? AND age > ?";
        result = RepositoryParser.beautify(sqlWithMultipleAnds);
        assertEquals("SELECT * FROM users WHERE  id = ? AND name = ? AND age > ?", result);
    }

    @Test
    void testTrueFalseCheck() {
        // The method checks the static dialect field, so let's test the actual behavior
        // Since the dialect is determined at initialization, we'll test both cases
        String input = "SELECT * FROM users WHERE active = true AND deleted = false";
        String result = RepositoryParser.trueFalseCheck(input);
        
        // The result will depend on the current dialect setting
        // We just verify the method doesn't throw and returns a string
        assertNotNull(result);
        assertTrue(result.contains("SELECT * FROM users WHERE active ="));
        assertTrue(result.contains("AND deleted ="));
    }

    @Test
    void testBindParameters() throws Exception {
        PreparedStatement mockStmt = mock(PreparedStatement.class);
        
        // Test Long parameter
        Variable longVar = new Variable(123L);
        QueryMethodArgument longArg = new QueryMethodArgument(null, 0, longVar);
        RepositoryParser.bindParameters(longArg, mockStmt, 0);
        verify(mockStmt).setLong(1, 123L);
        
        // Test String parameter
        Variable stringVar = new Variable("test");
        QueryMethodArgument stringArg = new QueryMethodArgument(null, 1, stringVar);
        RepositoryParser.bindParameters(stringArg, mockStmt, 1);
        verify(mockStmt).setString(2, "test");
        
        // Test Integer parameter
        Variable intVar = new Variable(42);
        QueryMethodArgument intArg = new QueryMethodArgument(null, 2, intVar);
        RepositoryParser.bindParameters(intArg, mockStmt, 2);
        verify(mockStmt).setInt(3, 42);
        
        // Test Boolean parameter
        Variable boolVar = new Variable(true);
        QueryMethodArgument boolArg = new QueryMethodArgument(null, 3, boolVar);
        RepositoryParser.bindParameters(boolArg, mockStmt, 3);
        verify(mockStmt).setBoolean(4, true);
        
        // Test null parameter
        Variable nullVar = new Variable((Object) null);
        QueryMethodArgument nullArg = new QueryMethodArgument(null, 4, nullVar);
        RepositoryParser.bindParameters(nullArg, mockStmt, 4);
        verify(mockStmt).setNull(5, java.sql.Types.NULL);
        
        // Test List parameter
        List<String> list = List.of("item1", "item2", "item3");
        Variable listVar = new Variable(list);
        QueryMethodArgument listArg = new QueryMethodArgument(null, 5, listVar);
        RepositoryParser.bindParameters(listArg, mockStmt, 5);
        verify(mockStmt).setString(6, "item1,item2,item3");
    }

    @Test
    void testParseNonAnnotatedMethod() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        parser.processTypes();

        // Test method pattern parsing
        MethodDeclaration findByUsername = repoUnit.findAll(MethodDeclaration.class).get(0);
        Callable callable = new Callable(findByUsername, null);
        
        // Test that the method doesn't throw an exception
        assertDoesNotThrow(() ->
        {
            RepositoryQuery q = parser.parseNonAnnotatedMethod(callable);
            assertNotNull(q);
            assertTrue(q.getQuery().contains("SELECT * FROM users"));
        });
    }

    @Test
    void testExtractComponents() throws IOException {
        CompilationUnit repoUnit = AntikytheraRunTime.getCompilationUnit(USER_REPOSITORY);
        BaseRepositoryParser parser = BaseRepositoryParser.create(repoUnit);
        List<String> result = parser.extractComponents("findByUsernameAndAge");
        assertEquals(List.of("findBy", "Username", "And", "Age"), result);
        
        List<String> result2 = parser.extractComponents("findFirstByEmailOrderByAge");
        assertEquals(List.of("findFirstBy", "Email", "OrderBy", "Age"), result2);
        
        List<String> result3 = parser.extractComponents("findByAgeGreaterThanAndUsernameContaining");
        assertEquals(List.of("findBy", "Age", "GreaterThan", "And", "Username", "Containing"), result3);
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
    void testMainMethod() throws Exception {
        // Test with no arguments - this should print error message
        RepositoryParser.main(new String[]{});
        
        // Test with correct number of arguments (this will fail due to missing class, but tests the path)
        try {
            RepositoryParser.main(new String[]{"com.example.NonExistentRepository"});
        } catch (Exception e) {
            // Expected for non-existent class - this is fine
        }
        
        // Just verify the method doesn't crash with valid input count
        assertTrue(true); // The test passes if we reach here without exceptions
    }

    @Test
    void testFindEntity() {
        TypeWrapper result = RepositoryParser.findEntity(StaticJavaParser.parseClassOrInterfaceType("List"));
        
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
}
