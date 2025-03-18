package sa.com.cloudsolutions.antikythera.evaluator;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Functional {
    Person a = new Person("A");
    Person b = new Person("B");
    List<Person> people = List.of(a, b);

    private void printHello(Function<String, String> f)
    {
        System.out.println(f.apply("Ashfaloth"));
    }

    private void printHello(BiFunction<String, String, String> f)
    {
        System.out.println(f.apply("Thorin", "Oakenshield"));
    }

    private void greet1() {
        printHello( a -> "Hello " + a );
    }

    private void greet2() {
        printHello( a -> { return "Hello " + a; });
    }

    private void greet3() {
        printHello( (a,b) -> "Hello " + a + " " + b);
    }

    private void sorting1() {
        List<Integer> numbers = new ArrayList<>(List.of(8,9,0,3,1,4,5,6,7,2));
        Collections.sort(numbers);
        numbers.forEach(System.out::print);
        System.out.println();
    }

    private void sorting2() {
        List<Integer> numbers = new ArrayList<>(List.of(8,9,0,3,1,4,5,6,7,2));
        Collections.sort(numbers, (a,b) -> b - a);
        numbers.forEach(System.out::print);
        System.out.println();
    }

    private void people1() {
        List<String> names = people.stream().map(Person::getName).collect(Collectors.toList());
        System.out.println(names);
    }

    private void people2() {
        List<String> names = people.stream().map( p -> p.getName()).collect(Collectors.toList());
        System.out.println(names);
    }

    private void people3() {

        List<String> names = new ArrayList();

        people.forEach(p -> names.add(p.getName()));
        System.out.println(names);
    }

    private void people4() {
        List<String> filtered = people.stream()
                .filter(person -> person.getName().equals("A"))
                .map(Person::getName)
                .toList();
        System.out.println(filtered);
    }

    private void people5() {
        Optional<Person> p = people.stream().findFirst();
        System.out.println(p.get().getName());
    }

    private void people6() {
        people.stream().findFirst().ifPresent(p -> System.out.println(p.getName()));
    }

    private void people7() {
        Function<String, String> f = a -> "Tom " + a;
        Person p = new Person(f.apply("Bombadil"));
        System.out.println(p.getName());
    }
    public static void main(String[] args) {
        Functional f = new Functional();
        f.greet1();
        f.greet2();
        f.greet3();
        f.sorting1();
        f.sorting2();
        f.people1();
        f.people2();
        f.people3();
        f.people4();
        f.people5();
        f.people6();
        f.people7();
    }

}
