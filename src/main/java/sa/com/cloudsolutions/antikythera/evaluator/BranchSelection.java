package sa.com.cloudsolutions.antikythera.evaluator;

/**
 * Identifies a specific branch choice: the target line of code, the side
 * (TRUE/FALSE) to force, and the truth-table row fingerprint that produced
 * this selection.
 */
public record BranchSelection(LineOfCode target, BranchSide targetSide, String rowFingerprint) {
}
