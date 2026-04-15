package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.BinaryExpr;
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

    /**
     * Returns true if cross-product exploration is still needed for {@code target}: specifically,
     * when there are two or more candidate preserved states (i.e., the target has sibling
     * predecessors) and at least one (side, preservedState) pair has never been attempted.
     *
     * <p>Intentionally does NOT look at per-row fingerprint exhaustion within a single preserved
     * state — that would cause spurious extra iterations on single-branch methods where the
     * TruthTable produces multiple rows for the same side.</p>
     */
    boolean hasUntriedCombinations(LineOfCode target) {
        List<PreservedPathState> candidateStates = candidatePreservedPathStates(target);
        // Cross-product work only exists when there are 2+ distinct preserved states.
        // A single-entry state (always [empty]) means no sibling predecessor enumeration needed.
        if (candidateStates.size() <= 1) {
            return false;
        }
        for (BranchSide side : List.of(BranchSide.FALSE, BranchSide.TRUE)) {
            for (PreservedPathState state : candidateStates) {
                AttemptKey key = new AttemptKey(target.getStatement().hashCode(), side, state);
                if (!attemptedRows.containsKey(key)) {
                    // This (side, preservedState) was never handed to the caller at all.
                    return true;
                }
            }
        }
        return false;
    }

    private List<PreservedPathState> candidatePreservedPathStates(LineOfCode target) {
        // Only consider sibling predecessors for cross-product expansion.
        // A sibling predecessor shares the same parent LineOfCode as the target — these are
        // branches that are sequential in the same block and are independently satisfiable.
        // Ancestor/nesting predecessors (e.g., outer if → inner else-if) share variables and
        // their truth-table rows are mutually constrained; expanding those would yield
        // contradictory preserved states and cause spurious extra iterations.
        List<LineOfCode> orderedPredecessors = target.getPredecessors().stream()
                .filter(p -> p.getParent() == target.getParent())
                .filter(p -> p.getConditionalExpression() != null)
                .toList();
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

            int trueRowCount = countOrAlternatives(predecessor);
            List<PreservedPathState> expanded = new ArrayList<>();
            for (PreservedPathState state : states) {
                for (BranchSide side : availableSides) {
                    if (side == BranchSide.TRUE && trueRowCount > 1) {
                        for (int row = 0; row < trueRowCount; row++) {
                            expanded.add(state.with(predecessor, side, row));
                        }
                    } else {
                        expanded.add(state.with(predecessor, side));
                    }
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

    private static int countOrAlternatives(LineOfCode branch) {
        Expression cond = branch.getConditionalExpression();
        if (cond == null) {
            return 1;
        }
        return countOrBranches(cond);
    }

    private static int countOrBranches(Expression expr) {
        if (expr instanceof BinaryExpr be && be.getOperator() == BinaryExpr.Operator.OR) {
            return countOrBranches(be.getLeft()) + countOrBranches(be.getRight());
        }
        return 1;
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
