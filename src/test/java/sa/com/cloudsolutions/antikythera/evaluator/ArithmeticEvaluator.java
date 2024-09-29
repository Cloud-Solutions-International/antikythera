package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

public class ArithmeticEvaluator extends AbstractCompiler {
    Logger logger = org.slf4j.LoggerFactory.getLogger(ArithmeticEvaluator.class);

    Evaluator evaluator = new Evaluator();

    protected ArithmeticEvaluator() throws IOException {
    }


    public static void main(String[] args) throws Exception {
        Settings.loadConfigMap();
        ArithmeticEvaluator arithmaticEvaluator = new ArithmeticEvaluator();
        arithmaticEvaluator.doStuff();
    }

    private void doStuff() throws IOException, EvaluatorException {
        CompilationUnit cu = javaParser.parse(new File("src/test/java/com/cloud/api/evaluator/Arithmetic.java")).getResult().get();
        evaluator.setupFields(cu);

        MethodDeclaration doStuffMethod = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();
        evaluator.setScope("arithmetic");

        evaluator.executeMethod(doStuffMethod);
    }
}
