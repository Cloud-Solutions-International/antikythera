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
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Precondition;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.TestSuiteEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Unit test generator.
 *
 * The responsibility of deciding what should be mocked and what should not be mocked lies here.
 * Each class that is marked as autowired will be considered a candidate for mocking. These will
 * be registered in the mocking registry.
 */

public class UnitTestGenerator extends TestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(UnitTestGenerator.class);
    public static final String TEST_NAME_SUFFIX = "AKTest";
    private final String filePath;
    private final BiConsumer<Parameter, Variable> mocker;
    private final Consumer<Expression> applyPrecondition;
    private boolean autoWired;
    private String instanceName;
    private CompilationUnit baseTestClass;

    public UnitTestGenerator(CompilationUnit cu) {
        super(cu);
        String packageDecl = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        String basePath = Settings.getProperty(Settings.BASE_PATH, String.class).orElseThrow();
        String className = AbstractCompiler.getPublicType(cu).getNameAsString() + TEST_NAME_SUFFIX;

        filePath = basePath.replace("main", "test") + File.separator +
                packageDecl.replace(".", File.separator) + File.separator + className + ".java";

        File file = new File(filePath);

        try {
            loadExisting(file);
        } catch (FileNotFoundException e) {
            logger.warn("Could not find file: {}", filePath);
            createTestClass(className, packageDecl);
        }

        if ("Evaluator".equals(Settings.getProperty(Settings.MOCK_WITH, String.class).orElse(""))) {
            this.mocker = this::mockWithEvaluator;
            this.applyPrecondition = this::applyPreconditionWithEvaluator;
        } else {
            this.mocker = this::mockWithMockito;
            this.applyPrecondition = this::applyPreconditionWithMockito;
        }
    }

    /**
     * Attempt to identify which fields have already been mocked.
     *
     * @param t the type declaration which holds the fields being mocked.
     */
    private static void identifyMockedTypes(TypeDeclaration<?> t) {
        for (FieldDeclaration fd : t.getFields()) {
            if (fd.getAnnotationByName("MockBean").isPresent() ||
                    fd.getAnnotationByName("Mock").isPresent()) {
                List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(fd.getVariable(0));
                MockingRegistry.markAsMocked(wrappers.getLast().getFullyQualifiedName());
            }
        }

    }

    /**
     * Loads any existing test class that has been generated previously.
     * This code is typically not available through the AntikythereRunTime class because we are
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
            identifyMockedTypes(t);
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

        ClassOrInterfaceDeclaration testClass = gen.addClass(className);
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
        String base = Settings.getProperty("base_test_class", String.class).orElse(null);
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
        String helperPath = basePath.replace("main", "test") + File.separator +
                AbstractCompiler.classToPath(baseClassName);
        try {
            baseTestClass = StaticJavaParser.parse(new File(helperPath));
            for (TypeDeclaration<?> t : baseTestClass.getTypes()) {
                identifyMockedTypes(t);
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
            throw new AntikytheraException("Base class could not be loaded for tests.");
        }
    }

    @Override
    public void     createTests(MethodDeclaration md, MethodResponse response) {
        methodUnderTest = md;
        testMethod = buildTestMethod(md);
        gen.getType(0).addMember(testMethod);
        createInstance();
        mockArguments();
        applyPreconditions();
        addWhens();
        String invocation = invokeMethod();
        addDependencies();

        if (response.getException() == null) {
            getBody(testMethod).addStatement(invocation);
            addAsserts(response);
        } else {
            String[] parts = invocation.split("=");
            assertThrows(parts.length == 2 ? parts[1] : parts[0], response);
        }
    }

    private void addDependencies() {
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
                if (mockedByBaseTestClass(argMethod.getScope().orElseThrow())) {
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
        else if (typeName.startsWith("List") || typeName.startsWith("java.util.List")) {
            TestGenerator.addImport(new ImportDeclaration("java.util.List", false, false));
        }
        else if (typeName.startsWith("Map") || typeName.startsWith("java.util.Map")) {
            TestGenerator.addImport(new ImportDeclaration("java.util.Map", false, false));
        }
        else {
            logger.warn("Unable to find import for: {}", ct.getNameAsString());
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
            if (c.getAnnotationByName("Service").isPresent()) {
                injectMocks(c);
            } else {
                instanceName = ClassProcessor.classToInstanceName(c.getNameAsString());
                getBody(testMethod).addStatement(ArgumentGenerator.instantiateClass(c, instanceName));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void injectMocks(ClassOrInterfaceDeclaration classUnderTest) {
        ClassOrInterfaceDeclaration testClass = testMethod.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow();
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
            instanceName = ClassProcessor.classToInstanceName(classUnderTest.getNameAsString());

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
            } else {
                addClassImports(paramType);
                Variable value = argumentGenerator.getArguments().get(nameAsString);
                if (value != null) {
                    mocker.accept(param, value);
                }
            }
        }
    }

     void mockWithEvaluator(Parameter param, Variable v) {
        String nameAsString = param.getNameAsString();
        if (v != null && v.getInitializer() != null) {
            getBody(testMethod).addStatement(param.getTypeAsString() + " " + nameAsString + " = " + v.getInitializer() + ";");
        }
        Type t = param.getType();
        TypeWrapper wrapper = AbstractCompiler.findType(compilationUnitUnderTest, t);

        if (wrapper != null) {
            if (wrapper.getType() != null) {
                getBody(testMethod).addStatement(
                        ArgumentGenerator.instantiateClass(wrapper.getType().asClassOrInterfaceDeclaration(),
                                nameAsString));
            }
            else {
                throw new UnsupportedOperationException("Not yet implemented");
            }
        }
    }

    void mockWithMockito(Parameter param, Variable v) {
        String nameAsString = param.getNameAsString();
        BlockStmt body = getBody(testMethod);
        Type t = param.getType();

        if (param.findCompilationUnit().isPresent()) {
            CompilationUnit cu = param.findCompilationUnit().orElseThrow();
            if (t instanceof ArrayType) {
                Variable mocked = Reflect.variableFactory(t.asString());
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = " + mocked.getInitializer() + ";");
                mockParameterFields(v, nameAsString);
                return;
            }
            if ( AbstractCompiler.isFinalClass(param.getType(), cu)) {
                cantMockFinalClass(param, v, cu);
                return;
            }
        }
        if (t != null && t.isClassOrInterfaceType() && t.asClassOrInterfaceType().getTypeArguments().isPresent()) {
            body.addStatement(param.getTypeAsString() + " " + nameAsString +
                    " = Mockito.mock(" + t.asClassOrInterfaceType().getNameAsString() + ".class);");
        } else {
            body.addStatement(param.getTypeAsString() + " " + nameAsString +
                    " = Mockito.mock(" + param.getTypeAsString() + ".class);");
        }

        mockParameterFields(v, nameAsString);
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
                        "Optional.of(" + mocked.getInitializer() + ");");
            } else {
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = Optional.empty();");
            }
        }
        else {
            body.addStatement(param.getTypeAsString() + " " + nameAsString + " = " + mocked.getInitializer() + ";");
            mockParameterFields(v, nameAsString);
        }
    }

    void mockParameterFields(Variable v, String nameAsString) {
        BlockStmt body = getBody(testMethod);
        if (v.getValue() instanceof Evaluator eval) {
            for (Map.Entry<String,Variable> entry : eval.getFields().entrySet()) {
                if (entry.getValue().getValue() != null && entry.getValue().getType() != null
                        && entry.getValue().getType().isPrimitiveType()
                        && !entry.getKey().equals("serialVersionUID")) {

                    Object value = entry.getValue().getValue();
                    body.addStatement(String.format("Mockito.when(%s.get%s()).thenReturn(%s);",
                            nameAsString,
                            ClassProcessor.instanceToClassName(entry.getKey()),
                            value instanceof Long ? value + "L" : value.toString()));
                }
            }
        }
    }

    private void applyPreconditions() {
        for (MockingCall  result : MockingRegistry.getAllMocks()) {
            if (! result.isFromSetup()) {
                applyRegistryCondition(result);
            }
        }

        for (Precondition expr : preConditions) {
            applyPrecondition.accept(expr.getExpression());
        }
    }

    void applyRegistryCondition(MockingCall result) {
        if (result.getVariable().getValue() instanceof Optional<?>) {
            applyPreconditionsForOptionals(result);
        }
        else {
            if (result.getExpression() != null) {
                addWhenThen(result.getExpression());
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

    private void applyPreconditionWithEvaluator(Expression expr) {
        BlockStmt body = getBody(testMethod);
        body.addStatement(expr);
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
                gen.addImport(imp.getImport());
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
        BlockStmt body = getBody(testMethod);
        if (t != null) {
            addClassImports(t);
            Variable result = response.getBody();
            if (result != null && result.getValue() != null) {
                body.addStatement(asserter.assertNotNull("resp"));
                if (result.getValue() instanceof Collection<?> c) {
                    if (c.isEmpty()) {
                        body.addStatement(asserter.assertEmpty("resp"));
                    }
                    else {
                        body.addStatement(asserter.assertNotEmpty("resp"));
                    }
                }
                asserter.addFieldAsserts(response, body);
            } else {
                body.addStatement(asserter.assertNull("resp"));
            }
        }
    }

    @Override
    public void setCommonPath(String commonPath) {
        throw new UnsupportedOperationException("Not needed here");
    }

    @Override
    public void addBeforeClass() {
        mockFields();

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
    public void mockFields() {
        for (TypeDeclaration<?> t : gen.getTypes()) {
            for (FieldDeclaration fd : t.getFields()) {
                List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(fd.getVariable(0));
                MockingRegistry.markAsMocked(wrappers.getLast().getFullyQualifiedName());
            }
        }

        gen.addImport("org.mockito.MockitoAnnotations");
        gen.addImport("org.junit.jupiter.api.BeforeEach");
        gen.addImport("org.mockito.Mock");
        gen.addImport("org.mockito.Mockito");
        gen.addImport("java.util.Optional");

        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            CompilationUnit cu = entry.getValue();
            mockFields(cu);
        }

        mockFields(compilationUnitUnderTest);
    }

    /**
     * Mock all the fields that have been marked as Autowired
     * Mockito.Mock will be preferred over Mockito.MockBean
     *
     * @param cu the compilation unit that contains code to be tested.
     */
    private void mockFields(CompilationUnit cu) {

        for (TypeDeclaration<?> decl : cu.getTypes()) {
            decl.getAnnotationByName("Service")
                    .ifPresent(b -> detectConstructorInjection(cu, decl));
            detectAutowiring(cu, decl);
        }
    }

    private void detectAutowiring(CompilationUnit cu, TypeDeclaration<?> decl) {
        Optional<ClassOrInterfaceDeclaration> suite = findSuite(decl);
        if (suite.isEmpty()) {
            return;
        }
        detectAutoWiringHelper(cu, decl, suite.get());
    }

    private void detectAutoWiringHelper(CompilationUnit cu, TypeDeclaration<?> decl, ClassOrInterfaceDeclaration t) {
        for (FieldDeclaration fd : decl.getFields()) {
            List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(fd.getVariable(0));
            if (wrappers.isEmpty()) {
                continue;
            }
            String registryKey = MockingRegistry.generateRegistryKey(wrappers);
            if (fd.getAnnotationByName("Autowired").isPresent() && !MockingRegistry.isMockTarget(registryKey)) {
                MockingRegistry.markAsMocked(registryKey);
                FieldDeclaration field = t.addField(fd.getElementType(), fd.getVariable(0).getNameAsString());
                field.addAnnotation("Mock");
                ImportWrapper wrapper = AbstractCompiler.findImport(cu, field.getElementType().asString());
                if (wrapper != null) {
                    gen.addImport(wrapper.getImport());
                }
            }
        }
    }

    private void detectConstructorInjection(CompilationUnit cu, TypeDeclaration<?> decl) {
        Optional<ClassOrInterfaceDeclaration> suite = findSuite(decl);
        if (suite.isEmpty()) {
            return;
        }

        ClassOrInterfaceDeclaration testClass = suite.get();
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
                    gen.addImport(imp.getImport());
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
}
