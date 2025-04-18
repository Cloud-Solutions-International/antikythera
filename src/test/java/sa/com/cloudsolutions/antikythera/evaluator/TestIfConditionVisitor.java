package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    }

    @Test
    void testCollectConditionsUpToMethod() {
        md = cu.findFirst(MethodDeclaration.class, f -> f.getNameAsString().equals("multiVariate")).get();
        md.accept(new VoidVisitorAdapter<Void>() {
                      @Override
                      public void visit(IfStmt n, Void arg) {
                          super.visit(n, arg);
                          List<Expression> conditions = IfConditionVisitor.collectConditionsUpToMethod(n);
                          if (n.getCondition().toString().equals("a == 0")) {
                              assertEquals(0, conditions.size());
                          } else {
                              assertEquals(1, conditions.size());
                          }
                      }
                  }, null
        );
    }

    @Test
    void testCollectConditionsUpToMethodAgain() {
        md = cu.findFirst(MethodDeclaration.class, f -> f.getNameAsString().equals("multiVariateDeep")).get();
        md.accept(new VoidVisitorAdapter<Void>() {
                      @Override
                      public void visit(IfStmt n, Void arg) {
                          super.visit(n, arg);
                          List<Expression> conditions = IfConditionVisitor.collectConditionsUpToMethod(n);
                          if (n.getCondition().toString().equals("a == 0")) {
                              assertEquals(0, conditions.size());
                          } else if (n.getCondition().toString().equals("b == 1")) {
                              assertEquals(2, conditions.size());
                          } else {
                              assertEquals(1, conditions.size());
                          }
                      }
                  }, null
        );
    }

    @Test
    void testGraph() {
        md = cu.findFirst(MethodDeclaration.class, f -> f.getNameAsString().equals("multiVariateDeep")).get();
        md.accept(new IfConditionVisitor(), null);
        List<LineOfCode> lines = Branching.get(md);
        assertEquals(4, lines.size());

        for (LineOfCode line : lines) {
            assertTrue(line.isUntravelled());
        }
        assertFalse(Branching.isCovered(md));

        lines.get(0).setResult(true);
        lines.get(2).setPathTaken(LineOfCode.BOTH_PATHS);
        assertTrue(lines.get(0).isUntravelled());
        assertTrue(lines.get(1).isFalsePath());
        assertFalse(Branching.isCovered(md));

        lines.get(1).setResult(true);
        lines.get(1).setPathTaken(LineOfCode.BOTH_PATHS);
        assertTrue(lines.get(1).isFullyTravelled());
        assertFalse(Branching.isCovered(md));

        lines.get(3).setPathTaken(LineOfCode.BOTH_PATHS);
        assertTrue(Branching.isCovered(md));

    }
}