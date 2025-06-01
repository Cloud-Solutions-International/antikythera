package sa.com.cloudsolutions.antikythera.evaluator;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures and tracks logging calls made through SLF4J loggers.
 */
public class LoggingEvaluator {
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

    public static class LogEntry {
        private final String level;
        private final String message;
        private final Object[] args;

        public LogEntry(String level, String message, Object[] args) {
            this.level = level;
            this.message = message;
            this.args = args;
        }

        public String getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }

        public Object[] getArgs() {
            return args;
        }
    }
}
