package sa.com.cloudsolutions.antikythera.evaluator;


import java.util.function.Function;

public class Functional {

    private void printHello(Function<String, String> f) {
        System.out.println(f.apply("Ashfaloth"));
    }

    private void greet1() {
        printHello( a -> "hello " + a );
    }

    private void greet2() {
        printHello( a -> { return "hello " + a; });
    }

    public static void main(String[] args) {
        Functional f = new Functional();
        f.greet1();
        f.greet2();
    }

}
