package sa.com.cloudsolutions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import sa.com.cloudsolutions.model.Person;
import sa.com.cloudsolutions.dto.EmployeeDepartmentDTO;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Person, Long> {

    @Query("SELECT new sa.com.cloudsolutions.dto.EmployeeDepartmentDTO(p.name, d.departmentName) " +
           "FROM Person p JOIN p.department d " +
           "WHERE d.id = :departmentId")
    List<EmployeeDepartmentDTO> findEmployeeDepartmentDTOByDepartmentId(@Param("departmentId") Long departmentId);
}
