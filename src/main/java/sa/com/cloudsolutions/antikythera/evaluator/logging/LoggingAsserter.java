package sa.com.cloudsolutions.antikythera.evaluator.logging;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
        MethodCallExpr condition = new MethodCallExpr("LoggingEvaluator.getLogEntries")
                .addArgument(new StringLiteralExpr(className));

        MethodCallExpr streamFilter = new MethodCallExpr("stream")
                .setScope(condition);

        MethodCallExpr anyMatch = new MethodCallExpr("anyMatch")
                .setScope(streamFilter)
                .addArgument(String.format("entry -> entry.getLevel().equals(\"%s\") && entry.getMessage().equals(\"%s\")",
                        level, expectedMessage));

        assertion.addArgument(anyMatch);
        return assertion;
    }
}
