package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
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
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.lang.reflect.Method;
import java.util.Optional;

public class MockingEvaluator extends Evaluator {

    protected MockingEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    /**
     * Execute a method call.
     * @param wrapper the method call expression wrapped so that the argument types are available
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
    @Override
    public Variable executeMethod(MCEWrapper wrapper) throws ReflectiveOperationException {
        returnFrom = null;

        Optional<TypeDeclaration<?>> cdecl = AbstractCompiler.getMatchingType(cu, getClassName());
        Optional<Callable> n = AbstractCompiler.findCallableDeclaration(wrapper, cdecl.orElseThrow().asClassOrInterfaceDeclaration());
        if (n.isPresent() ) {
            if (n.get().isMethod()) {
                Method m = n.get().getMethod();
                Class<?> returnType = m.getReturnType();

                Variable result = Reflect.variableFactory(returnType.getName());
                if (result != null) {
                    MethodCallExpr mockitoWhen = new MethodCallExpr(
                            new NameExpr("Mockito"),
                            "when"
                    );

                    // Create the method call with appropriate parameters
                    MethodCallExpr methodCall = new MethodCallExpr()
                            .setScope(new NameExpr(variableName))
                            .setName(m.getName());

                    // Add any() matchers for each parameter
                    NodeList<Expression> args = new NodeList<>();
                    java.lang.reflect.Parameter[] parameters = m.getParameters();
                    for (java.lang.reflect.Parameter p : parameters) {
                        String typeName = p.getType().getSimpleName();
                        args.add(createMockitoArgument(typeName));
                    }
                    methodCall.setArguments(args);

                    // Complete the when().thenReturn() expression
                    mockitoWhen.setArguments(new NodeList<>(methodCall));
                    MethodCallExpr thenReturn = new MethodCallExpr(mockitoWhen, "thenReturn")
                            .setArguments(new NodeList<>(expressionFactory(returnType.getName())));

                    TestGenerator.addWhenThen(thenReturn);
                    return result;
                }
            }
            else {
                return super.executeMethod(wrapper);
            }
        }

        return null;
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

        return mockReturnFromCompilationUnit(cd, md, returnType);
    }

    Variable mockReturnFromCompilationUnit(CallableDeclaration<?> cd, MethodDeclaration md, Type returnType) {
        Variable result = null;
        if (cd.findCompilationUnit().isPresent()) {
            CompilationUnit cu1 = cd.findCompilationUnit().get();
            if (returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.asClassOrInterfaceType().getNameAsString());
                result = Reflect.variableFactory(fqdn);
            }
            else {
                String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.toString());
                result = Reflect.variableFactory(fqdn);
            }
            addMockitoExpression(md, result.getValue());
        }
        return result;
    }

    private void addMockitoExpression(MethodDeclaration md, Object returnValue) {
        if (returnValue != null) {
            MethodCallExpr mockitoWhen = new MethodCallExpr(
                    new NameExpr("Mockito"),
                    "when"
            );

            // Create the method call with appropriate parameters
            MethodCallExpr methodCall = new MethodCallExpr()
                    .setScope(new NameExpr(variableName))
                    .setName(md.getNameAsString());

            // Add any() matchers for each parameter
            NodeList<Expression> args = fakeArguments(md);
            methodCall.setArguments(args);

            // Complete the when().thenReturn() expression
            mockitoWhen.setArguments(new NodeList<>(methodCall));
            MethodCallExpr thenReturn = new MethodCallExpr(mockitoWhen, "thenReturn")
                    .setArguments(new NodeList<>(expressionFactory(returnValue.getClass().getName())));

            TestGenerator.addWhenThen(thenReturn);
        }
    }

    static NodeList<Expression> fakeArguments(MethodDeclaration md) {
        NodeList<Expression> args = new NodeList<>();
        md.getParameters().forEach(param -> {
            String typeName = param.getType().asString();
            args.add(createMockitoArgument(typeName));
        });
        return args;
    }

    private static MethodCallExpr createMockitoArgument(String typeName) {
        return new MethodCallExpr(
                new NameExpr("Mockito"),
                switch (typeName) {
                    case "String" -> "anyString";
                    case "int", "Integer" -> "anyInt";
                    case "long", "Long" -> "anyLong";
                    case "double", "Double" -> "anyDouble";
                    case "boolean", "Boolean" -> "anyBoolean";
                    default -> "any";
                }
        );
    }

    static Expression expressionFactory(String qualifiedName) {
        if (qualifiedName == null) {
            return new NullLiteralExpr();
        }

        return switch (qualifiedName) {
            case "List", "java.util.List", "java.util.ArrayList" -> {
                TestGenerator.addDependency("java.util.ArrayList");
                yield new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("ArrayList"))
                    .setArguments(new NodeList<>());
            }

            case "Map", "java.util.Map", "java.util.HashMap" -> {
                TestGenerator.addDependency("java.util.HashMap");
                yield new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("HashMap"))
                    .setArguments(new NodeList<>());
            }

            case "java.util.TreeMap" -> {
                TestGenerator.addDependency("java.util.TreeMap");
                yield new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("TreeMap"))
                    .setArguments(new NodeList<>());
            }

            case "Set", "java.util.Set", "java.util.HashSet" -> {
                TestGenerator.addDependency("java.util.HashSet");
                yield new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("HashSet"))
                    .setArguments(new NodeList<>());
            }

            case "java.util.TreeSet" -> {
                TestGenerator.addDependency("java.util.TreeSet");
                yield new ObjectCreationExpr()
                    .setType(new ClassOrInterfaceType().setName("TreeSet"))
                    .setArguments(new NodeList<>());
            }

            case "java.util.Optional" -> {
                TestGenerator.addDependency("java.util.Optional");
                yield new MethodCallExpr(
                    new NameExpr("Optional"),
                    "empty"
                );
            }

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
