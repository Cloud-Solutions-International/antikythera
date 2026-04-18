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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLineOfCode {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @Test
    void tracksStructuralPredecessorsWithoutDuplication() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.evaluator.BranchingCombinations"
        );
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialDirect")).orElseThrow();
        IfStmt first = method.findAll(IfStmt.class).getFirst();
        IfStmt second = method.findAll(IfStmt.class).getLast();
        LineOfCode root = new LineOfCode(first);
        LineOfCode child = new LineOfCode(second);

        child.addPredecessor(root);
        child.addPredecessor(root);

        assertEquals(1, child.getPredecessors().size());
        assertTrue(child.getPredecessors().contains(root));
    }

    @Test
    void preservedPathStateTracksSidesByBranchIdentity() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Conditional"
        );
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("conditional4")).orElseThrow();
        IfStmt ifStmt = method.findAll(IfStmt.class).getFirst();
        LineOfCode lineOfCode = new LineOfCode(ifStmt);

        PreservedPathState state = PreservedPathState.empty().with(lineOfCode, BranchSide.TRUE);

        assertEquals(BranchSide.TRUE, state.sideFor(lineOfCode).orElseThrow());
        assertEquals(1, state.asMap().size());
    }
}
