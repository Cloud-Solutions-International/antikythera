package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
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
                String mockName = invocation.getMock().getClass().getInterfaces()[0]
                        .getSimpleName();
                mockName = Character.toLowerCase(mockName.charAt(0)) +
                        mockName.substring(1);

                if (!mockName.equals("traceable")) {
                    MethodCallExpr whenExpr = new MethodCallExpr(
                            new NameExpr("Mockito"),
                            "when"
                    );

                    MethodCallExpr methodCall = new MethodCallExpr()
                            .setScope(new NameExpr(mockName))
                            .setName(invocation.getMethod().getName());

                    // Add matchers for parameters
                    NodeList<Expression> args = new NodeList<>();
                    for (Class<?> paramType : invocation.getMethod().getParameterTypes()) {
                        args.add(createMatcher(paramType));
                    }
                    methodCall.setArguments(args);

                    whenExpr.setArguments(new NodeList<>(methodCall));
                    MethodCallExpr thenReturn = new MethodCallExpr(whenExpr, "thenReturn")
                            .setArguments(new NodeList<>(MockingRegistry.expressionFactory(clsName)));

                    TestGenerator.addWhenThen(thenReturn);
                }
            } catch (Exception ex) {
                logger.warn(ex.getMessage());
            }
        }
    }

    private MethodCallExpr createMatcher(Class<?> paramType) {
        String matcherName = switch (paramType.getName()) {
            case "java.lang.String" -> "anyString";
            case "int", "java.lang.Integer" -> "anyInt";
            case "long", "java.lang.Long" -> "anyLong";
            case "double", "java.lang.Double" -> "anyDouble";
            case "boolean", "java.lang.Boolean" -> "anyBoolean";
            default -> "any";
        };

        return new MethodCallExpr(new NameExpr("Mockito"), matcherName);
    }
}

