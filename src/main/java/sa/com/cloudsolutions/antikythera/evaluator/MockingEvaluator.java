package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

public class MockingEvaluator extends Evaluator {

    protected MockingEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    /**
     *
     * @param cd CallableDeclaration a method being executed.
     * @return null when the method defined by the callable declaration is of the type void.
     *   returns a reasonable value when the method is not void.
     * @throws ReflectiveOperationException if the operation fails due to reflective issues
     */
    @Override
    public Variable executeMethod(CallableDeclaration<?> cd) throws ReflectiveOperationException {
        if (!(cd instanceof MethodDeclaration md)) {
            return null;
        }

        setupParameters(md);
        Type returnType = md.getType();
        if (returnType.isVoidType()) {
            return null;
        }

        Variable result;
        if (returnType.isClassOrInterfaceType()) {
            result = Reflect.variableFactory(returnType.asClassOrInterfaceType().getNameAsString());
            if (result != null) {
                addMockitoExpression(md, result.getValue());
                return result;
            }
        }

        if (returnType.isPrimitiveType()) {
            result = Reflect.variableFactory(returnType.toString());
            addMockitoExpression(md, result.getValue());
            return result;
        }

        if (cd.findCompilationUnit().isPresent()) {
            CompilationUnit cu1 = cd.findCompilationUnit().get();
            if (returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.asClassOrInterfaceType().getNameAsString());
                result = Reflect.variableFactory(fqdn);
                addMockitoExpression(md, result.getValue());
                return result;
            }
            String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.toString());
            result = Reflect.variableFactory(fqdn);
            addMockitoExpression(md, result.getValue());
            return result;
        }
        return null;
    }

    private void addMockitoExpression(MethodDeclaration md, Object returnValue) {
        if (returnValue != null) {
            MethodCallExpr mockitoWhen = new MethodCallExpr(
                    new NameExpr("Mockito"),
                    "when"
            );

            MethodCallExpr methodCall = new MethodCallExpr()
                    .setName(md.getNameAsString())
                    .setArguments(new NodeList<>());

            mockitoWhen.setArguments(new NodeList<>(methodCall));

            Expression returnExpr = expressionFactory(returnValue.getClass().getName());
            new MethodCallExpr(mockitoWhen, "thenReturn")
                    .setArguments(new NodeList<>(returnExpr));
        }
    }

    public static Expression expressionFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return new NullLiteralExpr();
        }

        return switch (qualifiedName) {
            case "List", "java.util.List", "java.util.ArrayList" ->
                new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("ArrayList"))
                    .setArguments(new NodeList<>());

            case "Map", "java.util.Map", "java.util.HashMap" ->
                new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("HashMap"))
                    .setArguments(new NodeList<>());

            case "java.util.TreeMap" ->
                new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("TreeMap"))
                    .setArguments(new NodeList<>());

            case "Set", "java.util.Set", "java.util.HashSet" ->
                new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("HashSet"))
                    .setArguments(new NodeList<>());

            case "java.util.TreeSet" ->
                new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("TreeSet"))
                    .setArguments(new NodeList<>());

            case "java.util.Optional" ->
                new MethodCallExpr(
                    new NameExpr("Optional"),
                    "empty"
                );

            case "Boolean", Reflect.PRIMITIVE_BOOLEAN ->
                new BooleanLiteralExpr(false);

            case Reflect.PRIMITIVE_FLOAT, Reflect.FLOAT, Reflect.PRIMITIVE_DOUBLE, Reflect.DOUBLE ->
                new DoubleLiteralExpr("0.0");

            case Reflect.INTEGER, "int" ->
                new IntegerLiteralExpr("0");

            case "Long", "long", "java.lang.Long" ->
                new LongLiteralExpr("-100L");

            case "String", "java.lang.String" ->
                new StringLiteralExpr("Ibuprofen");

            default ->
                new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName(qualifiedName))
                    .setArguments(new NodeList<>());
        };
    }
}
