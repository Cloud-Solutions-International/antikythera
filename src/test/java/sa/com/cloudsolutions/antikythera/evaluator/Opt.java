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

    // Test map operation
    String mapToString(int id) {
        return getById(id)
                .map(val -> "Value: " + val)
                .orElse("No value");
    }

    // Test filter operation
    Optional<Integer> getEvenNumber(int id) {
        return getById(id)
                .filter(val -> val % 2 == 0);
    }

    // Test orElseGet with supplier
    Integer getOrSupply(int id) {
        return getById(id)
                .orElseGet(() -> -1);
    }

    // Test flatMap operation
    Optional<String> flatMapToString(int id) {
        return getById(id)
                .flatMap(val -> Optional.of("Mapped: " + val));
    }

    public static void main(String[] args) {
        Opt opt = new Opt();
        System.out.println(opt.flatMapToString(1));
    }
}
