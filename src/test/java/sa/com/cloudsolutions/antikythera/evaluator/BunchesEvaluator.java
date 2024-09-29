package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.io.IOException;

public class BunchesEvaluator extends AbstractCompiler {
    protected BunchesEvaluator() throws IOException {
    }

    public static void main(String[] args) throws IOException, EvaluatorException {
        Settings.loadConfigMap();
        BunchesEvaluator b = new BunchesEvaluator();
        b.doStuff();
    }

    public void doStuff() throws IOException, EvaluatorException {
        Evaluator evaluator = new Evaluator();

        CompilationUnit cu = javaParser.parse(new File("src/test/java/com/cloud/api/evaluator/Bunches.java")).getResult().get();
        evaluator.setupFields(cu);

        MethodDeclaration printList = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printList")).orElseThrow();
        evaluator.executeMethod(printList);


        MethodDeclaration printMap = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("printMap")).orElseThrow();
        evaluator.executeMethod(printMap);

    }
}
