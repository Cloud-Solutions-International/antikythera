package sa.com.cloudsolutions.antikythera.evaluator.functional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public class FunctionalConverter {

    private FunctionalConverter() {}

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
            } else if (parent instanceof VariableDeclarator vd) {
                Type t = vd.getType();
                if (t.isClassOrInterfaceType()) {
                    ClassOrInterfaceType ctype = t.asClassOrInterfaceType();
                    Optional<NodeList<Type>> generics = ctype.getTypeArguments();
                    generics.ifPresent(types -> parameters.set(0, new Parameter(types.get(0), "arg")));
                }
                body.addStatement(new ReturnStmt(call));
            }
        }
        else {
            body.addStatement(new ReturnStmt(call));
            call.setScope(new NameExpr("arg"));
        }

        LambdaExpr lambda = new LambdaExpr(parameters, body);
        lambda.setParentNode(methodRef.getParentNode().orElseThrow());
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

    private static MethodCallExpr createMethodCallExpression(MethodReferenceExpr methodRef) {
        MethodCallExpr call = new MethodCallExpr();
        Expression scope = methodRef.getScope();
        call.setName(methodRef.getIdentifier());

        if (scope != null && scope.isTypeExpr()) {
            TypeExpr typeExpr = scope.asTypeExpr();
            if (typeExpr.toString().startsWith("System")) {
                call.setScope(scope);
                call.addArgument(new NameExpr("arg"));
                return call;
            }

            createMethodCallExpressionArguments(methodRef, typeExpr, call);

        } else {
            // For instance method references like Person::getId, set scope to 'arg'
            call.setScope(new NameExpr("arg"));
        }
        return call;
    }

    private static void createMethodCallExpressionArguments(MethodReferenceExpr methodRef, TypeExpr typeExpr, MethodCallExpr call) {
        CompilationUnit cu = methodRef.findCompilationUnit().orElseThrow();
        TypeWrapper typeWrapper = AbstractCompiler.findType(cu, typeExpr.toString());

        if (typeWrapper == null)
        {
            return;
        }
        if (typeWrapper.getType() == null) {
            try {
                Method m = typeWrapper.getClazz().getDeclaredMethod(methodRef.getIdentifier(), Object.class);
                if (Modifier.isStatic(m.getModifiers())) {
                    call.setScope(methodRef.getScope());
                }
                else {
                    call.setScope(new NameExpr("arg"));
                }
                addArguments(m.getParameterCount(), call);
            } catch (NoSuchMethodException e) {
                throw new AntikytheraException(e);
            }
            return;
        }
        MethodDeclaration md = typeWrapper.getType().getMethodsByName(methodRef.getIdentifier()).getFirst();
        addArguments(md.getParameters().size(), call);

        if (md.isStatic()) {
            call.setScope(methodRef.getScope());
        }
        else {
            call.setScope(new NameExpr("arg"));
        }
    }

    private static void addArguments(int numberOfarguments, MethodCallExpr call) {
        if (numberOfarguments == 1) {
            call.addArgument(new NameExpr("arg"));
        }
        else {
            for(int i = 0 ; i < numberOfarguments ; i++) {
                call.addArgument(new NameExpr("arg" + i));
            }
        }
    }

    private static Method getFunctionalInterfaceMethod(Class<?> functionalInterface) {
        return java.util.Arrays.stream(functionalInterface.getMethods())
                .filter(m -> !m.isDefault() && !m.getDeclaringClass().equals(Object.class))
                .findFirst()
                .orElse(null);
    }
}
