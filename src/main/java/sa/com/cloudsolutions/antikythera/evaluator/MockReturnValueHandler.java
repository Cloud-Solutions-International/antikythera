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
        Object[] arguments = invocation.getArguments();
        for (Object arg : arguments) {
            if (arg instanceof Class) {
                Class<?> typeArg = (Class<?>) arg;
                // This argument might be specifying the return type
                clsName = typeArg.getName();
                returnType = typeArg;
                break;
            } else if (arg instanceof java.lang.reflect.Type) {
                // Handle TypeReference or other Type instances
                try {
                    java.lang.reflect.Type typeArg = (java.lang.reflect.Type) arg;
                    if (typeArg instanceof java.lang.reflect.ParameterizedType) {
                        java.lang.reflect.ParameterizedType pType = (java.lang.reflect.ParameterizedType) typeArg;
                        java.lang.reflect.Type rawType = pType.getRawType();
                        if (rawType instanceof Class) {
                            clsName = ((Class<?>) rawType).getName();
                            returnType = (Class<?>) rawType;
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to extract type from TypeReference", e);
                }
            }
        }

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

