package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.body.VariableDeclarator;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.MockReturnValueHandler;
import sa.com.cloudsolutions.antikythera.evaluator.MockingEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.withSettings;

public class MockingRegistry {
    private MockingRegistry() {

    }

    private static final Map<String, Map<Callable, Object>> mockedFields = new HashMap<>();

    public static void markAsMocked(String className) {
        mockedFields.put(className, new HashMap<>());
    }

    public static boolean isMockTarget(String className) {
        return mockedFields.containsKey(className);
    }

    public static void reset() {
        mockedFields.clear();
    }


    public static Variable useMockito(String className) throws ClassNotFoundException {
        Variable v;
        Class<?> cls = AbstractCompiler.loadClass(className);
        v = new Variable(Mockito.mock(cls, withSettings().defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT)));
        v.setClazz(cls);
        return v;
    }


    public static Variable mockIt(VariableDeclarator variable) throws ClassNotFoundException {
        String fqn = AbstractCompiler.findFullyQualifiedTypeName(variable);
        Variable v;
        if (AntikytheraRunTime.getCompilationUnit(fqn) != null) {
            Evaluator eval = EvaluatorFactory.createLazily(fqn, MockingEvaluator.class);
            eval.setVariableName(variable.getNameAsString());
            v = new Variable(eval);
        }
        else {
            v = MockingRegistry.useMockito(fqn);
        }
        v.setType(variable.getType());
        return v;
    }

}
