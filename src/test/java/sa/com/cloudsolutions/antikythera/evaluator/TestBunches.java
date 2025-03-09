package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class TestBunches  {
    public static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Bunches";
    Evaluator evaluator;
    CompilationUnit cu;

    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() throws AntikytheraException {
        evaluator = new Evaluator(SAMPLE_CLASS);
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testList() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration printList = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printList")).orElseThrow();
        evaluator.executeMethod(printList);
        assertTrue(outContent.toString().contains("[one, two]"));
    }

    @Test
    void testMap() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration printMap = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printMap")).orElseThrow();
        evaluator.executeMethod(printMap);
        assertTrue(outContent.toString().contains("{one=1, two=2}"));
    }

    @Test
    void testWithDTO() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration withDTO = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("withDTO")).orElseThrow();
        evaluator.executeMethod(withDTO);
        assertTrue(outContent.toString().contains("Bunches.DTO]"));
    }


    @Test
    void testDTOConstructor() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration withDTO = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("withDTOConstructor")).orElseThrow();
        evaluator.executeMethod(withDTO);
        assertTrue(outContent.toString().contains("Person]"));
    }
}
