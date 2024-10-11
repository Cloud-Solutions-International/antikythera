package sa.com.cloudsolutions.antikythera.parser;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestControllerParserTest {

    private RestControllerParser parser;
    private static String outputPath;

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap();
        outputPath = Settings.getProperty(Constants.OUTPUT_PATH).toString();

        String controllers = Settings.getProperty(Constants.CONTROLLERS).toString();
        parser = new RestControllerParser(Paths.get(Settings.getBasePath(),controllers.replaceAll("\\.","/")).toFile());
    }


    @Test
    void start_processesRestControllerSuccessfully() throws IOException, EvaluatorException {
        parser.start();

        File srcDirectory = new File(outputPath + "/src/main/java/");
        File testDirectory = new File(outputPath + "/src/test/java/");
        assertTrue(srcDirectory.exists() && srcDirectory.isDirectory());
        assertTrue(testDirectory.exists() && testDirectory.isDirectory());
    }

    @Test
    void getFields_returnsEmptyMapWhenNoFieldsPresent() {
        CompilationUnit cu = StaticJavaParser.parse("public class TempClass {}");
        Map<String, FieldDeclaration> fields = parser.getFields(cu, "TempClass");
        assertTrue(fields.isEmpty());
    }

    @Test
    void getFields_returnsMapWithFieldNamesAndTypes() throws IOException {
        Path testFilePath = Paths.get("src/test/resources/sources/src/main/java/sa/com/cloudsolutions/dto/SimpleDTO.java");
        CompilationUnit cu = StaticJavaParser.parse(testFilePath);

        Map<String, FieldDeclaration> fields = parser.getFields(cu, "SimpleDTO");
        assertFalse(fields.isEmpty());
        assertEquals(fields.size(), 6);
        assertTrue(fields.containsKey("id"));
        assertEquals(fields.get("id").toString(), "private Long id;");
        assertTrue(fields.containsKey("name"));
        assertEquals(fields.get("name").toString(), "private String name;");
        assertTrue(fields.containsKey("description"));
        assertEquals(fields.get("description").toString(), "private String description;");
    }


}
