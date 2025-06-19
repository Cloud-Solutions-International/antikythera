package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class GraphTest {
    @BeforeAll
    static void setup() throws IOException {
        DepSolver.reset();
        DepSolver.reset();
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        AbstractCompiler.reset();
    }

    @Test
    void testCreatGraphNode() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.ReturnValue");
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("returnConditionally")).orElseThrow();

        GraphNode gn = Graph.createGraphNode(md);
        assertEquals(md, gn.getNode());
        assertEquals("ReturnValue", gn.getEnclosingType().getNameAsString());
        assertNotNull(gn.getDestination());

        assertEquals(1, Graph.getNodes().size());
    }

    @Test
    void testPersonInterface()  {
         CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.Person");
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getName")).orElseThrow();

        GraphNode gn = Graph.createGraphNode(md);
        assertEquals(md, gn.getNode());
        assertEquals("Person", gn.getEnclosingType().getNameAsString());
        assertNotNull(gn.getDestination());

        gn.buildNode();
        assertTrue(gn.preProcessed);
        assertTrue(gn.getDestination().toString().contains("public class Person implements IPerson"));
    }
}
