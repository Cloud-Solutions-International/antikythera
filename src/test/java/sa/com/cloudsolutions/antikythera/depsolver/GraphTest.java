package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class GraphTest {
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap();

    }

    @Test
    void testCreatGraphNode() throws AntikytheraException, IOException {
        ReturnValueCompiler comp = new ReturnValueCompiler();
        CompilationUnit cu = comp.getCompilationUnit();
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("returnConditionally")).orElseThrow();

        GraphNode gn = Graph.createGraphNode(md);
        assertEquals(md, gn.getNode());
        assertEquals("ReturnValue",gn.getEnclosingType().getNameAsString());
        assertNotNull(gn.getDestination());

        gn.buildNode();
        // todo need assert
    }

    @Test
    void testPersonInterface() throws AntikytheraException, IOException {
        PersonCompiler comp = new PersonCompiler();
        CompilationUnit cu = comp.getCompilationUnit();
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getName")).orElseThrow();

        GraphNode gn = Graph.createGraphNode(md);
        assertEquals(md, gn.getNode());
        assertEquals("Person",gn.getEnclosingType().getNameAsString());
        assertNotNull(gn.getDestination());

        gn.buildNode();
        // to need assert
    }

    class ReturnValueCompiler extends AbstractCompiler {
        protected ReturnValueCompiler() throws IOException, AntikytheraException {
            cu = getJavaParser().parse(new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/ReturnValue.java")).getResult().get();
        }
    }

    class PersonCompiler extends AbstractCompiler {
        protected PersonCompiler() throws IOException, AntikytheraException {
            cu = getJavaParser().parse(new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Person.java")).getResult().get();
            AntikytheraRunTime.addClass("sa.com.cloudsolutions.antikythera.evaluator.IPerson",
                getJavaParser().parse(new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/IPerson.java")).getResult().get());
        }
    }
}
