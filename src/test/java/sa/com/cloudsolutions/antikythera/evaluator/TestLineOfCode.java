package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestLineOfCode {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @Test
    void tracksCombinationAttemptsPerBranchSide() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Conditional"
        );
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("conditional1")).orElseThrow();
        IfStmt ifStmt = method.findFirst(IfStmt.class).orElseThrow();
        LineOfCode lineOfCode = new LineOfCode(ifStmt);

        lineOfCode.recordCombinationAttempt(LineOfCode.FALSE_PATH, "row:false:0");
        lineOfCode.recordCombinationAttempt(LineOfCode.FALSE_PATH, "row:false:0");
        lineOfCode.recordCombinationAttempt(LineOfCode.TRUE_PATH, "row:true:1");

        assertEquals(Set.of("row:false:0"), lineOfCode.getAttemptedCombinations(LineOfCode.FALSE_PATH));
        assertEquals(Set.of("row:true:1"), lineOfCode.getAttemptedCombinations(LineOfCode.TRUE_PATH));
        assertEquals(1, lineOfCode.getAttemptedCombinationCount(LineOfCode.FALSE_PATH));
        assertEquals(1, lineOfCode.getAttemptedCombinationCount(LineOfCode.TRUE_PATH));
    }

    @Test
    void rejectsInvalidCombinationAttemptState() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Conditional"
        );
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("conditional1")).orElseThrow();
        IfStmt ifStmt = method.findFirst(IfStmt.class).orElseThrow();
        LineOfCode lineOfCode = new LineOfCode(ifStmt);

        assertThrows(IllegalArgumentException.class,
                () -> lineOfCode.recordCombinationAttempt(LineOfCode.BOTH_PATHS, "row:both"));
    }
}
