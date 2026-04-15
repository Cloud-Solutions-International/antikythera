package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
    void each() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AntikytheraRunTime.reset();
        MockingRegistry.reset();
        AbstractCompiler.preProcess();
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        evaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
        ((SpringEvaluator) evaluator).setArgumentGenerator(new DummyArgumentGenerator());
        System.setOut(new PrintStream(outContent));
        Branching.clear();
        BranchingTrace.enable();
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
    void sequentialProblemStringsDoesNotCrashDuringEvaluation() {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialProblemStrings")).orElseThrow();

        assertDoesNotThrow(() -> evaluator.visit(method));
    }

    @Test
    void deletedByLookupDoesNotCrashDuringEvaluation() {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("deletedByLookup")).orElseThrow();

        assertDoesNotThrow(() -> evaluator.visit(method));
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

    @Test
    void sequentialDirectRecordsMultipleSelectedRowFingerprints() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialDirect")).orElseThrow();

        evaluator.visit(method);

        Set<String> fingerprints = BranchingTrace.snapshot().stream()
                .filter(event -> event.startsWith("selectedRow:sequentialDirect"))
                .map(this::extractFingerprint)
                .filter(fingerprint -> !fingerprint.isEmpty())
                .collect(Collectors.toSet());

        assertTrue(fingerprints.size() > 1,
                "Expected more than one selected-row fingerprint for sequentialDirect");
    }

    @Test
    void sequentialDirectShouldCoverAllFourCombinations() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialDirect")).orElseThrow();

        evaluator.visit(method);

        String output = outContent.toString();
        Set<String> combinations = extractCombinations(
                output,
                Pattern.compile("TYPE_(?:EMPTY|SET)\\|VALUES_(?:EMPTY|PRESENT)")
        );
        assertEquals(4, combinations.size());
    }

    @Test
    void deletedByDirectShouldCoverAllFourCombinations() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("deletedByDirect")).orElseThrow();

        evaluator.visit(method);

        String output = outContent.toString();
        System.err.println("DEBUG deletedByDirect output: [" + output.replace("\n", "\\n") + "]");
        BranchingTrace.snapshot().stream()
                .filter(e -> e.contains("truthTable:") || e.contains("selected:") || e.contains("priorLocal:"))
                .forEach(e -> System.err.println("  TRACE: " + e));
        Set<String> combinations = extractCombinations(
                output,
                Pattern.compile("(?:ALL|OPEN)\\|(?:DELETED|ACTIVE)")
        );
        System.err.println("DEBUG deletedByDirect combinations: " + combinations);
        assertEquals(4, combinations.size());
    }

    @Test
    void sequentialProblemStringsShouldUseBothRepositorySources() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialProblemStrings")).orElseThrow();

        evaluator.visit(method);

        List<String> trace = BranchingTrace.snapshot();
        assertTrue(trace.stream().anyMatch(event -> event.contains("repository.findActive(")));
        assertTrue(trace.stream().anyMatch(event -> event.contains("repository.findActiveByDiagnosisType(")));
    }

    @Test
    void deletedByLookupShouldUseBothRepositorySources() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("deletedByLookup")).orElseThrow();

        evaluator.visit(method);

        List<String> trace = BranchingTrace.snapshot();
        assertTrue(trace.stream().anyMatch(event -> event.contains("repository.findAllRecords(")));
        assertTrue(trace.stream().anyMatch(event -> event.contains("repository.findOpenRecords(")));
    }

    private Set<String> extractCombinations(String text, Pattern pattern) {
        Set<String> combinations = new HashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            combinations.add(matcher.group());
        }
        return combinations;
    }

    private String extractFingerprint(String event) {
        int idx = event.indexOf("|fingerprint=");
        if (idx < 0) {
            return "";
        }
        int start = idx + "|fingerprint=".length();
        int end = event.indexOf("|mode=", start);
        return end >= 0 ? event.substring(start, end) : event.substring(start);
    }

        private int extractRowCount(String event) {
                Matcher matcher = Pattern.compile("\\|rows=(\\d+)\\|").matcher(event);
                if (!matcher.find()) {
                        throw new IllegalStateException("Trace event should include row count: " + event);
                }
                return Integer.parseInt(matcher.group(1));
        }

        @Test
        void sequentialDirectRecordsBranchSelectionTrace() throws ReflectiveOperationException {
                MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                                md -> md.getNameAsString().equals("sequentialDirect")).orElseThrow();

                evaluator.visit(method);

                List<String> trace = BranchingTrace.snapshot();
                long selectedCount = trace.stream()
                                .filter(event -> event.startsWith("selected:sequentialDirect"))
                                .count();

                assertTrue(trace.stream().anyMatch(event -> event.startsWith("target:sequentialDirect")),
                                "Expected target trace entries for sequentialDirect");
                assertTrue(trace.stream().anyMatch(event -> event.startsWith("selected:sequentialDirect")),
                                "Expected selected-combination trace entries for sequentialDirect");
                assertTrue(trace.stream()
                                                .filter(event -> event.startsWith("truthTable:sequentialDirect"))
                                                .map(this::extractRowCount)
                                                .allMatch(count -> count >= 1),
                                "Expected truth-table row counts in sequentialDirect trace");
                assertTrue(selectedCount >= 4,
                                "Expected multiple branch-attempt selections for sequentialDirect");
        }
}
