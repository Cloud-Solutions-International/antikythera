package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.CallableDeclaration;

import java.util.ArrayList;
import java.util.List;

final class BranchAttemptPlanner {
    BranchAttempt plan(CallableDeclaration<?> methodDeclaration, LineOfCode target, List<LineOfCode> relevantBranches) {
        List<Precondition> applicableConditions = new ArrayList<>();
        for (LineOfCode lineOfCode : relevantBranches) {
            if (lineOfCode.getPathTaken() != LineOfCode.BOTH_PATHS) {
                applicableConditions.addAll(lineOfCode.getPreconditions());
            }
        }

        BranchingTrace.record(() -> "attempt:"
                + methodDeclaration.getNameAsString()
                + "|target=" + (target == null ? "<none>" : target.getStatement())
                + "|count=" + applicableConditions.size()
                + "|branches=" + relevantBranches.stream()
                .map(lineOfCode -> lineOfCode.getPathTaken() + ":" + lineOfCode.getPreconditions().size()
                        + ":" + lineOfCode.getStatement())
                .toList());

        return new BranchAttempt(target, applicableConditions, PreservedPathState.empty(), null);
    }
}
