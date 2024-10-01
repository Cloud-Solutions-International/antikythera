package sa.com.cloudsolutions.antikythera.evaluator;

public class Hello {
    public void helloWorld() {
        System.out.println("Hello, Antikythera");
    }

    public void helloName(String name) {
        System.out.println("Hello, " + name);
    }

    public void helloUpper(String name) {
        String upper = name.toUpperCase();
        System.out.println("Hello, " + upper);
    }

    public void helloChained(String name) {
        String a = name.toUpperCase().substring(1);
        System.out.println("Hello, " + a);
    }
}
