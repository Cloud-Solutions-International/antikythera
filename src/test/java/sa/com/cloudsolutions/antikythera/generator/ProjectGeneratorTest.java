package sa.com.cloudsolutions.antikythera.generator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

class ProjectGeneratorTest {

    private Path tempDir;
    private Antikythera generator;

    @BeforeEach
    void setUp() throws IOException {
        generator = Antikythera.getInstance();
        tempDir = Files.createTempDirectory("testOutput");

    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    void getInstanceReturnsSameInstance() {
        Antikythera instance1 = Antikythera.getInstance();
        Antikythera instance2 = Antikythera.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void writeFileCreatesFileWithContent() throws IOException {
        String relativePath = "TestDummyFile.java";
        String content = "public class TestDummyFile {}";
        File file = new File(
                Settings.getProperty("output_path") + File.separator + "src" + File.separator + "main"
                        + File.separator + "java" + File.separator + relativePath);
        if (file.exists()) {
            Files.delete(file.toPath());
        }
        CopyUtils.writeFile(relativePath, content);
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
    }

    @Test
    void writeFilesToTestCreatesFileWithContent() throws IOException {
        String belongingPackage = Settings.getProperty(Settings.BASE_PACKAGE)  + ".controller";
        String filename = "TestDummyFile.java";
        String content = "public class TestDummyFile {}";
        generator.writeFilesToTest(belongingPackage, filename, content);
        File file = new File(Settings.getProperty("output_path") + File.separator + "src" + File.separator +
                "test" + File.separator + "java" + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename);
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
    }
}
