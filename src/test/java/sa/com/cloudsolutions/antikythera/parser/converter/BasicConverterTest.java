package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicConverterTest {
    @Test
    void testProcessJoinWithNoMatchingField() throws EvaluatorException {
        // Create a mock Join with a field that doesn't exist
        Join mockJoin = mock(Join.class);
        Table mockTable = mock(Table.class);
        when(mockTable.toString()).thenReturn("p.nonExistentField n");
        when(mockJoin.getRightItem()).thenReturn(mockTable);

        // Create a TypeWrapper for an entity without the referenced field
        CompilationUnit personCu = new CompilationUnit();
        personCu.setPackageDeclaration("sa.com.cloudsolutions.model");
        var personClass = personCu.addClass("Person");
        personClass.addField("String", "name"); // Different field

        TypeWrapper personWrapper = new TypeWrapper(personClass);

        List<TypeWrapper> units = new ArrayList<>();
        units.add(personWrapper);

        // Test the protected method with parts array
        String[] parts = {"p", "nonExistentField n"};

        // Call the method under test - should not throw exception and return null
        TypeWrapper result = assertDoesNotThrow(() -> BasicConverter.processJoin(mockJoin, units, parts));
        assertNull(result);
    }

    @Test
    void testProcessJoinWithEmptyUnits() throws EvaluatorException {
        // Create a mock Join
        Join mockJoin = mock(Join.class);

        // Empty units list - no entities to match against
        List<TypeWrapper> units = new ArrayList<>();

        // Test the protected method with valid parts but empty units
        String[] parts = {"p", "department d"};

        // Call the method under test - should return null since no matching entity
        TypeWrapper result = BasicConverter.processJoin(mockJoin, units, parts);

        // Should return null since no entities to match against
        assertNull(result);
    }

    @Test
    void testConvertFieldsToSnakeCaseWithSelectStar() throws JSQLParserException, EvaluatorException {
        // Create a simple SELECT statement with single character (representing SELECT *)
        String sql = "SELECT t FROM Person t";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");
        TypeWrapper entity = new TypeWrapper(personClass);

        // Call the method under test
        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Verify the statement was processed
        assertInstanceOf(Select.class, stmt);
        Select select = (Select) stmt;
        PlainSelect plainSelect = select.getPlainSelect();

        // Should have converted single character select to SELECT *
        assertEquals(1, plainSelect.getSelectItems().size());
        assertTrue(plainSelect.getSelectItems().get(0).toString().contains("*"));
    }

    @Test
    void testConvertFieldsToSnakeCaseWithSpecificFields() throws JSQLParserException, EvaluatorException {
        // Create a SELECT statement with specific fields in camelCase
        String sql = "SELECT firstName, lastName FROM Person";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");
        TypeWrapper entity = new TypeWrapper(personClass);

        // Call the method under test
        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Verify the statement was processed
        assertInstanceOf(Select.class, stmt);
        Select select = (Select) stmt;
        PlainSelect plainSelect = select.getPlainSelect();

        // Should have multiple select items
        assertTrue(plainSelect.getSelectItems().size() >= 1);
    }

    @Test
    void testConvertFieldsToSnakeCaseWithWhereClause() throws JSQLParserException, EvaluatorException {
        // Create a SELECT statement with WHERE clause containing camelCase fields
        String sql = "SELECT * FROM Person WHERE firstName = 'John'";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");
        TypeWrapper entity = new TypeWrapper(personClass);

        // Call the method under test
        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Verify the statement was processed
        assertInstanceOf(Select.class, stmt);
        Select select = (Select) stmt;
        PlainSelect plainSelect = select.getPlainSelect();

        // Should have a WHERE clause
        assertNotNull(plainSelect.getWhere());
    }

    @Test
    void testConvertFieldsToSnakeCaseWithGroupBy() throws JSQLParserException, EvaluatorException {
        // Create a SELECT statement with GROUP BY clause
        String sql = "SELECT COUNT(*) FROM Person GROUP BY departmentName";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");
        TypeWrapper entity = new TypeWrapper(personClass);

        // Call the method under test
        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Verify the statement was processed
        assertInstanceOf(Select.class, stmt);
        Select select = (Select) stmt;
        PlainSelect plainSelect = select.getPlainSelect();

        // Should have a GROUP BY clause
        assertNotNull(plainSelect.getGroupBy());
    }

    @Test
    void testConvertFieldsToSnakeCaseWithOrderBy() throws JSQLParserException, EvaluatorException {
        // Create a SELECT statement with ORDER BY clause
        String sql = "SELECT * FROM Person ORDER BY firstName, lastName";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");
        TypeWrapper entity = new TypeWrapper(personClass);

        // Call the method under test
        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Verify the statement was processed
        assertInstanceOf(Select.class, stmt);
        Select select = (Select) stmt;
        PlainSelect plainSelect = select.getPlainSelect();

        // Should have ORDER BY elements
        assertNotNull(plainSelect.getOrderByElements());
        assertFalse(plainSelect.getOrderByElements().isEmpty());
    }

    @Test
    void testConvertFieldsToSnakeCaseWithHaving() throws JSQLParserException, EvaluatorException {
        // Create a SELECT statement with HAVING clause
        String sql = "SELECT COUNT(*) FROM Person GROUP BY departmentName HAVING COUNT(*) > 1";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");
        TypeWrapper entity = new TypeWrapper(personClass);

        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Verify the statement was processed
        assertInstanceOf(Select.class, stmt);
        Select select = (Select) stmt;
        PlainSelect plainSelect = select.getPlainSelect();

        // Should have a HAVING clause
        assertNotNull(plainSelect.getHaving());
    }

    @Test
    void testConvertFieldsToSnakeCaseWithNonSelectStatement() throws JSQLParserException, EvaluatorException {
        // Create a non-SELECT statement (e.g., INSERT)
        String sql = "INSERT INTO Person (firstName) VALUES ('John')";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");
        TypeWrapper entity = new TypeWrapper(personClass);

        // Call the method under test - should handle non-SELECT statements gracefully
        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Should not be a Select statement
        assertFalse(stmt instanceof Select);
    }

    @Test
    void testConvertFieldsToSnakeCaseWithJoins() throws JSQLParserException, EvaluatorException {
        // Create a SELECT statement with JOINs
        String sql = "SELECT p.firstName, d.departmentName FROM Person p JOIN Department d ON p.departmentId = d.id";
        Statement stmt = CCJSqlParserUtil.parse(sql);

        // Create a TypeWrapper for entity
        CompilationUnit personCu = new CompilationUnit();
        var personClass = personCu.addClass("Person");

        // Add a department field to enable join processing
        FieldDeclaration departmentField = personClass.addField("Department", "department");
        NormalAnnotationExpr joinColumnAnnotation = new NormalAnnotationExpr();
        joinColumnAnnotation.setName("JoinColumn");
        joinColumnAnnotation.addPair("name", new StringLiteralExpr("department_id"));
        departmentField.addAnnotation(joinColumnAnnotation);

        TypeWrapper entity = new TypeWrapper(personClass);

        assertDoesNotThrow(() -> BasicConverter.convertFieldsToSnakeCase(stmt, entity));

        // Verify the statement was processed
        assertInstanceOf(Select.class, stmt);
        Select select = (Select) stmt;
        PlainSelect plainSelect = select.getPlainSelect();

        // Should have joins
        assertNotNull(plainSelect.getJoins());
    }
}
