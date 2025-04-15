package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.TestSuiteEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class UnitTestGenerator extends TestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(UnitTestGenerator.class);
    private final String filePath;
    private final BiConsumer<Parameter, Variable> mocker;
    private final Consumer<Expression> applyPrecondition;
    private boolean autoWired;
    private String instanceName;
    private CompilationUnit baseTestClass;

    public UnitTestGenerator(CompilationUnit cu) {
        super(cu);
        String packageDecl = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        String basePath = Settings.getProperty(Constants.BASE_PATH, String.class).orElseThrow();
        String className = AbstractCompiler.getPublicType(cu).getNameAsString() + "AKTest";

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
                MockingRegistry.markAsMocked(AbstractCompiler.findFullyQualifiedTypeName(fd.getVariable(0)));
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
     * that class can be found it will be loaded. If such an entry does not exist and the test suite
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
    private void loadPredefinedBaseClassForTest(String baseClassName) {
        String basePath = Settings.getProperty(Constants.BASE_PATH, String.class).orElseThrow();
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
    public void createTests(MethodDeclaration md, MethodResponse response) {
        methodUnderTest = md;
        testMethod = buildTestMethod(md);
        gen.getType(0).addMember(testMethod);

        createInstance();
        mockArguments();
        addWhens();
        addDependencies();
        String invocation = invokeMethod();

        if (response.getException() == null) {
            getBody(testMethod).addStatement(invocation);
            addAsserts(response);
        } else {
            String[] parts = invocation.split("=");
            assertThrows(parts.length == 2 ? parts[1] : parts[0], response);
        }
    }

    private void addDependencies() {
        for (String s : TestGenerator.getDependencies()) {
            gen.addImport(s);
        }
    }

    /**
     * Deals with adding Mockito.when().then() type expressions to the generated tests.
     */
    private void addWhens() {
        for (Expression expr : whenThen) {
            if (expr instanceof MethodCallExpr mce) {
                Optional<Expression> scope = mce.getScope();
                if (scope.isPresent() && scope.get() instanceof MethodCallExpr scoped) {

                    Optional<Expression> arg = scoped.getArguments().getFirst();
                    if (arg.isPresent() && baseTestClass != null
                            && arg.get() instanceof MethodCallExpr argMethod
                            && mockedByBaseTestClass(argMethod.getScope().orElseThrow())) {
                        continue;
                    }
                }
            }
            getBody(testMethod).addStatement(expr);
        }
        whenThen.clear();
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

    @SuppressWarnings("unchecked")
    void createInstance() {
        methodUnderTest.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(c -> {
            if (c.getAnnotationByName("Service").isPresent()) {
                injectMocks(c);
            } else {
                instanceName = ClassProcessor.classToInstanceName(c.getNameAsString());
                instantiateClass(c, instanceName);
            }
        });
    }

    void instantiateClass(ClassOrInterfaceDeclaration classUnderTest, String instanceName) {

        ConstructorDeclaration matched = null;
        String className = classUnderTest.getNameAsString();

        for (ConstructorDeclaration cd : classUnderTest.findAll(ConstructorDeclaration.class)) {
            if (matched == null) {
                matched = cd;
            }
            if (matched.getParameters().size() > cd.getParameters().size()) {
                matched = cd;
            }
        }
        if (matched != null) {
            StringBuilder b = new StringBuilder(className + " " + instanceName + " " + " = new " + className + "(");
            for (int i = 0; i < matched.getParameters().size(); i++) {
                b.append("null");
                if (i < matched.getParameters().size() - 1) {
                    b.append(", ");
                }
            }
            b.append(");");
            getBody(testMethod).addStatement(b.toString());
        } else {
            getBody(testMethod).addStatement(className + " " + instanceName + " = new " + className + "();");
        }
    }

    private void injectMocks(ClassOrInterfaceDeclaration classUnderTest) {
        ClassOrInterfaceDeclaration testClass = testMethod.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow();
        gen.addImport("org.mockito.InjectMocks");

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
            if (paramType.isPrimitiveType() || paramType.asClassOrInterfaceType().isBoxedType()) {
                String s = String.format("%s %s = %s;",
                        param.getTypeAsString(),
                        nameAsString,
                        Reflect.getDefault(paramType.asString())
                );
                getBody(testMethod).addStatement(s);
            } else {
                addClassImports(paramType);
                Variable value = argumentGenerator.getArguments().get(nameAsString);
                if (value != null) {
                    mocker.accept(param, value);
                }
            }
        }
        applyPreconditions();
    }

    private void mockWithEvaluator(Parameter param, Variable v) {
        String nameAsString = param.getNameAsString();
        if (v != null && v.getInitializer() != null) {
            getBody(testMethod).addStatement(param.getTypeAsString() + " " + nameAsString + " = " + v.getInitializer() + ";");
        }
        Type t = param.getType();
        String fullName = AbstractCompiler.findFullyQualifiedName(compilationUnitUnderTest, t.asString());
        if (fullName != null) {
            CompilationUnit cu = Graph.getDependencies().get(fullName);
            if (cu != null) {
                AbstractCompiler.getMatchingType(cu, t.asString()).ifPresentOrElse(type ->
                                instantiateClass(type.asClassOrInterfaceDeclaration(), nameAsString)
                        , () -> {
                            throw new AntikytheraException("Could not find matching type " + fullName);
                        });
            }
        }
    }

    private void mockWithMockito(Parameter param, Variable v) {
        String nameAsString = param.getNameAsString();
        BlockStmt body = getBody(testMethod);
        Type t = param.getType();
        if (t != null && t.isClassOrInterfaceType() && t.asClassOrInterfaceType().getTypeArguments().isPresent()) {
            body.addStatement(param.getTypeAsString() + " " + nameAsString +
                    " = Mockito.mock(" + t.asClassOrInterfaceType().getNameAsString() + ".class);");
        } else {
            body.addStatement(param.getTypeAsString() + " " + nameAsString +
                    " = Mockito.mock(" + param.getTypeAsString() + ".class);");
        }
    }

    private void applyPreconditions() {
        for (Expression expr : preConditions) {
            applyPrecondition.accept(expr);
        }
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
    }

    private void addClassImports(Type t) {
        for (ImportWrapper wrapper : AbstractCompiler.findImport(compilationUnitUnderTest, t)) {
            gen.addImport(wrapper.getImport());
        }
    }

    String invokeMethod() {
        StringBuilder b = new StringBuilder();

        Type t = methodUnderTest.getType();
        if (t != null && !t.toString().equals("void")) {
            b.append(t.asString()).append(" resp = ");
        }
        b.append(instanceName + "." + methodUnderTest.getNameAsString() + "(");
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
            if (response.getBody() != null && response.getBody().getValue() != null) {
                body.addStatement(asserter.assertNotNull("resp"));
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
        for (TypeDeclaration<?> t : gen.getTypes()) {
            t.addMember(before);
        }
    }

    @Override
    public void mockFields() {
        for (TypeDeclaration<?> t : gen.getTypes()) {
            for (FieldDeclaration fd : t.getFields()) {
                MockingRegistry.markAsMocked(AbstractCompiler.findFullyQualifiedTypeName(fd.getVariable(0)));
            }
        }

        gen.addImport("org.mockito.MockitoAnnotations");
        gen.addImport("org.junit.jupiter.api.BeforeEach");
        gen.addImport("org.mockito.Mock");
        gen.addImport("org.mockito.Mockito");

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
        gen.findFirst(TypeDeclaration.class, t -> t.getNameAsString().equals(decl.getNameAsString() + "AKTest"))
                .ifPresent(t -> {
                            for (FieldDeclaration fd : decl.getFields()) {
                                String fullyQualifiedTypeName = AbstractCompiler.findFullyQualifiedTypeName(fd.getVariable(0));
                                if (fd.getAnnotationByName("Autowired").isPresent() && !MockingRegistry.isMockTarget(fullyQualifiedTypeName)) {
                                    MockingRegistry.markAsMocked(fullyQualifiedTypeName);
                                    FieldDeclaration field = t.addField(fd.getElementType(), fd.getVariable(0).getNameAsString());
                                    field.addAnnotation("Mock");
                                    ImportWrapper wrapper = AbstractCompiler.findImport(cu, field.getElementType().asString());
                                    if (wrapper != null) {
                                        gen.addImport(wrapper.getImport());
                                    }
                                }
                            }
                        }
                );
    }

    private void detectConstructorInjection(CompilationUnit cu, TypeDeclaration<?> decl) {
        gen.findFirst(TypeDeclaration.class, t -> t.getNameAsString().equals(decl.getNameAsString() + "AKTest"))
                .ifPresent(t -> {
                            for (ConstructorDeclaration constructor : decl.getConstructors()) {
                                // Process constructor parameters as autowired fields
                                for (Parameter param : constructor.getParameters()) {
                                    String paramType = param.getTypeAsString();
                                    String paramName = param.getNameAsString();
                                    String fullyQualifiedType = AbstractCompiler.findFullyQualifiedName(cu, paramType);

                                    if (!MockingRegistry.isMockTarget(fullyQualifiedType)) {
                                        MockingRegistry.markAsMocked(fullyQualifiedType);
                                        FieldDeclaration field = t.addField(param.getType(), paramName);
                                        field.addAnnotation("Mock");
                                        ImportWrapper wrapper = AbstractCompiler.findImport(cu, paramType);
                                        if (wrapper != null) {
                                            gen.addImport(wrapper.getImport());
                                        }
                                    }
                                }
                            }
                        }
                );
    }

    @Override
    public void save() throws IOException {
        Antikythera.getInstance().writeFile(filePath, gen.toString());
    }
}
