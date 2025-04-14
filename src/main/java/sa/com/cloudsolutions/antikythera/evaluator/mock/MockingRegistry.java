package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.body.VariableDeclarator;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AKBuddy;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.MethodInterceptor;
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
        Class<?> cls = AbstractCompiler.loadClass(className);
        Variable v = new Variable(Mockito.mock(cls, withSettings().defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT)));
        v.setClazz(cls);
        return v;
    }

    public static Variable useByteBuddy(String className) throws ClassNotFoundException {
        Class<?> cls = AbstractCompiler.loadClass(className);
        MethodInterceptor interceptor = new MethodInterceptor(cls);
        Variable v = new Variable(AKBuddy.createDynamicClass(interceptor));
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
            String mocker = Settings.getProperty(Settings.MOCK_WITH_INTERNAL, String.class).orElse("ByteBuddy");
            if (mocker.equals("Mockito")) {
                v = MockingRegistry.useMockito(fqn);
            }
            else {
                v = MockingRegistry.useByteBuddy(fqn);
            }
        }
        v.setType(variable.getType());
        return v;
    }

}
