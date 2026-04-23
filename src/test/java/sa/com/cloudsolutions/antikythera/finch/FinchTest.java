package sa.com.cloudsolutions.antikythera.finch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FinchTest {

    @AfterEach
    void tearDown() {
        Finch.clear();
    }

    @Test
    void getFinchReturnsNullWhenNotLoaded() {
        assertNull(Finch.getFinch("com.example.Foo"));
    }

    @Test
    void clearResetsFinchesMap() {
        Finch.finches = new java.util.HashMap<>();
        Finch.finches.put("com.example.Foo", "bar");
        assertNotNull(Finch.getFinch("com.example.Foo"));

        Finch.clear();
        assertNull(Finch.getFinch("com.example.Foo"));
    }

    @Test
    void getFinchReturnsNullForUnknownClass() {
        Finch.finches = new java.util.HashMap<>();
        assertNull(Finch.getFinch("com.example.DoesNotExist"));
    }

    @Test
    void getFinchReturnsStoredInstance() {
        Finch.finches = new java.util.HashMap<>();
        Object instance = new Object();
        Finch.finches.put("com.example.MyFinch", instance);
        assertSame(instance, Finch.getFinch("com.example.MyFinch"));
    }

    @Test
    void loadClassesThrowsForInvalidDirectory() {
        File notADir = new File("/nonexistent/path/that/does/not/exist");
        assertThrows(IllegalArgumentException.class, () -> Finch.loadClasses(notADir));
    }

    @Test
    void loadClassesThrowsForFileNotDirectory() throws Exception {
        Path tempFile = Files.createTempFile("finch-test", ".txt");
        try {
            assertThrows(IllegalArgumentException.class, () -> Finch.loadClasses(tempFile.toFile()));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void loadFinchesHandlesMissingConfig() {
        // loadFinches should not throw even when finch config is absent
        Finch.clear();
        assertDoesNotThrow(Finch::loadFinches);
    }

    @Test
    void loadFinchesIsIdempotent() {
        Finch.loadFinches();
        // Second call should not re-initialize
        Finch.loadFinches();
        // finches should be non-null after loading (even if empty)
        assertNotNull(Finch.finches);
    }
}
