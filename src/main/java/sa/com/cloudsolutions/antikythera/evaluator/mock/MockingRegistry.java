package sa.com.cloudsolutions.antikythera.evaluator.mock;

import java.util.HashSet;
import java.util.Set;

public class MockingRegistry {

    private static final Set<String> mockedFields = new HashSet<>();

    public static void markAsMocked(String className) {
        mockedFields.add(className);
    }

    public static boolean isMocked(String className) {
        return mockedFields.contains(className);
    }

    public static void reset() {
        mockedFields.clear();
    }

}
