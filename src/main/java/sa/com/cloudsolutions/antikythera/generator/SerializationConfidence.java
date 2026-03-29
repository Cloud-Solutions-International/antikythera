package sa.com.cloudsolutions.antikythera.generator;

/**
 * Confidence that symbolic evaluation of serialization (Gson, Jackson, etc.) matches runtime
 * with Mockito-heavy setups. Used by the test generator to prefer {@code assertDoesNotThrow}
 * over {@code assertThrows} when the evaluator predicted serialization failures.
 */
public enum SerializationConfidence {
    HIGH,
    LOW
}
