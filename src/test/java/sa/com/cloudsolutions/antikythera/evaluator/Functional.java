package sa.com.cloudsolutions.antikythera.evaluator;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Functional {
    Person a = new Person("A");
    Person b = new Person("B");
    List<Person> people = List.of(a, b);
    private final List<Integer> numbers = new ArrayList<>(List.of(8,9,0,3,1,4,5,6,7,2));

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

    @SuppressWarnings("java:S1117")
    private void greet2() {
        printHello( a -> { return "Hello " + a; });
    }

    @SuppressWarnings("java:S1117")
    private void greet3() {
        printHello( (a,b) -> "Hello " + a + " " + b);
    }

    private void sorting1() {
        Collections.sort(numbers);
        numbers.forEach(System.out::print);
        System.out.println();
    }

    @SuppressWarnings("java:S1117")
    private void sorting2() {
        Collections.sort(numbers, (a,b) -> b - a);
        numbers.forEach(System.out::print);
        System.out.println();
    }

    private void people1() {
        List<String> names = people.stream().map(Person::getName).toList();
        System.out.println(names);
    }

    @SuppressWarnings("java:S1612")
    private void people2() {
        List<String> names = people.stream().map( p -> p.getName()).toList();
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

    @SuppressWarnings("java:S1117")
    private void people7() {
        /* I want to test what happens when the field is hidden */
        Function<String, String> f = a -> "Tom " + a;
        Person p = new Person(f.apply("Bombadil"));
        System.out.println(p.getName());
    }

    private void maps1() {
        a.setId(25);
        b.setId(30);

        Map<Integer, String> ageToName = people.stream()
            .collect(Collectors.toMap(
                Person::getId,
                Person::getName
            ));
        System.out.println(ageToName);
    }

    @SuppressWarnings({"java:S1864", "java:S6204"})
    private void staticMethodReference1() {
        List<Integer> shorty = List.of(1,2,3);
        List<Integer> incrementedNumbers = shorty.stream()
            .map(Functional::increment)
            .collect(Collectors.toList());
        incrementedNumbers.forEach(System.out::print);
        System.out.println();
    }

    @SuppressWarnings({"java:S1864","java:S6204","java:S1612"})
    private void staticMethodReference2() {
        List<Integer> shorty = List.of(1,2,3);
        /* I am using Collectors.toList() because i want to test that behaviour!
        *  Same goes for not using a Method Reference. I want to test a lambda dammit! */
        List<Integer> incrementedNumbers = shorty.stream()
                .map(i -> Functional.increment(i))
                .collect(Collectors.toList());
        incrementedNumbers.forEach(System.out::print);
        System.out.println();
    }

    private static int increment(int n) {
        return n + 1;
    }

    private void nestedStream() {
        numbers.forEach(n -> {
            int x = n;
            if (n == 1 || n == 2) {
                System.out.print(x);
                people.stream().forEach(p -> System.out.print(p.getName()));
            }
        });
        System.out.println();
    }

    @SuppressWarnings("java:S1117")
    private void valueOf() {
        Function<Integer, String> a = String::valueOf;
        System.out.println(a.apply(1));
    }

    @SuppressWarnings("java:S6204")
    private void collectAgain() {
        List<Person> people = List.of(new Person(1,"","","",""),
                new Person(2,"","","",""));
        List<Integer> ints = people.stream()
                .map(Person::getId)
                .collect(Collectors.toList());

        System.out.println(ints.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(" ")));
    }

    public static void main(String[] args) {
        Functional f = new Functional();
        f.greet1();
        f.greet2();
        f.greet3();
        f.sorting1();
        f.sorting2();
        System.out.println("People");
        f.people1();
        f.people2();
        f.people3();
        f.people4();
        f.people5();
        f.people6();
        f.people7();
        f.maps1();
        f.nestedStream();
        f.staticMethodReference1();
        f.collectAgain();
        f.valueOf();
    }

}
