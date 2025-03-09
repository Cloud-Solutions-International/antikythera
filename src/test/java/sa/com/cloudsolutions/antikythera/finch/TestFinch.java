package sa.com.cloudsolutions.antikythera.finch;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFinch {
    @Test
    void testLoadClasses() throws Exception {
        // Create test source directory path
        File sourceDir = new File("src/test/resources/finches");

        // Load and verify classes
        Map<String, Object> loadedClasses = Finch.loadClasses(sourceDir);

        // Verify that classes were loaded
        assertNotNull(loadedClasses);
        assertFalse(loadedClasses.isEmpty());

        // Verify specific test classes were loaded
        assertTrue(loadedClasses.containsKey("sa.com.cloudsolutions.Hello"));

        // Verify instance creation
        Object instance = loadedClasses.get("sa.com.cloudsolutions.finch.TestFinch");
        assertNotNull(instance);
        assertEquals("sa.com.cloudsolutions.finch.TestFinch", instance.getClass().getName());
    }
}
