package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
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
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.withSettings;

/**
 * Keep track of all the types that are being mocked internally while evaluating expressions.
 * Also supports the when/then type of mocking that you find in frameworks like mockito.
 */
public class MockingRegistry {
    private static final Map<String, Map<Callable, MockingCall>> mockedFields = new HashMap<>();
    public static final String MOCKITO = "Mockito";

    private MockingRegistry() {

    }

    /**
     * Mark a class as mocked.
     * @param className the name of the class to mark as mocked
     */
    public static void markAsMocked(String className) {
        mockedFields.put(className, new HashMap<>());
    }

    public static boolean isMockTarget(String className) {
        return mockedFields.containsKey(className);
    }

    public static String generateRegistryKey(List<TypeWrapper> resolvedTypes) {
        if (resolvedTypes.size() == 1) {
            return resolvedTypes.getFirst().getFullyQualifiedName();
        }

        StringBuilder joinedNames = new StringBuilder();
        for (int i = 0; i < resolvedTypes.size(); i++) {
            joinedNames.append(resolvedTypes.get(i).getFullyQualifiedName());
            if (i < resolvedTypes.size() - 1) {
                joinedNames.append(":");
            }
        }
        return joinedNames.toString();
    }

    public static void reset() {
        mockedFields.clear();
    }

    /**
     * Creates p 'Mockito.when().then()' style setup.
     * This may or may not translate to a real Mockito call. That depends on the mocking framework
     * being used.
     *
     * @param className the name of the class the mocked method belongs to.
     * @param mockingCall represents the method being called and the mocked return value
     */
    public static void when(String className, MockingCall mockingCall) {
        Map<Callable, MockingCall> map = mockedFields.computeIfAbsent(className, k -> new HashMap<>());
        map.put(mockingCall.getCallable(), mockingCall);
    }

    /**
     * Creates a mocked version of the given variable using mockito or byte buddy.
     *
     * @param variable This should be a part of a field declaration. The variable declared in the
     *                 field will be mocked.
     * @return a Variable representing the mocked object
     * @throws ClassNotFoundException if the class cannot be found
     */
    public static Variable mockIt(VariableDeclarator variable) throws ReflectiveOperationException {
        List<TypeWrapper> resolvedTypes = AbstractCompiler.findTypesInVariable(variable);

        String fqn = resolvedTypes.getFirst().getFullyQualifiedName();
        Variable v;
        if (AntikytheraRunTime.getCompilationUnit(fqn) != null) {
            if (resolvedTypes.size() == 1) {
                Evaluator eval = EvaluatorFactory.createLazily(fqn, MockingEvaluator.class);
                eval.setVariableName(variable.getNameAsString());
                v = new Variable(eval);
            }
            else {
                return mockCollection(resolvedTypes, fqn);
            }
        }
        else {
            String mocker = Settings.getProperty(Settings.MOCK_WITH_INTERNAL, String.class).orElse("ByteBuddy");
            if (mocker.equals(MOCKITO)) {
                v = MockingRegistry.createMockitoMockInstance(fqn);
            }
            else {
                v = MockingRegistry.createByteBuddyMockInstance(fqn);
            }
        }
        v.setType(variable.getType());
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Variable mockCollection(List<TypeWrapper> resolvedTypes, String fqn) {
        String collection = resolvedTypes.getLast().getFullyQualifiedName();
        Variable cv = Reflect.variableFactory(collection);
        if (collection.equals("java.util.List") || collection.equals("java.util.Set")
                || collection.equals("java.util.Collection")) {
            Collection<Object> c = (Collection<Object>) cv.getValue();
            for (String implementation : AntikytheraRunTime.findImplementations(fqn)) {
                Evaluator eval = EvaluatorFactory.createLazily(implementation, MockingEvaluator.class);
                c.add(eval);
            }
        }
        return cv;
    }

    public static Variable createMockitoMockInstance(String className) throws ClassNotFoundException {
        Class<?> cls = AbstractCompiler.loadClass(className);
        Variable v = new Variable(Mockito.mock(cls, withSettings().defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT)));
        v.setClazz(cls);
        return v;
    }

    public static Variable createByteBuddyMockInstance(String className) throws ReflectiveOperationException {
        Class<?> cls = AbstractCompiler.loadClass(className);
        MethodInterceptor interceptor = new MethodInterceptor(cls);
        Class<?> bb = AKBuddy.createDynamicClass(interceptor);
        Variable v = new Variable(AKBuddy.createInstance(bb, interceptor));
        v.setClazz(cls);
        return v;
    }

