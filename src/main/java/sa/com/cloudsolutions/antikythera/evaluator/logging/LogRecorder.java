package sa.com.cloudsolutions.antikythera.evaluator.logging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures and tracks logging calls made through SLF4J loggers.
 */
public class LogRecorder {
    private static final Map<String, List<LogEntry>> logEntries = new HashMap<>();

    public static void clearLogs() {
        logEntries.clear();
    }

    public static void captureLog(String className, String level, String message, Object[] args) {
        logEntries.computeIfAbsent(className, k -> new ArrayList<>())
                .add(new LogEntry(level, message, args));
    }

    public static List<LogEntry> getLogEntries(String className) {
        return logEntries.getOrDefault(className, new ArrayList<>());
    }

    public record LogEntry(String level, String message, Object[] args) {
    }
}
