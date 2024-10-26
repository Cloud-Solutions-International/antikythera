package sa.com.cloudsolutions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sa.com.cloudsolutions.model.Person;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
