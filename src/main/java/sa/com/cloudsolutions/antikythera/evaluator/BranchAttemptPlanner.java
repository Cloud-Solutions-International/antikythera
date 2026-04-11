package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.body.CallableDeclaration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BranchAttemptPlanner {
    private final Map<AttemptKey, LinkedHashSet<String>> attemptedRows = new LinkedHashMap<>();

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

    BranchAttempt selectNextAttempt(LineOfCode target, BranchSide side, List<Map<Expression, Object>> combinations) {
        List<PreservedPathState> candidateStates = candidatePreservedPathStates(target);
        Map<Expression, Object> fallback = combinations.isEmpty() ? Map.of() : combinations.getFirst();
        PreservedPathState fallbackState = candidateStates.getFirst();

        for (PreservedPathState state : candidateStates) {
            AttemptKey attemptKey = new AttemptKey(target.getStatement().hashCode(), side, state);
            LinkedHashSet<String> attemptedFingerprints = attemptedRows.computeIfAbsent(attemptKey, ignored -> new LinkedHashSet<>());

            for (Map<Expression, Object> combination : combinations) {
                String fingerprint = BranchAttemptFingerprint.fingerprintCombination(combination);
                if (attemptedFingerprints.add(fingerprint)) {
                    recordSelection(target, side, fingerprint, "new", state);
                    BranchSelection selection = new BranchSelection(target, side, fingerprint);
                    return new BranchAttempt(target, List.of(), state, selection);
                }
            }
        }

        String fallbackFingerprint = BranchAttemptFingerprint.fingerprintCombination(fallback);
        recordSelection(target, side, fallbackFingerprint, "reuse", fallbackState);
        BranchSelection selection = new BranchSelection(target, side, fallbackFingerprint);
        return new BranchAttempt(target, List.of(), fallbackState, selection);
    }

    void clear() {
        attemptedRows.clear();
    }

    private List<PreservedPathState> candidatePreservedPathStates(LineOfCode target) {
        List<LineOfCode> orderedPredecessors = target.getPredecessors().stream().toList();
        if (orderedPredecessors.isEmpty()) {
            return List.of(PreservedPathState.empty());
        }

        List<PreservedPathState> states = new ArrayList<>();
        states.add(PreservedPathState.empty());
        for (LineOfCode predecessor : orderedPredecessors) {
            List<BranchSide> availableSides = availableSides(predecessor);
            if (availableSides.isEmpty()) {
                continue;
            }

            List<PreservedPathState> expanded = new ArrayList<>();
            for (PreservedPathState state : states) {
                for (BranchSide side : availableSides) {
                    expanded.add(state.with(predecessor, side));
                }
            }
            states = expanded;
        }
        return states.isEmpty() ? List.of(PreservedPathState.empty()) : states;
    }

    private List<BranchSide> availableSides(LineOfCode predecessor) {
        if (predecessor.isFullyTravelled()) {
            return List.of(BranchSide.FALSE, BranchSide.TRUE);
        }
        if (predecessor.isFalsePath()) {
            return List.of(BranchSide.FALSE);
        }
        if (predecessor.isTruePath()) {
            return List.of(BranchSide.TRUE);
        }
        return List.of();
    }

    private record AttemptKey(int targetHash, BranchSide side, PreservedPathState preservedPathState) {
    }

    private void recordSelection(LineOfCode target,
                                 BranchSide side,
                                 String fingerprint,
                                 String mode,
                                 PreservedPathState state) {
        BranchingTrace.record(() -> "selectedRow:"
                + target.getCallableDeclaration().getNameAsString()
                + "|path=" + side.legacyPath()
                + "|fingerprint=" + fingerprint
                + "|mode=" + mode
                + "|preserved=" + state);
    }
}
