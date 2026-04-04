package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
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
import org.mockito.exceptions.base.MockitoException;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import sa.com.cloudsolutions.antikythera.evaluator.GeneratorState;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    private static final Logger logger = LoggerFactory.getLogger(MockingRegistry.class);
    private static final Map<String, Map<Callable, MockingCall>> mockedFields = new HashMap<>();
    private static Map<String, List<Expression>> customMockExpressions = new HashMap<>();

    public static final String MOCKITO = "Mockito";
    public static final String MOCKITO_FQN = "org.mockito.Mockito";

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
        clearCustomMockExpressions();
    }

    /**
     * Clears only the mocked fields tracking map, preserving custom mock expressions.
     * Use this between service generations to avoid cross-service state leakage.
     */
    public static void clearMockedFields() {
        mockedFields.clear();
    }

    /**
     * Creates a 'Mockito.when().then()' style setup.
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

        for(TypeWrapper wrapper : resolvedTypes) {
            if (wrapper.getClazz() != null && Modifier.isFinal(wrapper.getClazz().getModifiers())) {
                continue;
            }
            String fqn = wrapper.getFullyQualifiedName();
            GeneratorState.addImport(new ImportDeclaration(fqn, false, false));
            Variable v;
            if (AntikytheraRunTime.getCompilationUnit(fqn) != null) {
                if (resolvedTypes.size() == 1) {
                    Evaluator eval = EvaluatorFactory.createLazily(fqn, MockingEvaluator.class);
                    eval.setVariableName(variable.getNameAsString());
                    v = new Variable(eval);
                } else {
                    return mockCollection(resolvedTypes, fqn);
                }
            } else {
                String mocker = Settings.getProperty(Settings.MOCK_WITH_INTERNAL, String.class).orElse(MOCKITO);
                if (mocker.equals(MOCKITO)) {
                    v = MockingRegistry.createMockitoMockInstance(fqn);
                } else {
                    v = MockingRegistry.createByteBuddyMockInstance(fqn);
                }
            }
            v.setType(variable.getType());
            return v;
        }
        return null;
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
        return createMockitoMockInstance(cls);
    }

    public static Variable createMockitoMockInstance(Class<?> cls) {
        try {
            String mockName = cls.getSimpleName();
            mockName = Character.toLowerCase(mockName.charAt(0)) + mockName.substring(1);
            Variable v = new Variable(Mockito.mock(cls, withSettings().name(mockName).defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT)));
            v.setClazz(cls);
            // Set initializer so test generator knows to use Mockito.mock()
            v.setInitializer(List.of(new MethodCallExpr(
                new NameExpr(MOCKITO), "mock",
                new NodeList<>(new ClassExpr(new ClassOrInterfaceType(null, cls.getSimpleName())))
            )));
            return v;
        } catch (MockitoException e) {
            logger.warn("Cannot create Mockito mock for {} ({}), substituting null — tests involving this type may be incomplete",
                    cls.getName(), e.getMessage().lines().findFirst().orElse(""));
            Variable v = new Variable(null);
            v.setClazz(cls);
            return v;
        }
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
        return buildMockitoWhen(methodName, expressionFactory(resolveReturnTypeForStub(returnType, methodName, variableName)), variableName);
    }

    /**
     * When the declared return type is {@code Object}, use a cast target from
     * {@link GeneratorState} (see {@link sa.com.cloudsolutions.antikythera.evaluator.MethodBodyMockStubAnalyzer})
     * or from {@code (T) mock.call()} so {@code thenReturn} is compatible with downstream casts.
     */
    static String resolveReturnTypeForStub(String returnType, String methodName, String mockScopeVariableName) {
        if (!"java.lang.Object".equals(returnType)) {
            return returnType;
        }
        String pending = GeneratorState.peekPendingObjectStubReturnFqn();
        if (pending != null) {
            return pending;
        }
        if (mockScopeVariableName != null) {
            String hint = GeneratorState.getMockStubReturnHint(mockScopeVariableName, methodName);
            if (hint != null) {
                return hint;
            }
        }
        return returnType;
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
        GeneratorState.addWhenThen(thenReturn);

        return methodCall;
    }

    public static void addMockitoExpression(MethodDeclaration md, Object returnValue, String variableName) {
        if (returnValue != null && variableName != null) {
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

    public static NodeList<Expression> generateArgumentsForWhen(Method m, Object[] invocationArguments) {
        NodeList<Expression> args = new NodeList<>();
        java.lang.reflect.Parameter[] parameters = m.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Object invocationArgument = invocationArguments != null && i < invocationArguments.length
                    ? invocationArguments[i]
                    : null;
            args.add(MockingRegistry.createMockitoArgument(parameters[i].getType(), invocationArgument));
        }
        return args;
    }


    public static Expression expressionFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return new NullLiteralExpr();
        }

        // Check custom mock expressions first (project-specific overrides)
        List<Expression> customExprs = getCustomMockExpressions(qualifiedName);
        if (!customExprs.isEmpty()) {
            return customExprs.get(0);
        }

        return switch (qualifiedName) {
            case "List", "java.util.List", "java.util.ArrayList" -> {
                GeneratorState.addImport(new ImportDeclaration("java.util.ArrayList", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("ArrayList<>"))
                        .setArguments(new NodeList<>());
            }

            case "Map", "java.util.Map", "java.util.HashMap" -> {
                GeneratorState.addImport(new ImportDeclaration("java.util.HashMap", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("HashMap"))
                        .setArguments(new NodeList<>());
            }

            case "java.util.TreeMap" -> {
                GeneratorState.addImport(new ImportDeclaration("java.util.TreeMap", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("TreeMap"))
                        .setArguments(new NodeList<>());
            }

            case "Set", "java.util.Set", "java.util.HashSet" -> {
                GeneratorState.addImport(new ImportDeclaration("java.util.HashSet", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("HashSet"))
                        .setArguments(new NodeList<>());
            }

            case "java.util.TreeSet" -> {
                GeneratorState.addImport(new ImportDeclaration("java.util.TreeSet", false, false));
                yield new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("TreeSet"))
                        .setArguments(new NodeList<>());
            }

            case Reflect.JAVA_UTIL_OPTIONAL -> {
                GeneratorState.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_OPTIONAL, false, false));
                yield new MethodCallExpr(
                        new NameExpr("Optional"),
                        "empty"
                );
            }

            case "Boolean", "java.lang.Boolean", Reflect.PRIMITIVE_BOOLEAN -> new BooleanLiteralExpr(false);

            case Reflect.PRIMITIVE_FLOAT, Reflect.FLOAT, Reflect.PRIMITIVE_DOUBLE, Reflect.DOUBLE,
                    "java.lang.Float", "java.lang.Double" ->
                    new DoubleLiteralExpr("0.0");

            case Reflect.INTEGER, "int", Reflect.JAVA_LANG_INTEGER -> new IntegerLiteralExpr("0");

            case "Short", "short", "java.lang.Short" -> new IntegerLiteralExpr("0");

            case "Byte", "byte", Reflect.JAVA_LANG_BYTE -> new IntegerLiteralExpr("0");

            case "Character", "char", Reflect.JAVA_LANG_CHARACTER -> new IntegerLiteralExpr("0");

            case "Long", "long", Reflect.JAVA_LANG_LONG -> new LongLiteralExpr("-100L");

            case "String", "java.lang.String" -> new StringLiteralExpr("0");

            default -> createExpressionForUnknownType(qualifiedName);
        };
    }

    /**
     * Creates an expression for types not explicitly handled in expressionFactory.
     * For interfaces and abstract classes, or classes without no-arg constructors,
     * generates Mockito.mock(). Otherwise generates new ClassName().
     */
    private static Expression createExpressionForUnknownType(String qualifiedName) {
        try {
            Class<?> cls = AbstractCompiler.loadClass(qualifiedName);
            if (isJavaLangPrimitiveWrapper(cls)) {
                return expressionFactory(cls.getName());
            }
            if (cls.isInterface() || java.lang.reflect.Modifier.isAbstract(cls.getModifiers())) {
                return createMockExpression(cls.getSimpleName());
            }
            // Try to find no-arg constructor
            cls.getDeclaredConstructor();
            return new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName(qualifiedName))
                    .setArguments(new NodeList<>());
        } catch (NoSuchMethodException e) {
            // No no-arg constructor, use Mockito.mock()
            String simpleName = qualifiedName.contains(".")
                    ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                    : qualifiedName;
            // Handle inner class names (replace $ with .)
            simpleName = simpleName.replace('$', '.');
            return createMockExpression(simpleName);
        } catch (ClassNotFoundException e) {
            // Class not in classpath; use Mockito.mock() as fallback to avoid no-arg constructor issues
            String simpleName = qualifiedName.contains(".")
                    ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                    : qualifiedName;
            simpleName = simpleName.replace('$', '.');
            return createMockExpression(simpleName);
        }
    }

    private static boolean isJavaLangPrimitiveWrapper(Class<?> cls) {
        return Integer.class.equals(cls) || Long.class.equals(cls) || Short.class.equals(cls)
                || Byte.class.equals(cls) || Character.class.equals(cls) || Float.class.equals(cls)
                || Double.class.equals(cls) || Boolean.class.equals(cls);
    }

    /**
     * Creates a Mockito.mock(ClassName.class) expression.
     */
    private static Expression createMockExpression(String simpleName) {
        return new MethodCallExpr(
                new NameExpr(MOCKITO), "mock",
                new NodeList<>(new ClassExpr(new ClassOrInterfaceType(null, simpleName)))
        );
    }

    public static Expression createMockitoArgument(String typeName) {
        MethodCallExpr mce = generateAnyExpression(typeName);
        GeneratorState.addImport(new ImportDeclaration(MOCKITO_FQN, false, false));
        // If it's a generic Mockito.any() call, add casting
        if (mce.getNameAsString().equals("any") && !typeName.equals("Object") && !typeName.equals("Type")) {
            return new CastExpr(new ClassOrInterfaceType(null, typeName),mce);
        }
        return mce;
    }

    public static Expression createMockitoArgument(Class<?> parameterType, Object invocationArgument) {
        if (Class.class.equals(parameterType) && invocationArgument instanceof Class<?> clazz) {
            GeneratorState.addImport(new ImportDeclaration(MOCKITO_FQN, false, false));
            return new MethodCallExpr(
                    new NameExpr(MOCKITO),
                    "eq",
                    new NodeList<>(new ClassExpr(new ClassOrInterfaceType(null, clazz.getCanonicalName())))
            );
        }
        return createMockitoArgument(parameterType.getSimpleName());
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

    public static void addCustomMockExpression(String className, Expression expr) {
        customMockExpressions.computeIfAbsent(className, k -> new ArrayList<>()).add(expr);
    }
    public static List<Expression> getCustomMockExpressions(String className) {
        return customMockExpressions.getOrDefault(className, new ArrayList<>());
    }
    public static void clearCustomMockExpressions() {
        customMockExpressions.clear();
    }
    public static void setCustomMockExpressions(Map<String, List<Expression>> customMockExpressions) {
        MockingRegistry.customMockExpressions = customMockExpressions;
    }
}
