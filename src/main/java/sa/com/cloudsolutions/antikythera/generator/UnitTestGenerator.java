package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Precondition;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.TestSuiteEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.evaluator.logging.LogRecorder;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Unit test generator.</p>
 *
 * <p>The responsibility of deciding what should be mocked and what should not be mocked lies here.
 * Each class that is marked as autowired will be considered a candidate for mocking. These will
 * be registered in the mocking registry.</p>
 *
 * <p>Then the evaluators will in turn add more mocking statements to the mocking registry. When the
 * tests are being generated, this class will check the registry to find out what additional mocks
 * need to be added.</p>
 */
public class UnitTestGenerator extends TestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(UnitTestGenerator.class);
    public static final String TEST_NAME_SUFFIX = "AKTest";
    private final String filePath;

    private boolean autoWired;
    private String instanceName;
    private CompilationUnit baseTestClass;
    private ClassOrInterfaceDeclaration testClass;

    public UnitTestGenerator(CompilationUnit cu) {
        super(cu);
        String packageDecl = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        String basePath = Settings.getProperty(Settings.BASE_PATH, String.class).orElseThrow();
        String className = AbstractCompiler.getPublicType(cu).getNameAsString() + TEST_NAME_SUFFIX;

        filePath = basePath.replace("main/java", "test/java") + File.separator +
                packageDecl.replace(".", File.separator) + File.separator + className + ".java";

        File file = new File(filePath);

        try {
            loadExisting(file);
        } catch (FileNotFoundException e) {
            logger.debug("Could not find file: {}", filePath);
            createTestClass(className, packageDecl);
        }
    }

    /**
     * Attempt to identify which fields have already been mocked.
     *
     * @param t the type declaration which holds the fields being mocked.
     */
    private static void identifyExistingMocks(TypeDeclaration<?> t) {
        for (FieldDeclaration fd : t.getFields()) {
            if (fd.getAnnotationByName("MockBean").isPresent() ||
                    fd.getAnnotationByName("Mock").isPresent()) {
                List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(fd.getVariable(0));
                if(!wrappers.isEmpty() && wrappers.getLast() != null){
                    MockingRegistry.markAsMocked(MockingRegistry.generateRegistryKey(wrappers));
                }
            }
        }

    }

    /**
     * Loads any existing test class that has been generated previously.
     * This code is typically not available through the {@link AntikytheraRunTime} class because we are
     * processing only the src/main and mostly ignoring src/test
     *
     * @param file the file name
     * @throws FileNotFoundException if the source code cannot be found.
     */
    void loadExisting(File file) throws FileNotFoundException {
        gen = StaticJavaParser.parse(file);
        List<MethodDeclaration> remove = new ArrayList<>();
        for (TypeDeclaration<?> t : gen.getTypes()) {
            for (MethodDeclaration md : t.getMethods()) {
                md.getComment().ifPresent(c -> {
                    if (!c.getContent().contains("Author: Antikythera")) {
                        remove.add(md);
                    }
                });
            }
            for (MethodDeclaration md : remove) {
                gen.getType(0).remove(md);
            }

            if (t.isClassOrInterfaceDeclaration()) {
                loadPredefinedBaseClassForTest(t.asClassOrInterfaceDeclaration());
            }
            identifyExistingMocks(t);
        }
    }

    /**
     * Create a clas for the test suite
     *
     * @param className   the name of the class
     * @param packageDecl the package it should be placed in.
     */
    private void createTestClass(String className, String packageDecl) {
        gen = new CompilationUnit();
        if (packageDecl != null && !packageDecl.isEmpty()) {
            gen.setPackageDeclaration(packageDecl);
        }

        testClass = gen.addClass(className);
        loadPredefinedBaseClassForTest(testClass);
    }

    /**
     * <p>Loads a base class that is common to all generated test classes.</p>
     * <p>
     * Provided that an entry called base_test_class exists in the settings file and the source for
     * that class can be found, it will be loaded. If such an entry does not exist and the test suite
     * had previously been generated, we will check the extended types of the test class.
     *
     * @param testClass the declaration of the test suite being built
     */
    @SuppressWarnings("unchecked")
    private void loadPredefinedBaseClassForTest(ClassOrInterfaceDeclaration testClass) {
        String base = Settings.getProperty(Settings.BASE_TEST_CLASS, String.class).orElse(null);
        if (base != null && testClass.getExtendedTypes().isEmpty()) {
            testClass.addExtendedType(base);
            loadPredefinedBaseClassForTest(base);
        } else if (!testClass.getExtendedTypes().isEmpty()) {
            SimpleName n = testClass.getExtendedTypes().get(0).getName();
            String baseClassName = n.toString();
            String fqn = AbstractCompiler.findFullyQualifiedName(testClass.findAncestor(CompilationUnit.class).orElseThrow(),
                    baseClassName);
            if (fqn != null) {
                loadPredefinedBaseClassForTest(testClass);
            } else {
                n.getParentNode().ifPresent(p ->
                        loadPredefinedBaseClassForTest(p.toString())
                );
            }
        }
    }

    /**
     * Loads the base class for the tests if such a file exists.
     *
     * @param baseClassName the name of the base class.
     */
    void loadPredefinedBaseClassForTest(String baseClassName) {
        String basePath = Settings.getProperty(Settings.BASE_PATH, String.class).orElseThrow();
        String helperPath = basePath.replace("src/main", "src/test") + File.separator +
                AbstractCompiler.classToPath(baseClassName);
        try {
            baseTestClass = StaticJavaParser.parse(new File(helperPath));
            for (TypeDeclaration<?> t : baseTestClass.getTypes()) {
                identifyExistingMocks(t);
            }

            baseTestClass.findFirst(MethodDeclaration.class,
                            md -> md.getNameAsString().equals("setUpBase"))
                    .ifPresent(md -> {
                        TestSuiteEvaluator eval = new TestSuiteEvaluator(baseTestClass, baseClassName);
                        try {
                            eval.setupFields();
                            eval.initializeFields();
                            eval.executeMethod(md);
                        } catch (ReflectiveOperationException e) {
                            throw new AntikytheraException(e);
                        }
                    });

        } catch (FileNotFoundException e) {
            throw new AntikytheraException("Base class could not be loaded for tests.", e);
        }
    }

    @Override
    public void createTests(MethodDeclaration md, MethodResponse response) {
        methodUnderTest = md;
        testMethod = buildTestMethod(md);
        gen.getType(0).addMember(testMethod);
        createInstance();
        mockArguments();
        applyPreconditions();
        addWhens();
        String invocation = invokeMethod();

        addDependencies();
        setupAsserterImports();

        if (response.getException() == null) {
            getBody(testMethod).addStatement(invocation);
            addAsserts(response);
            for (ReferenceType t : md.getThrownExceptions()) {
                testMethod.addThrownException(t);
            }
        } else {
            String[] parts = invocation.split("=");
            assertThrows(parts.length == 2 ? parts[1] : parts[0], response);
        }
    }

    void addDependencies() {
        for (ImportDeclaration imp : TestGenerator.getImports()) {
            gen.addImport(imp);
        }
    }

    /**
     * Deals with adding Mockito.when().then() type expressions to the generated tests.
     */
    private void addWhens() {
        for (Expression expr : whenThen) {
            if (expr instanceof MethodCallExpr mce && skipWhenUsage(mce)) {
                continue;
            }
            getBody(testMethod).addStatement(expr);
        }
        whenThen.clear();
    }

    private boolean skipWhenUsage(MethodCallExpr mce) {
        Optional<Expression> scope = mce.getScope();
        if (scope.isPresent() && scope.get() instanceof MethodCallExpr scoped) {

            Optional<Expression> arg = scoped.getArguments().getFirst();
            if (arg.isPresent() && baseTestClass != null
                    && arg.get() instanceof MethodCallExpr argMethod) {
                if (argMethod.getScope().isPresent() && mockedByBaseTestClass(argMethod.getScope().orElseThrow())) {
                    return true;
                }
                addImportsForCasting(argMethod);
            }
        }
        return false;
    }

    /**
     * Mockito.any() calls often need casting to avoid ambiguity
     * @param argMethod a method call that may contain mocks
     */
    private void addImportsForCasting(MethodCallExpr argMethod) {
        for (Expression e : argMethod.getArguments()) {
            if (e instanceof CastExpr cast && cast.getType() instanceof ClassOrInterfaceType ct) {
                List<ImportWrapper> imports = AbstractCompiler.findImport(compilationUnitUnderTest, ct);
                if (imports.isEmpty()) {
                    solveCastingProblems(ct);
                }
                else {
                    for (ImportWrapper iw : imports) {
                        TestGenerator.addImport(iw.getImport());
                    }
                }
            }
        }
    }

    private static void solveCastingProblems(ClassOrInterfaceType ct) {
        /* We are mocking a variable, but the code may not have been written with an
         * interface as the type of the variable. For example, Antikythera might be
         * using Set<Long> while in the application under test, they may have used
         * LinkedHashSet<Long> instead. So we will be unable to find the required
         * imports from the CompilationUnit.
         */
        String typeName = ct.getNameAsString();
        if (typeName.startsWith("Set") || typeName.startsWith("java.util.Set")) {
            TestGenerator.addImport(new ImportDeclaration("java.util.Set", false, false));
        }
        else if (typeName.startsWith("List") || typeName.startsWith(Reflect.JAVA_UTIL_LIST)) {
            TestGenerator.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_LIST, false, false));
        }
        else if (typeName.startsWith("Map") || typeName.startsWith("java.util.Map")) {
            TestGenerator.addImport(new ImportDeclaration("java.util.Map", false, false));
        }
        else {
            logger.debug("Unable to find import for: {}", ct.getNameAsString());
        }
    }

    private boolean mockedByBaseTestClass(Expression arg) {
        for (TypeDeclaration<?> t : baseTestClass.getTypes()) {
            for (FieldDeclaration fd : t.getFields()) {
                if ((fd.getAnnotationByName("MockBean").isPresent() ||
                        fd.getAnnotationByName("Mock").isPresent()) &&
                        fd.getVariable(0).getNameAsString().equals(arg.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates an instance of the class under test
     */
    @SuppressWarnings("unchecked")
    void createInstance() {
        methodUnderTest.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(c -> {
            if (c.getAnnotationByName("Service").isPresent()
                    || c.getAnnotationByName("org.springframework.stereotype.Service").isPresent()) {
                injectMocks(c);
            } else {
                instanceName = AbstractCompiler.classToInstanceName(c.getNameAsString());
                getBody(testMethod).addStatement(ArgumentGenerator.instantiateClass(c, instanceName));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void injectMocks(ClassOrInterfaceDeclaration classUnderTest) {
        if (testClass == null) {
            testClass = testMethod.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow();
        }
        addImport(new ImportDeclaration("org.mockito.InjectMocks", false, false));

        if (!autoWired) {
            for (FieldDeclaration fd : testClass.getFields()) {
                if (fd.getElementType().asString().equals(classUnderTest.getNameAsString())) {
                    autoWired = true;
                    instanceName = fd.getVariable(0).getNameAsString();
                    break;
                }
            }
        }
        if (!autoWired) {
            instanceName = AbstractCompiler.classToInstanceName(classUnderTest.getNameAsString());

            if (testClass.getFieldByName(classUnderTest.getNameAsString()).isEmpty()) {
                FieldDeclaration fd = testClass.addField(classUnderTest.getNameAsString(), instanceName);
                fd.addAnnotation("InjectMocks");
            }
            autoWired = true;
        }
    }

    void mockArguments() {
        for (var param : methodUnderTest.getParameters()) {
            Type paramType = param.getType();
            String nameAsString = param.getNameAsString();
            if (paramType.isPrimitiveType() ||
                    (paramType.isClassOrInterfaceType() && paramType.asClassOrInterfaceType().isBoxedType())) {
                mockSimpleArgument(param, nameAsString, paramType);
            } else {
                addClassImports(paramType);
                Variable v = argumentGenerator.getArguments().get(nameAsString);
                if (v != null) {
                    if (v.getValue() != null && v.getValue().getClass().getName().startsWith("java.util")) {
                        gen.addImport(v.getValue().getClass().getName());
                        mockWithoutMockito(param, v);
                    }
                    else {
                        mockWithMockito(param, v);
                    }
                }
            }
        }
    }

    private void mockSimpleArgument(Parameter param, String nameAsString, Type paramType) {
        VariableDeclarationExpr varDecl = new VariableDeclarationExpr();
        VariableDeclarator v = new VariableDeclarator();
        v.setType(param.getType());
        v.setName(nameAsString);

        String typeName = paramType.asString();

        switch (typeName) {
            case "Integer" -> v.setInitializer("0");
            case "Long" -> v.setInitializer("0L");
            case "Double" -> v.setInitializer("0.0");
            case "Float" -> v.setInitializer("0.0f");
            case "boolean", "Boolean" -> v.setInitializer("false");
            case "Character" -> v.setInitializer("'\\0'");
            case "Byte" -> v.setInitializer("(byte)0");
            case "Short" -> v.setInitializer("(short)0");
            default -> v.setInitializer("null");
        }


        varDecl.addVariable(v);
        getBody(testMethod).addStatement(varDecl);
    }

    void mockWithMockito(Parameter param, Variable v) {
        String nameAsString = param.getNameAsString();
        if (!v.getInitializer().isEmpty()) {
            mockWhenInitializerIsPresent(param, v);
        }
        else {
            if (mockWhenInitializerIsAbsent(param, v)) return;
        }
        mockParameterFields(v, nameAsString);
    }

    private boolean mockWhenInitializerIsAbsent(Parameter param, Variable v) {
        BlockStmt body = getBody(testMethod);
        String nameAsString = param.getNameAsString();
        Type t = param.getType();

        if (param.findCompilationUnit().isPresent()) {
            CompilationUnit cu = param.findCompilationUnit().orElseThrow();
            if (t instanceof ArrayType) {
                Variable mocked = Reflect.variableFactory(t.asString());
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = " + mocked.getInitializer().getFirst() + ";");
                mockParameterFields(v, nameAsString);
                return true;
            }
            if (AbstractCompiler.isFinalClass(param.getType(), cu)) {
                cantMockFinalClass(param, v, cu);
                return true;
            }
        }
        if (t != null && t.isClassOrInterfaceType() && t.asClassOrInterfaceType().getTypeArguments().isPresent()) {
            body.addStatement(buildMockDeclaration(t.asClassOrInterfaceType().getNameAsString(), nameAsString));
        } else {
            body.addStatement(buildMockDeclaration(param.getTypeAsString(), nameAsString));
        }
        return false;
    }

    private void mockWhenInitializerIsPresent(Parameter param, Variable v) {
        String nameAsString = param.getNameAsString();
        BlockStmt body = getBody(testMethod);
        Type t = param.getType();

        if (v.getInitializer().size() == 1 && v.getInitializer().getFirst().isObjectCreationExpr() &&
                    isMockitoMock(v.getValue())) {
            body.addStatement(buildMockDeclaration(t.asClassOrInterfaceType().getNameAsString(), nameAsString));
            return;
        }

        mockWithoutMockito(param, v);

        for (int i = 1; i < v.getInitializer().size() ; i++) {
            body.addStatement(v.getInitializer().get(i));
        }
    }

    public static boolean isMockitoMock(Object object) {
        if (object == null) {
            return false;
        }
        try {
            return Mockito.mockingDetails(object).isMock();
        } catch (Exception e) {
            return false;
        }
    }

    private void mockWithoutMockito(Parameter param, Variable v) {
        BlockStmt body = getBody(testMethod);
        String nameAsString = param.getNameAsString();
        body.addStatement(param.getTypeAsString() + " " + nameAsString + " = " +
                v.getInitializer().getFirst() + ";");
    }

    private static String buildMockDeclaration(String type, String variableName) {
        return String.format("%s %s = Mockito.mock(%s.class);", type, variableName, type);
    }

    private void cantMockFinalClass(Parameter param, Variable v, CompilationUnit cu) {
        String nameAsString = param.getNameAsString();
        BlockStmt body = getBody(testMethod);
        Type t = param.getType();

        String fullClassName = AbstractCompiler.findFullyQualifiedName(cu, t);
        Variable mocked = Reflect.variableFactory(fullClassName);
        if (v.getValue() instanceof Optional<?> value) {

            if (value.isPresent()) {
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = " +
                        "Optional.of(" + mocked.getInitializer().getFirst() + ");");
            } else {
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = Optional.empty();");
            }
        }
        else {
            if (mocked.getInitializer().isEmpty()) {
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = null;");
            }
            else {
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = " + mocked.getInitializer() + ";");
                mockParameterFields(v, nameAsString);
            }
        }
    }

    void mockParameterFields(Variable v, String nameAsString) {
        if (v.getValue() instanceof Evaluator eval) {
            Optional<TypeDeclaration<?>> typeDeclarationOpt = AntikytheraRunTime.getTypeDeclaration(eval.getClassName());
            if (typeDeclarationOpt.isPresent()) {
                TypeDeclaration<?> t = typeDeclarationOpt.get();
                for (FieldDeclaration field : t.getFields()) {
                    if (!v.getInitializer().isEmpty() && v.getInitializer().getFirst() instanceof ObjectCreationExpr) {
                        mockFieldWithSetter(nameAsString, eval, field);
                    }
                    else {
                        mockFieldWithMockito(nameAsString, eval, field);
                    }
                }
            }
        }
    }

    private void mockFieldWithSetter(String nameAsString, Evaluator eval, FieldDeclaration field) {
        BlockStmt body = getBody(testMethod);
        String name = field.getVariable(0).getNameAsString();

        if (doesFieldNeedMocking(eval, name)) {
            Variable fieldVar = eval.getField(name);
            Object value = fieldVar.getValue();
            if (value instanceof List) {
                TestGenerator.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_LIST, false, false));
                body.addStatement(String.format("%s.set%s(new ArrayList());", nameAsString, ClassProcessor.instanceToClassName(name)));
            } else {
                if (!fieldVar.getInitializer().isEmpty()) {
                    Expression first = fieldVar.getInitializer().getFirst();
                    if (first.isMethodCallExpr() && first.toString().startsWith("set")) {
                        body.addStatement(String.format("%s.%s;",
                                nameAsString, first));
                    }
                    else {
                        body.addStatement(String.format("%s.set%s(%s);",
                                nameAsString, ClassProcessor.instanceToClassName(name), first));
                    }
                }
            }
        }
    }

    private boolean doesFieldNeedMocking(Evaluator eval, String name) {
        Variable f = eval.getField(name);
        if (f == null || f.getType() == null || name.equals("serialVersionUID")) {
            return false;
        }

        Object value = f.getValue();
        if (value == null) {
            return false;
        }

        return (f.getType().isPrimitiveType() && f.getValue().equals(Reflect.getDefault(f.getClazz())));

    }

    private void mockFieldWithMockito(String nameAsString, Evaluator eval, FieldDeclaration field) {
        BlockStmt body = getBody(testMethod);
        String name = field.getVariable(0).getNameAsString();

        if (!doesFieldNeedMocking(eval, name)) {
            return;
        }
        Object value = eval.getField(name).getValue();
        if (value instanceof List) {
            TestGenerator.addImport(new ImportDeclaration(Reflect.JAVA_UTIL_LIST, false, false));
            body.addStatement(String.format("Mockito.when(%s.get%s()).thenReturn(List.of());",
                    nameAsString,
                    ClassProcessor.instanceToClassName(name)
            ));
        }
        else {
            if (value instanceof String) {
                body.addStatement(String.format("Mockito.when(%s.get%s()).thenReturn(\"%s\");",
                        nameAsString,
                        ClassProcessor.instanceToClassName(name), value));
            }
            else {
                body.addStatement(String.format("Mockito.when(%s.get%s()).thenReturn(%s);",
                        nameAsString,
                        ClassProcessor.instanceToClassName(name),
                        value instanceof Long ? value + "L" : value.toString()));
            }
        }
    }

    void applyPreconditions() {
        for (MockingCall  result : MockingRegistry.getAllMocks()) {
            if (! result.isFromSetup()) {
                applyRegistryCondition(result);
            }
        }

        for (Precondition expr : preConditions) {
            applyPreconditionWithMockito(expr.getExpression());
        }
    }

    void applyRegistryCondition(MockingCall result) {
        if (result.getVariable().getValue() instanceof Optional<?>) {
            applyPreconditionsForOptionals(result);
        }
        else {
            if (result.getExpression() != null) {
                for (Expression e : result.getExpression()) {
                    addWhenThen(e);
                }
            }
        }
    }

    void applyPreconditionsForOptionals(MockingCall result) {
        if (result.getVariable().getValue() instanceof Optional<?> value) {
            Callable callable = result.getCallable();
            MethodCallExpr methodCall;
            if (value.isPresent()) {
                methodCall = applyPreconditionForOptionalPresent(result, value.get(), callable);
            }
            else {
                // create an expression that represents Optional.empty()
                Expression empty = StaticJavaParser.parseExpression("Optional.empty()");
                methodCall = MockingRegistry.buildMockitoWhen(
                        callable.getNameAsString(), empty, result.getVariableName());
            }
            if (callable.isMethodDeclaration()) {
                methodCall.setArguments(MockingRegistry.fakeArguments(callable.asMethodDeclaration()));
            } else {
                methodCall.setArguments(MockingRegistry.generateArgumentsForWhen(callable.getMethod()));
            }
        }
    }

    private MethodCallExpr applyPreconditionForOptionalPresent(MockingCall result, Object value, Callable callable) {
        MethodCallExpr methodCall;
        if (value instanceof Evaluator eval) {
            if (baseTestClass != null) {
                String mock = String.format("Mockito.mock(%s.class, new DefaultObjectAnswer())",eval.getClassName());
                Expression opt = StaticJavaParser.parseExpression("Optional.of(" + mock +   ")");
                methodCall = MockingRegistry.buildMockitoWhen(
                        callable.getNameAsString(), opt, result.getVariableName());
            }
            else {
                Expression opt = StaticJavaParser.parseExpression("Optional.of(new " + eval.getClassName() + "())");
                methodCall = MockingRegistry.buildMockitoWhen(
                        callable.getNameAsString(), opt, result.getVariableName());
            }
        }
        else {
            throw new IllegalStateException("Not implemented yet");
        }
        return methodCall;
    }

    private void applyPreconditionWithMockito(Expression expr) {
        BlockStmt body = getBody(testMethod);
        if (expr.isMethodCallExpr()) {
            MethodCallExpr mce = expr.asMethodCallExpr();
            mce.getScope().ifPresent(scope -> {
                String name = mce.getNameAsString();

                if (expr.toString().contains("set")) {
                    body.addStatement("Mockito.when(%s.%s()).thenReturn(%s);".formatted(
                            scope.toString(),
                            name.replace("set", "get"),
                            mce.getArguments().get(0).toString()
                    ));
                }
            });
        }
        else if (expr instanceof AssignExpr assignExpr) {
            Expression target = assignExpr.getTarget();
            Expression value = assignExpr.getValue();
            if (target instanceof NameExpr nameExpr) {
                replaceInitializer(testMethod, nameExpr.getNameAsString(), value);
            }
        }
    }

    private void addClassImports(Type t) {
        for (ImportWrapper wrapper : AbstractCompiler.findImport(compilationUnitUnderTest, t)) {
            addImport(wrapper.getImport());
        }
    }

    String invokeMethod() {
        StringBuilder b = new StringBuilder();

        Type t = methodUnderTest.getType();
        if (t != null && !t.toString().equals("void")) {
            b.append(t.asString()).append(" resp = ");
            for (ImportWrapper imp : AbstractCompiler.findImport(compilationUnitUnderTest, t)) {
                addImport(imp.getImport());
            }
        }

        b.append(instanceName).append(".").append(methodUnderTest.getNameAsString()).append("(");
        for (int i = 0; i < methodUnderTest.getParameters().size(); i++) {
            b.append(methodUnderTest.getParameter(i).getNameAsString());
            if (i < methodUnderTest.getParameters().size() - 1) {
                b.append(", ");
            }
        }
        b.append(");");
        return b.toString();
    }

    private void addAsserts(MethodResponse response) {
        Type t = methodUnderTest.getType();

        if (t == null) {
            return;
        }

        addClassImports(t);

        if (response.getBody() != null) {
            noSideEffectAsserts(response);
        } else {
            sideEffectAsserts();
        }
    }

    @SuppressWarnings("unchecked")
    private void sideEffectAsserts() {
        BlockStmt body = getBody(testMethod);
        TypeDeclaration<?> type = methodUnderTest.findAncestor(TypeDeclaration.class).orElseThrow();
        String className = type.getFullyQualifiedName().orElseThrow();
        List<LogRecorder.LogEntry> logs = LogRecorder.getLogEntries(className);

        if (Settings.getProperty(Settings.LOG_APPENDER, String.class).isPresent()) {
            if (testClass.getMethodsByName("setupLoggers").isEmpty()) {
                setupLoggers();
            }
            if (logs.isEmpty()) {
                MethodCallExpr assertion = new MethodCallExpr("assertTrue");
                MethodCallExpr condition = new MethodCallExpr("LogAppender.events.isEmpty");
                assertion.addArgument(condition);
                body.addStatement(assertion);
            }
            else {
                for (int i = 0, j = Math.min(5, logs.size()); i < j; i++) {
                    LogRecorder.LogEntry entry = logs.get(i);
                    String level = entry.level();
                    String message = entry.message();
                    body.addStatement(assertLoggedWithLevel(className, level, message));
                }
            }
        }
    }

    private void noSideEffectAsserts(MethodResponse response) {
        Variable result = response.getBody();
        BlockStmt body = getBody(testMethod);
        if (result.getValue() != null) {
            if (result.getType() != null && result.getType().isPrimitiveType()) {
                body.addStatement(asserter.assertEquals(String.valueOf(result.getValue()), "resp"));
            }
            else {
                body.addStatement(asserter.assertNotNull("resp"));
                if (result.getValue() instanceof Collection<?> c) {
                    if (c.isEmpty()) {
                        body.addStatement(asserter.assertEmpty("resp"));
                    } else {
                        body.addStatement(asserter.assertNotEmpty("resp"));
                    }
                }
            }
            asserter.addFieldAsserts(response, body);
        }
        else {
            body.addStatement(asserter.assertNull("resp"));
        }
    }

    @SuppressWarnings("unchecked")
    private void setupLoggers() {
        methodUnderTest.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(classDeclaration -> {
            BlockStmt body = new BlockStmt();
            MethodDeclaration md = new MethodDeclaration().setName("setupLoggers")
                    .setType(void.class)
                    .addAnnotation("BeforeEach")
                    .setJavadocComment("Author : Antikythera")
                    .setBody(body);

            body.addStatement(String.format("appLogger = (Logger) LoggerFactory.getLogger(%s.class);",
                    classDeclaration.getFullyQualifiedName().orElseThrow()));
            body.addStatement("appLogger.setAdditive(false);");
            body.addStatement("logAppender = new LogAppender();");
            body.addStatement("logAppender.start();");
            body.addStatement("appLogger.addAppender(logAppender);");
            body.addStatement("appLogger.setLevel(Level.INFO);");
            body.addStatement("LogAppender.events.clear(); // Clear static list from previous tests");

            if (testClass.getFieldByName("appLogger").isEmpty()) {
                testClass.addPrivateField("Logger", "appLogger");
                testClass.addPrivateField("LogAppender", "logAppender");
            }

            testClass.addMember(md);
            gen.addImport("ch.qos.logback.classic.Logger");
            gen.addImport("ch.qos.logback.classic.Level");
            gen.addImport("org.slf4j.LoggerFactory");
            gen.addImport(Settings.getProperty(Settings.LOG_APPENDER, String.class).orElseThrow());
        });
    }

    @Override
    public void setCommonPath(String commonPath) {
        throw new UnsupportedOperationException("Not needed here");
    }

    @Override
    public void addBeforeClass() {
        identifyFieldsToBeMocked();

        MethodDeclaration before = new MethodDeclaration();
        before.setType(void.class);
        before.addAnnotation("BeforeEach");
        before.setName("setUp");
        BlockStmt beforeBody = new BlockStmt();
        before.setBody(beforeBody);
        beforeBody.addStatement("MockitoAnnotations.openMocks(this);");
        before.setJavadocComment("Author : Antikythera");

        if (baseTestClass != null) {
            baseTestClass.findFirst(MethodDeclaration.class,
                            md -> md.getNameAsString().equals("setUpBase"))
                    .ifPresent(md -> beforeBody.addStatement("setUpBase();"));
        }

        for (TypeDeclaration<?> t : gen.getTypes()) {
            if(t.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("setUp")).isEmpty()) {
                t.addMember(before);
            }
        }
    }

    @Override
    public void identifyFieldsToBeMocked() {
        for (TypeDeclaration<?> t : gen.getTypes()) {
            for (FieldDeclaration fd : t.getFields()) {
                List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(fd.getVariable(0));
                if (!wrappers.isEmpty() && wrappers.getLast() != null) {
                    MockingRegistry.markAsMocked(MockingRegistry.generateRegistryKey(wrappers));
                }
            }
        }

        addImport(new ImportDeclaration("org.mockito.MockitoAnnotations", false, false));
        addImport(new ImportDeclaration("org.junit.jupiter.api.BeforeEach", false, false));
        addImport(new ImportDeclaration("org.mockito.Mock", false, false));
        addImport(new ImportDeclaration("org.mockito.Mockito", false, false));

        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            CompilationUnit cu = entry.getValue();
            identifyFieldsToBeMocked(cu);
        }

        identifyFieldsToBeMocked(compilationUnitUnderTest);
    }

    /**
     * Mock all the fields that have been marked as Autowired
     * Mockito.Mock will be preferred over Mockito.MockBean
     *
     * @param cu the compilation unit that contains code to be tested.
     */
    private void identifyFieldsToBeMocked(CompilationUnit cu) {

        for (TypeDeclaration<?> decl : cu.getTypes()) {
            if (decl.isAnnotationPresent("org.springframework.stereotype.Service")
                    || decl.isAnnotationPresent("Service")) {
                detectConstructorInjection(cu, decl);
            }

            identifyAutoWiring(cu, decl);
        }
    }

    private void identifyAutoWiring(CompilationUnit cu, TypeDeclaration<?> decl) {
        Optional<ClassOrInterfaceDeclaration> suite = findSuite(decl);
        if (suite.isEmpty()) {
            return;
        }
        detectAutoWiringHelper(cu, decl, suite.get());
    }

    private void detectAutoWiringHelper(CompilationUnit cu, TypeDeclaration<?> classUnderTest,
                                        ClassOrInterfaceDeclaration testSuite) {
        for (FieldDeclaration fd : classUnderTest.getFields()) {
            List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(fd.getVariable(0));
            if (wrappers.isEmpty()) {
                continue;
            }
            String registryKey = MockingRegistry.generateRegistryKey(wrappers);
            if (fd.getAnnotationByName("Autowired").isPresent() && !MockingRegistry.isMockTarget(registryKey)) {
                addMockedField(cu, testSuite, fd, registryKey);
            }
        }
    }

    private static void addMockedField(CompilationUnit cu, ClassOrInterfaceDeclaration testSuite, FieldDeclaration fd, String registryKey) {
        MockingRegistry.markAsMocked(registryKey);
        FieldDeclaration field = testSuite.addField(fd.getElementType(), fd.getVariable(0).getNameAsString());
        field.addAnnotation("Mock");
        ImportWrapper wrapper = AbstractCompiler.findImport(cu, field.getElementType().asString());
        if (wrapper != null) {
            addImport(wrapper.getImport());
        }
    }

    private void detectConstructorInjection(CompilationUnit cu, TypeDeclaration<?> decl) {
        for (ConstructorDeclaration constructor : decl.getConstructors()) {
            Map<String, String> paramToFieldMap = mapParamToFields(constructor);
            for (Parameter param : constructor.getParameters()) {
                detectConstructorInjectionHelper(cu, testClass, param, paramToFieldMap);
            }
        }
    }

    private void detectConstructorInjectionHelper(CompilationUnit cu, ClassOrInterfaceDeclaration suite,
                                                  Parameter param, Map<String, String> paramToFieldMap) {
        List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(param);
        String registryKey = MockingRegistry.generateRegistryKey(wrappers);
        String paramName = param.getNameAsString();
        String fieldName = paramToFieldMap.getOrDefault(paramName, paramName);

        if (!MockingRegistry.isMockTarget(registryKey) && suite.getFieldByName(fieldName).isEmpty()) {
            MockingRegistry.markAsMocked(registryKey);
            FieldDeclaration field = suite.addField(param.getType(), fieldName);
            field.addAnnotation("Mock");

            for (TypeWrapper wrapper : wrappers) {
                ImportWrapper imp = AbstractCompiler.findImport(cu, wrapper.getFullyQualifiedName());
                if (imp != null) {
                    addImport(imp.getImport());
                }
            }
        }
    }

    private Map<String, String> mapParamToFields(ConstructorDeclaration constructor) {
        Map<String, String> paramToFieldMap = new HashMap<>();

        constructor.getBody().findAll(AssignExpr.class).forEach(assignExpr -> {
            if (assignExpr.getTarget().isFieldAccessExpr()) {
                String fieldName = assignExpr.getTarget().asFieldAccessExpr().getName().asString();
                if (assignExpr.getValue().isNameExpr()) {
                    String paramName = assignExpr.getValue().asNameExpr().getNameAsString();
                    paramToFieldMap.put(paramName, fieldName);
                }
            }
        });

        return paramToFieldMap;
    }

    Optional<ClassOrInterfaceDeclaration> findSuite(TypeDeclaration<?> decl) {
        return gen.findFirst(ClassOrInterfaceDeclaration.class,
                t -> t.getNameAsString().equals(decl.getNameAsString() + TEST_NAME_SUFFIX));

    }

    @Override
    public void save() throws IOException {
        DepSolver.sortClass(testClass);
        // Remove duplicate tests before saving
        boolean removedDuplicates = removeDuplicateTests();
        if (removedDuplicates) {
            logger.info("Removed duplicate test methods from {}", filePath);
        }
        Antikythera.getInstance().writeFile(filePath, gen.toString());
    }

    static void replaceInitializer(MethodDeclaration method, String name, Expression initialization) {
        method.getBody().ifPresent(body ->{
            NodeList<Statement> statements = method.getBody().get().getStatements();
            for (Statement statement : statements) {
                if (statement instanceof ExpressionStmt exprStmt && exprStmt.getExpression() instanceof VariableDeclarationExpr varDeclExpr) {
                    for (VariableDeclarator varDeclarator : varDeclExpr.getVariables()) {
                        if (varDeclarator.getName().getIdentifier().equals(name)) {
                            varDeclarator.setInitializer(initialization);
                        }
                    }
                }
            }
        });
    }

    public static Expression assertLoggedWithLevel(String className, String level, String expectedMessage) {
        MethodCallExpr assertion = new MethodCallExpr("assertTrue");
        MethodCallExpr condition = new MethodCallExpr("LogAppender.hasMessage")
                .addArgument(new FieldAccessExpr(new NameExpr("Level"), level))
                .addArgument(new StringLiteralExpr(className))
                .addArgument(new StringLiteralExpr(expectedMessage));

        assertion.addArgument(condition);
        return assertion;
    }
}