    public static List<MockingCall> getAllMocks() {
        List<MockingCall> result = new ArrayList<>();
        for (Map<Callable, MockingCall> map : mockedFields.values()) {
            result.addAll(map.values());
        }
        return result;
    }

    /**
     * What value should be returned when the method is called.
     * Intended for use my MethodInterceptor instances attached to dynamic classes.
     * @param className the name of the class that is supposed to have been mocked.
     * @param callable identifies the method for which a when/then has been set up.
     * @return the MockingCall that was created for the method.
     */
    public static MockingCall getThen(String className, Callable callable) {
        Map<Callable, MockingCall> map = mockedFields.get(className);
        if (map != null) {
            return map.get(callable);
        }
        return null;
    }

    public static MethodCallExpr buildMockitoWhen(String methodName, String returnType, String variableName) {
        return buildMockitoWhen(methodName, expressionFactory(returnType), variableName);
    }

    public static MethodCallExpr buildMockitoWhen(String methodName, Expression returnValue, String scopeVariable) {
        MethodCallExpr mockitoWhen = new MethodCallExpr(
                new NameExpr(MOCKITO),
                "when"
        );

        MethodCallExpr methodCall = new MethodCallExpr()
                .setName(methodName);

        if (scopeVariable != null) {
            methodCall.setScope(new NameExpr(scopeVariable));
        }

        mockitoWhen.setArguments(new NodeList<>(methodCall));

        MethodCallExpr thenReturn = new MethodCallExpr(mockitoWhen, "thenReturn")
                .setArguments(new NodeList<>(returnValue));
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

    public static NodeList<Expression> generateArgumentsForWhen(Method m) {
        NodeList<Expression> args = new NodeList<>();
        java.lang.reflect.Parameter[] parameters = m.getParameters();
        for (java.lang.reflect.Parameter p : parameters) {
            String typeName = p.getType().getSimpleName();
            args.add(MockingRegistry.createMockitoArgument(typeName));
        }
        return args;
    }


    public static Expression expressionFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return new NullLiteralExpr();
        }

        return switch (qualifiedName) {
            case "List", "java.util.List", "java.util.ArrayList" -> {
                TestGenerator.addImport(new ImportDeclaration("java.util.ArrayList", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("ArrayList"))
                        .setArguments(new NodeList<>());
            }

            case "Map", "java.util.Map", "java.util.HashMap" -> {
                TestGenerator.addImport(new ImportDeclaration("java.util.HashMap", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("HashMap"))
                        .setArguments(new NodeList<>());
            }

            case "java.util.TreeMap" -> {
                TestGenerator.addImport(new ImportDeclaration("java.util.TreeMap", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("TreeMap"))
                        .setArguments(new NodeList<>());
            }

            case "Set", "java.util.Set", "java.util.HashSet" -> {
                TestGenerator.addImport(new ImportDeclaration("java.util.HashSet", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("HashSet"))
                        .setArguments(new NodeList<>());
            }

            case "java.util.TreeSet" -> {
                TestGenerator.addImport(new ImportDeclaration("java.util.TreeSet", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("TreeSet"))
                        .setArguments(new NodeList<>());
            }

            case Reflect.JAVA_UTIL_OPTIONAL -> {
                TestGenerator.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_OPTIONAL, false, false));
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

    public static Expression createMockitoArgument(String typeName) {
        MethodCallExpr mce = generateAnyExpression(typeName);
        TestGenerator.addImport(new ImportDeclaration(MOCKITO, false, false));
        // If it's a generic Mockito.any() call, add casting
        if (mce.getNameAsString().equals("any") && !typeName.equals("Object")) {
            return new CastExpr(new ClassOrInterfaceType(null, typeName),mce);
        }
        return mce;
    }

    private static MethodCallExpr generateAnyExpression(String typeName) {
        MethodCallExpr mce = new MethodCallExpr(
                new NameExpr(MOCKITO),
                switch (typeName) {
                    case "String" -> "anyString";
                    case "int", "Integer" -> "anyInt";
                    case "long", "Long" -> "anyLong";
                    case "double", "Double" -> "anyDouble";
                    case "boolean", "Boolean" -> "anyBoolean";
                    default -> "any";
                }
        );
        mce.setScope(new NameExpr(MOCKITO));
        return mce;
    }
}
