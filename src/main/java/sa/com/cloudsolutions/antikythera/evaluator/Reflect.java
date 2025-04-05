package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
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
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class Reflect {
    public static final String PRIMITIVE_BOOLEAN = "boolean";
    public static final String PRIMITIVE_FLOAT = "float";
    public static final String PRIMITIVE_DOUBLE = "double";
    public static final String INTEGER = "Integer";
    public static final String DOUBLE = "Double";
    public static final String FLOAT = "Float";
    /**
     * Keeps a map of wrapper types to their primitive counterpart
     * for example : Integer.class -> int.class
     */
    static Map<Class<?>, Class<?>> wrapperToPrimitive = new HashMap<>();
    /**
     * The opposite of wrapperToPrimitive
     * here int.class -> Integer.class
     */
    static Map<Class<?>, Class<?>> primitiveToWrapper = new HashMap<>();

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
    }

    private Reflect() {
    }

    /**
     * Build the suitable set of arguments for use with a reflective  method call
     *
     * @param methodCall ObjectCreationExpr from java parser , which will be used as the basis for finding the
     *                   *            method to be called along with it's arguments.
     * @param evaluator  the evaluator to use to evaluate the arguments if any
     * @return A ReflectionArguments object which contains all the information required to execute a method
     * using reflection.
     * @throws AntikytheraException         if something goes wrong with the parser related code
     * @throws ReflectiveOperationException if reflective operations fail
     */
    public static ReflectionArguments buildArguments(MethodCallExpr methodCall, Evaluator evaluator, Variable scope)
            throws AntikytheraException, ReflectiveOperationException {
        return buildArgumentsCommon(methodCall.getNameAsString(), methodCall.getArguments(), evaluator, scope);
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
        return buildArgumentsCommon(null, oce.getArguments(), evaluator, scope);
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
                    String className = arguments.get(0).calculateResolvedType().describe();
                    className = primitiveToWrapper(className);
                    argumentTypes[i] = Class.forName(className);
                } catch (UnsolvedSymbolException|ReflectiveOperationException us) {
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
        }
    }

    public static String primitiveToWrapper(String className) {
        return switch (className) {
            case PRIMITIVE_BOOLEAN -> "java.lang.Boolean";
            case "int" -> "java.lang.Integer";
            case "long" -> Long.class.getName();
            case PRIMITIVE_FLOAT -> "java.lang.Float";
            case PRIMITIVE_DOUBLE -> "java.lang.Double";
            case "char" -> "java.lang.Character";
            default -> className;
        };
    }

    public static Class<?> getComponentClass(String elementType) throws ClassNotFoundException {
        return switch (elementType) {
            case "int" -> int.class;
            case INTEGER -> Integer.class;
            case PRIMITIVE_DOUBLE -> double.class;
            case DOUBLE -> Double.class;
            case PRIMITIVE_BOOLEAN -> boolean.class;
            case "Boolean" -> Boolean.class;
            case "long" -> long.class;
            case "Long" -> Long.class;
            case PRIMITIVE_FLOAT -> float.class;
            case FLOAT -> Float.class;
            case "short" -> short.class;
            case "Short" -> Short.class;
            case "byte" -> byte.class;
            case "Byte" -> Byte.class;
            case "char" -> char.class;
            case "Character" -> Character.class;
            default -> AbstractCompiler.loadClass(elementType);
        };
    }

    public static Type getComponentType(Class<?> clazz) {
        return switch (clazz.getName()) {
            case "int", "java.lang.Integer" -> PrimitiveType.intType();
            case PRIMITIVE_DOUBLE, DOUBLE, "java.lang.Double" -> PrimitiveType.doubleType();
            case PRIMITIVE_BOOLEAN, "java.lang.Boolean" -> PrimitiveType.booleanType();
            case "long", "java.lang.Long", "java.lang.BigDecimal" -> PrimitiveType.longType();
            case PRIMITIVE_FLOAT, FLOAT, "java.lang.Float" -> PrimitiveType.floatType();
            case "short", "java.lang.Short" -> PrimitiveType.shortType();
            case "byte", "java.lang.Byte" -> PrimitiveType.byteType();
            case "char", "java.lang.Character" -> PrimitiveType.charType();
            case "java.lang.String" -> new ClassOrInterfaceType().setName("String");
            default -> null;
        };
    }

    public static Expression createLiteralExpression(Object value) {
        if (value == null) {
            return new NullLiteralExpr();
        }

        return switch (value.getClass().getSimpleName()) {
            case INTEGER, "Long" -> new IntegerLiteralExpr(value.toString());
            case DOUBLE, FLOAT -> new DoubleLiteralExpr(value.toString());
            case "Boolean" -> new BooleanLiteralExpr(Boolean.parseBoolean(value.toString()));
            case "Character" -> new CharLiteralExpr(value.toString().charAt(0));
            default -> new StringLiteralExpr(value.toString());
        };
    }

    public static Object getDefault(String elementType) {
        return switch (elementType) {
            case "int" -> 0;
            case PRIMITIVE_DOUBLE -> 0.0;
            case PRIMITIVE_BOOLEAN -> false;
            case "long" -> 0L;
            case PRIMITIVE_FLOAT -> 0.0f;
            case "short" -> Short.valueOf("0");
            case "byte", "char" -> 0x0;
            default -> null;
        };
    }

    public static Object getDefault(Class<?> returnType) {
        if (returnType.equals(String.class)) return "0";
        if (returnType.equals(Integer.class) || returnType.equals(int.class)) return 0;
        if (returnType.equals(Long.class) || returnType.equals(long.class)) return 0L;
        if (returnType.equals(Boolean.class) || returnType.equals(boolean.class)) return false;
        if (returnType.equals(Double.class) || returnType.equals(double.class)) return 0.0;
        if (returnType.equals(Float.class) || returnType.equals(float.class)) return 0.0f;
        if (returnType.equals(Byte.class) || returnType.equals(byte.class)) return (byte) 0;
        if (returnType.equals(Short.class) || returnType.equals(short.class)) return (short) 0;
        if (returnType.equals(Character.class) || returnType.equals(char.class)) return '\0';

        // Handle common collections
        if (returnType.equals(List.class)) return new ArrayList<>();
        if (returnType.equals(Map.class)) return new HashMap<>();
        if (returnType.equals(Set.class)) return new HashSet<>();
        return null;
    }

    private static Variable createVariable(Object initialValue, String typeName, String stringValue) {
        Variable v = new Variable(initialValue);

        switch (typeName) {
            case "Long", DOUBLE, INTEGER, FLOAT, "Boolean" -> {
                Expression scope = new NameExpr(typeName);
                Expression mce = new MethodCallExpr(scope, "valueOf")
                    .addArgument(new StringLiteralExpr(initialValue.toString()));
                v.setInitializer(mce);
            }
            default -> {
                ObjectCreationExpr expr = new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName(typeName));
                if (stringValue != null) {
                    expr.setArguments(NodeList.nodeList(new StringLiteralExpr(stringValue)));
                } else {
                    expr.setArguments(NodeList.nodeList());
                }
                v.setInitializer(expr);
            }
        }
        return v;
    }

    public static Variable variableFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }

        return switch (qualifiedName) {
            case "List", "java.util.List", "java.util.ArrayList" ->
                    createVariable(new ArrayList<>(), "java.util.ArrayList", null);

            case "Map", "java.util.Map", "java.util.HashMap" ->
                    createVariable(new HashMap<>(), "java.util.HashMap", null);

            case "java.util.TreeMap" -> createVariable(new TreeMap<>(), "java.util.TreeMap", null);

            case "Set", "java.util.Set", "java.util.HashSet" ->
                    createVariable(new HashSet<>(), "java.util.HashSet", null);

            case "java.util.TreeSet" -> createVariable(new TreeSet<>(), "java.util.TreeSet", null);

            case "java.util.Optional" -> createVariable(Optional.empty(), "java.util.Optional", null);

            case "Boolean", PRIMITIVE_BOOLEAN , "java.lang.Boolean" -> createVariable(false, "Boolean", "false");

            case PRIMITIVE_FLOAT, FLOAT, PRIMITIVE_DOUBLE, DOUBLE, "java.lang.Double" -> createVariable(0.0, DOUBLE, "0.0");

            case INTEGER, "int", "java.lang.Integer" -> createVariable(0, INTEGER, "0");

            case "Long", "long", "java.lang.Long" -> createVariable(-100L, "Long", "-100");

            case "String", "java.lang.String" -> createVariable("Ibuprofen", "String", "Ibuprofen");

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
        Method method = Reflect.findMethod(clazz, reflectionArguments);
        if (method != null) return method;

        // Search interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            method = Reflect.findMethod(iface, reflectionArguments);
            if (method != null) return method;
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

}
