package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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

public class Reflect {
    /**
     * Keeps a map of wrapper types to their primitive counter part
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
     *      *            method to be called and it's argument.
     * @param evaluator  the evaluator to use to evaluate the arguments if any
     * @return A ReflectionArguments object which contains all the information required to execute a method
     *          using reflection.
     * @throws AntikytheraException if something goes wrong with the parser related code
     * @throws ReflectiveOperationException if reflective operations fail
     */
    public static ReflectionArguments buildArguments(MethodCallExpr methodCall, Evaluator evaluator)
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
    public static ReflectionArguments buildArguments(ObjectCreationExpr oce, Evaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        return buildArgumentsCommon(null, oce.getArguments(), evaluator);
    }

    private static ReflectionArguments buildArgumentsCommon(String methodName, List<Expression> arguments, Evaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        Variable[] argValues = new Variable[arguments.size()];
        Class<?>[] paramTypes = new Class<?>[arguments.size()];
        Object[] args = new Object[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            argValues[i] = evaluator.evaluateExpression(arguments.get(i));
            if (argValues[i] != null) {
                Class<?> wrapperClass = argValues[i].getClazz() == null ? argValues[i].getValue().getClass() : argValues[i].getClazz();
                paramTypes[i] = wrapperClass;
                args[i] = argValues[i].getValue();
            } else {
                try {
                    String className = arguments.get(0).calculateResolvedType().describe();
                    className = switch (className) {
                        case "boolean" -> "java.lang.Boolean";
                        case "int" -> "java.lang.Integer";
                        case "long" -> "java.lang.Long";
                        case "float" -> "java.lang.Float";
                        case "double" -> "java.lang.Double";
                        case "char" -> "java.lang.Character";
                        default -> className;
                    };
                    paramTypes[i] = Class.forName(className);
                } catch (UnsolvedSymbolException us) {
                    paramTypes[i] = Object.class;
                }
            }
        }

        return new ReflectionArguments(methodName, args, paramTypes);
    }

    public static Class<?> getComponentClass(String elementType) throws ClassNotFoundException {
        return switch (elementType) {
            case "int" -> int.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "short" -> short.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            default -> Class.forName(elementType);
        };
    }

    public static Object getDefault(String elementType)  {
        return switch (elementType) {
            case "int" -> 0;
            case "double" -> 0.0;
            case "boolean" -> false;
            case "long" -> 0L;
            case "float" -> 0.0f;
            case "short" -> Short.valueOf("0");
            case "byte", "char" -> 0x0;
            default -> null;
        };
    }

    public static Variable variableFactory(String qualifiedName) {
        return switch (qualifiedName) {
            case "java.util.List" ->  new Variable(new ArrayList<>());
            case "java.util.Map" ->  new Variable(new HashMap<>());
            case "java.util.Set" ->  new Variable(new HashSet<>());
            case "java.util.Optional" ->  new Variable(Optional.empty());
            case "java.lang.Long" ->  new Variable(0L);
            default -> null;
        };
    }


    /**
     * Finds a matching method using parameters.
     *
     * This function has side effects. The paramTypes may end up being converted from a boxed to
     * primitive or wise versa. This is because the Variable class that we use has an Object
     * representing the value. Where as some of the methods have parameters that require a primitive
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
                if (types.length != paramTypes.length) {
                    continue;
                }
                boolean found = true;
                for(int i = 0 ; i < paramTypes.length ; i++) {
                    if (findMatch(paramTypes, types, i)) continue;
                    if(types[i].getName().equals("java.lang.Object")) {
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

}
