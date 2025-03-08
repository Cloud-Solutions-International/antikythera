package sa.com.cloudsolutions.antikythera.evaluator;


import java.util.function.BiFunction;
import java.util.function.Function;

public class Functional {

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

    public static void main(String[] args) {
        Functional f = new Functional();
        f.greet1();
        f.greet2();
        f.greet3();
    }

}
