package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GraphNodeTest  {
    @BeforeAll
    static void setup() throws IOException {
        AbstractCompiler.preProcess();
        Settings.loadConfigMap();
        DepSolver.reset();
    }

    @Test
    void testGraphNode() throws AntikytheraException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.ReturnValue");
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("returnConditionally")).orElseThrow();

        GraphNode gn = Graph.createGraphNode(md);
        assertEquals(md, gn.getNode());
        assertEquals("ReturnValue",gn.getEnclosingType().getNameAsString());
        assertNotNull(gn.getDestination());
    }

    @Test
    void testKitchenSink() throws AntikytheraException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.KitchenSink");
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class,
                m -> m.getNameAsString().equals("getSomething")).orElseThrow();

        GraphNode gn = Graph.createGraphNode(md);
        assertEquals(md, gn.getNode());
        assertEquals("KitchenSink",gn.getEnclosingType().getNameAsString());
        assertNotNull(gn.getDestination());
        assertEquals(0, gn.getDestination().getImports().size());

        FieldDeclaration vdecl = gn.getEnclosingType().findFirst(FieldDeclaration.class,
            fd -> fd.toString().contains("itsComplicated")).orElseThrow();

        gn.addTypeArguments(vdecl.getElementType().asClassOrInterfaceType());
        assertEquals(2, gn.getDestination().getImports().size());
    }
}
