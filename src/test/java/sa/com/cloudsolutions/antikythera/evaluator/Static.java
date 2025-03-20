package sa.com.cloudsolutions.antikythera.evaluator;

public class Static {
    private class Inner {
        static int counter = 0;
        String name;
        Inner(String name) {
            counter++;
            this.name = name;
        }
    }

    // intentionally given the same name as the counter static field in the inner class
    void counter() {
        new Inner("a");
        Inner b = new Inner("b");

        System.out.println(Inner.counter + " " + b.name);
    }

    public static void main(String[] args) {
        Static s = new Static();
        s.counter();
    }
}
