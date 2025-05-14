package sa.com.cloudsolutions.antikythera.evaluator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface FakeRepository extends JpaRepository<FakeEntity, Integer>, JpaSpecificationExecutor<FakeEntity> {
    List<FakeEntity> findAllByName(String name);
    Optional<Integer> findByListAndSet(List<Integer> a, Set<Integer> b);

}
