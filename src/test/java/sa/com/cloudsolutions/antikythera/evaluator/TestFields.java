package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFields extends TestHelper {

    @BeforeEach
    public void each() throws Exception {
        compiler = new TestFieldsCompiler();
        System.setOut(new PrintStream(outContent));
    }


    @Test
    void testPrintNumberField() throws  AntikytheraException, ReflectiveOperationException {
        CompilationUnit cu = compiler.getCompilationUnit();
        MethodDeclaration ts = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("toString")).orElseThrow();
        evaluator.executeMethod(ts);
        assertTrue(outContent.toString().contains("10"));
    }


    class TestFieldsCompiler extends AbstractCompiler {
        protected TestFieldsCompiler() throws IOException, AntikytheraException {
            String path = Settings.getProperty("base_path", String.class).orElseGet(() -> "");
            JavaParserTypeSolver solver = new JavaParserTypeSolver(path.replace("/resources/sources/src/main",""));
            combinedTypeSolver.add(solver);

            cu = getJavaParser().parse(new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Employee.java")).getResult().get();
            evaluator = new Evaluator("");
            evaluator.setupFields(cu);
        }
    }
}
