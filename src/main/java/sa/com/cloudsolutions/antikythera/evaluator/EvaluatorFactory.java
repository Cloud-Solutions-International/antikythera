package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;

public class EvaluatorFactory {
    private EvaluatorFactory() {}

    public static Evaluator create(String className, Evaluator enclosure) {
        CompilationUnit primary = AntikytheraRunTime.getCompilationUnit(className);
        String parent = className.substring(0, className.lastIndexOf('.'));
        CompilationUnit secondary = AntikytheraRunTime.getCompilationUnit(parent);

        if (primary.equals(secondary)) {
            InnerClassEvaluator eval = new InnerClassEvaluator(className);
            eval.setEnclosure(enclosure);
            return eval;
        }

        if (enclosure instanceof SpringEvaluator) {
            return new SpringEvaluator(className);
        }
        return new Evaluator(className);
    }
}
