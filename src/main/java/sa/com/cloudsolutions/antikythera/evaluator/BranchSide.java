package sa.com.cloudsolutions.antikythera.evaluator;

/**
 * Enumerates the two sides of a conditional branch (TRUE / FALSE) and maps them
 * to the legacy integer constants used by {@link LineOfCode}.
 */
public enum BranchSide {
    FALSE(LineOfCode.FALSE_PATH),
    TRUE(LineOfCode.TRUE_PATH);

    private final int legacyPath;

    BranchSide(int legacyPath) {
        this.legacyPath = legacyPath;
    }

    public int legacyPath() {
        return legacyPath;
    }

    public static BranchSide fromLegacyPath(int path) {
        return switch (path) {
            case LineOfCode.FALSE_PATH -> FALSE;
            case LineOfCode.TRUE_PATH -> TRUE;
            default -> throw new IllegalArgumentException("Unsupported branch path: " + path);
        };
    }
}
