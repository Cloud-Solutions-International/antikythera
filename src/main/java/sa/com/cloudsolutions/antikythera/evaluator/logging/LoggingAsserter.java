package sa.com.cloudsolutions.antikythera.evaluator.logging;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

/**
 * Provides methods for asserting logging behavior in tests.
 */
public class LoggingAsserter {

    /**
     * Creates an assertion to verify that a log message was output at a specific level
     */
    public static Expression assertLoggedWithLevel(String className, String level, String expectedMessage) {
        MethodCallExpr assertion = new MethodCallExpr("assertTrue");
        MethodCallExpr condition = new MethodCallExpr("LogAppender.hasMesage")
                .addArgument(new FieldAccessExpr(new NameExpr("Level"), level))
                .addArgument(new StringLiteralExpr(className))
                .addArgument(new StringLiteralExpr(expectedMessage));

        assertion.addArgument(condition);
        return assertion;
    }
}
