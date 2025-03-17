package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Method;
import java.util.Optional;

public class FunctionalConverter {

    /**
     * Converts a MethodReferenceExpr to a LambdaExpr
     */
    public static LambdaExpr convertToLambda(MethodReferenceExpr methodRef, Variable outerScope) {
        NodeList<Parameter> parameters = new NodeList<>();
        parameters.add(new Parameter(new ClassOrInterfaceType().setName("Object"), "arg"));

        MethodCallExpr call = createMethodCallExpression(methodRef);
        BlockStmt body = new BlockStmt();

        if (outerScope != null) {
            Node parent = methodRef.getParentNode().orElseThrow();
            if (parent instanceof MethodCallExpr mce) {
                searchForFunctionals(methodRef, outerScope, mce, body, call);
            }
        }
        else {
            body.addStatement(new ReturnStmt(call));
            call.setScope(new NameExpr("arg"));
        }

        LambdaExpr lambda = new LambdaExpr(parameters, body);
        lambda.setParentNode(methodRef.getParentNode().get());
        return lambda;
    }

    private static void searchForFunctionals(MethodReferenceExpr methodRef, Variable outerScope, MethodCallExpr mce, BlockStmt body, MethodCallExpr call) {

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
            for (Method m : Reflect.getMethodsByName(clazz, name)) {
                if (m.getParameterCount() == mce.getArguments().size()) {
                    Class<?> param = m.getParameterTypes()[pos];
                    if (param.isInterface() && param.isAnnotationPresent(FunctionalInterface.class)) {
                        Method functionalMethod = getFunctionalInterfaceMethod(param);
                        if (functionalMethod != null && functionalMethod.getReturnType() != void.class) {
                            body.addStatement(new ReturnStmt(call));
                        } else {
                            body.addStatement(call);
                        }
                        break;
                    }
                }
            }
        }
    }

    private static MethodCallExpr createMethodCallExpression(MethodReferenceExpr methodRef) {
        MethodCallExpr call = new MethodCallExpr();
        Expression scope = methodRef.getScope();
        call.setName(methodRef.getIdentifier());

        if (scope != null && scope.isTypeExpr()) {
            TypeExpr typeExpr = scope.asTypeExpr();
            if (typeExpr.toString().startsWith("System")) {
                call.setScope(scope);
                call.addArgument(new NameExpr("arg"));
            }
            else {
                call.setScope(new NameExpr("arg"));
                CompilationUnit cu = methodRef.findCompilationUnit().orElseThrow();
                String fqn = AbstractCompiler.findFullyQualifiedName(cu, typeExpr.toString());
                if (fqn != null) {
                    CompilationUnit typeCu = AntikytheraRunTime.getCompilationUnit(fqn);
                    if (typeCu != null) {
                        Optional<MethodDeclaration> md = typeCu.findFirst(MethodDeclaration.class,
                                m -> m.getNameAsString().equals(methodRef.getIdentifier()));
                        if (md.isPresent()) {
                            for(int i = 0 ; i < md.get().getParameters().size() ; i++) {
                                call.addArgument(new NameExpr("arg" + i));
                            }
                        }
                    }
                }
            }
        }
        return call;
    }

    private static Method getFunctionalInterfaceMethod(Class<?> functionalInterface) {
        return java.util.Arrays.stream(functionalInterface.getMethods())
                .filter(m -> !m.isDefault() && !m.getDeclaringClass().equals(Object.class))
                .findFirst()
                .orElse(null);
    }
}
