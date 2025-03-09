package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
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
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


class TestOverlord extends TestHelper {
    @BeforeEach
    public void each() throws IOException {
        compiler = new FindMethodDeclarationCompiler();
        System.setOut(new PrintStream(outContent));
    }

    /**
     * Test finding the method declaration when type arguments are not present.
     */
    @Test
    void testFindMethodDeclaration() {
        List<MethodDeclaration> mds = compiler.getCompilationUnit().findAll(MethodDeclaration.class);
        assertEquals(4, mds.size());

        List<MethodCallExpr> methodCalls = mds.get(3).findAll(MethodCallExpr.class);

        /*
         * Should be able to  locate three method calls in the class, finding the corresponding
         * methodDeclaration is another matter
         */
        assertEquals(3, methodCalls.size());

        List<MethodDeclaration> decs = compiler.getCompilationUnit().findAll(MethodDeclaration.class);

        MethodCallExpr mce = methodCalls.getFirst();
        MCEWrapper wrapper = new MCEWrapper(mce);
        wrapper.setArgumentTypes(new NodeList<>());
        wrapper.getArgumentTypes().add(new ClassOrInterfaceType("String"));

        Optional<Callable> cd = AbstractCompiler.findMethodDeclaration(wrapper,
                AbstractCompiler.getPublicType(compiler.getCompilationUnit()));
        assertTrue(cd.isPresent());

    }

    /**
     * Test finding the method declaration when type arguments are present.
     */
    @Test
    void testFindMethodDeclaration2() throws AntikytheraException {
        DepSolver.getNames().put("args", StaticJavaParser.parseType("String[]"));

        List<MethodDeclaration> mds = compiler.getCompilationUnit().findAll(MethodDeclaration.class);
        List<MethodCallExpr> methodCalls = mds.get(3).findAll(MethodCallExpr.class);

        TypeDeclaration<?> decl = AbstractCompiler.getPublicType(compiler.getCompilationUnit());
        assertNotNull(decl);

        MethodCallExpr mce = methodCalls.getFirst();
        MCEWrapper wrapper = Resolver.resolveArgumentTypes(Graph.createGraphNode(mce), mce);

        Optional<Callable> md = AbstractCompiler.findMethodDeclaration(wrapper, decl);
        assertTrue(md.isPresent());
        assertTrue(md.get().isMethodDeclaration());

        assertEquals("print", md.get().asMethodDeclaration().getNameAsString());
        assertEquals(1, md.get().asMethodDeclaration().getParameters().size());

    }

    class FindMethodDeclarationCompiler extends AbstractCompiler {
        protected FindMethodDeclarationCompiler() throws IOException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Overlord.java");
            cu = getJavaParser().parse(file).getResult().get();
            evaluator = new Evaluator(cu.getType(0).asClassOrInterfaceDeclaration().getFullyQualifiedName().get());
        }
    }
}
