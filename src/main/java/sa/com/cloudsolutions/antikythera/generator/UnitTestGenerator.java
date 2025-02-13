package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import com.github.javaparser.ast.type.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnitTestGenerator extends TestGenerator {
    private static final Logger logger = LoggerFactory.getLogger(SpringTestGenerator.class);
    private final String filePath;
    private CompilationUnit classUnderTest;
    private MethodDeclaration methodUnderTest;
    private MethodDeclaration testMethod;

    public UnitTestGenerator(CompilationUnit classUnderTest) {
        String packageDecl = classUnderTest.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        String basePath = Settings.getProperty(Constants.BASE_PATH, String.class).orElse(null);
        String className = AbstractCompiler.getPublicType(classUnderTest).getNameAsString() + "Test";
        this.classUnderTest = classUnderTest;

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
    }

    private void loadExisting(File file) throws FileNotFoundException {
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
    }

    private void createTestClass(String className, String packageDecl) {
        gen = new CompilationUnit();
        gen.setPackageDeclaration(packageDecl);
        gen.addClass(className);

    }

    @Override
    public void createTests(MethodDeclaration md, ControllerResponse response) {
        methodUnderTest = md;
        testMethod = buildTestMethod(md);
        gen.getType(0).addMember(testMethod);

        createInstance();
        mockArguments();
        invokeMethod();
        addAsserts(response);
    }

    private void createInstance() {
        methodUnderTest.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(c -> {
            ConstructorDeclaration matched = null;
            String className = methodUnderTest.findAncestor(TypeDeclaration.class).get().getNameAsString();

            for (ConstructorDeclaration cd : c.findAll(ConstructorDeclaration.class)) {
                if (matched == null) {
                    matched = cd;
                }
                if (matched.getParameters().size() > cd.getParameters().size()) {
                    matched = cd;
                }
            }
            if (matched != null) {
                StringBuilder b = new StringBuilder(className + " cls " + " = new " + className + "(");
                for (int i = 0; i < matched.getParameters().size(); i++) {
                    b.append("null");
                    if (i < matched.getParameters().size() - 1) {
                        b.append(", ");
                    }
                }
                b.append(");");
                getBody(testMethod).addStatement(b.toString());
            }
            else {
                getBody(testMethod).addStatement(className + " cls = new " + className + "();");
            }
        });
    }

    void mockArguments() {
        BlockStmt body = getBody(testMethod);

        for(var param : methodUnderTest.getParameters()) {
            addClassImports(param.getType());

            String nameAsString = param.getNameAsString();
            Variable value = argumentGenerator.getArguments().get(nameAsString);
            if (value != null ) {
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = Mockito.mock(" + param.getTypeAsString() + ".class);");
            }
        }
    }

    private void addClassImports(Type t) {
        for (ImportWrapper wrapper : AbstractCompiler.findImport(classUnderTest, t)) {
            gen.addImport(wrapper.getImport());
        }
    }

    void invokeMethod() {
        BlockStmt body = getBody(testMethod);
        StringBuilder b = new StringBuilder();

        Type t = methodUnderTest.getType();
        if (t != null) {
            b.append(t.asString() + " result = ");
        }
        b.append("cls." + methodUnderTest.getNameAsString() + "(");
        for (int i = 0 ; i < methodUnderTest.getParameters().size(); i++) {
            b.append(methodUnderTest.getParameter(i).getNameAsString());
            if (i < methodUnderTest.getParameters().size() - 1) {
                b.append(", ");
            }
        }
        b.append(");");

        body.addStatement(b.toString());
    }

    private void addAsserts(ControllerResponse response) {
        Type t = methodUnderTest.getType();
        BlockStmt body = getBody(testMethod);
        if (t != null) {
            addClassImports(t);
            asserter.assertNotNull(body, "result");
        }
    }

    @Override
    public void setCommonPath(String commonPath) {

    }

    @Override
    public void setPreconditions(List<Expression> expr) {

    }

    @Override
    public boolean isBranched() {
        return false;
    }

    @Override
    public void setBranched(boolean branched) {

    }

    @Override
    public void addBeforeClass() {
        mockFields();
    }

    @Override
    public void mockFields() {
        TypeDeclaration<?> t = gen.getType(0);
        Set<Type> oldFields = new HashSet<>();
        for (FieldDeclaration fd : t.getFields()) {
            oldFields.add(fd.getElementType());
        }

        gen.addImport("org.springframework.boot.test.mock.mockito.MockBean");
        gen.addImport("org.mockito.Mockito");
        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            CompilationUnit cu = entry.getValue();
            for (TypeDeclaration<?> decl : cu.getTypes()) {
                decl.findAll(FieldDeclaration.class).forEach(fd -> {
                    fd.getAnnotationByName("Autowired").ifPresent(ann -> {
                        if (!oldFields.contains(fd.getElementType())) {
                            FieldDeclaration field = t.addField(fd.getElementType(), fd.getVariable(0).getNameAsString());
                            field.addAnnotation("MockBean");
                            ImportWrapper wrapper = AbstractCompiler.findImport(cu, field.getElementType().asString());
                            if (wrapper != null) {
                                gen.addImport(wrapper.getImport());
                            }
                        }
                    });
                });
            }
        }
    }

    public void save() throws IOException {
        Antikythera.getInstance().writeFile(filePath, gen.toString());
    }
}
