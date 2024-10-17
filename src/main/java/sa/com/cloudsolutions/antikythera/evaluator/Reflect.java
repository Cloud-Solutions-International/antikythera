package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class Reflect {

    /**
     * Build the suitable set of arguments for use with a reflective  method call
     * @param methodCall ObjectCreationExpr from java parser , which will be used as the basis for finding the
     *      *            method to be called and it's argument.
     * @param evaluator  the evaluator to use to evaluate the arguments if any
     * @return A ReflectionArguments object which contains all the information required to execute a method
     *          using reflection.
     * @throws AntikytheraException if something goes wrong with the parser related code
     * @throws ReflectiveOperationException if reflective oeprations fail
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
}
