package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;

import java.util.List;

public interface TestGenerator {
    void createTests(MethodDeclaration md, ControllerResponse response);

    /**
     * The common path represents the path declared in the RestController.
     * Every method in the end point will be relative to this.
     * @param commonPath the url param for the controller.
     */
    void setCommonPath(String commonPath);

    CompilationUnit getCompilationUnit();

    void setPreconditions(List<Expression> expr);
}
