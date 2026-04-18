package sa.com.cloudsolutions.antikythera.evaluator;

public record BranchSelection(LineOfCode target, BranchSide targetSide, String rowFingerprint) {
}
