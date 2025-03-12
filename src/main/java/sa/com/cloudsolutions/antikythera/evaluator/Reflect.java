package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * for example Integer.class -> int.class
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
    public static ReflectionArguments buildArguments(MethodCallExpr methodCall, ExpressionEvaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        return buildArgumentsCommon(methodCall.getNameAsString(), methodCall.getArguments(), evaluator);
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
    public static ReflectionArguments buildArguments(ObjectCreationExpr oce, ExpressionEvaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        return buildArgumentsCommon(null, oce.getArguments(), evaluator);
    }

    private static ReflectionArguments buildArgumentsCommon(String methodName, List<Expression> arguments, ExpressionEvaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        Variable[] argValues = new Variable[arguments.size()];
        Class<?>[] paramTypes = new Class<?>[arguments.size()];
        Object[] args = new Object[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            argValues[i] = evaluator.evaluateExpression(arguments.get(i));
            if (argValues[i] != null) {
                args[i] = argValues[i].getValue();
                if (argValues[i].getClazz() != null ) {
                    paramTypes[i] = argValues[i].getClazz();
                }
                else if (args[i] != null) {
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
        }

        return new ReflectionArguments(methodName, args, paramTypes);
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
            default -> Class.forName(elementType);
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

    public static Variable variableFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }

        return switch (qualifiedName) {
            case "java.util.List", "java.util.ArrayList" -> {
                Variable v = new Variable(new ArrayList<>());
                v.setInitializer(new ObjectCreationExpr(null,
                        new ClassOrInterfaceType("java.util.ArrayList"), NodeList.nodeList()));
                yield v;
            }
            case "java.util.Map", "java.util.HashMap" -> {
                Variable v = new Variable(new HashMap<>());
                v.setInitializer(new ObjectCreationExpr(null,
                        new ClassOrInterfaceType("java.util.HashMap"), NodeList.nodeList()));
                yield v;
            }
            case "java.util.TreeMap" -> {
                Variable v = new Variable(new TreeMap<>());
                v.setInitializer(new ObjectCreationExpr(null,
                        new ClassOrInterfaceType("java.util.TreeMap"), NodeList.nodeList()));
                yield v;
            }
            case "java.util.Set", "java.util.HashSet" -> {
                Variable v = new Variable(new HashSet<>());
                v.setInitializer(new ObjectCreationExpr(null,
                        new ClassOrInterfaceType("java.util.HashSet"), NodeList.nodeList()));
                yield v;
            }
            case "java.util.TreeSet" -> {
                Variable v = new Variable(new TreeSet<>());
                v.setInitializer(new ObjectCreationExpr(null,
                        new ClassOrInterfaceType("java.util.TreeSet"), NodeList.nodeList()));
                yield v;
            }
            case "java.util.Optional" -> {
                Variable v = new Variable(Optional.empty());
                v.setInitializer(new ObjectCreationExpr(null,
                        new ClassOrInterfaceType("java.util.Optional.empty()"), NodeList.nodeList()));
                yield v;
            }
            case "Boolean", "boolean" -> {
                Variable v = new Variable(false);
                v.setInitializer(new ObjectCreationExpr(null, new ClassOrInterfaceType("Boolean"), NodeList.nodeList(new StringLiteralExpr("false"))));
                yield v;
            }
            case "float", "Float", "double", DOUBLE -> {
                Variable v = new Variable(0.0);
                v.setInitializer(new ObjectCreationExpr(null, new ClassOrInterfaceType(DOUBLE), NodeList.nodeList(new StringLiteralExpr("0.0"))));
                yield v;
            }
            case INTEGER, "int" -> {
                Variable v = new Variable(0);
                v.setInitializer(new ObjectCreationExpr(null, new ClassOrInterfaceType(INTEGER), NodeList.nodeList(new StringLiteralExpr("0"))));
                yield v;
            }
            case "Long", "long" , "java.lang.Long" -> {
                Variable v = new Variable(-100L);
                v.setInitializer(new ObjectCreationExpr(null, new ClassOrInterfaceType("Long"), NodeList.nodeList(new StringLiteralExpr("-100"))));
                yield v;
            }
            case "String", "java.lang.String" -> {
                Variable v = new Variable("Ibuprofen");
                v.setInitializer(new ObjectCreationExpr(null, new ClassOrInterfaceType("String"), NodeList.nodeList(new StringLiteralExpr("Ibuprofen"))));
                yield v;
            }
            default -> new Variable(null);
        };
    }


    /**
     * Finds a matching method using parameters.
     *
     * This function has side effects. The paramTypes may end up being converted from a boxed to
     * primitive or vice versa. This is because the Variable class that we use has an Object
     * representing the value. Whereas some of the methods have parameters that require a primitive
     * type. Hence the conversion needs to happen.
     *
     * @param clazz the class on which we need to match the method name
     * @param methodName the name of the method to find
     * @param paramTypes and array or parameter types.
     * @return a Method instance or null.
     */
    public static Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {

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


    private static boolean findMatch(Class<?>[] paramTypes, Class<?>[] types, int i) {
        if (types[i].isAssignableFrom(paramTypes[i])) {
            return true;
        }
        if (types[i].equals(paramTypes[i])) {
            return true;
        }
        if (wrapperToPrimitive.get(types[i]) != null && wrapperToPrimitive.get(types[i]).equals(paramTypes[i])) {
            paramTypes[i] = wrapperToPrimitive.get(types[i]);
            return true;
        }
        if(primitiveToWrapper.get(types[i]) != null && primitiveToWrapper.get(types[i]).equals(paramTypes[i])) {
            paramTypes[i] = primitiveToWrapper.get(types[i]);
            return true;
        }
        return false;
    }


    public static Object[] buildObjects(ReflectionArguments reflectionArguments, Method method) {
        return method.getParameterTypes().length == 1 &&
                method.getParameterTypes()[0].equals(Object[].class) ?
                new Object[]{reflectionArguments.getArgs()} :
                reflectionArguments.getArgs();
    }

}
