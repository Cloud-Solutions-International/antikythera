package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEntities extends TestHelper {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }
    @BeforeEach
    void before() {
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testJson() throws ReflectiveOperationException {
        Evaluator evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.util.Util", Evaluator.class);
        MethodDeclaration md = evaluator.getCompilationUnit().findFirst(
                MethodDeclaration.class, m -> m.getNameAsString().equals("toJson")).orElseThrow();
        evaluator.executeMethod(md);
        String json = outContent.toString().trim();
        assertEquals("""
                {"id":null,"name":"Hornblower","address":"Admiralty House","phone":"Le Harve","email":"governor@leharve.fr","age":0}
                """, json);
    }
}
