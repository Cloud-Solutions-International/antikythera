package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GraphNodeTest  {
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap();
    }

    @Test
    void testGraphNode() throws AntikytheraException, IOException {
        ReturnValueCompiler comp = new ReturnValueCompiler();
        CompilationUnit cu = comp.getCompilationUnit();
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("returnConditionally")).orElseThrow();

        GraphNode gn = Graph.createGraphNode(md);
        assertEquals(md, gn.getNode());
        assertEquals("ReturnValue",gn.getEnclosingType().getNameAsString());
        assertNotNull(gn.getDestination());
    }

    class ReturnValueCompiler extends AbstractCompiler {
        protected ReturnValueCompiler() throws IOException, AntikytheraException {
            cu = getJavaParser().parse(new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/ReturnValue.java")).getResult().get();
        }
    }
}
