package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStrings extends TestHelper{
    @BeforeEach
    public void each() throws Exception {
        compiler = new HelloEvaluator();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testUpperCase() throws AntikytheraException, ReflectiveOperationException {
        Variable u = new Variable("upper cased");
        AntikytheraRunTime.push(u);
        MethodDeclaration helloUpper = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloUpper")).orElseThrow();
        evaluator.executeMethod(helloUpper);

        assertTrue(outContent.toString().contains("Hello, UPPER CASED"));
    }

    @Test
    void testHello() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration helloWorld = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloWorld")).orElseThrow();
        evaluator.executeMethod(helloWorld);

        assertTrue(outContent.toString().contains("Hello, Antikythera"));
    }

    @Test
    void testLongChain() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration helloWorld = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("longChain")).orElseThrow();
        evaluator.executeMethod(helloWorld);

        assertTrue(outContent.toString().contains("his is a field"));
    }
    @Test
    void testHelloArgs() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration helloName = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloName")).orElseThrow();
        Variable v = new Variable("Cloud Solutions");
        AntikytheraRunTime.push(v);
        evaluator.executeMethod(helloName);
        assertTrue(outContent.toString().contains("Hello, Cloud Solutions"));
    }

    @Test
    void testChained() throws AntikytheraException, ReflectiveOperationException {
        Variable v = new Variable("World");
        AntikytheraRunTime.push(v);
        MethodDeclaration helloChained = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloChained")).orElseThrow();
        evaluator.executeMethod(helloChained);

        assertTrue(outContent.toString().contains("Hello, ORLD"));
    }

    class HelloEvaluator extends AbstractCompiler {

        protected HelloEvaluator() throws IOException, ReflectiveOperationException{
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Hello.java");
            cu = getJavaParser().parse(file).getResult().get();
            evaluator = new Evaluator("sa.com.cloudsolutions.antikythera.evaluator.Hello");
            evaluator.setupFields(cu);
        }
    }
}
