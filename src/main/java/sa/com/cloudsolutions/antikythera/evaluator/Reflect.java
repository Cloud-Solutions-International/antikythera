package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.functional.FunctionalConverter;
import sa.com.cloudsolutions.antikythera.evaluator.functional.LambdaInvocationHandler;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
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
    public static final String BOOLEAN = "boolean";
    public static final String FLOAT = "float";
    public static final String PRIMITIVE_DOUBLE = "double";
    public static final String INTEGER = "Integer";
    public static final String DOUBLE = "Double";
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

        for(Map.Entry<Class<?>, Class<?>> entry : wrapperToPrimitive.entrySet()) {
            primitiveToWrapper.put(entry.getValue(), entry.getKey());
        }
    }

    private Reflect() {}

    /**
     * Build the suitable set of arguments for use with a reflective  method call
     * @param methodCall ObjectCreationExpr from java parser , which will be used as the basis for finding the
     *      *            method to be called along with it's arguments.
     * @param evaluator  the evaluator to use to evaluate the arguments if any
     * @return A ReflectionArguments object which contains all the information required to execute a method
     *          using reflection.
     * @throws AntikytheraException if something goes wrong with the parser related code
     * @throws ReflectiveOperationException if reflective operations fail
     */
    public static ReflectionArguments buildArguments(MethodCallExpr methodCall, Evaluator evaluator, Variable scope)
            throws AntikytheraException, ReflectiveOperationException {
        return buildArgumentsCommon(methodCall.getNameAsString(), methodCall.getArguments(), evaluator, scope);
    }

    /**
     * Build the set of arguments to be used with instantiating a class using reflection.
     * @param oce ObjectCreationExpr from java parser , which will be used as the basis for finding the right
     *            constructor to use.
     * @param evaluator the evaluator to use to evaluate the arguments if any
     * @return A ReflectionArguments object which contains all the information required to execute a method
     *      *          using reflection.
     * @throws AntikytheraException if the reflection arguments cannot be solved
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
        Class<?>[] paramTypes = new Class<?>[arguments.size()];
        Object[] args = new Object[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            Expression expr = arguments.get(i);
            if (expr.isMethodReferenceExpr()) {
                expr = FunctionalConverter.convertToLambda(expr.asMethodReferenceExpr());
            }
            if (expr.isLambdaExpr()) {
                LambdaExpr lambdaExpr = expr.asLambdaExpr();
                FPEvaluator<?> eval = FPEvaluator.create(lambdaExpr, evaluator, scope);

                Variable v = new Variable(eval);
                v.setType(eval.getType());
                argValues[i] = v;
            }
            else {
                argValues[i] = evaluator.evaluateExpression(expr);
            }

            if (argValues[i] != null) {
                args[i] = argValues[i].getValue();
                if (argValues[i].getClazz() != null) {
                    paramTypes[i] = argValues[i].getClazz();
                } else if (args[i] != null) {
                    paramTypes[i] = argValues[i].getValue().getClass();
                }
            } else {
                try {
                    String className = arguments.get(0).calculateResolvedType().describe();
                    className = primitiveToWrapper(className);
                    paramTypes[i] = Class.forName(className);
                } catch (UnsolvedSymbolException us) {
                    paramTypes[i] = Object.class;
                }
            }

            Class<?> functional = getFunctionalInterface(paramTypes[i]);

            if (args[i] instanceof FPEvaluator<?> && functional != null) {
                Object proxy = Proxy.newProxyInstance(
                        paramTypes[i].getClassLoader(),
                        new Class<?>[] { functional },
                        new FunctionalInvocationHandler((FPEvaluator<?>) args[i])
                );
                args[i] = proxy;
                paramTypes[i] = functional;
            }

        }

        ReflectionArguments reflectionArguments = new ReflectionArguments(methodName, args, paramTypes);
        reflectionArguments.setScope(scope);
        reflectionArguments.setEnclosure(evaluator);
        return reflectionArguments;
    }


    private static class FunctionalInvocationHandler implements InvocationHandler {
        private final FPEvaluator<?> evaluator;

        FunctionalInvocationHandler(FPEvaluator<?> evaluator) {
            this.evaluator = evaluator;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            // Delegate the method call to the FPEvaluator
            return evaluator.executeLocalMethod(createMethodCall(method, args));
        }

        private MethodCallExpr createMethodCall(Method method, Object[] args) {
            MethodCallExpr call = new MethodCallExpr();
            call.setName(method.getName());
            if (args != null) {
                for (Object arg : args) {
                    call.addArgument(arg.toString());
                }
            }
            return call;
        }
    }

    public static String primitiveToWrapper(String className) {
        return switch (className) {
            case BOOLEAN -> "java.lang.Boolean";
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case FLOAT -> "java.lang.Float";
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
            case BOOLEAN -> boolean.class;
            case "Boolean" -> Boolean.class;
            case "long" -> long.class;
            case "Long" -> Long.class;
            case FLOAT -> float.class;
            case "Float" -> Float.class;
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
            case "int","java.lang.Integer" -> PrimitiveType.intType();
            case "double","java.lang.Double" -> PrimitiveType.doubleType();
            case "boolean","java.lang.Boolean" -> PrimitiveType.booleanType();
            case "long","java.lang.Long","java.lang.BigDecimal" -> PrimitiveType.longType();
            case "float","java.lang.Float" -> PrimitiveType.floatType();
            case "short","java.lang.Short" -> PrimitiveType.shortType();
            case "byte","java.lang.Byte" -> PrimitiveType.byteType();
            case "char","java.lang.Character" -> PrimitiveType.charType();
            case "java.lang.String" -> new ClassOrInterfaceType("String");
            default -> null;
        };
    }

    public static Object getDefault(String elementType)  {
        return switch (elementType) {
            case "int" -> 0;
            case PRIMITIVE_DOUBLE -> 0.0;
            case BOOLEAN -> false;
            case "long" -> 0L;
            case FLOAT -> 0.0f;
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
        if (returnType.equals(Byte.class) || returnType.equals(byte.class)) return (byte)0;
        if (returnType.equals(Short.class) || returnType.equals(short.class)) return (short)0;
        if (returnType.equals(Character.class) || returnType.equals(char.class)) return '\0';

        // Handle common collections
        if (returnType.equals(List.class)) return new ArrayList<>();
        if (returnType.equals(Map.class)) return new HashMap<>();
        if (returnType.equals(Set.class)) return new HashSet<>();
        return null;
    }

    private static Variable createVariable(Object initialValue, String typeName, String stringValue) {
        Variable v = new Variable(initialValue);
        ObjectCreationExpr expr = new ObjectCreationExpr()
            .setType(new ClassOrInterfaceType().setName(typeName));

        if (stringValue != null) {
            expr.setArguments(NodeList.nodeList(new StringLiteralExpr(stringValue)));
        } else {
            expr.setArguments(NodeList.nodeList());
        }

        v.setInitializer(expr);
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

            case "java.util.TreeMap" ->
                createVariable(new TreeMap<>(), "java.util.TreeMap", null);

            case "Set", "java.util.Set", "java.util.HashSet" ->
                createVariable(new HashSet<>(), "java.util.HashSet", null);

            case "java.util.TreeSet" ->
                createVariable(new TreeSet<>(), "java.util.TreeSet", null);

            case "java.util.Optional" ->
                createVariable(Optional.empty(), "java.util.Optional", null);

            case "Boolean", "boolean" ->
                createVariable(false, "Boolean", "false");

            case "float", "Float", "double", DOUBLE ->
                createVariable(0.0, DOUBLE, "0.0");

            case INTEGER, "int" ->
                createVariable(0, INTEGER, "0");

            case "Long", "long", "java.lang.Long" ->
                createVariable(-100L, "Long", "-100");

            case "String", "java.lang.String" ->
                createVariable("Ibuprofen", "String", "Ibuprofen");

            default -> new Variable(null);
        };
    }
    /**
     * Finds a matching method using parameters.
     *
     * This function has side effects. The paramTypes in reflectionArguments may end up being
     * converted from a boxed to primitive or vice versa.
     * This is because the Variable class that we use has an Object representing the value.
     * Whereas some of the methods have parameters that require a primitive type.
     * Hence, the conversion needs to happen.
     *
     * @param clazz the class on which we need to match the method name
     * @return a Method instance or null.
     */
    public static Method findMethod(Class<?> clazz, ReflectionArguments reflectionArguments) {
        String methodName = reflectionArguments.getMethodName();
        Class<?>[] paramTypes = reflectionArguments.getParamTypes();

        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                Class<?>[] types = m.getParameterTypes();
                if(types.length == 1 && types[0].equals(Object[].class)) {
                    return m;
                }
                if (paramTypes == null || types.length != paramTypes.length) {
                    continue;
                }
                boolean found = true;
                for (int i = 0 ; i < paramTypes.length ; i++) {
                    if (findMatch(paramTypes, types, i) || types[i].getName().equals("java.lang.Object")) {
                        continue;
                    }
                    found = false;
                }
                if (found) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Find a constructor matching the given parameters.
     *
     * This method has side effects. The paramTypes may end up being converted from a boxed to primitive
     * or vice verce
     *
     * @param clazz the Class for which we need to find a constructor
     * @param paramTypes the types of the parameters we are looking for.
     * @return a Constructor instance or null.
     */
    public static Constructor<?> findConstructor(Class<?> clazz, Class<?>[] paramTypes) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            Class<?>[] types = c.getParameterTypes();
            if (types.length != paramTypes.length) {
                continue;
            }
            boolean found = true;
            for(int i = 0 ; i < paramTypes.length ; i++) {
                if (findMatch(paramTypes, types, i)) continue;
                found = false;
            }
            if (found) {
                return c;
            }
        }
        return null;
    }

    public static Class<?> wrapperToPrimitive(Class<?> clazz) {
        return wrapperToPrimitive.getOrDefault(clazz, null);
    }

    public static Class<?> primitiveToWrapper(Class<?> clazz) {
        return primitiveToWrapper.getOrDefault(clazz, null);
    }


    /**
     * Determine if parameters match.
     * This method does part of it's job through side effects, hence the reason that arrays are
     * passed as arguments instead of single values.
     *
     * @param paramTypes
     * @param types
     * @param i
     * @return
     */
    private static boolean findMatch(Class<?>[] paramTypes, Class<?>[] types, int i) {
        Class<?> argumentType = types[i];
        if (argumentType.isAssignableFrom(paramTypes[i])) {
            return true;
        }
        if (argumentType.equals(paramTypes[i])) {
            return true;
        }
        if (wrapperToPrimitive.get(argumentType) != null && wrapperToPrimitive.get(argumentType).equals(paramTypes[i])) {
            paramTypes[i] = wrapperToPrimitive.get(argumentType);
            return true;
        }
        if(primitiveToWrapper.get(argumentType) != null && primitiveToWrapper.get(argumentType).equals(paramTypes[i])) {
            paramTypes[i] = primitiveToWrapper.get(argumentType);
            return true;
        }

        for (Class<?> iface : argumentType.getInterfaces()) {
            for (Class<?> iface2 : paramTypes[i].getInterfaces()) {
                if (iface.equals(iface2))
                {
                    return iface.isAnnotationPresent(FunctionalInterface.class) && iface2.isAnnotationPresent(FunctionalInterface.class);
                }
            }
        }
        return false;
    }

    private static Class<?> getFunctionalInterface(Class cls) {
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

    public static Object[] buildObjects(ReflectionArguments reflectionArguments, Method method) {
        return method.getParameterTypes().length == 1 &&
                method.getParameterTypes()[0].equals(Object[].class) ?
                new Object[]{reflectionArguments.getArgs()} :
                reflectionArguments.getArgs();
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
            } catch (NoSuchMethodException ignored) {}
        }

        // Try superclass hierarchy
        Class<?> superclass = clazz.getSuperclass();
        while (superclass != null) {
            try {
                Method method = superclass.getMethod(methodName, paramTypes);
                if (Modifier.isPublic(method.getModifiers())) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {}
            superclass = superclass.getSuperclass();
        }

        return null;
    }

}
