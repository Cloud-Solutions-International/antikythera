package sa.com.cloudsolutions.antikythera.evaluator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FakeRepository extends JpaRepository<FakeEntity, Integer>, JpaSpecificationExecutor<FakeEntity> {
    public List<FakeEntity> findAllByName(String name);

}
