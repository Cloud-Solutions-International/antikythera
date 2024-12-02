package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DepSolverIntegrationTest {

    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void testEmployee() throws IOException, AntikytheraException {
        DepSolver depSolver = DepSolver.createSolver();
        depSolver.processMethod("sa.com.cloudsolutions.antikythera.evaluator.Employee#simpleAccess");

        Map<String, CompilationUnit> dependencies = Graph.getDependencies();
        assertFalse(dependencies.isEmpty());
        CompilationUnit cu = dependencies.get("sa.com.cloudsolutions.antikythera.evaluator.Employee");
        assertNotNull(cu);
        assertEquals("java.io.Serializable", cu.getImports().get(0).getNameAsString());
    }

}
