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

    void ifPresent(int a) {
        getById(a).ifPresent(id -> System.out.println("ID: " + id));
    }

    void ifEmpty(int a) {
        getById(a).ifPresentOrElse(
            id -> System.out.println("ID: " + id),
            () -> System.out.println("ID not found")
        );
    }

    Integer getOrNull1(int id) {
        return getById(id).orElse(null);
    }

    Integer getOrNull2(int id) {
        return this.getById(id).orElse(null);
    }

    Integer getOrNull3(int id) {
        Opt instance = new Opt();
        return instance.getById(id).orElse(null);
    }

    Integer getOrThrowIllegal(int id) {
        return getById(id).orElseThrow(() -> new IllegalArgumentException("ID not found"));
    }

    Integer getOrThrow1(int id) {
        return getById(id).orElseThrow();
    }

    Integer getOrThrow2(int id) {
        return this.getById(id).orElseThrow();
    }
}
