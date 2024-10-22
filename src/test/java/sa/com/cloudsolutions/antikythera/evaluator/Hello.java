package sa.com.cloudsolutions.antikythera.evaluator;

/**
 * Note Cloneable to help test the InterfaceSolver
 */
public class Hello implements  Cloneable{
    Integer field = 10;

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

    public void longChain() {
        String a = field.toString().replace("10","This IS A FIELD").toUpperCase().toString().toLowerCase().substring(1);
        System.out.println(a);
    }
    public void helloChained(String name) {
        String a = name.toUpperCase().substring(1);
        System.out.println("Hello, " + a);
    }

    public static void main(String[] args) {
        Hello hello = new Hello();
        hello.longChain();
    }

    @Override
    public Hello clone() throws CloneNotSupportedException {
        return (Hello) super.clone();
    }
}
