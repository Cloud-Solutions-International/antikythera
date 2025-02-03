package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.List;

public class UnitTestGenerator extends TestGenerator {
    @Override
    public void createTests(MethodDeclaration md, ControllerResponse response) {

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
}
