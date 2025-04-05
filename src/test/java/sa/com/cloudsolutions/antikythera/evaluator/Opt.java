package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.Optional;

public class Opt {
    Optional<Integer> getById(int id) {
        if (id == 0) {
            return Optional.empty();
        } else {
            return Optional.of(id);
        }
    }

    Integer getOrNull(int id) {
        return getById(id).orElse(null);
    }

    Integer getOrThrowIllegal(int id) {
        return getById(id).orElseThrow(() -> new IllegalArgumentException("ID not found"));
    }

    Integer getOrThrow(int id) {
        return getById(id).orElseThrow();
    }
}
