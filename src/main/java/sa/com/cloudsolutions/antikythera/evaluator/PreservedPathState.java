package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class PreservedPathState {
    private final Map<Integer, BranchSide> preservedSidesByBranch;

    private PreservedPathState(Map<Integer, BranchSide> preservedSidesByBranch) {
        this.preservedSidesByBranch = Collections.unmodifiableMap(new LinkedHashMap<>(preservedSidesByBranch));
    }

    public static PreservedPathState empty() {
        return new PreservedPathState(Map.of());
    }

    public PreservedPathState with(LineOfCode branch, BranchSide side) {
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(side, "side");
        LinkedHashMap<Integer, BranchSide> copy = new LinkedHashMap<>(preservedSidesByBranch);
        copy.put(branch.getStatement().hashCode(), side);
        return new PreservedPathState(copy);
    }

    public Optional<BranchSide> sideFor(LineOfCode branch) {
        Objects.requireNonNull(branch, "branch");
        return Optional.ofNullable(preservedSidesByBranch.get(branch.getStatement().hashCode()));
    }

    public Map<Integer, BranchSide> asMap() {
        return preservedSidesByBranch;
    }

    public boolean isEmpty() {
        return preservedSidesByBranch.isEmpty();
    }

    @Override
    public String toString() {
        return preservedSidesByBranch.toString();
    }
}
