package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;

import java.util.List;

public abstract class TestGenerator {
    protected ArgumentGenerator argumentGenerator;

    public abstract void createTests(MethodDeclaration md, ControllerResponse response);

    /**
     * The common path represents the path declared in the RestController.
     * Every method in the end point will be relative to this.
     * @param commonPath the url param for the controller.
     */
    public abstract void setCommonPath(String commonPath);

    public abstract CompilationUnit getCompilationUnit();

    public abstract void setPreconditions(List<Expression> expr);

    public abstract boolean isBranched();

    public abstract void setBranched(boolean branched);

    public abstract void setQuery(RepositoryQuery query);

    public abstract RepositoryQuery getQuery();

    public ArgumentGenerator getArgumentGenerator() {
        return argumentGenerator;
    }

    public void setArgumentGenerator(ArgumentGenerator argumentGenerator) {
        this.argumentGenerator = argumentGenerator;
    }
}
