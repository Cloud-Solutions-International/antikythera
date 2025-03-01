package sa.com.cloudsolutions.antikythera.evaluator;


import java.util.function.Function;

public class Functional {

    private void printHello(Function<String, String> f) {
        System.out.println(f.apply("Ashfaloth"));
    }

    private void greet() {
        printHello( a -> "hello " + a );
    }

    public static void main(String[] args) {
        Functional f = new Functional();
        f.greet();
    }

}
