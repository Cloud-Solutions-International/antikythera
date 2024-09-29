package sa.com.cloudsolutions.antikythera.generator;

import com.cloud.api.generator.ControllerResponse;
import com.github.javaparser.ast.body.MethodDeclaration;

public interface TestGenerator {
    void createTests(MethodDeclaration md, ControllerResponse response);
}
