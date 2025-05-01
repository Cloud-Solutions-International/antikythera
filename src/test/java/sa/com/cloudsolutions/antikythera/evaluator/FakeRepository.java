package sa.com.cloudsolutions.antikythera.evaluator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FakeRepository extends JpaRepository<FakeEntity, Integer> {

}
