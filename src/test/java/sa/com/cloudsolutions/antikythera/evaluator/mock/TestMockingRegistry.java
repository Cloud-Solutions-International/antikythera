package sa.com.cloudsolutions.antikythera.evaluator.mock;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.body.VariableDeclarator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestMockingRegistry extends TestHelper {

    public static final String CLASS_UNDER_TEST = "sa.com.cloudsolutions.antikythera.evaluator.Employee";

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        evaluator = EvaluatorFactory.create(CLASS_UNDER_TEST, Evaluator.class);
    }

    @Test
    void testUseMockito() throws ClassNotFoundException {
        VariableDeclarator variableDeclarator = evaluator.getCompilationUnit()
                .findFirst(VariableDeclarator.class, vd -> vd.getNameAsString().equals("objectMapper")).orElseThrow();

        Variable result = MockingRegistry.mockIt(variableDeclarator);

        assertNotNull(result);
        assertInstanceOf(ObjectMapper.class, result.getValue());
        assertEquals(ObjectMapper.class, result.getClazz());
    }
}
