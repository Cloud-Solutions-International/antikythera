package sa.com.cloudsolutions.antikythera.generator;

/**
 * How strongly the generator should assert on a non-void method's return value when the
 * symbolic evaluator produced a value (including {@code null}).
 * <p>
 * {@link #LOW} is used for declared interface return types where Mockito / deep stubs often
 * disagree with pure symbolic null.
 */
public enum AssertionConfidence {
    /** Prefer strict assertions (e.g. {@code assertNull} when the evaluated value is null). */
    HIGH,
    /** Softer assertions when null vs mock mismatch is likely (e.g. interface return types). */
    LOW
}
