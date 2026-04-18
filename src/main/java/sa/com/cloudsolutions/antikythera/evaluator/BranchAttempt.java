package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.ArrayList;
import java.util.List;

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
