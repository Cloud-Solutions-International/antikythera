package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import static org.mockito.Mockito.withSettings;


class MockReturnValueHandler implements Answer<Object> {
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

        // Add Mockito when-then expression
        if (result != null) {
            // Get mock variable name by converting class name to camelCase
            String mockName = invocation.getMock().getClass().getInterfaces()[0]
                .getSimpleName();
            mockName = Character.toLowerCase(mockName.charAt(0)) +
                mockName.substring(1);

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
                .setArguments(new NodeList<>(MockingEvaluator.expressionFactory(clsName)));

            TestGenerator.addWhenThen(thenReturn);
        }

        return result;
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

