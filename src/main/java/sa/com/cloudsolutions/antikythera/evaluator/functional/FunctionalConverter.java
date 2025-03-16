package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.lang.reflect.Method;

public class FunctionalConverter {

    /**
     * Converts a MethodReferenceExpr to a LambdaExpr
     */
    public static LambdaExpr convertToLambda(MethodReferenceExpr methodRef, Variable outerScope) {
        String methodName = methodRef.getIdentifier();
        Expression methodScope = methodRef.getScope();

        NodeList<Parameter> parameters = new NodeList<>();
        parameters.add(new Parameter(new ClassOrInterfaceType("Object"), "arg"));

        MethodCallExpr call = new MethodCallExpr();
        call.setName(methodName);
        call.setScope(methodScope);
        call.addArgument(new NameExpr("arg"));

        BlockStmt body = new BlockStmt();

        if (outerScope != null) {
            methodRef.getParentNode().ifPresent(parent -> {
                if (parent instanceof MethodCallExpr mce) {
                    String name = mce.getNameAsString();
                    int pos = -1;
                    for (int i = 0 ; i < mce.getArguments().size() ; i++) {
                        if (mce.getArguments().get(i).equals(methodRef)) {
                            pos = i;
                        }
                    }
                    if (outerScope.getValue() instanceof Evaluator eval) {

                    }
                    else {
                        Class<?> clazz = outerScope.getClazz();
                        for (Method m : clazz.getMethods()) {
                            if (m.getName().equals(name) && m.getParameterCount() == mce.getArguments().size()) {
                                Class<?> param = m.getParameterTypes()[pos];
                                if (param.isInterface() && param.isAnnotationPresent(FunctionalInterface.class)) {
                                    Method functionalMethod = getFunctionalInterfaceMethod(param);
                                    if (functionalMethod != null && functionalMethod.getReturnType() != void.class) {
                                        body.addStatement(new ReturnStmt(call));
                                    } else {
                                        body.addStatement(call);
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
        else {
            body.addStatement(new ReturnStmt(call));
        }

        return new LambdaExpr(parameters, body);
    }

    private static Method getFunctionalInterfaceMethod(Class<?> functionalInterface) {
        return java.util.Arrays.stream(functionalInterface.getMethods())
                .filter(m -> m.isDefault() == false && !m.getDeclaringClass().equals(Object.class))
                .findFirst()
                .orElse(null);
    }
}
