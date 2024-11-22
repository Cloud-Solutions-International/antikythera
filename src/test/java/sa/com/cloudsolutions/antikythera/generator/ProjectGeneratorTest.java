package sa.com.cloudsolutions.antikythera.generator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

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
    void getInstanceReturnsSameInstance() throws IOException {
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
        String belongingPackage = Settings.getProperty("base_package")  + ".controller";
        String filename = "TestDummyFile.java";
        String content = "public class TestDummyFile {}";
        generator.writeFilesToTest(belongingPackage, filename, content);
        File file = new File(Settings.getProperty("output_path") + File.separator + "src" + File.separator +
                "test" + File.separator + "java" + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename);
        assertTrue(file.exists());
        assertEquals(content, Files.readString(file.toPath()));
    }

    @Test
    void generateCreatesMavenProjectStructure() throws IOException, XmlPullParserException, EvaluatorException {
        generator.generate();

        String outputPath = Settings.getProperty(Constants.OUTPUT_PATH).toString();

        String basePackage = Settings.getProperty(Constants.BASE_PACKAGE).toString().replace(".", File.separator);
        File mainJavaDir = new File(outputPath + File.separator + "src" + File.separator
                + "main" + File.separator + "java" + File.separator + basePackage);
        File mainResourcesDir = new File(outputPath + File.separator + "src" + File.separator + "main" + File.separator + "resources");
        File testJavaDir = new File(outputPath + File.separator + "src" + File.separator +
                "test" + File.separator + "java" + File.separator + basePackage);
        File testResourcesDir = new File(outputPath + File.separator + "src" + File.separator + "test" + File.separator + "resources");
        File pomFile = new File(outputPath + File.separator + "pom.xml");
        File mainJavaConstantsDir = new File(outputPath + "/src/main/java/sa/com/cloudsolutions/antikythera/constants");
        File testBaseDir = new File(outputPath + "/src/test/java/sa/com/cloudsolutions/antikythera/base");

        assertTrue(mainJavaDir.exists() && mainJavaDir.isDirectory());
        assertTrue(mainResourcesDir.exists() && mainResourcesDir.isDirectory());
        assertTrue(testJavaDir.exists() && testJavaDir.isDirectory());
        assertTrue(testResourcesDir.exists() && testResourcesDir.isDirectory());
        assertTrue(pomFile.exists());
        assertTrue(mainJavaConstantsDir.exists() && mainJavaConstantsDir.isDirectory());
        assertTrue(testBaseDir.exists() && testBaseDir.isDirectory());
    }
}
