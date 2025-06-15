package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MockingEvaluator extends ControlFlowEvaluator {
    private static final Set<String> collectionTypes = Set.of(
            "java.util.List",
            "java.util.ArrayList",
            "java.util.Collection",
            "java.util.Set",
            "java.util.HashSet",
            "java.util.TreeSet",
            "java.lang.Iterable",
            "List",
            "ArrayList",
            "Collection",
            "Set",
            "HashSet",
            "TreeSet",
            "Iterable"
    );

    protected MockingEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    @Override
    Variable executeMethod(Method m) {
        return mockExecution(m,  m.getReturnType().getName());
    }

    /**
     * Pretend to execute a method
     * @param m the method that we are supposed to execute
     * @param returnType the return type that we should create an instance of
     * @return a Variable that contains an instance of the returnType
     */
    private Variable mockExecution(Method m, String returnType) {
        if (variableName != null) {
            Variable result = Reflect.variableFactory(returnType);
            if (result != null) {
                MethodCallExpr methodCall = MockingRegistry.buildMockitoWhen(m.getName(), returnType, variableName);
                methodCall.setArguments(MockingRegistry.generateArgumentsForWhen(m));
                return result;
            }
        }
        if (m.getDeclaringClass().equals(Object.class)) {
            try {
                Object o = m.invoke(this);
                return new Variable(o);
            } catch (ReflectiveOperationException e) {
                throw new AntikytheraException(e);
            }
        }
        return null;
    }

    @Override
    protected Variable executeCallable(Scope sc, Callable callable) throws ReflectiveOperationException {
        returnFrom = null;

        if (callable.isMethodDeclaration()) {
            if (isRepository()) {
                return mockRepositoryMethodCall(sc, callable);
            }
            return super.executeCallable(sc, callable);
        }
        else {
            if (isRepository()) {
                return mockRepositoryMethodCall(sc, callable);
            }
            return mockBinaryMethodExecution(sc, callable);
        }
    }

    private boolean isRepository() {
        if (typeDeclaration.getAnnotationByName("Repository").isPresent()) {
            return true;
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration cdecl) {
            for (ClassOrInterfaceType t : cdecl.getExtendedTypes()) {
                if (t.getNameAsString().equals("JpaRepository") || t.getNameAsString().equals("CrudRepository")) {
                    return true;
                }
            }
        }

        return false;
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

    private Variable mockRepositoryMethodCall(Scope sc, Callable callable) throws ReflectiveOperationException {
        Method method = callable.getMethod();
        if (method != null) {
            return mockRepositoryMethod(sc, callable);
        }
        else {
            return mockRepositoryMethodDeclaration(sc, callable);
        }
    }

    @SuppressWarnings("unchecked")
    private Variable mockRepositoryMethodDeclaration(Scope sc, Callable callable) throws ReflectiveOperationException {
        MethodDeclaration md = callable.getCallableDeclaration().asMethodDeclaration();
        Type t = md.getType();
        if (t.isClassOrInterfaceType() && t.asClassOrInterfaceType().isBoxedType()) {
            MethodCallExpr methodCall = sc.getScopedMethodCall();
            Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
            LineOfCode l = Branching.get(stmt.hashCode());
            Variable v =new Variable(Reflect.getDefault(t.asClassOrInterfaceType().getNameAsString()));
            if (l == null) {
                l = new LineOfCode(stmt);
                Branching.add(l);
            }
            else {
                l.setPathTaken(LineOfCode.TRUE_PATH);
            }
            MethodCallExpr when = createWhenExpression(methodCall);
            MockingCall then = createThenExpression(sc, v, when);
            MockingRegistry.when(className, then);

            return v;
        }
        if (!t.isPrimitiveType() && !t.isVoidType()) {
            List<ImportWrapper> imports = AbstractCompiler.findImport(cu, t);
            ImportWrapper imp = imports.getLast();
            String s = imp.getNameAsString();
            if (collectionTypes.contains(s)) {
                return handleRepositoryCollectionHelper(sc, s);
            }
        }
        return super.executeCallable(sc, callable);
    }

    private Variable mockRepositoryMethod(Scope sc, Callable callable) throws ReflectiveOperationException {
        Method method = callable.getMethod();
        if (method.getName().equals("save")) {
            return mockRepositorySave(callable, method);
        }
        String returnType = method.getReturnType().getName();
        if (collectionTypes.contains(returnType)) {
            return handleRepositoryCollectionHelper(sc, returnType);
        }
        if (returnType.equals(Reflect.JAVA_UTIL_OPTIONAL)) {
            return handleOptionals(sc);
        }
        return mockFromTypeArguments(
                typeDeclaration.asClassOrInterfaceDeclaration().getExtendedTypes(0), method);
    }

    private Variable mockRepositorySave(Callable callable, Method method) {
        List<Variable> variables = new ArrayList<>();
        for (int i = 0; i < method.getParameters().length ; i++) {
            variables.add(AntikytheraRunTime.pop());
        }

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
        mockingCall.setExpression(List.of(mce));

        MockingRegistry.when(className, mockingCall);
        return variables.getFirst();
    }

    private Optional<ClassOrInterfaceType> findParentWithGenerics(Class<?> foundIn) {
        if (typeDeclaration.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration cdecl = typeDeclaration.asClassOrInterfaceDeclaration();
            for (ClassOrInterfaceType parent : cdecl.getExtendedTypes()) {
                if (parent.getTypeArguments().isPresent() &&
                        (foundIn.getName().equals(parent.getName().asString())
                                || foundIn.getSimpleName().equals(parent.getName().asString()))) {

                    return Optional.of(parent);
                }
            }
        }
        return Optional.empty();
    }

    private Variable mockReturnFromBinaryParent(Class<?> foundIn, Method method) {
        Optional<ClassOrInterfaceType> parent = findParentWithGenerics(foundIn);
        return parent.map(
                classOrInterfaceType -> mockFromTypeArguments(classOrInterfaceType, method))
                .orElse(null);

    }

     Variable mockFromTypeArguments(ClassOrInterfaceType parent, Method method) {
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
        Optional<BlockStmt> body = md.getBody();
        String methodName = md.getNameAsString();

        Variable v = getIdField();
        if (v != null) {
            setupParameters(md);
            return v;
        }

        Type returnType = md.getType();
        if (returnType.isVoidType()) {
            if (methodName.startsWith("set") && body.isPresent() && body.get().getStatements().size() == 1) {
                super.executeMethod(cd);
            }
            else {
                setupParameters(md);
            }
            return null;
        }

        setupParameters(md);
        if (methodName.startsWith("get") && body.isPresent() && body.get().getStatements().size() == 1) {
            Variable f = getField(AbstractCompiler.classToInstanceName(methodName.substring(3)));
            if (f != null) {
                return f;
            }
        }

        Variable result ;
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

     Variable getIdField() {
        if (typeDeclaration != null && typeDeclaration.getAnnotationByName("Entity").isPresent()) {
            for (FieldDeclaration f : typeDeclaration.getFields()) {
                if (f.getAnnotationByName("Id").isPresent()) {
                    Variable v = getField(f.getVariable(0).getNameAsString());
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
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
                        TestGenerator.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_OPTIONAL, false, false));
                        return new Variable(Optional.of(typeEval));
                    }
                    Variable v = optionalByteBuddy(ciType.getNameAsString());
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    Variable optionalByteBuddy(String typeName) throws ReflectiveOperationException {
        TypeWrapper wrapper = AbstractCompiler.findType(cu, typeName);
        if (wrapper != null) {
            Class<?> clazz = wrapper.getClazz();
            TestGenerator.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_OPTIONAL, false, false));
            if (clazz != null) {
                MethodInterceptor interceptor = new MethodInterceptor(clazz);
                Class<?> dynamicClass = AKBuddy.createDynamicClass(interceptor);
                Object instance = AKBuddy.createInstance(dynamicClass, interceptor);
                return new Variable(Optional.of(instance));
            }
            Evaluator eval = EvaluatorFactory.create(wrapper.getFullyQualifiedName(), Evaluator.class);
            MethodInterceptor interceptor = new MethodInterceptor(eval);
            Class<?> dynamicClass = AKBuddy.createDynamicClass(interceptor);
            Object instance = AKBuddy.createInstance(dynamicClass, interceptor);
            return new Variable(Optional.of(instance));
        }
        return null;
    }

    @Override
    Variable optionalEmptyPath(Scope sc, LineOfCode l) {
        return new Variable(Optional.empty());
    }

    /**
     * Mock execution of a JPA Repository method that returns a collection
     * @param sc the scope of the method call
     * @param collectionTypeName something like java.util.Iterable or Set
     * @return a Variable representing the mocked execution.
     */
    @SuppressWarnings("unchecked")
    Variable handleRepositoryCollectionHelper(Scope sc, String collectionTypeName) {
        MethodCallExpr methodCall = sc.getScopedMethodCall();
        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        LineOfCode l = Branching.get(stmt.hashCode());

        Variable v = (l == null) ? repositoryFullPath(sc, stmt, collectionTypeName)
                : repositoryEmptyPath(collectionTypeName);

        MethodCallExpr when = createWhenExpression(methodCall);
        MockingCall then = createThenExpression(sc, v, when);
        MockingRegistry.when(className, then);
        return v;
    }

    private MockingCall createThenExpression(Scope sc, Variable v, MethodCallExpr mce) {
        MockingCall then = new MockingCall(sc.getMCEWrapper().getMatchingCallable(), v);
        then.setVariableName(variableName);

        List<Expression> initializer = then.getVariable().getInitializer();
        List<Expression> mocks = new ArrayList<>();
        if (initializer.size() > 1) {
            for(int i = 0 ; i < initializer.size() -1; i++) {
                mocks.add(initializer.get(i));
            }
        }
        MethodCallExpr when = StaticJavaParser.parseExpression(
                String.format(
                        "Mockito.when(%s).thenReturn(%s)",
                        mce, initializer.getLast())
        );
        mocks.add(when);
        then.setExpression(mocks);
        return then;
    }

    private static MethodCallExpr createWhenExpression(MethodCallExpr methodCall) {
        MethodCallExpr mce = new MethodCallExpr(methodCall.getNameAsString());
        methodCall.getScope().ifPresent(mce::setScope);

        List<Expression> args = new ArrayList<>();
        for (int i = methodCall.getArguments().size() - 1; i >= 0; i--) {
            Variable v = AntikytheraRunTime.pop();
            args.add(MockingRegistry.createMockitoArgument(v.getType().asString()));
        }

        for (Expression e : args.reversed()) {
            mce.addArgument(e);
        }
        return mce;
    }

    Variable repositoryEmptyPath(String collectionTypeName) {
        Variable v = Reflect.variableFactory(collectionTypeName);
        if (v != null && v.getInitializer().getFirst() instanceof ObjectCreationExpr oce) {
            String typeName = oce.getTypeAsString();
            if (typeName.endsWith("ArrayList") || typeName.endsWith("LinkedList") || typeName.endsWith("List")) {
                TestGenerator.addImport(new ImportDeclaration("java.util.List", false, false));
                v.setInitializer(
                        List.of(new MethodCallExpr("of").setScope(new NameExpr("List"))));
            }
        }
        return v;
    }

    /**
     * Creates a non-empty list of entities.
     * Finding the entity type is the real challenge as that information is not readily available
     * when dealing with a method that is declared in JPARepository or one of it's ancestors.
     * @param sc scope of the method call
     * @param stmt the statement that involves the method call
     * @param collectionTypeName the type of collection that is required
     * @return a collection of the type that matches collectionTypeName which will contain a
     *      single entity.
     */
    private Variable repositoryFullPath(Scope sc, Statement stmt, String collectionTypeName) {
        LineOfCode l = new LineOfCode(stmt);
        l.setPathTaken(LineOfCode.TRUE_PATH);
        Branching.add(l);

        Callable callable = sc.getMCEWrapper().getMatchingCallable();
        CallableDeclaration<?> callableDeclaration = callable.getCallableDeclaration();
        Variable v = Reflect.variableFactory(collectionTypeName);

        NodeList<Type> typeArgs = null;
        if (callableDeclaration != null) {
            Type type = callableDeclaration.asMethodDeclaration().getType();
            typeArgs = type.asClassOrInterfaceType().getTypeArguments().orElse(new NodeList<>());
        }
        else {
            Optional<ClassOrInterfaceType> t = findParentWithGenerics(callable.getFoundInClass());
            if (t.isPresent()) {
                NodeList<Type> args = t.get().getTypeArguments().orElse(new NodeList<>());
                typeArgs = new NodeList<>();
                if (args.isNonEmpty()) {
                    typeArgs.add(args.get(0));
                }
            }
        }
        if (typeArgs != null) {
            if (typeArgs.isEmpty()) {
                typeArgs.add(new ClassOrInterfaceType().setName("Object"));
            }
            List<Expression> expressions = setupNonEmptyCollection(typeArgs, v, new NameExpr("bada"));
            if (expressions != null) {
                v.setInitializer(expressions);
            }
        }
        return v;
    }

    @Override
    protected Variable resolveExpressionHelper(TypeWrapper wrapper) {
        if (wrapper.getType() != null) {
            Variable v;
            Evaluator eval = EvaluatorFactory.createLazily(wrapper.getType().getFullyQualifiedName().orElseThrow(), MockingEvaluator.class);
            v = new Variable(eval);
            return v;
        }
        return null;
    }

    @Override
    void setupFieldWithoutInitializer(VariableDeclarator variableDeclarator) {
        TypeWrapper wrapper = AbstractCompiler.findType(cu, variableDeclarator.getType().toString());
        if (wrapper != null && !wrapper.getFullyQualifiedName().equals(Reflect.JAVA_LANG_STRING)) {
            Variable v = Reflect.variableFactory(wrapper.getFullyQualifiedName());
            v.setType(variableDeclarator.getType());
            fields.put(variableDeclarator.getNameAsString(), v);
        }
        else {
            super.setupFieldWithoutInitializer(variableDeclarator);
        }
    }
}
