package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PreservedPathState {
    private final Map<LineOfCode, BranchSide> preservedSidesByBranch;
    private final int rowHint;

    private PreservedPathState(Map<LineOfCode, BranchSide> preservedSidesByBranch) {
        this(preservedSidesByBranch, 0);
    }

    private PreservedPathState(Map<LineOfCode, BranchSide> preservedSidesByBranch, int rowHint) {
        this.preservedSidesByBranch = Collections.unmodifiableMap(new LinkedHashMap<>(preservedSidesByBranch));
        this.rowHint = rowHint;
    }

    public static PreservedPathState empty() {
        return new PreservedPathState(Map.of());
    }

    public PreservedPathState with(LineOfCode branch, BranchSide side) {
        return with(branch, side, 0);
    }

    public PreservedPathState with(LineOfCode branch, BranchSide side, int rowHint) {
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(side, "side");
        LinkedHashMap<LineOfCode, BranchSide> copy = new LinkedHashMap<>(preservedSidesByBranch);
        copy.put(branch, side);
        return new PreservedPathState(copy, rowHint);
    }

    public int getRowHint() {
        return rowHint;
    }

    public Optional<BranchSide> sideFor(LineOfCode branch) {
        Objects.requireNonNull(branch, "branch");
        return Optional.ofNullable(preservedSidesByBranch.get(branch));
    }

    public Map<LineOfCode, BranchSide> asMap() {
        return preservedSidesByBranch;
    }

    public boolean isEmpty() {
        return preservedSidesByBranch.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PreservedPathState other)) {
            return false;
        }
        return preservedSidesByBranch.equals(other.preservedSidesByBranch) && rowHint == other.rowHint;
    }

    @Override
    public int hashCode() {
        return 31 * preservedSidesByBranch.hashCode() + rowHint;
    }

    @Override
    public String toString() {
        return preservedSidesByBranch.toString();
    }
}
