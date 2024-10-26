package sa.com.cloudsolutions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sa.com.cloudsolutions.model.Person;
import org.springframework.data.domain.Example;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
