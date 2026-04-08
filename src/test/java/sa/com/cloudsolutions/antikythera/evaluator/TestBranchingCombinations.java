package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestBranchingCombinations extends TestHelper {
    private static final String SAMPLE_CLASS =
            "sa.com.cloudsolutions.antikythera.testhelper.evaluator.BranchingCombinations";
    private CompilationUnit cu;

    @BeforeAll
    static void setupFixture() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
        AntikytheraRunTime.reset();
        MockingRegistry.reset();
    }

    @BeforeEach
    void each() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
        ((SpringEvaluator) evaluator).setArgumentGenerator(new DummyArgumentGenerator());
        System.setOut(new PrintStream(outContent));
        Branching.clear();
        BranchingTrace.clear();
    }

    @Test
    void sequentialDirectVisitEmitsMultiplePaths() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialDirect")).orElseThrow();

        evaluator.visit(method);

        String output = outContent.toString();
        assertTrue(output.contains("TYPE_"), "Expected diagnosis-type branch output");
        assertTrue(output.contains("VALUES_"), "Expected values branch output");
    }

    @Test
    void deletedByDirectVisitEmitsMultiplePaths() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("deletedByDirect")).orElseThrow();

        evaluator.visit(method);

        String output = outContent.toString();
        assertTrue(output.contains("ALL") || output.contains("OPEN"), "Expected record-source branch output");
        assertTrue(output.contains("DELETED") || output.contains("ACTIVE"), "Expected deletedBy branch output");
    }

    @Test
    void sequentialDirectRecordsTruthTableSelectionTrace() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialDirect")).orElseThrow();

        evaluator.visit(method);

        List<String> trace = BranchingTrace.snapshot();
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("target:sequentialDirect")),
                "Expected target trace entries for sequentialDirect");
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("truthTable:sequentialDirect")),
                "Expected truth-table trace entries for sequentialDirect");
        assertTrue(trace.stream().anyMatch(event -> event.startsWith("selected:sequentialDirect")),
                "Expected selected-combination trace entries for sequentialDirect");
    }

    @Disabled("Pending branch-combination exploration fix")
    @Test
    void sequentialDirectShouldCoverAllFourCombinations() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialDirect")).orElseThrow();

        evaluator.visit(method);

        Set<String> combinations = extractCombinations(
                outContent.toString(),
                Pattern.compile("TYPE_(?:EMPTY|SET)\\|VALUES_(?:EMPTY|PRESENT)")
        );
        assertEquals(4, combinations.size());
    }

    @Disabled("Pending branch-combination exploration fix")
    @Test
    void deletedByDirectShouldCoverAllFourCombinations() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("deletedByDirect")).orElseThrow();

        evaluator.visit(method);

        Set<String> combinations = extractCombinations(
                outContent.toString(),
                Pattern.compile("(?:ALL|OPEN)\\|(?:DELETED|ACTIVE)")
        );
        assertEquals(4, combinations.size());
    }

    private Set<String> extractCombinations(String text, Pattern pattern) {
        Set<String> combinations = new HashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            combinations.add(matcher.group());
        }
        return combinations;
    }
}
