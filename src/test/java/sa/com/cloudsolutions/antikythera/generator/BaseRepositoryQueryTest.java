package sa.com.cloudsolutions.antikythera.generator;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseRepositoryQueryTest {

    @Test
    void convertExpressionToSnakeCaseAndExpression() {
        AndExpression andExpr = new AndExpression(new Column("firstName"), new Column("lastName"));
        Expression result = BaseRepositoryQuery.convertExpressionToSnakeCase(andExpr);
        assertEquals("first_name AND last_name", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseIsNullExpression() {
        IsNullExpression isNullExpr = new IsNullExpression(new Column("middleName"));
        Expression result = BaseRepositoryQuery.convertExpressionToSnakeCase(isNullExpr);
        assertEquals("middle_name IS NULL", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseComparisonOperator() {
        EqualsTo equalsExpr = new EqualsTo(new Column("salary"), new LongValue(5000));
        Expression result = BaseRepositoryQuery.convertExpressionToSnakeCase(equalsExpr);
        assertEquals("salary = 5000", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseFunction() {
        Function functionExpr = new Function();
        functionExpr.setName("SUM");
        functionExpr.setParameters(new ExpressionList(new Column("totalAmount")));
        Expression result = BaseRepositoryQuery.convertExpressionToSnakeCase(functionExpr);
        assertEquals("SUM(total_amount)", result.toString());
    }

    @Test
    void convertExpressionToSnakeCaseCaseExpression() {
        // Test CASE expression conversion - this tests the fix for the complex query
        // issue
        try {
            // Create a CASE expression: CASE WHEN balanceAmount > 0 THEN
            // COALESCE(balanceAmount, 0) ELSE 0 END
            net.sf.jsqlparser.expression.CaseExpression caseExpr = new net.sf.jsqlparser.expression.CaseExpression();

            // Initialize when clauses list
            caseExpr.setWhenClauses(new java.util.ArrayList<>());

            // Create WHEN clause
            net.sf.jsqlparser.expression.WhenClause whenClause = new net.sf.jsqlparser.expression.WhenClause();

            // WHEN condition: balanceAmount > 0
            net.sf.jsqlparser.expression.operators.relational.GreaterThan whenCondition = new net.sf.jsqlparser.expression.operators.relational.GreaterThan();
            whenCondition.setLeftExpression(new Column("balanceAmount"));
            whenCondition.setRightExpression(new net.sf.jsqlparser.expression.LongValue(0));
            whenClause.setWhenExpression(whenCondition);

            // THEN expression: COALESCE(balanceAmount, 0)
            Function coalesceFunc = new Function();
            coalesceFunc.setName("COALESCE");
            coalesceFunc.setParameters(new ExpressionList(
                    new Column("balanceAmount"),
                    new net.sf.jsqlparser.expression.LongValue(0)));
            whenClause.setThenExpression(coalesceFunc);

            caseExpr.getWhenClauses().add(whenClause);

            // ELSE expression: 0
            caseExpr.setElseExpression(new net.sf.jsqlparser.expression.LongValue(0));

            // Convert the expression
            Expression result = BaseRepositoryQuery.convertExpressionToSnakeCase(caseExpr);

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
    void testSetQueryWithBackslashes() {
        BaseRepositoryQuery query = new BaseRepositoryQuery();
        String sql = "SELECT count(*) \\\n" +
                "FROM table_name";
        query.setQuery(sql);
        assertNotNull(query.getStatement());
    }
}
