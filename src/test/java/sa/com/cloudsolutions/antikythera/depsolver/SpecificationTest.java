package sa.com.cloudsolutions.antikythera.depsolver;


import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecificationTest {
    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        DepSolver.reset();
    }

    @Test
    void testSpecification() {
        DepSolver depSolver = DepSolver.createSolver();
        DepSolver.reset();

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.antikythera.evaluator.FakeService");
        TypeDeclaration<?> t = cu.getType(0).asClassOrInterfaceDeclaration();
        GraphNode node = Graph.createGraphNode(t.asClassOrInterfaceDeclaration().findFirst(
                MethodDeclaration.class, md -> md.getNameAsString().equals("searchFakeDataWithCriteria")
        ).orElseThrow()); // Use the Graph.createGraphNode method to create GraphNode

        depSolver.dfs();
        Map<String, CompilationUnit> a = Graph.getDependencies();
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeService"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeSearchModel"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeEntity"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.FakeRepository"));
        assertTrue(a.containsKey("sa.com.cloudsolutions.antikythera.evaluator.CrazySpecification"));

        CompilationUnit cs = a.get("sa.com.cloudsolutions.antikythera.evaluator.CrazySpecification");
        ClassOrInterfaceDeclaration cst = cs.getType(0).asClassOrInterfaceDeclaration().asClassOrInterfaceDeclaration();
        assertEquals(1, cst.findAll(MethodDeclaration.class).size());

    }
}
