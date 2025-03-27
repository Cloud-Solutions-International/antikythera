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
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnitTestGenerator extends TestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(UnitTestGenerator.class);
    private final String filePath;

    private boolean autoWired;
    private String instanceName;

    private final BiConsumer<Parameter, Variable> mocker;
    private final Consumer<Expression> applyPrecondition;

    public UnitTestGenerator(CompilationUnit cu) {
        super(cu);
        String packageDecl = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        String basePath = Settings.getProperty(Constants.BASE_PATH, String.class).orElseThrow();
        String className = AbstractCompiler.getPublicType(cu).getNameAsString() + "Test";

        filePath = basePath.replace("main","test") + File.separator +
                packageDecl.replace(".", File.separator) + File.separator + className + ".java";

        File file = new File(filePath);
        if (file.exists()) {
            try {
                loadExisting(file);
            } catch (FileNotFoundException e) {
                logger.warn("Could not find file: {}" , filePath);
                createTestClass(className, packageDecl);
            }
        }
        else {
            createTestClass(className, packageDecl);
        }

        if (Settings.getProperty("use_mockito", String.class).isPresent()) {
            this.mocker = this::mockWithMockito;
            this.applyPrecondition = this::applyPreconditionWithMockito;
        }
        else {
            this.mocker = this::mockWithEvaluator;
            this.applyPrecondition = this::applyPreconditionWithEvaluator;
        }
    }


    void loadExisting(File file) throws FileNotFoundException {
        gen = StaticJavaParser.parse(file);
        List<MethodDeclaration> remove = new ArrayList<>();
        for (MethodDeclaration md : gen.getType(0).getMethods()) {
            md.getComment().ifPresent(c -> {
                if (!c.getContent().contains("Author: Antikythera")) {
                    remove.add(md);
                }
            });
        }
        for (MethodDeclaration md : remove) {
            gen.getType(0).remove(md);
        }

        for (TypeDeclaration<?> t : gen.getTypes()) {
            if (t.isClassOrInterfaceDeclaration()) {
                loadBaseClassForTest(t.asClassOrInterfaceDeclaration());
            }
        }
    }

    private void createTestClass(String className, String packageDecl) {
        gen = new CompilationUnit();
        if (packageDecl != null && !packageDecl.isEmpty()) {
            gen.setPackageDeclaration(packageDecl);
        }

        ClassOrInterfaceDeclaration testClass = gen.addClass(className);
        loadBaseClassForTest(testClass);
    }

    private void loadBaseClassForTest(ClassOrInterfaceDeclaration testClass) {
        String base = Settings.getProperty("base_class", String.class).orElse(null);
        if (base != null) {
            if (!testClass.getExtendedTypes().stream().map(Type::asString).filter(s -> s.equals(base)).findFirst().isPresent()) {
                testClass.addExtendedType(base);
            }
            String basePath = Settings.getProperty(Constants.BASE_PATH, String.class).orElse(null);
            String helperPath = basePath.replace("main","test") + File.separator +
                    AbstractCompiler.classToPath(base);
            try {
                CompilationUnit cu = StaticJavaParser.parse(new File(helperPath));
                TypeDeclaration<?> t = AbstractCompiler.getPublicType(cu);
                for (FieldDeclaration fd : t.getFields()) {
                    if (fd.getAnnotationByName("MockBean").isPresent() ||
                            fd.getAnnotationByName("Mock").isPresent()) {
                        AntikytheraRunTime.markAsMocked(fd.getElementType());
                    }
                }
            } catch (FileNotFoundException e) {
                throw new AntikytheraException("Base class could not be loaded for tests.");
            }
        }
    }

    @Override
    public void createTests(MethodDeclaration md, MethodResponse response) {
        methodUnderTest = md;
        testMethod = buildTestMethod(md);
        gen.getType(0).addMember(testMethod);

        createInstance();
        mockArguments();
        String invocation = invokeMethod();

        if (response.getException() == null) {
            getBody(testMethod).addStatement(invocation);
            addAsserts(response);
        }
        else {
            String[] parts = invocation.split("=");
            assertThrows(parts.length == 2 ? parts[1] : parts[0], response);
        }
    }

    private void createInstance() {
        methodUnderTest.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(c -> {
            if (c.getAnnotationByName("Service").isPresent()) {
                autoWireClass(c);
            }
            else {
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

    private void autoWireClass(ClassOrInterfaceDeclaration classUnderTest) {
        ClassOrInterfaceDeclaration testClass = testMethod.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow();

        if (!autoWired) {
            gen.addImport("org.springframework.beans.factory.annotation.Autowired");

            for (FieldDeclaration fd : testClass.getFields()) {
                if (fd.getElementType().asString().equals(classUnderTest.getNameAsString())) {
                    autoWired = true;
                    instanceName = fd.getVariable(0).getNameAsString();
                    break;
                }
            }
        }
        if (!autoWired) {
            if (testClass.getAnnotationByName("ContextConfiguration").isEmpty()) {
                gen.addImport("org.springframework.test.context.ContextConfiguration");
                NormalAnnotationExpr contextConfig = new NormalAnnotationExpr();
                contextConfig.setName("ContextConfiguration");
                contextConfig.addPair("classes", String.format("{%s.class}", classUnderTest.getNameAsString()));
                testClass.addAnnotation(contextConfig);
            }
            if (testClass.getAnnotationByName("ExtendWith").isEmpty()) {
                gen.addImport("org.junit.jupiter.api.extension.ExtendWith");
                gen.addImport("org.springframework.test.context.junit.jupiter.SpringExtension");
                NormalAnnotationExpr extendsWith = new NormalAnnotationExpr();
                extendsWith.setName("ExtendWith");
                extendsWith.addPair("value", "SpringExtension.class");
                testClass.addAnnotation(extendsWith);
            }

            instanceName =  ClassProcessor.classToInstanceName( classUnderTest.getNameAsString());

            if (testClass.getFieldByName(classUnderTest.getNameAsString()).isEmpty()) {
                FieldDeclaration fd = testClass.addField(classUnderTest.getNameAsString(), instanceName);
                fd.addAnnotation("Autowired");
            }
            autoWired = true;
        }
    }

    void mockArguments() {
        for(var param : methodUnderTest.getParameters()) {
            addClassImports(param.getType());
            String nameAsString = param.getNameAsString();
            Variable value = argumentGenerator.getArguments().get(nameAsString);
            if (value != null ) {
                mocker.accept(param, value);
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
            ClassOrInterfaceDeclaration cdecl = AbstractCompiler.getPublicType(cu).asClassOrInterfaceDeclaration();
            if (cdecl != null) {
                instantiateClass(cdecl, nameAsString);
            } else {
                throw new AntikytheraException("Could not find class for " + t.asString());
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
        }
        else {
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
                            name.replace("set","get"),
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
        if (t != null) {
            b.append(t.asString() + " resp = ");
        }
        b.append(instanceName + "." + methodUnderTest.getNameAsString() + "(");
        for (int i = 0 ; i < methodUnderTest.getParameters().size(); i++) {
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
            body.addStatement(asserter.assertNotNull("resp"));
            asserter.addFieldAsserts(response, body);
        }
    }

    @Override
    public void setCommonPath(String commonPath) {
        throw new UnsupportedOperationException("Not needed here");
    }

    @Override
    public void addBeforeClass() {
        mockFields();
    }

    @Override
    public void mockFields() {
        TypeDeclaration<?> t = gen.getType(0);

        for (FieldDeclaration fd : t.getFields()) {
            AntikytheraRunTime.markAsMocked(fd.getElementType());
        }

        gen.addImport("org.springframework.boot.test.mock.mockito.MockBean");
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
     * @param cu the compilation unit that contains code to be tested.
     */
    private void mockFields(CompilationUnit cu) {
        final TypeDeclaration<?> t = gen.getType(0);
        for (TypeDeclaration<?> decl : cu.getTypes()) {
            for (FieldDeclaration fd : decl.getFields()) {
                if (fd.getAnnotationByName("Autowired").isPresent() && ! AntikytheraRunTime.isMocked(fd.getElementType())) {
                    AntikytheraRunTime.markAsMocked(fd.getElementType());
                    FieldDeclaration field = t.addField(fd.getElementType(), fd.getVariable(0).getNameAsString());
                    field.addAnnotation("MockBean");
                    ImportWrapper wrapper = AbstractCompiler.findImport(cu, field.getElementType().asString());
                    if (wrapper != null) {
                        gen.addImport(wrapper.getImport());
                    }
                }
            }
        }
    }

    @Override
    public void save() throws IOException {
        Antikythera.getInstance().writeFile(filePath, gen.toString());
    }
}
