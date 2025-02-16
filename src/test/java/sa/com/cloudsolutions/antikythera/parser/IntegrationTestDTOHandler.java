package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;

import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.DTOHandler;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationTestDTOHandler {
    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap();
        AbstractCompiler.reset();
    }

    @Test
    void testSimpleDTO() throws IOException {
        DTOHandler handler = new DTOHandler();
        handler.copyDTO(AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.SimpleDTO"));

        File file = Paths.get( Settings.getProperty("output_path").toString() , "src/main/java/sa/com/cloudsolutions/dto/SimpleDTO.java").toFile();

        CompilationUnit cu = StaticJavaParser.parse(file);

        assertEquals(1, cu.getTypes().size());
        assertEquals(4, cu.getImports().size());
        assertEquals(0, cu.getType(0).getConstructors().size());
    }

    @Test
    void testMediumDTO() throws IOException {
        DTOHandler handler = new DTOHandler();
        handler.copyDTO(AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.MediumDTO"));

        File file = Paths.get( Settings.getProperty("output_path").toString() , "src/main/java/sa/com/cloudsolutions/dto/MediumDTO.java").toFile();

        CompilationUnit cu = StaticJavaParser.parse(file);

        assertEquals(1, cu.getTypes().size());
        assertEquals(7, cu.getImports().size());
        assertEquals(0, cu.getType(0).getConstructors().size());
    }

    @Test
    void testComplexDTO() throws IOException {
        DTOHandler handler = new DTOHandler();
        handler.copyDTO(AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.ComplexDTO"));

        File file = Paths.get( Settings.getProperty("output_path").toString() ,
                "src/main/java/sa/com/cloudsolutions/dto/ComplexDTO.java").toFile();

        CompilationUnit cu = StaticJavaParser.parse(file);

        assertEquals(1, cu.getTypes().size());
        assertEquals(8, cu.getImports().size());
        assertEquals(0, cu.getType(0).getConstructors().size());

        File constants = Paths.get( Settings.getProperty("output_path").toString() ,
                "src/main/java/sa/com/cloudsolutions/dto/Constants.java").toFile();
        assertTrue(constants.exists());
    }

    @Test
    void testGetters() throws IOException {
        DTOHandler handler = new DTOHandler();
        handler.parse(AbstractCompiler.classToPath("sa.com.cloudsolutions.dto.MediumDTO"));

        Evaluator eval = new Evaluator("sa.com.cloudsolutions.dto.MediumDTO");
        eval.setupFields(handler.getCompilationUnit());

        Variable v = eval.getFields().get("group");
        assertNotNull(v);
    }
}
