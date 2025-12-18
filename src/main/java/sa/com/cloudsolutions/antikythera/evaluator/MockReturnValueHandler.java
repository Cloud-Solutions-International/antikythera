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
        if (clsName.equals("void")) {
            return null;
        }

        Object result = null;

        // Check if this is a generic method with a Class parameter that specifies the return type
        Class<?> better = findBetterReturnType(invocation);
        if (better != null) {
            returnType = better;
            clsName = returnType.getName();
        }

        if (AntikytheraRunTime.getCompilationUnit(clsName) != null) {
            result = EvaluatorFactory.create(clsName, Evaluator.class);
        } else {
            result = Reflect.getDefault(returnType);
            if (result == null) {
                Class<?> cls = AbstractCompiler.loadClass(clsName);
                // Create mock with a proper name based on the class simple name
                String mockName = cls.getSimpleName();
                mockName = Character.toLowerCase(mockName.charAt(0)) + mockName.substring(1);
                result = Mockito.mock(cls, withSettings()
                    .name(mockName)
                    .defaultAnswer(new MockReturnValueHandler())
                    .strictness(Strictness.LENIENT));
            }
        }

        whenThen(invocation, result, clsName);

        return result;
    }

    private static Class<?> findBetterReturnType(InvocationOnMock invocation) {
        Object[] arguments = invocation.getArguments();
        for (Object arg : arguments) {
            if (arg instanceof Class<?> clazz) {
                return clazz;
            } else if (arg instanceof java.lang.reflect.Type typeArg) {
                // Handle TypeReference or other Type instances
                try {
                    if (typeArg instanceof java.lang.reflect.ParameterizedType pType) {
                        java.lang.reflect.Type rawType = pType.getRawType();
                        if (rawType instanceof Class<?> clazz) {
                            return clazz;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to extract type from TypeReference", e);
                }
            }
        }
        return null;
    }

    void whenThen(InvocationOnMock invocation, Object result, String clsName) {
        if (result != null) {
            try {
                String mockName = Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getMockName().toString();

                // Check if this is a default Mockito mock name
                if (mockName.contains("mock for")) {
                    // Fallback to interface-based naming
                    Class<?>[] interfaces = invocation.getMock().getClass().getInterfaces();
                    if (interfaces.length > 0) {
                        mockName = interfaces[0].getSimpleName();
                        mockName = Character.toLowerCase(mockName.charAt(0)) + mockName.substring(1);
                    }
                } else {
                    // Strip any wrapper text if present
                    mockName = mockName.replaceAll("[\"']", "");
                }

                if (!mockName.equals("traceable")) {
                    MethodCallExpr methodCall = MockingRegistry.buildMockitoWhen(invocation.getMethod().getName(), clsName, mockName);
                    methodCall.setArguments(MockingRegistry.generateArgumentsForWhen(invocation.getMethod()));
                }
            } catch (Exception ex) {
                logger.warn(ex.getMessage());
            }
        }
    }
}

