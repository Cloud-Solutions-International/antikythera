package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton that collects a structured processing report as Antikythera evaluates
 * classes and methods.  At the end of a run call {@link #toJson()} to obtain a
 * formatted JSON summary of every class and method that was processed, skipped, or
 * failed, together with the number of tests generated per method.
 *
 * <p>Thread safety: not required – Antikythera processes classes sequentially.</p>
 */
public class ProcessingReport {

    private static final ProcessingReport INSTANCE = new ProcessingReport();

    /** Currently active class entry (set by {@link #beginClass}). */
    private ClassEntry currentClass;

    /** Currently active method entry within {@link #currentClass}. */
    private MethodEntry currentMethod;

    private final List<ClassEntry> classes = new ArrayList<>();

    private ProcessingReport() {}

    public static ProcessingReport getInstance() {
        return INSTANCE;
    }

    /** Clears all collected data (useful for tests). */
    public void reset() {
        classes.clear();
        currentClass = null;
        currentMethod = null;
    }

    // -----------------------------------------------------------------------
    // Class-level recording
    // -----------------------------------------------------------------------

    /**
     * Marks the beginning of processing for {@code name}.  Subsequent
     * {@link #beginMethod} / {@link #recordMethodSkipped} calls are associated
     * with this class until the next {@code beginClass} call.
     */
    public void beginClass(String name) {
        currentMethod = null;
        currentClass = new ClassEntry(name, "processed");
        classes.add(currentClass);
    }

    /**
     * Records a class that was intentionally not processed (e.g. entity, interface,
     * private inner class).
     */
    public void recordClassSkipped(String name, String reason) {
        ClassEntry e = new ClassEntry(name, "skipped");
        e.reason = reason;
        classes.add(e);
    }

    /**
     * Records a class whose processing failed due to an unexpected error.
     * If there is already a {@link #currentClass} entry for {@code name} it is
     * updated in-place; otherwise a new entry is added.
     */
    public void recordClassFailed(String name, String error) {
        ClassEntry e = (currentClass != null && currentClass.name.equals(name))
                ? currentClass
                : new ClassEntry(name, "failed");
        e.status = "failed";
        e.reason = error;
        if (e != currentClass) {
            classes.add(e);
        }
    }

    // -----------------------------------------------------------------------
    // Method-level recording
    // -----------------------------------------------------------------------

    /**
     * Marks the beginning of evaluation for {@code methodName} within the current
     * class.  If an entry for this method already exists (e.g. because a controller
     * method is evaluated with multiple argument generators) the existing entry is
     * reused so that test counts accumulate.
     */
    public void beginMethod(String methodName) {
        if (currentClass == null) return;
        for (MethodEntry m : currentClass.methods) {
            if (m.name.equals(methodName)) {
                currentMethod = m;
                return;
            }
        }
        currentMethod = new MethodEntry(methodName, "processed");
        currentClass.methods.add(currentMethod);
    }

    /**
     * Marks the beginning of evaluation for the given callable using its signature so overloaded
     * methods and constructors are tracked separately.
     */
    public void beginMethod(CallableDeclaration<?> callable) {
        beginMethod(callable.getSignature().asString());
    }

    /**
     * Records a method that was intentionally skipped (e.g. private, annotated with
     * {@code @ExceptionHandler}, filtered by name).
     */
    public void recordMethodSkipped(String methodName, String reason) {
        if (currentClass == null) return;
        MethodEntry e = new MethodEntry(methodName, "skipped");
        e.reason = reason;
        currentClass.methods.add(e);
    }

    /**
     * Records a callable that was intentionally skipped using its signature so overloaded methods
     * and constructors are tracked separately.
     */
    public void recordMethodSkipped(CallableDeclaration<?> callable, String reason) {
        recordMethodSkipped(callable.getSignature().asString(), reason);
    }

    /**
     * Records a processing failure for the currently active method.
     * <ul>
     *   <li>If no tests have been generated yet the method is marked {@code "failed"}
     *       and {@code error} is stored as {@code reason}.</li>
     *   <li>If at least one test was already generated (e.g. a later generator run
     *       failed) the status stays {@code "processed"} but the error is preserved
     *       in the {@code error} field.</li>
     * </ul>
     */
    public void recordCurrentMethodFailed(String error) {
        if (currentMethod == null) return;
        if (currentMethod.testsGenerated == 0) {
            currentMethod.status = "failed";
            currentMethod.reason = error;
        } else {
            currentMethod.error = error;
        }
    }

    /**
     * Increments the test count for the currently active method and ensures its
     * status is {@code "processed"}.
     */
    public void incrementCurrentMethodTests() {
        if (currentMethod == null) return;
        currentMethod.testsGenerated++;
        currentMethod.status = "processed";
    }

    // -----------------------------------------------------------------------
    // JSON serialisation
    // -----------------------------------------------------------------------

    /**
     * Returns a pretty-printed JSON representation of the full processing report.
     *
     * @return JSON string, never {@code null}
     */
    public String toJson() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("generatedAt", Instant.now().toString());
            root.put("summary", buildSummary());
            root.put("classes", classes);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Could not serialize processing report: " + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> buildSummary() {
        long classesProcessed = classes.stream().filter(c -> "processed".equals(c.status)).count();
        long classesSkipped   = classes.stream()
                .filter(c -> "skipped".equals(c.status)
                        || c.methods.stream().anyMatch(m -> "skipped".equals(m.status)))
                .count();
        long classesFailed    = classes.stream()
                .filter(c -> "failed".equals(c.status)
                        || c.methods.stream().anyMatch(m -> "failed".equals(m.status)))
                .count();

        long methodsProcessed = classes.stream()
                .flatMap(c -> c.methods.stream())
                .filter(m -> "processed".equals(m.status)).count();
        long methodsSkipped   = classes.stream()
                .flatMap(c -> c.methods.stream())
                .filter(m -> "skipped".equals(m.status)).count();
        long methodsFailed    = classes.stream()
                .flatMap(c -> c.methods.stream())
                .filter(m -> "failed".equals(m.status)).count();
        long totalTests       = classes.stream()
                .flatMap(c -> c.methods.stream())
                .mapToLong(m -> m.testsGenerated == null ? 0 : m.testsGenerated).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("classesProcessed",  classesProcessed);
        summary.put("classesSkipped",    classesSkipped);
        summary.put("classesFailed",     classesFailed);
        summary.put("methodsProcessed",  methodsProcessed);
        summary.put("methodsSkipped",    methodsSkipped);
        summary.put("methodsFailed",     methodsFailed);
        summary.put("totalTestsGenerated", totalTests);
        return summary;
    }

    // -----------------------------------------------------------------------
    // Data model
    // -----------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClassEntry {
        public String name;
        public String status;   // "processed" | "skipped" | "failed"
        public String reason;   // skip/fail explanation (null when processed)
        public List<MethodEntry> methods = new ArrayList<>();

        ClassEntry(String name, String status) {
            this.name   = name;
            this.status = status;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MethodEntry {
        public String name;
        public String status;          // "processed" | "skipped" | "failed"
        public String reason;          // skip/fail explanation
        public String error;           // set when processed but a generator run also failed
        public Integer testsGenerated; // null for skipped/failed (no tests)

        MethodEntry(String name, String status) {
            this.name   = name;
            this.status = status;
            if ("processed".equals(status)) {
                this.testsGenerated = 0;
            }
        }
    }
}
