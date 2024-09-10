package com.cloud.api.generator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class ProjectGeneratorTest {

    private Path tempDir;
    private ProjectGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("testOutput");
        generator = ProjectGenerator.getInstance();
        generator.outputPath = tempDir.toString();
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
        File file = new File(generator.outputPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + relativePath);
        if (file.exists()) {
            Files.delete(file.toPath());
        }
        generator.writeFile(relativePath, content);
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
    }

    @Test
    void writeFilesToTestCreatesFileWithContent() throws IOException {
        String belongingPackage = generator.basePackage + ".controller";
        String filename = "TestDummyFile.java";
        String content = "public class TestDummyFile {}";
        generator.writeFilesToTest(belongingPackage, filename, content);
        File file = new File(generator.outputPath + File.separator + "src" + File.separator + "test" + File.separator + "java" + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename);
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
    }

    @Test
    void generateCreatesMavenProjectStructure() throws IOException {
        generator.generate();

        File mainJavaDir = new File(generator.outputPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + generator.basePackage.replace(".", File.separator));
        File mainResourcesDir = new File(generator.outputPath + File.separator + "src" + File.separator + "main" + File.separator + "resources");
        File testJavaDir = new File(generator.outputPath + File.separator + "src" + File.separator + "test" + File.separator + "java" + File.separator + generator.basePackage.replace(".", File.separator));
        File testResourcesDir = new File(generator.outputPath + File.separator + "src" + File.separator + "test" + File.separator + "resources");
        File pomFile = new File(generator.outputPath + File.separator + "pom.xml");
        File mainJavaConstantsDir = new File(generator.outputPath + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + "com" + File.separator + "cloud" + File.separator + "api" + File.separator + "constants");
        File testBaseDir = new File(generator.outputPath + File.separator + "src" + File.separator + "test" + File.separator + "java" + File.separator + "com" + File.separator + "cloud" + File.separator + "api");

        assertTrue(mainJavaDir.exists() && mainJavaDir.isDirectory());
        assertTrue(mainResourcesDir.exists() && mainResourcesDir.isDirectory());
        assertTrue(testJavaDir.exists() && testJavaDir.isDirectory());
        assertTrue(testResourcesDir.exists() && testResourcesDir.isDirectory());
        assertTrue(pomFile.exists());
        assertTrue(mainJavaConstantsDir.exists() && mainJavaConstantsDir.isDirectory());
        assertTrue(testBaseDir.exists() && testBaseDir.isDirectory());
    }
}