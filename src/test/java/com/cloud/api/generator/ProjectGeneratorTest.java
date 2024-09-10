package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
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
    private ProjectGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        generator = ProjectGenerator.getInstance();
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
    void getInstanceReturnsSameInstance() throws IOException {
        ProjectGenerator instance1 = ProjectGenerator.getInstance();
        ProjectGenerator instance2 = ProjectGenerator.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void writeFileCreatesFileWithContent() throws IOException {
        String relativePath = "TestDummyFile.java";
        String content = "public class TestDummyFile {}";
        File file = new File(
                Settings.getProperty("OUTPUT_PATH") + File.separator + "src" + File.separator + "main"
                        + File.separator + "java" + File.separator + relativePath);
        if (file.exists()) {
            Files.delete(file.toPath());
        }
        generator.writeFile(relativePath, content);
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
    }

    @Test
    void writeFilesToTestCreatesFileWithContent() throws IOException {
        String belongingPackage = Settings.getProperty("BASE_PACKAGE")  + ".controller";
        String filename = "TestDummyFile.java";
        String content = "public class TestDummyFile {}";
        generator.writeFilesToTest(belongingPackage, filename, content);
        File file = new File(Settings.getProperty("OUTPUT_PATH") + File.separator + "src" + File.separator +
                "test" + File.separator + "java" + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename);
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
    }

    @Test
    void generateCreatesMavenProjectStructure() throws IOException {
        generator.generate();

        String outputPath = Settings.getProperty("OUTPUT_PATH");

        File mainJavaDir = new File(outputPath + File.separator + "src" + File.separator
                + "main" + File.separator + "java" + File.separator + Settings.getProperty("BASE_PACKAGE").replace(".", File.separator));
        File mainResourcesDir = new File(outputPath + File.separator + "src" + File.separator + "main" + File.separator + "resources");
        File testJavaDir = new File(outputPath + File.separator + "src" + File.separator +
                "test" + File.separator + "java" + File.separator + Settings.getProperty("BASE_PACKAGE").replace(".", File.separator));
        File testResourcesDir = new File(outputPath + File.separator + "src" + File.separator + "test" + File.separator + "resources");
        File pomFile = new File(outputPath + File.separator + "pom.xml");
        File mainJavaConstantsDir = new File(outputPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + "com" + File.separator + "cloud" + File.separator + "api" + File.separator + "constants");
        File testBaseDir = new File(outputPath + File.separator + "src" + File.separator + "test" + File.separator + "java" + File.separator + "com" + File.separator + "cloud" + File.separator + "api");

        assertTrue(mainJavaDir.exists() && mainJavaDir.isDirectory());
        assertTrue(mainResourcesDir.exists() && mainResourcesDir.isDirectory());
        assertTrue(testJavaDir.exists() && testJavaDir.isDirectory());
        assertTrue(testResourcesDir.exists() && testResourcesDir.isDirectory());
        assertTrue(pomFile.exists());
        assertTrue(mainJavaConstantsDir.exists() && mainJavaConstantsDir.isDirectory());
        assertTrue(testBaseDir.exists() && testBaseDir.isDirectory());
    }
}
