package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;

import java.util.List;
import java.util.Map;

public class UnitTestGenerator extends TestGenerator {
    @Override
    public void createTests(MethodDeclaration md, ControllerResponse response) {
        System.out.println("Creating tests for " + md.getNameAsString());
    }

    @Override
    public void setCommonPath(String commonPath) {

    }

    @Override
    public CompilationUnit getCompilationUnit() {
        return null;
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
        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            CompilationUnit cu = entry.getValue();
            for (TypeDeclaration<?> decl : cu.getTypes()) {
                decl.findAll(FieldDeclaration.class).forEach(fd -> {
                    fd.getAnnotationByName("Autowired").ifPresent(ann -> {
                        System.out.println("Autowired found: " + fd.getVariable(0).getNameAsString());
                    });
                });
            }
        }
    }
}
