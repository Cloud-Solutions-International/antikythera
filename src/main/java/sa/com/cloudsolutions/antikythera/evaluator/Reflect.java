package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.util.List;

public class Reflect {

    public static ReflectionArguments buildArguments(MethodCallExpr methodCall, Evaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        return buildArgumentsCommon(methodCall.getNameAsString(), methodCall.getArguments(), evaluator);
    }

    public static ReflectionArguments buildArguments(ObjectCreationExpr methodCall, Evaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        return buildArgumentsCommon(null, methodCall.getArguments(), evaluator);
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
                    paramTypes[i] = Class.forName(className);
                } catch (UnsolvedSymbolException us) {
                    paramTypes[i] = Object.class;
                }
            }
        }

        return new ReflectionArguments(methodName, args, paramTypes);
    }
}
