package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.HashMap;

public class Branching {
    private static final HashMap<Integer, LineOfCode> branches = new HashMap<>();

    private Branching() {

    }

    public static void clear() {
        branches.clear();
    }

    public static void add(LineOfCode lineOfCode) {
        branches.putIfAbsent(lineOfCode.getStatement().hashCode(), lineOfCode);
    }

    public static LineOfCode get(int hashCode) {
        return branches.get(hashCode);
    }
}
