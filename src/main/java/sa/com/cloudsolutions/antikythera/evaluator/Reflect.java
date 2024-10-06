package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import java.util.List;

public class Reflect {

    public static ReflectionArguments buildArguments(MethodCallExpr methodCall, Evaluator evaluator)
            throws AntikytheraException, ReflectiveOperationException {
        String methodName = methodCall.getNameAsString();
        List<Expression> arguments = methodCall.getArguments();
        Variable[] argValues = new Variable[arguments.size()];
        Class<?>[] paramTypes = new Class<?>[arguments.size()];
        Object[] args = new Object[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            argValues[i] = evaluator.evaluateExpression(arguments.get(i));
            Class<?> wrapperClass = argValues[i].getClazz() == null ? argValues[i].getValue().getClass() : argValues[i].getClazz();
            paramTypes[i] = wrapperClass;
            args[i] = argValues[i].getValue();
        }

        return new ReflectionArguments(methodName, args, paramTypes);
    }
}
