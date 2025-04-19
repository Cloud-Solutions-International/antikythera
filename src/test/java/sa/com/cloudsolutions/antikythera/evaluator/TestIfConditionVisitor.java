package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

class TestIfConditionVisitor {

    private CompilationUnit cu;
    private MethodDeclaration md;

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void loadCompilationUnitAndMethod() {
        String cls = "sa.com.cloudsolutions.antikythera.evaluator.Conditional";
        cu = AntikytheraRunTime.getCompilationUnit(cls);
        Branching.clear();
    }
}