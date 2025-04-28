package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
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
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.withSettings;

public class MockingRegistry {
    private static final Map<String, Map<Callable, MockingCall>> mockedFields = new HashMap<>();

    private MockingRegistry() {

    }

    public static void markAsMocked(String className) {
        mockedFields.put(className, new HashMap<>());
    }

    public static boolean isMockTarget(String className) {
        return mockedFields.containsKey(className);
    }

    public static void reset() {
        mockedFields.clear();
    }

    public static void when(String className, Callable callable, MockingCall then) {
        Map<Callable, MockingCall> map = mockedFields.computeIfAbsent(className, k -> new HashMap<>());
        map.put(callable, then);
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

    public static List<MockingCall> getAllMocks() {
        List<MockingCall> result = new ArrayList<>();
        for (Map<Callable, MockingCall> map : mockedFields.values()) {
            result.addAll(map.values());
        }
        return result;
    }

    public static MockingCall getThen(String className, Callable callable) {
        Map<Callable, MockingCall> map = mockedFields.get(className);
        if (map != null) {
            return map.get(callable);
        }
        return null;
    }


    public static MethodCallExpr buildMockitoWhen(String name, String returnType, String variableName) {
        MethodCallExpr mockitoWhen = new MethodCallExpr(
                new NameExpr("Mockito"),
                "when"
        );

        MethodCallExpr methodCall = new MethodCallExpr()
                .setScope(new NameExpr(variableName))
                .setName(name);
        mockitoWhen.setArguments(new NodeList<>(methodCall));

        MethodCallExpr thenReturn = new MethodCallExpr(mockitoWhen, "thenReturn")
                .setArguments(new NodeList<>(expressionFactory(returnType)));
        TestGenerator.addWhenThen(thenReturn);

        return methodCall;
    }

    public static void addMockitoExpression(MethodDeclaration md, Object returnValue, String variableName) {
        if (returnValue != null) {
            MethodCallExpr methodCall = MockingRegistry.buildMockitoWhen(
                    md.getNameAsString(), returnValue.getClass().getName(), variableName);
            NodeList<Expression> args = fakeArguments(md);
            methodCall.setArguments(args);
        }
    }


    public static NodeList<Expression> fakeArguments(MethodDeclaration md) {
        NodeList<Expression> args = new NodeList<>();
        md.getParameters().forEach(param -> {
            String typeName = param.getType().asString();
            args.add(createMockitoArgument(typeName));
        });
        return args;
    }

    public static Expression expressionFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return new NullLiteralExpr();
        }

        return switch (qualifiedName) {
            case "List", "java.util.List", "java.util.ArrayList" -> {
                TestGenerator.addDependency("java.util.ArrayList");
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("ArrayList"))
                        .setArguments(new NodeList<>());
            }

            case "Map", "java.util.Map", "java.util.HashMap" -> {
                TestGenerator.addDependency("java.util.HashMap");
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("HashMap"))
                        .setArguments(new NodeList<>());
            }

            case "java.util.TreeMap" -> {
                TestGenerator.addDependency("java.util.TreeMap");
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("TreeMap"))
                        .setArguments(new NodeList<>());
            }

            case "Set", "java.util.Set", "java.util.HashSet" -> {
                TestGenerator.addDependency("java.util.HashSet");
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("HashSet"))
                        .setArguments(new NodeList<>());
            }

            case "java.util.TreeSet" -> {
                TestGenerator.addDependency("java.util.TreeSet");
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("TreeSet"))
                        .setArguments(new NodeList<>());
            }

            case "java.util.Optional" -> {
                TestGenerator.addDependency("java.util.Optional");
                yield new MethodCallExpr(
                        new NameExpr("Optional"),
                        "empty"
                );
            }

            case "Boolean", Reflect.PRIMITIVE_BOOLEAN -> new BooleanLiteralExpr(false);

            case Reflect.PRIMITIVE_FLOAT, Reflect.FLOAT, Reflect.PRIMITIVE_DOUBLE, Reflect.DOUBLE ->
                    new DoubleLiteralExpr("0.0");

            case Reflect.INTEGER, "int" -> new IntegerLiteralExpr("0");

            case "Long", "long", "java.lang.Long" -> new LongLiteralExpr("-100L");

            case "String", "java.lang.String" -> new StringLiteralExpr("Ibuprofen");

            default -> new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName(qualifiedName))
                    .setArguments(new NodeList<>());
        };
    }

    public static MethodCallExpr createMockitoArgument(String typeName) {
        return new MethodCallExpr(
                new NameExpr("Mockito"),
                switch (typeName) {
                    case "String" -> "anyString";
                    case "int", "Integer" -> "anyInt";
                    case "long", "Long" -> "anyLong";
                    case "double", "Double" -> "anyDouble";
                    case "boolean", "Boolean" -> "anyBoolean";
                    default -> "any";
                }
        );
    }
}
