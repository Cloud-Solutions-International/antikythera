package sa.com.cloudsolutions.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sa.com.cloudsolutions.dto.EmployeeDepartmentDTO;
import sa.com.cloudsolutions.model.Person;
import sa.com.cloudsolutions.repository.DepartmentRepository;
import sa.com.cloudsolutions.repository.PersonRepository;

import java.util.List;
import java.util.Optional;

@Component
public class Service {
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private DepartmentRepository departmentRepository;

    private int notAutoWired = 0;

    public void queries1() {
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
        List<Person> ageLike = personRepository.findByNameLike("10");

    }

    public void queries2() {
        List<EmployeeDepartmentDTO> employeeDepartmentDTO = departmentRepository.findEmployeeDepartmentDTOByDepartmentId(1L);
    }

    public void queries3(Map<String, Object> map) {
        List<EmployeeDepartmentDTO> employeeDepartmentDTO = departmentRepository.findEmployeeDepartmentDTOByDepartmentId(1L);
    }

    public void queries4(Long id) {
        Optional<Person> p = personRepository.findById(id);
    }

    public void queries5(int id) {
        Optional<Person> p = personRepository.findById(id);
    }
}

