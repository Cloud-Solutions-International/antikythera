package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.List;

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

    /**
     * Verify that a message was logged at INFO level
     */
    public static Expression assertLoggedInfo(String className, String expectedMessage) {
        return assertLoggedWithLevel(className, "INFO", expectedMessage);
    }

    /**
     * Verify that a message was logged at ERROR level
     */
    public static Expression assertLoggedError(String className, String expectedMessage) {
        return assertLoggedWithLevel(className, "ERROR", expectedMessage);
    }

    /**
     * Verify that a message was logged at DEBUG level
     */
    public static Expression assertLoggedDebug(String className, String expectedMessage) {
        return assertLoggedWithLevel(className, "DEBUG", expectedMessage);
    }

    /**
     * Verify that a message was logged at WARN level
     */
    public static Expression assertLoggedWarn(String className, String expectedMessage) {
        return assertLoggedWithLevel(className, "WARN", expectedMessage);
    }

    /**
     * Verify that a message was logged at TRACE level
     */
    public static Expression assertLoggedTrace(String className, String expectedMessage) {
        return assertLoggedWithLevel(className, "TRACE", expectedMessage);
    }

    /**
     * Clear any captured log entries before running a new test
     */
    public static void clearLogs() {
        LoggingEvaluator.clearLogs();
    }
}
