package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.depsolver.Resolver;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


class TestOverlord extends TestHelper {
    private static final String CLASS_NAME = "sa.com.cloudsolutions.antikythera.evaluator.Overlord";
    private CompilationUnit compilationUnit;

    @BeforeEach
    void each() {
        compilationUnit = AntikytheraRunTime.getCompilationUnit(CLASS_NAME);
        System.setOut(new PrintStream(outContent));
    }

    /**
     * Test finding the method declaration when type arguments are not present.
     */
    @Test
    void testFindMethodDeclaration() {
        List<MethodDeclaration> mds = compilationUnit.findAll(MethodDeclaration.class);
        assertEquals(4, mds.size());

        List<MethodCallExpr> methodCalls = mds.get(3).findAll(MethodCallExpr.class);

        /*
         * Should be able to locate three method calls in the class, finding the corresponding
         * methodDeclaration is another matter
         */
        assertEquals(3, methodCalls.size());

        MethodCallExpr mce = methodCalls.getFirst();
        MCEWrapper wrapper = new MCEWrapper(mce);
        wrapper.setArgumentTypes(new NodeList<>());
        wrapper.getArgumentTypes().add(new ClassOrInterfaceType().setName("String"));

        Optional<Callable> cd = AbstractCompiler.findMethodDeclaration(wrapper,
                AbstractCompiler.getPublicType(compilationUnit));
        assertTrue(cd.isPresent());

    }

    /**
     * Test finding the method declaration when type arguments are present.
     */
    @Test
    void testFindMethodDeclaration2() throws AntikytheraException {
        DepSolver.getNames().put("args", StaticJavaParser.parseType("String[]"));


        List<MethodDeclaration> mds = compilationUnit.findAll(MethodDeclaration.class);
        List<MethodCallExpr> methodCalls = mds.get(3).findAll(MethodCallExpr.class);

        TypeDeclaration<?> decl = AbstractCompiler.getPublicType(compilationUnit);
        assertNotNull(decl);

        MethodCallExpr mce = methodCalls.getFirst();
        MCEWrapper wrapper = Resolver.resolveArgumentTypes(Graph.createGraphNode(mce), mce);

        Optional<Callable> md = AbstractCompiler.findMethodDeclaration(wrapper, decl);
        assertTrue(md.isPresent());
        assertTrue(md.get().isMethodDeclaration());

        assertEquals("print", md.get().asMethodDeclaration().getNameAsString());
        assertEquals(1, md.get().asMethodDeclaration().getParameters().size());

    }
}
