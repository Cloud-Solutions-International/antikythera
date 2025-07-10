package sa.com.cloudsolutions.antikythera.evaluator.logging;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogRecorderTest {

    @Test
    void testLogEntryEqualityAndHashCode() {
        LogRecorder.LogEntry entry1 = new LogRecorder.LogEntry("INFO", "Test message", new Object[]{"arg1", 42});
        LogRecorder.LogEntry entry2 = new LogRecorder.LogEntry("INFO", "Test message", new Object[]{"arg1", 42});
        LogRecorder.LogEntry entry3 = new LogRecorder.LogEntry("ERROR", "Test message", new Object[]{"arg1", 42});
        LogRecorder.LogEntry entry4 = new LogRecorder.LogEntry("INFO", "Test message", new Object[]{"arg1", 43});

        assertEquals(entry1, entry2);
        assertEquals(entry1.hashCode(), entry2.hashCode());
        assertNotEquals(entry1, entry3);
        assertNotEquals(entry1, entry4);
    }

    @Test
    void testLogEntryToString() {
        LogRecorder.LogEntry entry = new LogRecorder.LogEntry("DEBUG", "Hello", new Object[]{"foo", 123});
        String str = entry.toString();
        assertTrue(str.contains("level='DEBUG'"));
        assertTrue(str.contains("message='Hello'"));
        assertTrue(str.contains("args=[foo, 123]"));
    }
}

