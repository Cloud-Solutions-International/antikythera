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
    }
}
