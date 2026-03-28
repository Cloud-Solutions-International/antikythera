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

        if (isRepository()) {
            return mockRepositoryMethodCall(sc, callable);
        }
        return mockBinaryMethodExecution(sc, callable);
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

    Variable mockBinaryMethodExecution(Scope sc, Callable callable) throws ReflectiveOperationException {
        Method method = getMethod(callable);

        Class<?> clazz = method.getReturnType();
        if (Optional.class.equals(clazz)) {
            return handleOptionals(sc);
        }
        else if (Object.class.equals(clazz)) {
            for (int i = 0 ; i < method.getParameters().length ; i++) {
                AntikytheraRunTime.pop();
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
        return mockRepositoryMethodDeclaration(sc, callable);
    }

    private Variable mockRepositoryMethodDeclaration(Scope sc, Callable callable) throws ReflectiveOperationException {
        MethodDeclaration md = callable.getCallableDeclaration().asMethodDeclaration();
        Type t = md.getType();
        if (t.isClassOrInterfaceType()) {
            Variable v = createVariable(sc, t, md);
            if (v != null) return v;
        }
        if (!t.isPrimitiveType() && !t.isVoidType()) {
            List<ImportWrapper> imports = AbstractCompiler.findImport(cu, t);
            if (!imports.isEmpty()) {
                ImportWrapper imp = imports.getLast();
                String s = imp.getNameAsString();
                if (collectionTypes.contains(s)) {
                    return handleRepositoryCollectionHelper(sc, s);
                }
                // Single-entity return: record a stub so the generated test doesn't get null
                if (t.isClassOrInterfaceType() && !t.asClassOrInterfaceType().isBoxedType()) {
                    return handleRepositorySingleEntityReturn(sc, t, imp);
                }
            }
        }
        return super.executeCallable(sc, callable);
    }

    private Variable createVariable(Scope sc, Type t, MethodDeclaration md) {
        if (t.asClassOrInterfaceType().isBoxedType()) {
            MethodCallExpr methodCall = sc.getScopedMethodCall();
            Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
            LineOfCode l = Branching.get(stmt.hashCode());
            Variable v;
            if (l == null) {
                l = new LineOfCode(stmt);
                Branching.add(l);
                v = Reflect.generateDefaultVariable(t.asClassOrInterfaceType().getNameAsString());
            }
            else {
                l.setPathTaken(LineOfCode.TRUE_PATH);
                v = Reflect.generateNonDefaultVariable(t.asClassOrInterfaceType().getNameAsString());
            }
            MethodCallExpr when = createWhenExpression(methodCall);
            MockingCall then = createThenExpression(sc, v, when);
            MockingRegistry.when(className, then);

            return v;
        }
        else {
            ClassOrInterfaceType classType = t.asClassOrInterfaceType();
            if (Reflect.OPTIONAL.equals(classType.getNameAsString())
                    || Reflect.JAVA_UTIL_OPTIONAL.equals(classType.getNameAsString())) {
                return handleRepositoryOptionalDeclaration(sc, md);
            }
        }
        return null;
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
        Variable v = mockFromTypeArguments(
                typeDeclaration.asClassOrInterfaceDeclaration().getExtendedTypes(0), method);
        if (v != null && v.getValue() instanceof Evaluator eval
                && method.getReturnType() != void.class && method.getReturnType() != Void.class) {
            String entityClassName = eval.getClassName();
            int dotIdx = entityClassName.lastIndexOf('.');
            String shortName = dotIdx >= 0 ? entityClassName.substring(dotIdx + 1) : entityClassName;
            v = stubEntityReturnWithBranching(sc, entityClassName, shortName);
        }
        return v;
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
        BlockStmt body = md.getBody().orElse(null);
        String methodName = md.getNameAsString();

        Variable v = getIdField();
        if (v != null) {
            setupParameters(md);
            return v;
        }

        Type returnType = md.getType();
        if (returnType.isVoidType()) {
            executeVoidMethod(cd, md, methodName, body);
            return null;
        }

        setupParameters(md);
        Variable getterValue = resolveSimpleGetterReturn(methodName, body);
        if (getterValue != null) {
            return getterValue;
        }

        Variable classResult = createClassReturnValue(md, returnType);
        if (classResult != null) {
            return classResult;
        }

        Variable primitiveResult = createPrimitiveReturnValue(md, returnType);
        if (primitiveResult != null) {
            return primitiveResult;
        }

        return mockReturnFromCompilationUnit(cd, md, returnType);
    }

    private void executeVoidMethod(CallableDeclaration<?> cd, MethodDeclaration md, String methodName,
                                   BlockStmt body) throws ReflectiveOperationException {
        if (isSingleStatementAccessor(methodName, "set", body)) {
            super.executeMethod(cd);
            return;
        }
        setupParameters(md);
    }

    private Variable resolveSimpleGetterReturn(String methodName, BlockStmt body) {
        if (!isSingleStatementAccessor(methodName, "get", body)) {
            return null;
        }
        String fieldName = AbstractCompiler.classToInstanceName(methodName.substring(3));
        Variable field = getField(fieldName);
        if (field != null) {
            return field;
        }
        // Lazy evaluators can have no initialized fields; mimic Java's default null field value.
        if (fields.isEmpty()) {
            return new Variable((Object) null);
        }
        return null;
    }

    private Variable createClassReturnValue(MethodDeclaration md, Type returnType) {
        if (!returnType.isClassOrInterfaceType()) {
            return null;
        }
        Variable result = Reflect.variableFactory(returnType.asClassOrInterfaceType().getNameAsString());
        if (result != null) {
            MockingRegistry.addMockitoExpression(md, result.getValue(), variableName);
        }
        return result;
    }

    private Variable createPrimitiveReturnValue(MethodDeclaration md, Type returnType) {
        if (!returnType.isPrimitiveType()) {
            return null;
        }
        Variable result = Reflect.variableFactory(returnType.toString());
        MockingRegistry.addMockitoExpression(md, result.getValue(), variableName);
        return result;
    }

    private boolean isSingleStatementAccessor(String methodName, String prefix, BlockStmt body) {
        return methodName.startsWith(prefix) && body != null && body.getStatements().size() == 1;
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
                        GeneratorState.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_OPTIONAL, false, false));
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
            GeneratorState.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_OPTIONAL, false, false));
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

    private Variable handleRepositoryOptionalDeclaration(Scope sc, MethodDeclaration md) {
        MethodCallExpr methodCall = sc.getScopedMethodCall();
        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        LineOfCode branch = Branching.get(stmt.hashCode());
        Variable v;
        if (branch == null) {
            branch = new LineOfCode(stmt);
            Branching.add(branch);
            branch.setPathTaken(LineOfCode.TRUE_PATH);
            v = createRepositoryOptionalDeclarationValue(md, true);
        }
        else if (branch.getPathTaken() == LineOfCode.TRUE_PATH) {
            v = createRepositoryOptionalDeclarationValue(md, false);
        }
        else {
            v = createRepositoryOptionalDeclarationValue(md, true);
        }

        MockingCall then = new MockingCall(sc.getMCEWrapper().getMatchingCallable(), v);
        then.setVariableName(variableName);
        MockingRegistry.when(className, then);
        return v;
    }

    private Variable createRepositoryOptionalDeclarationValue(MethodDeclaration md, boolean present) {
        ClassOrInterfaceType classType = md.getType().asClassOrInterfaceType();
        Variable v = present
                ? new Variable(Optional.ofNullable(createRepositoryOptionalPayload(classType)))
                : new Variable(Optional.empty());
        v.setType(classType);
        return v;
    }

    private Object createRepositoryOptionalPayload(ClassOrInterfaceType classType) {
        Type nestedType = classType.getTypeArguments().flatMap(args -> args.getFirst()).orElse(null);
        if (nestedType == null) {
            return 1;
        }
        Variable payload = Reflect.generateNonDefaultVariable(nestedType.asString());
        return payload != null && payload.getValue() != null ? payload.getValue() : 1;
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

        for (int i = 0 ;  i < methodCall.getArguments().size() ; i++) {
            Variable v = AntikytheraRunTime.pop();
            String typeName = (v != null && v.getType() != null) ? v.getType().asString() : "Object";
            mce.addArgument(MockingRegistry.createMockitoArgument(typeName));
        }
        return mce;
    }

    Variable repositoryEmptyPath(String collectionTypeName) {
        Variable v = Reflect.variableFactory(collectionTypeName);
        if (v != null && v.getInitializer().getFirst() instanceof ObjectCreationExpr oce) {
            String typeName = oce.getTypeAsString();
            if (typeName.endsWith("ArrayList") || typeName.endsWith("LinkedList") || typeName.endsWith("List")) {
                GeneratorState.addImport(new ImportDeclaration("java.util.ArrayList", false, false));
                v.setInitializer(
                        List.of(new ObjectCreationExpr().setType("ArrayList<>").setArguments(new com.github.javaparser.ast.NodeList<>())));
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
     * @return A variable instance that is a collection of the `type` that matches
     *      collectionTypeName which will contain a single entity.
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
            // Create a ClassOrInterfaceType to hold the type arguments
            ClassOrInterfaceType type = new ClassOrInterfaceType().setName("Collection");
            type.setTypeArguments(typeArgs);
            List<Expression> expressions = setupNonEmptyCollection(type, v, new NameExpr("bada"));
            if (expressions != null) {
                v.setInitializer(expressions);
            }
        }
        return v;
    }

    private Variable handleRepositorySingleEntityReturn(Scope sc, Type t, ImportWrapper imp) {
        String entityShortName = t.asClassOrInterfaceType().getNameAsString();
        String entityFullName = imp.getNameAsString() != null ? imp.getNameAsString() : entityShortName;
        return stubEntityReturnWithBranching(sc, entityFullName, entityShortName);
    }

    /**
     * Common branching + Mockito stub logic for repository methods that return a single entity.
     * <p>
     * First iteration (no existing {@link LineOfCode} branch): registers the branch, pops arguments
     * from the stack and returns {@code null} so that the null-path test is generated.
     * <br>
     * Second iteration: creates a lazy evaluator for the entity, wraps it in a {@link Variable},
     * sets an {@code ObjectCreationExpr} initializer, records the required import, and registers
     * a Mockito when/thenReturn stub via {@link MockingRegistry}.
     *
     * @param sc              the current method-call scope
     * @param entityFullName  fully-qualified class name of the entity
     * @param entityShortName simple (unqualified) class name of the entity
     * @return {@code null} on the first pass; a {@link Variable} wrapping the entity evaluator on
     *         subsequent passes
     */
    private Variable stubEntityReturnWithBranching(Scope sc, String entityFullName, String entityShortName) {
        MethodCallExpr methodCall = sc.getScopedMethodCall();
        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        LineOfCode l = Branching.get(stmt.hashCode());

        if (l == null) {
            // First iteration: return null so the null-path test is generated.
            l = new LineOfCode(stmt);
            Branching.registerBranch(l);
            for (int i = 0; i < methodCall.getArguments().size(); i++) {
                AntikytheraRunTime.pop();
            }
            return new Variable((Object) null);
        }

        // Second iteration: return a non-null entity with a stub.
        Variable v;
        Optional<com.github.javaparser.ast.body.TypeDeclaration<?>> tdOpt = AntikytheraRunTime.getTypeDeclaration(entityFullName);
        if (tdOpt.isPresent()) {
            // Use createLazily so the evaluator has no initialized fields — prevents stale state.
            Evaluator eval = EvaluatorFactory.createLazily(entityFullName, MockingEvaluator.class);
            v = new Variable(eval);
        } else {
            v = new Variable((Object) null);
        }
        ObjectCreationExpr oce = new ObjectCreationExpr(null,
                new ClassOrInterfaceType(null, entityShortName), new NodeList<>());
        v.setInitializer(java.util.List.of(oce));
        GeneratorState.addImport(new ImportDeclaration(entityFullName, false, false));
        MethodCallExpr when = createWhenExpression(methodCall);
        MockingCall then = createThenExpression(sc, v, when);
        MockingRegistry.when(className, then);
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
}
