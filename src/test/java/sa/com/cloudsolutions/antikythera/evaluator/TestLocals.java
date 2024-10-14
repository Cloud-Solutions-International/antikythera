package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLocals extends TestHelper {
    @BeforeEach
    public void each() throws AntikytheraException, IOException {
        compiler = new TestLocalsCompiler();
        System.setOut(new PrintStream(outContent));
    }

    @Test
    void testScope() throws AntikytheraException, ReflectiveOperationException {
        MethodDeclaration doStuff = compiler.getCompilationUnit()
                .findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();
        evaluator.executeMethod(doStuff);
        assertEquals("10,20,100\n20,30,200\n", outContent.toString());
    }

    class TestLocalsCompiler extends AbstractCompiler {

        protected TestLocalsCompiler() throws IOException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Locals.java");
            cu = getJavaParser().parse(file).getResult().get();
            evaluator = new Evaluator("bada");
            evaluator.setupFields(cu);
        }
    }
}
