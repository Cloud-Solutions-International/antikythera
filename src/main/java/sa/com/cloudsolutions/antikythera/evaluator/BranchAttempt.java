package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of an attempt to force a particular branch side, bundling
 * the target line, the conditions that must hold, and the preserved path state
 * so the evaluator can backtrack and retry alternative branch paths.
 */
public record BranchAttempt(
        LineOfCode target,
        List<Precondition> applicableConditions,
        PreservedPathState preservedPathState,
        BranchSelection selection
) {
    public BranchAttempt {
        applicableConditions = List.copyOf(new ArrayList<>(applicableConditions));
        preservedPathState = preservedPathState == null ? PreservedPathState.empty() : preservedPathState;
    }
}
