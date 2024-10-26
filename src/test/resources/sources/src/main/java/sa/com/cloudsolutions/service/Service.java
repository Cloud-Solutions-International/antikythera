package sa.com.cloudsolutions.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sa.com.cloudsolutions.model.Person;
import sa.com.cloudsolutions.repository.PersonRepository;

import java.util.List;
import java.util.Optional;

@Component
public class Service {
    @Autowired
    private PersonRepository personRepository;

    public void queries() {
        Optional<Person> p = personRepository.findById(1L);
        List<Person> personList = personRepository.findAll();
        List<Person> ageGroup = personRepository.findByAgeBetween(10, 20);
        List<Person> age = personRepository.findByAge(10);
        List<Person> ageGreaterThan = personRepository.findByAgeGreaterThan(10);
        List<Person> ageLessThan = personRepository.findByAgeLessThan(10);
        List<Person> ageLessThanEqual = personRepository.findByAgeLessThanEqual(10);
        List<Person> ageGreaterThanEqual = personRepository.findByAgeGreaterThanEqual(10);
        List<Person> ageIn = personRepository.findByAgeIn(new int[]{10, 20, 30});
        List<Person> ageNotIn = personRepository.findByAgeNotIn(new int[]{10, 20, 30});
        List<Person> ageIsNull = personRepository.findByAgeIsNull();
        List<Person> ageIsNotNull = personRepository.findByAgeIsNotNull();
        List<Person> ageLike = personRepository.findByAgeLike("10");

    }
}

