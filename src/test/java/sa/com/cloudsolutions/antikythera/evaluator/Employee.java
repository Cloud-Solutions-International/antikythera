package sa.com.cloudsolutions.antikythera.evaluator;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;

/**
 * Note Serializable to help test the InterfaceSolver
 */
public class Employee implements Serializable {
    ObjectMapper objectMapper = new ObjectMapper();

    int id = 100;
    Person p = new Person("Hornblower");

    public static void main(String[] args) {
        Employee emp = new Employee();
        System.out.println(emp);
        emp.jsonDump();
    }

    public void jsonDump() {
        try {
            String json = objectMapper.writeValueAsString(p);
            System.out.println(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    public void simpleAccess() {
        System.out.println(p.getName());
        System.out.println(p.getAddress());
        p.setAddress("Colombo");
        System.out.println(p.getAddress());
    }

    @SuppressWarnings("unused")
    public void publicAccess() {
        System.out.println(p.name);
    }

    @SuppressWarnings("unused")
    public void thisAccess() {
        System.out.println(this.p.getName());
    }

    @SuppressWarnings("unused")
    public void chained() {
        System.out.println(p.name.toUpperCase().contains("horn"));
    }

    @Override
    public String toString() {
        return "Patient id = %d , Name = %s".formatted(id, p.getName());
    }
}
