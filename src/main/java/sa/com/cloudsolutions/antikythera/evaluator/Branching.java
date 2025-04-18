package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Branching {
    private static final HashMap<Integer, LineOfCode> branches = new HashMap<>();
    private static final HashMap<MethodDeclaration, List<LineOfCode>> conditionals = new HashMap<>();
    private static int loopCount = 0;

    private Branching() {

    }

    public static void clear() {
        branches.clear();
        loopCount = 0;
    }

    public static void add(LineOfCode lineOfCode) {
        List<LineOfCode> lines = conditionals.computeIfAbsent(lineOfCode.getMethodDeclaration(), k -> new ArrayList<>());
        lines.add(lineOfCode);
        branches.putIfAbsent(lineOfCode.getStatement().hashCode(), lineOfCode);
    }

    public static LineOfCode get(int hashCode) {
        return branches.get(hashCode);
    }

    public static List<LineOfCode> get(MethodDeclaration methodDeclaration) {
        return conditionals.getOrDefault(methodDeclaration, new ArrayList<>());
    }

    public static List<Precondition> getApplicableConditions(MethodDeclaration methodDeclaration) {
        List<Precondition> applicableConditions = new ArrayList<>();
        List<LineOfCode> lines = conditionals.getOrDefault(methodDeclaration, Collections.emptyList());
        for (LineOfCode lineOfCode : lines) {
            if (lineOfCode.getPathTaken() != LineOfCode.BOTH_PATHS) {
                applicableConditions.addAll(lineOfCode.getPreconditions());
            }
        }
        return applicableConditions;
    }

    /**
     * Returns true if all the branches have been covered
     * @return true if both branches in if statements have been covered.
     */
    public static boolean isCovered(MethodDeclaration md) {
        List<LineOfCode> lines = conditionals.get(md);
        if (lines != null) {
            for (LineOfCode l : lines) {
                if (l.getPathTaken() != LineOfCode.BOTH_PATHS) {
                    return false;
                }
            }
        }
        else {
            loopCount++;
            return loopCount < 2;
        }
        return true;
    }
}
