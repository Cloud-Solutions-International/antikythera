package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

public class ReturnValueEvaluator  extends AbstractCompiler  {
    Logger logger = org.slf4j.LoggerFactory.getLogger(ReturnValueEvaluator.class);

    Evaluator evaluator = new Evaluator();

    protected ReturnValueEvaluator() throws IOException {
    }

    public static void main(String[] args) throws IOException, EvaluatorException {
        Settings.loadConfigMap();

        ReturnValueEvaluator returnValueEvaluator = new ReturnValueEvaluator();
        returnValueEvaluator.doStuff();
    }

    private void doStuff() throws IOException, EvaluatorException {
        CompilationUnit cu = javaParser.parse(new File("src/test/java/com/cloud/api/evaluator/ReturnValue.java")).getResult().get();
        evaluator.setupFields(cu);
        evaluator.setScope("returnValue");

        MethodDeclaration printName = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printName")).orElseThrow();
        evaluator.executeMethod(printName);

        MethodDeclaration printNumber = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printNumberField")).orElseThrow();
        evaluator.executeMethod(printNumber);
    }

}
