package sa.com.cloudsolutions.antikythera.evaluator;

public class Conditional {
    public void testMethod(Person person) {
        if (person.getName() != null) {
            System.out.println(person.getName());
        }
        else {
            System.out.println("The name is null");
        }
    }

    public static void main(String[] args) {
        Person p = new Person("Hello");
        Conditional c = new Conditional();
        c.testMethod(p);
        p.setName(null);
        c.testMethod(p);
    }
}
