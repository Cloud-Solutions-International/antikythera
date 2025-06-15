package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.MethodCallExpr;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import static org.mockito.Mockito.withSettings;


public class MockReturnValueHandler implements Answer<Object> {
    private static final Logger logger = LoggerFactory.getLogger(MockReturnValueHandler.class);

    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        Class<?> returnType = invocation.getMethod().getReturnType();
        String clsName = returnType.getName();

        Object result;
        if (AntikytheraRunTime.getCompilationUnit(clsName) != null) {
            result = EvaluatorFactory.create(clsName, Evaluator.class);
        } else {
            result = Reflect.getDefault(returnType);
            if (result == null) {
                Class<?> cls = AbstractCompiler.loadClass(clsName);
                result = Mockito.mock(cls, withSettings()
                    .defaultAnswer(new MockReturnValueHandler())
                    .strictness(Strictness.LENIENT));
            }
        }

        whenThen(invocation, result, clsName);

        return result;
    }

    void whenThen(InvocationOnMock invocation, Object result, String clsName) {
        if (result != null) {
            try {
                Class<?>[] interfaces = invocation.getMock().getClass().getInterfaces();
                if (interfaces.length > 0) {
                    String mockName = interfaces[0]
                            .getSimpleName();
                    mockName = Character.toLowerCase(mockName.charAt(0)) +
                            mockName.substring(1);

                    if (!mockName.equals("traceable")) {

                        MethodCallExpr methodCall = MockingRegistry.buildMockitoWhen(invocation.getMethod().getName(), clsName, mockName);
                        methodCall.setArguments(MockingRegistry.generateArgumentsForWhen(invocation.getMethod()));
                    }
                }
            } catch (Exception ex) {
                logger.warn(ex.getMessage());
            }
        }
    }
}

