package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestIfConditionVisitor {
    private static final String SAMPLE_CLASS = "sa.com.cloudsolutions.antikythera.evaluator.Conditional";
    private CompilationUnit cu;
    private IfConditionVisitor visitor;

    @BeforeAll
    static void setupAll() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AntikytheraRunTime.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setup() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        visitor = new IfConditionVisitor();
        Branching.clear();
    }

    @Test
    void testSimpleIfElse() {
        MethodDeclaration method = findMethod("conditional1");
        IfStmt ifStmt = method.findFirst(IfStmt.class).orElseThrow();
        visitor.visit(ifStmt, null);

        // Verify tree structure
        LineOfCode ifNode = Branching.get(ifStmt);
        assertNotNull(ifNode);
        assertTrue(ifNode.isUntravelled());

        List<LineOfCode> children = getChildren(ifNode);
        assertEquals(2, children.size());

        // Verify then branch
        LineOfCode thenNode = children.get(0);
        assertEquals(ifNode, thenNode.getParent());
        assertTrue(thenNode.isUntravelled());

        // Verify else branch
        LineOfCode elseNode = children.get(1);
        assertEquals(ifNode, elseNode.getParent());
        assertTrue(elseNode.isUntravelled());
    }

    @Test
    void testNestedIf() {
        MethodDeclaration method = findMethod("nested");
        IfStmt outerIf = method.findFirst(IfStmt.class).orElseThrow();
        visitor.visit(outerIf, null);

        // Verify outer if structure
        LineOfCode outerNode = Branching.get(outerIf);
        assertNotNull(outerNode);

        List<LineOfCode> outerChildren = getChildren(outerNode);
        assertEquals(2, outerChildren.size());

        // Verify inner if structure
        IfStmt innerIf = outerIf.getThenStmt().findFirst(IfStmt.class).orElseThrow();
        LineOfCode innerNode = Branching.get(innerIf);
        assertNotNull(innerNode);
        assertEquals(outerChildren.get(0), innerNode.getParent());
    }

    @Test
    void testMultiVariate() {
        MethodDeclaration method = findMethod("multiVariate");
        IfStmt outerIf = method.findFirst(IfStmt.class).orElseThrow();
        visitor.visit(outerIf, null);

        // Verify complete tree structure
        LineOfCode root = Branching.get(outerIf);
        assertNotNull(root);

        // Verify path states
        assertTrue(root.isUntravelled());
        List<LineOfCode> pathToRoot = Branching.getPathToRoot(outerIf);
        assertEquals(1, pathToRoot.size());

        // Verify inner conditions
        List<LineOfCode> children = getChildren(root);
        assertEquals(2, children.size());
        assertTrue(children.stream().allMatch(LineOfCode::isUntravelled));
    }

    @Test
    void testEmptyElseBlock() {
        MethodDeclaration method = findMethod("missingElse");
        IfStmt ifStmt = method.findFirst(IfStmt.class).orElseThrow();

        visitor.visit(ifStmt, null);

        LineOfCode ifNode = Branching.get(ifStmt);
        assertNotNull(ifNode);

        List<LineOfCode> children = getChildren(ifNode);
        assertEquals(2, children.size());

        assertInstanceOf(BlockStmt.class, children.get(1).getStatement());

        BlockStmt elseBlock = (BlockStmt)children.get(1).getStatement();
        assertTrue(elseBlock.isEmpty());
    }

    private MethodDeclaration findMethod(String methodName) {
        return cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals(methodName)).orElseThrow();
    }

    private List<LineOfCode> getChildren(LineOfCode parent) {
        return Branching.getAllNodes().stream()
                .filter(node -> node.getParent() == parent)
                .toList();
    }
}
