package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockingEvaluator extends ControlFlowEvaluator {

    protected MockingEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    @Override
    Variable executeMethod(Method m) {
        return mockExecution(m,  m.getReturnType().getName());
    }

    private Variable mockExecution(Method m, String returnType) {
        Variable result = Reflect.variableFactory(returnType);
        if (result != null) {
            MethodCallExpr methodCall = MockingRegistry.buildMockitoWhen(m.getName(), returnType, variableName);
            methodCall.setArguments(MockingRegistry.generateArgumentsForWhen(m));

            return result;
        }
        return null;
    }

    @Override
    protected Variable executeCallable(Scope sc, Callable callable) throws ReflectiveOperationException {
        if (callable.isMethodDeclaration()) {
            if (typeDeclaration.getAnnotationByName("Repository").isPresent()) {
                MethodDeclaration md = callable.getCallableDeclaration().asMethodDeclaration();
                Type t = md.getType();
                List<ImportWrapper> imports = AbstractCompiler.findImport(cu, t);
                ImportWrapper imp = imports.getLast();
                String s = imp.getNameAsString();
                if (s.endsWith("List") || s.endsWith("Collection") || s.endsWith("Set")) {
                    return handleRepositoryCollectionHelper(sc, s);
                }
            }
            return super.executeCallable(sc, callable);
        }
        else {
            return mockBinaryMethodExecution(sc, callable);
        }
    }

    private Variable mockBinaryMethodExecution(Scope sc, Callable callable) throws ReflectiveOperationException {
        Method method = getMethod(callable);

        Class<?> clazz = method.getReturnType();
        if (Optional.class.equals(clazz)) {
            return handleOptionals(sc);
        }
        else if (Object.class.equals(clazz)) {
            List<Variable> variables = new ArrayList<>();
            for (int i = 0 ; i < method.getParameters().length ; i++) {
                variables.add(AntikytheraRunTime.pop());
            }

            if (typeDeclaration.getAnnotationByName("Repository").isPresent()) {
                return mockRepositoryMethod(callable, method, variables);
            }

            Class<?> foundIn = callable.getFoundInClass();
            if (foundIn != null) {
                Variable v = mockReturnFromBinaryParent(foundIn, method);
                if (v != null) {
                    return v;
                }
            }
        }
        return executeMethod(method);
    }

    private Variable mockRepositoryMethod(Callable callable, Method method, List<Variable> variables) {
        if (method.getName().equals("save")) {
            MockingCall call = MockingRegistry.getThen(className, callable);
            if (call != null) {
                return call.getVariable();
            }
            MockingCall mockingCall = new MockingCall(callable, variables.getFirst());
            MethodCallExpr mce = StaticJavaParser.parseExpression(
                String.format(
                        "Mockito.when(%s.save(Mockito.any())).thenAnswer(invocation-> invocation.getArgument(0))",
                        variableName)
            );
            mockingCall.setExpression(mce);

            MockingRegistry.when(className, mockingCall);
            return variables.getFirst();
        }
        return mockFromTypeArguments(
                typeDeclaration.asClassOrInterfaceDeclaration().getExtendedTypes(0), method);
    }

    private Variable mockReturnFromBinaryParent(Class<?> foundIn, Method method) {
        if (! typeDeclaration.isClassOrInterfaceDeclaration()) {
            return null;
        }

        ClassOrInterfaceDeclaration cdecl = typeDeclaration.asClassOrInterfaceDeclaration();
        for (ClassOrInterfaceType parent : cdecl.getExtendedTypes()) {
            if ( parent.getTypeArguments().isPresent() &&
                    (foundIn.getName().equals(parent.getName().asString())
                            || foundIn.getSimpleName().equals(parent.getName().asString()))) {

                Variable v = mockFromTypeArguments(parent, method);
                if (v != null) {
                    return v;
                }
            }
        }

        return null;
    }

    private Variable mockFromTypeArguments(ClassOrInterfaceType parent, Method method) {
        Type r = parent.getTypeArguments().orElseThrow().getFirst().orElseThrow();
        String fullName = AbstractCompiler.findFullyQualifiedName(cu, r.toString());
        if (fullName != null) {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullName);
            if (cu != null) {
                return new Variable(EvaluatorFactory.create(fullName, Evaluator.class));
            }
            return mockExecution(method, r.toString());
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
                TypeWrapper wrapper = AbstractCompiler.findType(cdecl.findCompilationUnit().orElse(null), ciType.getNameAsString());
                if (wrapper != null) {
                    if (wrapper.getType() != null) {
                        // Type is available as source code, use Evaluator
                        String typeName = wrapper.getType().getFullyQualifiedName().orElse(ciType.getNameAsString());
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
    Variable optionalEmptyPath(Scope sc, LineOfCode l) {
        return new Variable(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    Variable handleRepositoryCollectionHelper(Scope sc, String collectionTypeName) {
        MethodCallExpr methodCall = sc.getScopedMethodCall();
        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        LineOfCode l = Branching.get(stmt.hashCode());

        for (int i = 0, j = methodCall.getArguments().size(); i < j; i++) {
            AntikytheraRunTime.pop();
        }

        Variable v = (l == null) ? repositoryFullPath(sc, stmt, methodCall, collectionTypeName)
                : repositoryEmptyPath(sc, l, collectionTypeName);
        MockingCall then = new MockingCall(sc.getMCEWrapper().getMatchingCallable(), v);
        then.setVariableName(variableName);

        MockingRegistry.when(className, then);
        return v;
    }

    private Variable repositoryEmptyPath(Scope sc, LineOfCode l, String collectionTypeName) {
        return Reflect.variableFactory(collectionTypeName);
    }

    private Variable repositoryFullPath(Scope sc, Statement stmt, MethodCallExpr methodCall, String collectionTypeName) {
        LineOfCode l = new LineOfCode(stmt);
        l.setPathTaken(LineOfCode.TRUE_PATH);
        Branching.add(l);

        Type type = sc.getMCEWrapper()
                .getMatchingCallable().getCallableDeclaration()
                .asMethodDeclaration().getType();
        NodeList<Type> typeArgs = type.asClassOrInterfaceType().getTypeArguments().orElse(new NodeList<>());
        if (typeArgs.isEmpty()) {
            typeArgs.add(new ClassOrInterfaceType().setName("Object"));
        }
        Variable v =  Reflect.variableFactory(collectionTypeName);
        l.addPrecondition(new Precondition(setupNonEmptyCollection(typeArgs,v, new NameExpr("bada"))));
        return v;
    }

}
