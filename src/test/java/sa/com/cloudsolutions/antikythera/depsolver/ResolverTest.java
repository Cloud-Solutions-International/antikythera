package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

public class ResolverTest extends TestHelper {
    private DepSolver depSolver;
    private GraphNode node;
    private CompilationUnit cu;
    private ClassOrInterfaceDeclaration sourceClass;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        DepSolver.reset();
    }

    @BeforeEach
    void each() {
        depSolver = DepSolver.createSolver();
        DepSolver.reset();

        cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Person");
        sourceClass = cu.getType(0).asClassOrInterfaceDeclaration();
        node = Graph.createGraphNode(sourceClass); // Use the Graph.createGraphNode method to create GraphNode

    }

}
