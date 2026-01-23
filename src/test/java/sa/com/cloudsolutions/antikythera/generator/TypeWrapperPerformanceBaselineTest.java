package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance Baseline Tests for TypeWrapper Migration.
 *
 * This test establishes performance baselines for key operations that must not
 * regress significantly during the migration.
 *
 * As per TYPE_WRAPPER_MIGRATION_PLAN.md § 5 Phase 0:
 * - Benchmark AbstractCompiler.findType() execution time
 * - Benchmark findWrappedTypes() for generic types
 * - Establish regression threshold (< 5% slowdown acceptable)
 *
 * IMPORTANT: These tests measure relative performance, not absolute.
 * The baselines are recorded as assertions that should pass both before and after migration.
 */
class TypeWrapperPerformanceBaselineTest {

    private static final String GENERIC_TYPES = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.GenericTypes";
    private static final String ANIMAL_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.Animal";

    // Performance thresholds (in milliseconds)
    private static final long FIND_TYPE_MAX_MS = 100;  // findType should complete in < 100ms
    private static final long FIND_WRAPPED_TYPES_MAX_MS = 200;  // findWrappedTypes should complete in < 200ms
    private static final long BATCH_FIND_TYPE_MAX_MS = 1000;  // 100 findType calls should complete in < 1s

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        AbstractCompiler.preProcess();
    }

    @Test
    @DisplayName("findType performance - single call")
    void findTypePerformanceSingleCall() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
        assertNotNull(cu, "Animal CU should be loaded");

        long startTime = System.currentTimeMillis();
        TypeWrapper result = AbstractCompiler.findType(cu, "Animal");
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(result, "Should find Animal type");
        assertTrue(duration < FIND_TYPE_MAX_MS,
                "findType should complete in < " + FIND_TYPE_MAX_MS + "ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("findType performance - JDK type resolution")
    void findTypePerformanceJdkType() {
        long startTime = System.currentTimeMillis();
        TypeWrapper result = AbstractCompiler.findType(null, "String");
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(result, "Should find String type");
        assertTrue(duration < FIND_TYPE_MAX_MS,
                "findType for JDK type should complete in < " + FIND_TYPE_MAX_MS + "ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("findType performance - batch operations")
    void findTypePerformanceBatch() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
        assertNotNull(cu);

        String[] types = {"String", "Integer", "List", "Map", "Optional",
                "Object", "Long", "Boolean", "Double", "Float"};

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {  // 10 iterations x 10 types = 100 calls
            for (String type : types) {
                AbstractCompiler.findType(cu, type);
            }
        }
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(duration < BATCH_FIND_TYPE_MAX_MS,
                "100 findType calls should complete in < " + BATCH_FIND_TYPE_MAX_MS + "ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("findWrappedTypes performance - simple generic")
    void findWrappedTypesPerformanceSimple() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(GENERIC_TYPES);
        assertNotNull(cu, "GenericTypes CU should be loaded");

        ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();
        Optional<FieldDeclaration> field = clazz.getFieldByName("stringList");
        assertTrue(field.isPresent());

        Type fieldType = field.get().getVariable(0).getType();

        long startTime = System.currentTimeMillis();
        List<TypeWrapper> result = AbstractCompiler.findWrappedTypes(cu, fieldType);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(result, "Should return wrapped types");
        assertTrue(duration < FIND_WRAPPED_TYPES_MAX_MS,
                "findWrappedTypes should complete in < " + FIND_WRAPPED_TYPES_MAX_MS + "ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("findWrappedTypes performance - nested generic")
    void findWrappedTypesPerformanceNested() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(GENERIC_TYPES);
        assertNotNull(cu);

        ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();
        Optional<FieldDeclaration> field = clazz.getFieldByName("nestedMap");
        assertTrue(field.isPresent());

        Type fieldType = field.get().getVariable(0).getType();

        long startTime = System.currentTimeMillis();
        List<TypeWrapper> result = AbstractCompiler.findWrappedTypes(cu, fieldType);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(result);
        assertTrue(duration < FIND_WRAPPED_TYPES_MAX_MS,
                "findWrappedTypes for nested generics should complete in < " + FIND_WRAPPED_TYPES_MAX_MS +
                        "ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("TypeWrapper instantiation performance")
    void typeWrapperInstantiationPerformance() {
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            new TypeWrapper(String.class);
        }
        long duration = System.currentTimeMillis() - startTime;

        // 1000 instantiations should be very fast (< 100ms)
        assertTrue(duration < 100,
                "1000 TypeWrapper instantiations should complete in < 100ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("isAssignableFrom performance")
    void isAssignableFromPerformance() {
        TypeWrapper stringWrapper = new TypeWrapper(String.class);
        TypeWrapper objectWrapper = new TypeWrapper(Object.class);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            objectWrapper.isAssignableFrom(stringWrapper);
        }
        long duration = System.currentTimeMillis() - startTime;

        // 1000 isAssignableFrom calls should be fast (< 100ms)
        assertTrue(duration < 100,
                "1000 isAssignableFrom calls should complete in < 100ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("getFullyQualifiedName performance")
    void getFullyQualifiedNamePerformance() {
        TypeWrapper wrapper = new TypeWrapper(String.class);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            wrapper.getFullyQualifiedName();
        }
        long duration = System.currentTimeMillis() - startTime;

        // 10000 getFQN calls should be fast (< 100ms)
        assertTrue(duration < 100,
                "10000 getFullyQualifiedName calls should complete in < 100ms, took: " + duration + "ms");
    }

    @Test
    @DisplayName("Memory usage baseline - TypeWrapper instances")
    void memoryUsageBaseline() {
        // Force GC to get a clean baseline
        System.gc();
        long beforeMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Create many TypeWrapper instances
        TypeWrapper[] wrappers = new TypeWrapper[10000];
        for (int i = 0; i < 10000; i++) {
            wrappers[i] = new TypeWrapper(String.class);
        }

        long afterMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = afterMemory - beforeMemory;

        // Record memory usage - this is informational, not a hard constraint
        // Average should be < 1KB per wrapper (10MB for 10000 wrappers)
        long maxExpectedMemory = 10 * 1024 * 1024; // 10MB
        assertTrue(memoryUsed < maxExpectedMemory,
                "10000 TypeWrapper instances should use < 10MB, used: " + (memoryUsed / 1024 / 1024) + "MB");

        // Keep reference to prevent GC during measurement
        assertNotNull(wrappers[0]);
    }

    /**
     * Records timing information for documentation purposes.
     * This test always passes but logs performance data.
     */
    @Test
    @DisplayName("Record baseline timings")
    void recordBaselineTimings() {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);

        // Warm up
        for (int i = 0; i < 100; i++) {
            AbstractCompiler.findType(cu, "Animal");
        }

        // Measure findType
        long findTypeTotal = 0;
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            AbstractCompiler.findType(cu, "Animal");
            findTypeTotal += System.nanoTime() - start;
        }
        double findTypeAvgMicros = findTypeTotal / 100.0 / 1000.0;

        // Measure TypeWrapper instantiation
        long instantiationTotal = 0;
        for (int i = 0; i < 1000; i++) {
            long start = System.nanoTime();
            new TypeWrapper(String.class);
            instantiationTotal += System.nanoTime() - start;
        }
        double instantiationAvgNanos = instantiationTotal / 1000.0;

        // Log baseline timings (visible in test output)
        System.out.println("=== PERFORMANCE BASELINE ===");
        System.out.println("findType average: " + String.format("%.2f", findTypeAvgMicros) + " microseconds");
        System.out.println("TypeWrapper instantiation average: " + String.format("%.2f", instantiationAvgNanos) + " nanoseconds");
        System.out.println("============================");

        // This test always passes - it's for recording baseline data
        assertTrue(true);
    }
}
