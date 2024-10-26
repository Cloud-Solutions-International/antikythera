package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;


class TestOverlord extends TestHelper {
    @BeforeEach
    public void each() throws AntikytheraException, IOException {
        compiler = new FindMethodDeclarationCompiler();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testFindMethodDeclaration() throws AntikytheraException, ReflectiveOperationException {
        List<MethodDeclaration> mds = compiler.getCompilationUnit().findAll(MethodDeclaration.class);
        assertEquals(4, mds.size());

        List<MethodCallExpr> expressions = mds.get(3).findAll(MethodCallExpr.class);

        assertEquals(3, expressions.size());
        Optional<MethodDeclaration> md = AbstractCompiler.findMethodDeclaration(expressions.get(0), mds);
        assertTrue(md.isPresent());
        assertEquals("print", md.get().getNameAsString());
        assertEquals(1, md.get().getParameters().size());

        md = AbstractCompiler.findMethodDeclaration(expressions.get(1), mds);
        assertTrue(md.isPresent());

        md = AbstractCompiler.findMethodDeclaration(expressions.get(2), mds);
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
