package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRepositoryParser {
    private RepositoryParser parser;
    private CompilationUnit cu;

    @BeforeAll
    static void setUpAll() throws IOException {
        Settings.loadConfigMap();
    }

    @BeforeEach
    void setUp() throws IOException {
        parser = new RepositoryParser();
        cu = StaticJavaParser.parse("""
                @Table(name = "table_name")
                public class AdmissionClearance implements Serializable {}
                """);
    }

    @Test
    void testFindTableName() {

        assertEquals("table_name", RepositoryParser.findTableName(cu));

        cu = StaticJavaParser.parse("""
                public class AdmissionClearanceTable implements Serializable {}
                """);
        assertEquals("admission_clearance_table", RepositoryParser.findTableName(cu));
    }

    @Test
    void testParseNonAnnotatedMethod() throws ReflectiveOperationException {
        Field entityCuField = RepositoryParser.class.getDeclaredField("entityCu");
        entityCuField.setAccessible(true);
        entityCuField.set(parser, cu);

        MethodDeclaration md1 = new MethodDeclaration().setName("findAll");
        parser.parseNonAnnotatedMethod(md1);
        Map<MethodDeclaration, RepositoryQuery> queries = parser.getQueries();
        assertTrue(queries.containsKey(md1));
        assertEquals("SELECT * FROM table_name", queries.get(md1).getQuery());

        // Test findById method
        MethodDeclaration md2 = new MethodDeclaration().setName("findById");
        parser.parseNonAnnotatedMethod(md2);
        queries = parser.getQueries();
        assertTrue(queries.containsKey(md2));
        assertEquals("SELECT * FROM table_name WHERE id = ?", queries.get(md2).getQuery().strip());

        // Test findAllById method
        MethodDeclaration md3 = new MethodDeclaration().setName("findAllById");
        parser.parseNonAnnotatedMethod(md3);
        queries = parser.getQueries();
        assertTrue(queries.containsKey(md3));
        assertEquals("SELECT * FROM table_name WHERE id = ?", queries.get(md3).getQuery());
    }

    @Test
    void convertExpressionToSnakeCaseAndExpression() {
        AndExpression andExpr = new AndExpression(new Column("firstName"), new Column("lastName"));
        Expression result = parser.convertExpressionToSnakeCase(andExpr, true);
        assertEquals("first_name AND last_name", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseIsNullExpression() {
        IsNullExpression isNullExpr = new IsNullExpression(new Column("middleName"));
        Expression result = parser.convertExpressionToSnakeCase(isNullExpr, true);
        assertEquals("middle_name IS NULL", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseComparisonOperator() {
        EqualsTo equalsExpr = new EqualsTo(new Column("salary"), new LongValue(5000));
        Expression result = parser.convertExpressionToSnakeCase(equalsExpr, true);
        assertEquals("salary = 5000", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseFunction() {
        Function functionExpr = new Function();
        functionExpr.setName("SUM");
        functionExpr.setParameters(new ExpressionList(new Column("totalAmount")));
        Expression result = parser.convertExpressionToSnakeCase(functionExpr, true);
        assertEquals("SUM(total_amount)", result.toString());
    }
}
