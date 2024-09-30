package sa.com.cloudsolutions.antikythera.generator;

import com.cloud.api.generator.ControllerResponse;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

public interface TestGenerator {
    void createTests(MethodDeclaration md, ControllerResponse response);

    /**
     * The common path represents the path declared in the RestController.
     * Every method in the end point will be relative to this.
     * @param commonPath
     */
    void setCommonPath(String commonPath);

    CompilationUnit getCompilationUnit();
}
