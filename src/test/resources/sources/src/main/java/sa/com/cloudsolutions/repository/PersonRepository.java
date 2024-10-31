package sa.com.cloudsolutions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sa.com.cloudsolutions.model.Person;
import org.springframework.data.domain.Example;

import java.util.List;

public interface PersonRepository extends JpaRepository<Person, Long> {
    List<Person> findByAgeBetween(int a, int b);
    List<Person> findByAge(int a);
    List<Person> findByAgeGreaterThan(int a);
    List<Person> findByAgeLessThan(int a);
    List<Person> findByAgeLessThanEqual(int a);
    List<Person> findByAgeGreaterThanEqual(int a);
    List<Person> findByAgeIn(int[] ages);
    List<Person> findByAgeNotIn(int[] ages);
    List<Person> findByAgeIsNull();
    List<Person> findByAgeIsNotNull();
    List<Person> findByNameLike(String a);


}
