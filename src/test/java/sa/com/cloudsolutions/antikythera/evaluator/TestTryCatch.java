package sa.com.cloudsolutions.antikythera.evaluator;


import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTryCatch extends TestHelper {
    @BeforeEach
    public void each() throws AntikytheraException, IOException {
        compiler = new TryCatchCompiler();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testNPE() throws AntikytheraException, ReflectiveOperationException {

        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("tryNPE")).orElseThrow();
        evaluator.executeMethod(doStuff);

        assertTrue(outContent.toString().contains("Caught an exception\n"));
        assertFalse(outContent.toString().contains("This bit of code should not be executed\n"));
        assertTrue(outContent.toString().contains("Finally block\n"));
    }

    @Test
    void testNested() throws AntikytheraException, ReflectiveOperationException {

        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("nested")).orElseThrow();
        evaluator.executeMethod(doStuff);

        assertTrue(outContent.toString().contains("Caught an exception\n"));
        assertTrue(outContent.toString().contains("Caught another exception\n"));
        assertFalse(outContent.toString().contains("This bit of code should not be executed\n"));
        assertTrue(outContent.toString().contains("The first finally block\n"));
        assertTrue(outContent.toString().contains("The second finally block\n"));
    }


    @Test
    void testThrowing()  {

        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("throwTantrum")).orElseThrow();

        AntikytheraRunTime.push(new Variable(1));
        assertThrows(AUTException.class, () -> evaluator.executeMethod(doStuff));

        assertFalse(outContent.toString().contains("No tantrum thrown\n"));
    }

    @Test
    void testNotThrowing()  {

        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("throwTantrum")).orElseThrow();

        AntikytheraRunTime.push(new Variable(2));
        assertDoesNotThrow(() -> evaluator.executeMethod(doStuff));

        assertTrue(outContent.toString().contains("No tantrum thrown\n"));
    }

    class TryCatchCompiler extends AbstractCompiler {

        protected TryCatchCompiler() throws IOException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/TryCatch.java");
            cu = getJavaParser().parse(file).getResult().get();
            evaluator = new Evaluator(cu.getType(0).asClassOrInterfaceDeclaration().getFullyQualifiedName().get());
            evaluator.setupFields();
        }
    }
}
