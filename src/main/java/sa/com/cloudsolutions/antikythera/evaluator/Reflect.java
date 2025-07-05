package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ArrayCreationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.functional.FunctionalConverter;
import sa.com.cloudsolutions.antikythera.evaluator.functional.FunctionalInvocationHandler;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class Reflect {
    public static final String ANTIKYTHERA = "Antikythera";
    public static final String BOOLEAN = "Boolean";
    public static final String BYTE = "Byte";
    public static final String CHARACTER = "Character";
    public static final String DOUBLE = "Double";
    public static final String FLOAT = "Float";
    public static final String INTEGER = "Integer";
    public static final String LONG = "Long";
    public static final String OPTIONAL = "Optional";
    public static final String SHORT = "Short";
    public static final String STRING = "String";

    public static final String JAVA_LANG_BIG_DECIMAL = "java.lang.BigDecimal";
    public static final String JAVA_LANG_BOOLEAN = "java.lang.Boolean";
    public static final String JAVA_LANG_BYTE = "java.lang.Byte";
    public static final String JAVA_LANG_CHARACTER = "java.lang.Character";
    public static final String JAVA_LANG_DOUBLE = "java.lang.Double";
    public static final String JAVA_LANG_INTEGER = "java.lang.Integer";
    public static final String JAVA_LANG_LONG = "java.lang.Long";
    public static final String JAVA_LANG_STRING = "java.lang.String";
    public static final String JAVA_UTIL_ARRAY_LIST = "java.util.ArrayList";
    public static final String JAVA_UTIL_HASH_SET = "java.util.HashSet";
    public static final String JAVA_UTIL_OPTIONAL = "java.util.Optional";

    public static final String PRIMITIVE_BOOLEAN = "boolean";
    public static final String PRIMITIVE_BYTE = "byte";
    public static final String PRIMITIVE_CHAR = "char";
    public static final String PRIMITIVE_DOUBLE = "double";
    public static final String PRIMITIVE_FLOAT = "float";
    public static final String PRIMITIVE_INT = "int";
    public static final String PRIMITIVE_LONG = "long";
    public static final String PRIMITIVE_SHORT = "short";
    public static final String JAVA_UTIL_LIST = "java.util.List";
    public static final String JAVA_UTIL_SET = "java.util.Set";
    public static final String JAVA_UTIL_MAP = "java.util.Map";


    /**
     * Keeps a map of wrapper types to their primitive counterpart
     * for example, `Integer.class -> int.class`
     */
    static Map<Class<?>, Class<?>> wrapperToPrimitive = new HashMap<>();
    /**
     * The opposite of wrapperToPrimitive
     * here `int.class -> Integer.class`
     */
    static Map<Class<?>, Class<?>> primitiveToWrapper = new HashMap<>();

    static Set<String> basicTypes = new HashSet<>();
    private static final Map<String, Class<?>> BOXED_TYPE_MAP = new HashMap<>();

    static {
        /*
         * of course those maps are absolutely no use unless we can fill them up
         */
        wrapperToPrimitive.put(Integer.class, int.class);
        wrapperToPrimitive.put(Double.class, double.class);
        wrapperToPrimitive.put(Boolean.class, boolean.class);
        wrapperToPrimitive.put(Long.class, long.class);
        wrapperToPrimitive.put(Float.class, float.class);
        wrapperToPrimitive.put(Short.class, short.class);
        wrapperToPrimitive.put(Byte.class, byte.class);
        wrapperToPrimitive.put(Character.class, char.class);

        for (Map.Entry<Class<?>, Class<?>> entry : wrapperToPrimitive.entrySet()) {
            primitiveToWrapper.put(entry.getValue(), entry.getKey());
        }

        basicTypes.add(JAVA_LANG_BIG_DECIMAL);
        basicTypes.add(JAVA_LANG_BOOLEAN);
        basicTypes.add(JAVA_LANG_BYTE);
        basicTypes.add(JAVA_LANG_CHARACTER);
        basicTypes.add(JAVA_LANG_DOUBLE);
        basicTypes.add(JAVA_LANG_INTEGER);
        basicTypes.add(JAVA_LANG_LONG);
        basicTypes.add(JAVA_LANG_STRING);
        basicTypes.add(JAVA_UTIL_ARRAY_LIST);
        basicTypes.add(JAVA_UTIL_HASH_SET);
        basicTypes.add(JAVA_UTIL_OPTIONAL);

        BOXED_TYPE_MAP.put(PRIMITIVE_BOOLEAN, boolean.class);
        BOXED_TYPE_MAP.put(PRIMITIVE_BYTE, byte.class);
        BOXED_TYPE_MAP.put(PRIMITIVE_CHAR, char.class);
        BOXED_TYPE_MAP.put(PRIMITIVE_DOUBLE, double.class);
        BOXED_TYPE_MAP.put(PRIMITIVE_FLOAT, float.class);
        BOXED_TYPE_MAP.put(PRIMITIVE_INT, int.class);
        BOXED_TYPE_MAP.put(PRIMITIVE_LONG, long.class);
        BOXED_TYPE_MAP.put(PRIMITIVE_SHORT, short.class);

        BOXED_TYPE_MAP.put(BOOLEAN, Boolean.class);
        BOXED_TYPE_MAP.put(BYTE, Byte.class);
        BOXED_TYPE_MAP.put(CHARACTER, Character.class);
        BOXED_TYPE_MAP.put(DOUBLE, Double.class);
        BOXED_TYPE_MAP.put(FLOAT, Float.class);
        BOXED_TYPE_MAP.put(INTEGER, Integer.class);
        BOXED_TYPE_MAP.put(LONG, Long.class);
        BOXED_TYPE_MAP.put(OPTIONAL, Optional.class);
        BOXED_TYPE_MAP.put(SHORT, Short.class);
        BOXED_TYPE_MAP.put(STRING, String.class);

    }

    private Reflect() {
    }

    /**
     * Build the suitable set of arguments for use with a reflective method call
     *
     * @param methodCall ObjectCreationExpr from java parser, which will be used as the basis for finding the
     *                   *            method to be called along with its arguments.
     * @param evaluator  the evaluator to use to evaluate the arguments if any
     * @return A ReflectionArguments object which contains all the information required to execute a method
     * using reflection.
     * @throws AntikytheraException         if something goes wrong with the parser related code
     * @throws ReflectiveOperationException if reflective operations fail
     */
    public static ReflectionArguments buildArguments(MethodCallExpr methodCall, Evaluator evaluator, Variable scope)
            throws AntikytheraException, ReflectiveOperationException {
        ReflectionArguments args = buildArgumentsCommon(methodCall.getNameAsString(), methodCall.getArguments(), evaluator, scope);
        args.setMethodCallExpression(methodCall);
        return args;
    }

    /**
     * Build the set of arguments to be used with instantiating a class using reflection.
     *
     * @param oce       ObjectCreationExpr from java parser , which will be used as the basis for finding the right
     *                  constructor to use.
     * @param evaluator the evaluator to use to evaluate the arguments if any
     * @return A ReflectionArguments object which contains all the information required to execute a method
     * *          using reflection.
     * @throws AntikytheraException         if the reflection arguments cannot be solved
     * @throws ReflectiveOperationException if the reflective methods failed.
     */
    public static ReflectionArguments buildArguments(ObjectCreationExpr oce, Evaluator evaluator, Variable scope)
            throws AntikytheraException, ReflectiveOperationException {
        ReflectionArguments args = buildArgumentsCommon(null, oce.getArguments(), evaluator, scope);
        args.setMethodCallExpression(oce);
        return args;
    }

    private static ReflectionArguments buildArgumentsCommon(String methodName, List<Expression> arguments,
                                                            Evaluator evaluator, Variable scope)
            throws AntikytheraException, ReflectiveOperationException {
        Variable[] argValues = new Variable[arguments.size()];
        Class<?>[] argumentTypes = new Class<?>[arguments.size()];
        Object[] args = new Object[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            Expression expr = arguments.get(i);
            if (expr.isMethodReferenceExpr()) {
                expr = FunctionalConverter.convertToLambda(expr.asMethodReferenceExpr(), scope);
            }
            if (expr.isLambdaExpr()) {
                argValues[i] = FPEvaluator.create(expr.asLambdaExpr(), evaluator);
            } else {
                argValues[i] = evaluator.evaluateExpression(expr);
            }

            if (argValues[i] != null) {
                args[i] = argValues[i].getValue();
                if (argValues[i].getClazz() != null) {
                    argumentTypes[i] = argValues[i].getClazz();
                } else if (args[i] != null) {
                    argumentTypes[i] = argValues[i].getValue().getClass();
                }
            } else {
                try {
                    String className = arguments.getFirst().calculateResolvedType().describe();
                    argumentTypes[i] = primitiveToWrapper(className);
                } catch (UnsolvedSymbolException | IllegalStateException us) {
                    argumentTypes[i] = Object.class;
                }
            }
            if (argumentTypes[i] != null) {
                dynamicProxy(argumentTypes, i, args);
            }
        }

        ReflectionArguments reflectionArguments = new ReflectionArguments(methodName, args, argumentTypes);
        reflectionArguments.setScope(scope);
        reflectionArguments.setEnclosure(evaluator);
        return reflectionArguments;
    }

    private static void dynamicProxy(Class<?>[] argumentTypes, int i, Object[] args) {

        Class<?> functional = getFunctionalInterface(argumentTypes[i]);
        if (functional != null) {
            FPEvaluator<?> evaluator = null;

            // Check if the argument is already a proxy
            if (Proxy.isProxyClass(args[i].getClass())) {
                FunctionalInvocationHandler handler = (FunctionalInvocationHandler) Proxy.getInvocationHandler(args[i]);
                evaluator = handler.getEvaluator();
            } else if (args[i] instanceof FPEvaluator<?>) {
                evaluator = (FPEvaluator<?>) args[i];
            }

            if (evaluator != null) {
                Object proxy = Proxy.newProxyInstance(
                        argumentTypes[i].getClassLoader(),
                        new Class<?>[]{functional},
                        new FunctionalInvocationHandler(evaluator)
                );
                args[i] = proxy;
                argumentTypes[i] = functional;
            }
        } else if (args[i] instanceof Evaluator eval){
            MethodInterceptor interceptor = new MethodInterceptor(eval);
            try {
                Class<?> c = AKBuddy.createDynamicClass(interceptor);
                args[i] = AKBuddy.createInstance(c, interceptor);
                argumentTypes[i] = c;
            } catch (ReflectiveOperationException e) {
                throw new AntikytheraException(e);
            }
        }
    }

    public static Class<?> primitiveToWrapper(String className) {
        return switch (className) {
            case PRIMITIVE_BOOLEAN -> Boolean.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case PRIMITIVE_FLOAT -> Float.class;
            case PRIMITIVE_DOUBLE -> Double.class;
            case "char" -> Character.class;
            case PRIMITIVE_SHORT -> short.class;
            case "byte" -> byte.class;
            default -> Object.class;
        };
    }

    public static Class<?> getComponentClass(String elementType) throws ClassNotFoundException {
        if (basicTypes.contains(elementType)) {
            return Class.forName(elementType);
        }
        Class<?> boxed = BOXED_TYPE_MAP.get(elementType);
        if (boxed != null) {
            return boxed;
        }
        return AbstractCompiler.loadClass(elementType);

    }

    public static Type getComponentType(Class<?> clazz) {

        return switch (clazz.getName()) {
            case "int", JAVA_LANG_INTEGER -> PrimitiveType.intType();
            case PRIMITIVE_DOUBLE, DOUBLE, JAVA_LANG_DOUBLE -> PrimitiveType.doubleType();
            case PRIMITIVE_BOOLEAN, JAVA_LANG_BOOLEAN -> PrimitiveType.booleanType();
            case "long", JAVA_LANG_LONG, JAVA_LANG_BIG_DECIMAL -> PrimitiveType.longType();
            case PRIMITIVE_FLOAT, FLOAT, "java.lang.Float" -> PrimitiveType.floatType();
            case PRIMITIVE_SHORT, "java.lang.Short" -> PrimitiveType.shortType();
            case "byte", JAVA_LANG_BYTE -> PrimitiveType.byteType();
            case "char", JAVA_LANG_CHARACTER -> PrimitiveType.charType();
            case JAVA_LANG_STRING -> new ClassOrInterfaceType().setName(STRING);
            default -> null;
        };
    }

    public static Expression createLiteralExpression(Object value) {
        if (value == null) {
            return new NullLiteralExpr();
        }

        return switch (value.getClass().getSimpleName()) {
            case INTEGER -> new IntegerLiteralExpr(value.toString());
            case "Long" -> new LongLiteralExpr(value.toString());
            case DOUBLE, FLOAT -> new DoubleLiteralExpr(value.toString());
            case PRIMITIVE_BOOLEAN , BOOLEAN -> new BooleanLiteralExpr(Boolean.parseBoolean(value.toString()));
            case CHARACTER -> new CharLiteralExpr(value.toString().charAt(0));
            default -> new StringLiteralExpr(value.toString());
        };
    }

    public static Object getDefault(String elementType) {
        return switch (elementType) {
            case "int", INTEGER -> 0;
            case DOUBLE, PRIMITIVE_DOUBLE -> 0.0;
            case PRIMITIVE_BOOLEAN -> false;
            case "long", "Long" -> 0L;
            case PRIMITIVE_FLOAT -> 0.0f;
            case PRIMITIVE_SHORT -> Short.valueOf("0");
            case "byte", "char" -> 0x0;
            default -> null;
        };
    }

    public static Object getDefault(Class<?> returnType) {
        return switch (returnType) {
            case Class<?> c when c.equals(String.class) -> "0";
            case Class<?> c when c.equals(Integer.class) || c.equals(int.class) -> 0;
            case Class<?> c when c.equals(Long.class) || c.equals(long.class) -> 0L;
            case Class<?> c when c.equals(Boolean.class) || c.equals(boolean.class) -> false;
            case Class<?> c when c.equals(Double.class) || c.equals(double.class) -> 0.0;
            case Class<?> c when c.equals(Float.class) || c.equals(float.class) -> 0.0f;
            case Class<?> c when c.equals(Byte.class) || c.equals(byte.class) -> (byte) 0;
            case Class<?> c when c.equals(Short.class) || c.equals(short.class) -> (short) 0;
            case Class<?> c when c.equals(Character.class) || c.equals(char.class) -> '\0';
            case Class<?> c when c.equals(List.class) -> new ArrayList<>();
            case Class<?> c when c.equals(Map.class) -> new HashMap<>();
            case Class<?> c when c.equals(Set.class) -> new HashSet<>();
            default -> null;
        };
    }

    public static Variable createVariable(Object initialValue, String typeName, String stringValue) {
        Variable v = new Variable(initialValue);

        switch (typeName) {
            case "Long", DOUBLE, INTEGER, FLOAT, BOOLEAN -> {
                Expression scope = new NameExpr(typeName);
                Expression mce = new MethodCallExpr(scope, "valueOf")
                    .addArgument(new StringLiteralExpr(initialValue.toString()));
                v.setInitializer(List.of(mce));
            }
            case JAVA_UTIL_LIST, JAVA_UTIL_ARRAY_LIST -> {
                ObjectCreationExpr init = new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("ArrayList"));
                v.setInitializer(List.of(init));
            }
            case "java.util.Set", JAVA_UTIL_HASH_SET -> {
                ObjectCreationExpr init = new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName("HashSet"));
                v.setInitializer(List.of(init));
            }
            case JAVA_UTIL_OPTIONAL, OPTIONAL -> {
                MethodCallExpr init = new MethodCallExpr("empty");
                init.setScope(new NameExpr(OPTIONAL));
                v.setInitializer(List.of(init));
            }
            default -> {
                ObjectCreationExpr expr = new ObjectCreationExpr()
                        .setType(new ClassOrInterfaceType().setName(typeName));
                if (stringValue != null) {
                    expr.setArguments(NodeList.nodeList(new StringLiteralExpr(stringValue)));
                } else {
                    expr.setArguments(NodeList.nodeList());
                }
                v.setInitializer(List.of(expr));
                TestGenerator.addImport(new ImportDeclaration(typeName, false, false));
            }
        }
        if (v.getType() == null) {
            v.setType(new ClassOrInterfaceType().setName(typeName));
        }
        return v;
    }

    /**
     * Generate variables holding reasonable values.
     * All numerics will be 1.
     * Strings will be Antikythera
     * Booleans will be true
     *
     * @param qualifiedName a fully qualified name of a type
     * @return a variable representing a suitable default for that type
     */
    public static Variable variableFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }

        // Handle array types
        if (qualifiedName.endsWith("[]")) {
            return generateArrayVariable(qualifiedName);
        }

        return generateDefaultVariable(qualifiedName);
    }

    private static Variable generateArrayVariable(String qualifiedName) {
        String baseType = qualifiedName.substring(0, qualifiedName.length() - 2);
        return switch (baseType) {
            case STRING, JAVA_LANG_STRING -> {
                String[] arr = new String[]{ANTIKYTHERA};
                Variable v = new Variable(arr);
                ArrayCreationExpr init = new ArrayCreationExpr()
                        .setElementType(new ClassOrInterfaceType().setName(STRING))
                        .setInitializer(new ArrayInitializerExpr(new NodeList<>(new StringLiteralExpr(ANTIKYTHERA))));
                v.setInitializer(List.of(init));
                yield v;
            }
            case INTEGER, JAVA_LANG_INTEGER -> {
                Integer[] arr = new Integer[]{1};
                Variable v = new Variable(arr);
                ArrayCreationExpr init = new ArrayCreationExpr()
                        .setElementType(new ClassOrInterfaceType().setName(INTEGER))
                        .setInitializer(new ArrayInitializerExpr());
                v.setInitializer(List.of(init));
                yield v;
            }
            case LONG, JAVA_LANG_LONG -> {
                Long[] arr = new Long[]{1L};
                Variable v = new Variable(arr);
                ArrayCreationExpr init = new ArrayCreationExpr()
                        .setElementType(new ClassOrInterfaceType().setName("Long"))
                        .setInitializer(new ArrayInitializerExpr());
                v.setInitializer(List.of(init));
                yield v;
            }
            case DOUBLE, JAVA_LANG_DOUBLE -> {
                Double[] arr = new Double[]{1.0};
                Variable v = new Variable(arr);
                ArrayCreationExpr init = new ArrayCreationExpr()
                        .setElementType(new ClassOrInterfaceType().setName(DOUBLE))
                        .setInitializer(new ArrayInitializerExpr());
                v.setInitializer(List.of(init));
                yield v;
            }
            case BOOLEAN, JAVA_LANG_BOOLEAN -> {
                Boolean[] arr = new Boolean[]{true};
                Variable v = new Variable(arr);
                ArrayCreationExpr init = new ArrayCreationExpr()
                        .setElementType(new ClassOrInterfaceType().setName(BOOLEAN))
                        .setInitializer(new ArrayInitializerExpr());
                v.setInitializer(List.of(init));
                yield v;
            }
            default -> new Variable(new Object[0]);
        };
    }

    public static Variable generateDefaultVariable(String qualifiedName) {
        return switch (qualifiedName) {
            case "List", JAVA_UTIL_LIST, JAVA_UTIL_ARRAY_LIST, "java.lang.Iterable" ->
                    createVariable(new ArrayList<>(), JAVA_UTIL_ARRAY_LIST, null);
            case "java.util.LinkedList" -> createVariable(new LinkedList<>(), "java.util.LinkedList", null);
            case "Map", "java.util.Map", "java.util.HashMap" ->
                    createVariable(new HashMap<>(), "java.util.HashMap", null);
            case "java.util.TreeMap" -> createVariable(new TreeMap<>(), "java.util.TreeMap", null);
            case "Set", "java.util.Set", JAVA_UTIL_HASH_SET ->
                    createVariable(new HashSet<>(), JAVA_UTIL_HASH_SET, null);
            case "java.util.TreeSet" -> createVariable(new TreeSet<>(), "java.util.TreeSet", null);
            case JAVA_UTIL_OPTIONAL, OPTIONAL -> createVariable(Optional.empty(), JAVA_UTIL_OPTIONAL, null);
            case BOOLEAN, PRIMITIVE_BOOLEAN, JAVA_LANG_BOOLEAN -> createVariable(true, BOOLEAN, "true");
            case PRIMITIVE_FLOAT, FLOAT, PRIMITIVE_DOUBLE, DOUBLE, JAVA_LANG_DOUBLE ->
                    createVariable(0.0, DOUBLE, "0.0");
            case INTEGER, "int", JAVA_LANG_INTEGER -> createVariable(0, INTEGER, "0");
            case "Long", "long", JAVA_LANG_LONG -> createVariable(0L, "Long", "0");
            case STRING, JAVA_LANG_STRING -> {
                Variable result = createVariable(ANTIKYTHERA, STRING, ANTIKYTHERA);
                result.setInitializer(List.of(new StringLiteralExpr(ANTIKYTHERA)));
                yield result;
            }
            default -> new Variable(null);
        };
    }

    public static Variable generateNonDefaultVariable(String qualifiedName) {
        return switch (qualifiedName) {
            case JAVA_UTIL_OPTIONAL, OPTIONAL -> createVariable(Optional.empty(), JAVA_UTIL_OPTIONAL, null);
            case BOOLEAN, PRIMITIVE_BOOLEAN, JAVA_LANG_BOOLEAN -> createVariable(true, BOOLEAN, "true");
            case PRIMITIVE_FLOAT, FLOAT, PRIMITIVE_DOUBLE, DOUBLE, JAVA_LANG_DOUBLE ->
                    createVariable(1.0, DOUBLE, "1.0");
            case INTEGER, "int", JAVA_LANG_INTEGER -> createVariable(1, INTEGER, "0");
            case "Long", "long", JAVA_LANG_LONG -> createVariable(1L, "Long", "0");
            case STRING, JAVA_LANG_STRING -> {
                Variable result = createVariable(ANTIKYTHERA, STRING, ANTIKYTHERA);
                result.setInitializer(List.of(new StringLiteralExpr(ANTIKYTHERA)));
                yield result;
            }
            default -> new Variable(null);
        };
    }

    /**
     * Finds a matching method using parameters.
     * <p>
     * This function has side effects. The paramTypes in reflectionArguments may end up being
     * converted from a boxed to primitive or vice versa.
     * This is because the Variable class that we use has an Object representing the value.
     * Whereas some of the methods have parameters that require a primitive type.
     * Hence, the conversion needs to happen.
     *
     * @param clazz the class on which we need to match the method name
     * @return a Method instance or null.
     */
    @SuppressWarnings("java:S1872")
    public static Method findMethod(Class<?> clazz, ReflectionArguments reflectionArguments) {
        String methodName = reflectionArguments.getMethodName();
        Class<?>[] argumentTypes = reflectionArguments.getArgumentTypes();

        for (Method m : getMethodsByName(clazz, methodName)) {
            Class<?>[] parameterTypes = m.getParameterTypes();
            if (parameterTypes.length == 1 && parameterTypes[0].equals(Object[].class)) {
                return m;
            }
            if (argumentTypes == null || parameterTypes.length != argumentTypes.length) {
                continue;
            }
            boolean found = true;
            for (int i = 0; i < argumentTypes.length; i++) {
                if (matchArgumentVsParameter(argumentTypes, parameterTypes, reflectionArguments.getArguments(), i) ||
                        parameterTypes[i].getName().equals("java.lang.Object")) {
                    continue;
                }
                found = false;
            }
            if (found) {
                return m;
            }
        }
        return null;
    }

    /**
     * Get methods matching a name from the given class
     * by using this you are probably making one more iteration than you have to, but it's worth
     * the reduction in method complexity.
     * @param clazz the haystack to search
     * @param name the needle to find.
     * @return a list of methods that match the name
     */
    public static List<Method> getMethodsByName(Class<?> clazz, String name) {
        List<Method> methods = new ArrayList<>();
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) {
                methods.add(m);
            }
        }

        return methods;
    }

    /**
     * <p>Find a constructor matching the given parameters.</p>
     *
     * This method has side effects. The argumentTypes may end up being converted from a boxed to primitive
     * or vice verce
     *
     * @param clazz      the Class for which we need to find a constructor
     * @param argumentTypes the types of the parameters we are looking for.
     * @return a Constructor instance or null.
     */
    public static Constructor<?> findConstructor(Class<?> clazz, Class<?>[] argumentTypes, Object[] arguments) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = c.getParameterTypes();
            if (parameterTypes.length != argumentTypes.length) {
                continue;
            }
            boolean found = true;
            for (int i = 0; i < argumentTypes.length; i++) {
                if (matchArgumentVsParameter(argumentTypes, parameterTypes, arguments, i)) continue;
                found = false;
            }
            if (found) {
                return c;
            }
        }
        return null;
    }

    /**
     * <p>Determine if the ith parameter matches the ith argument</p>
     *
     * <p>This method does part of it's job through side effects, hence the reason that arrays are
     * passed as arguments instead of single values.</p>
     *
     * @param argumentTypes the array of types that we are trying to pass in as arguments
     * @param parameterTypes the array of types that the function is expecting as parameters
     * @param i the position in the array that we are looking at.
     *          arrays are used because the function operates via side effects.
     * @return true if a match has been found.
     */
    private static boolean matchArgumentVsParameter(Class<?>[] argumentTypes, Class<?>[] parameterTypes,
                                                    Object[] arguments, int i) {
        if (arguments.length < parameterTypes.length) {
            return false;
        }
        Class<?> parameterType = parameterTypes[i];
        if (arguments[i] == null || parameterType.isAssignableFrom(argumentTypes[i]) || parameterType.equals(argumentTypes[i])) {
            return true;
        }

        if (wrapperToPrimitive.get(parameterType) != null && wrapperToPrimitive.get(parameterType).equals(argumentTypes[i])) {
            argumentTypes[i] = wrapperToPrimitive.get(parameterType);
            return true;
        }
        if (primitiveToWrapper.get(parameterType) != null && primitiveToWrapper.get(parameterType).equals(argumentTypes[i])) {
            argumentTypes[i] = primitiveToWrapper.get(parameterType);
            return true;
        }

        if (parameterType.isAnnotationPresent(FunctionalInterface.class)) {
            for (Class<?> iface2 : argumentTypes[i].getInterfaces()) {
                if (iface2.isAnnotationPresent(FunctionalInterface.class)) {
                    dynamicProxy(argumentTypes, i, arguments);
                    return true;
                }
            }
            if (argumentTypes[i].isAnnotationPresent(FunctionalInterface.class)) {
                dynamicProxy(parameterTypes, i, arguments);
                return true;
            }
        }
        return false;
    }

    private static Class<?> getFunctionalInterface(Class<?> cls) {
        for (Class<?> iface : cls.getInterfaces()) {
            if (iface.isAnnotationPresent(FunctionalInterface.class)) {
                return iface;
            }
        }
        if (cls.isAnnotationPresent(FunctionalInterface.class)) {
            return cls;
        }
        return null;
    }

    public static Method findAccessibleMethod(Class<?> clazz, ReflectionArguments reflectionArguments) {
        // First check direct method lookup
        Method method = Reflect.findMethod(clazz, reflectionArguments);
        if (method != null) return method;

        // Search interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            method = Reflect.findMethod(iface, reflectionArguments);
            if (method != null) return method;

            // Try super-interfaces
            for (Class<?> superIface : iface.getInterfaces()) {
                method = findAccessibleMethod(superIface, reflectionArguments);
                if (method != null) return method;
            }
        }

        // Search superclass if no interface method found
        Class<?> superclass = clazz.getSuperclass();
        return superclass != null ? findAccessibleMethod(superclass, reflectionArguments) : null;
    }

    public static Method findPublicMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        // Try public interfaces first
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                Method method = iface.getMethod(methodName, paramTypes);
                if (Modifier.isPublic(method.getModifiers())) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // not a concern
            }
        }

        // Try superclass hierarchy
        Class<?> superclass = clazz.getSuperclass();
        while (superclass != null) {
            try {
                Method method = superclass.getMethod(methodName, paramTypes);
                if (Modifier.isPublic(method.getModifiers())) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // not a concern
            }
            superclass = superclass.getSuperclass();
        }

        return null;
    }

    public static Class<?> literalExpressionToClass(LiteralExpr lit) {
        if (lit.isBooleanLiteralExpr()) {
            return boolean.class;
        } else if (lit.isCharLiteralExpr()) {
            return char.class;
        } else if (lit.isDoubleLiteralExpr()) {
            return double.class;
        } else if (lit.isIntegerLiteralExpr()) {
            return int.class;
        } else if (lit.isLongLiteralExpr()) {
            return long.class;
        } else if (lit.isNullLiteralExpr()) {
            return null;
        } else if (lit.isStringLiteralExpr()) {
            return java.lang.String.class;
        }
        return null;
    }

    public static boolean isPrimitiveOrBoxed(Class<?> clazz) {
        return primitiveToWrapper.containsKey(clazz) || wrapperToPrimitive.containsKey(clazz);
    }

    public static boolean isPrimitiveOrBoxed(String type) {
        try {
            return primitiveToWrapper.containsKey(getComponentClass(type)) || wrapperToPrimitive.containsKey(getComponentClass(type));
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
