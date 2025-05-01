package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;

import java.lang.reflect.Method;
import java.util.Optional;

public class MockingEvaluator extends ControlFlowEvaluator {

    protected MockingEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    @Override
    Variable executeMethod(Method m) {
        Class<?> returnType = m.getReturnType();

        Variable result = Reflect.variableFactory(returnType.getName());
        if (result != null) {
            MethodCallExpr methodCall = MockingRegistry.buildMockitoWhen(m.getName(), returnType.getName(), variableName);
            methodCall.setArguments(MockingRegistry.generateArgumentsForWhen(m));

            return result;
        }
        return null;
    }

    /**
     * @param cd CallableDeclaration a method being executed.
     * @return null when the method defined by the callable declaration is of the type void.
     * returns a reasonable value when the method is not void.
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
                MockingRegistry.addMockitoExpression(md, result.getValue(), variableName);
                return result;
            }
        }

        if (returnType.isPrimitiveType()) {
            result = Reflect.variableFactory(returnType.toString());
            MockingRegistry.addMockitoExpression(md, result.getValue(), variableName);
            return result;
        }

        return mockReturnFromCompilationUnit(cd, md, returnType);
    }

    Variable mockReturnFromCompilationUnit(CallableDeclaration<?> cd, MethodDeclaration md, Type returnType) {
        Variable result = null;
        Optional<CompilationUnit> compilationUnit = cd.findCompilationUnit();
        if (compilationUnit.isPresent()) {
            CompilationUnit cu1 = compilationUnit.get();
            if (returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.asClassOrInterfaceType().getNameAsString());
                result = Reflect.variableFactory(fqdn);
            } else {
                String fqdn = AbstractCompiler.findFullyQualifiedName(cu1, returnType.toString());
                result = Reflect.variableFactory(fqdn);
            }
            MockingRegistry.addMockitoExpression(md, result.getValue(), variableName);
        }
        return result;
    }

    @Override
    Variable handleOptionals(Scope scope) throws ReflectiveOperationException {
        Callable callable = scope.getMCEWrapper().getMatchingCallable();
        Method m = callable.getMethod();
        Variable v = handleOptionalsHelper(scope);
        if (v == null) {
            return executeMethod(m);
        }
        for (int i = 0 , j = scope.getScopedMethodCall().getArguments().size() ; i < j ; i++) {
            AntikytheraRunTime.pop();
        }

        return v;
    }

    @Override
    Variable optionalPresentPath(Scope sc, Statement stmt, MethodCallExpr methodCall) throws ReflectiveOperationException {
        LineOfCode l = new LineOfCode(stmt);
        Branching.add(l);

        if (sc.getVariable().getValue() instanceof MockingEvaluator eval) {
            l.setPathTaken(LineOfCode.TRUE_PATH);
            CompilationUnit cu = eval.getCompilationUnit();
            if (cu != null) {
                TypeDeclaration<?> typeDeclaration = AbstractCompiler.getMatchingType(cu, eval.getClassName()).orElseThrow();
                if (typeDeclaration instanceof ClassOrInterfaceDeclaration cdecl) {
                    return straightPathHelper(cdecl);
                }
            }
        }
        return null;
    }

    Variable straightPathHelper(ClassOrInterfaceDeclaration cdecl) throws ReflectiveOperationException {
        for (ClassOrInterfaceType t : cdecl.getExtendedTypes()) {
            Type x = t.getTypeArguments().orElse(new NodeList<>()).getFirst().orElse(null);
            if (x instanceof ClassOrInterfaceType ciType) {
                // Check if type is available as source code
                Optional<TypeDeclaration<?>> typeDecl = AbstractCompiler.resolveTypeSafely(ciType, t);
                if (typeDecl.isPresent()) {
                    // Type is available as source code, use Evaluator
                    String typeName = typeDecl.get().getFullyQualifiedName().orElse(ciType.getNameAsString());
                    Evaluator typeEval = EvaluatorFactory.create(typeName, Evaluator.class);
                    typeEval.setupFields();
                    typeEval.initializeFields();
                    return new Variable(Optional.of(typeEval));
                } else {
                    Variable v = optionalByteBuddy(ciType.getNameAsString());
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private Variable optionalByteBuddy(String typeName) throws ReflectiveOperationException {
        String resolvedClass = AbstractCompiler.findFullyQualifiedName(cu, typeName);
        if (resolvedClass != null) {
            Class<?> clazz = AbstractCompiler.loadClass(resolvedClass);
            MethodInterceptor interceptor = new MethodInterceptor(clazz);
            Class<?> dynamicClass = AKBuddy.createDynamicClass(interceptor);
            Object instance = dynamicClass.getDeclaredConstructor().newInstance();
            return new Variable(Optional.of(instance));
        }
        return null;
    }

    @Override
    Variable optionalEmptyPath(Scope sc, LineOfCode l) throws ReflectiveOperationException {
        return new Variable(Optional.empty());
    }
}
