package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;

public class TestSuiteEvaluator extends Evaluator {
    public TestSuiteEvaluator(CompilationUnit cu, String className) {
        super();
        this.className = className;
        this.cu = cu;
    }
}
