package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import com.github.javaparser.ast.type.Type;

import java.util.List;
import java.util.Map;

public class UnitTestGenerator extends TestGenerator {
    @Override
    public void createTests(MethodDeclaration md, ControllerResponse response) {
        MethodDeclaration testMethod = buildTestMethod(md);
        gen.getType(0).addMember(testMethod);

        createInstance(md, testMethod);
        mockArguments(md, testMethod);
        invokeMethod(md, testMethod);
        addAsserts(md, testMethod, response);
    }

    private void createInstance(MethodDeclaration md, MethodDeclaration testMethod) {
        md.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(c -> {
            ConstructorDeclaration matched = null;
            String className = md.findAncestor(TypeDeclaration.class).get().getNameAsString();

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

    void mockArguments(MethodDeclaration md, MethodDeclaration testMethod) {
        BlockStmt body = getBody(testMethod);
        for(var param : md.getParameters()) {
            String nameAsString = param.getNameAsString();
            Variable value = argumentGenerator.getArguments().get(nameAsString);
            if (value != null ) {
                body.addStatement(param.getTypeAsString() + " " + nameAsString + " = Mockito.mock(" + param.getTypeAsString() + ".class);");
            }
        }
    }


    void invokeMethod(MethodDeclaration md, MethodDeclaration testMethod) {
        BlockStmt body = getBody(testMethod);
        StringBuilder b = new StringBuilder();

        Type t = md.getType();
        if (t != null) {
            b.append(t.asString() + " result = ");
        }
        b.append("cls." + md.getNameAsString() + "(");
        for (int i = 0 ; i < md.getParameters().size(); i++) {
            b.append(md.getParameter(i).getNameAsString());
            if (i < md.getParameters().size() - 1) {
                b.append(", ");
            }
        }
        b.append(");");

        body.addStatement(b.toString());
    }

    private void addAsserts(MethodDeclaration md, MethodDeclaration testMethod, ControllerResponse response) {
        Type t = md.getType();
        BlockStmt body = getBody(testMethod);
        if (t != null) {
            body.addStatement("assertNotNull(result);");
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
        gen.addImport("org.springframework.boot.test.mock.mockito.MockBean");
        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            CompilationUnit cu = entry.getValue();
            for (TypeDeclaration<?> decl : cu.getTypes()) {
                decl.findAll(FieldDeclaration.class).forEach(fd -> {
                    fd.getAnnotationByName("Autowired").ifPresent(ann -> {
                        FieldDeclaration field = t.addField(fd.getElementType(), fd.getVariable(0).getNameAsString());
                        field.addAnnotation("MockBean");
                        ImportWrapper wrapper = AbstractCompiler.findImport(cu, field.getElementType().asString());
                        if (wrapper != null) {
                            gen.addImport(wrapper.getImport());
                        }
                    });
                });
            }
        }
    }
}
