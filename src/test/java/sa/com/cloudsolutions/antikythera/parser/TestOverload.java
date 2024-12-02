package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


class TestOverlord extends TestHelper {
    @BeforeEach
    public void each() throws AntikytheraException, IOException {
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
         * Should be able to  locate three method calls in the class, finding the correspondnig
         * methodDeclaration is another matter
         */
        assertEquals(3, methodCalls.size());
        Optional<MethodDeclaration> md = AbstractCompiler.findMethodDeclaration(methodCalls.getFirst(),
                AbstractCompiler.getPublicType(compiler.getCompilationUnit()));
        assertTrue(md.isEmpty());
        assertTrue(methodCalls.getFirst().getTypeArguments().isEmpty());

    }

    /**
     * Test finding the method declaration when type arguments are present.
     */
    @Test
    void testFindMethodDeclaration2() {
        List<MethodDeclaration> mds = compiler.getCompilationUnit().findAll(MethodDeclaration.class);
        List<MethodCallExpr> methodCalls = mds.get(3).findAll(MethodCallExpr.class);

        TypeDeclaration<?> decl = AbstractCompiler.getPublicType(compiler.getCompilationUnit());
        assertNotNull(decl);

        MethodCallExpr mce = methodCalls.getFirst();

        Optional<MethodDeclaration> md = AbstractCompiler.findMethodDeclaration(methodCalls.getFirst(), decl);
        assertTrue(md.isPresent());

        assertEquals("print", md.get().getNameAsString());
        assertEquals(1, md.get().getParameters().size());

        md = AbstractCompiler.findMethodDeclaration(methodCalls.get(1), decl);
        assertTrue(md.isEmpty());

        md = AbstractCompiler.findMethodDeclaration(methodCalls.get(2), decl);
        assertTrue(md.isPresent());
    }

    class FindMethodDeclarationCompiler extends AbstractCompiler {
        protected FindMethodDeclarationCompiler() throws IOException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Overlord.java");
            cu = getJavaParser().parse(file).getResult().get();
            evaluator = new Evaluator("");
            evaluator.setupFields(cu);
        }
    }
}
