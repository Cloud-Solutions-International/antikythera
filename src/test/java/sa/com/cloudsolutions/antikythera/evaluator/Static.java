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

    private static class StaticInner {
        static int counter = 0;
        String name;
        StaticInner(String name) {
            counter++;
            this.name = name;
        }
    }

    private static class Initializer {
        static int number = 25;
        static {
            number++;
            --number;
            ++number;
            --number;
            ++number;
        }
    }

    // intentionally given the same name as the counter static field in the inner class
    void counter1() {
        Inner a = new Inner("a");
        Inner b = new Inner("b");

        System.out.println(Inner.counter + " " + b.name);
        System.out.println(Inner.counter + " " + a.name);
        System.out.println(a.counter + " " + b.name);
    }


    // intentionally given the same name as the counter static field in the inner class
    void counter2() {
        StaticInner a = new StaticInner("a");
        StaticInner b = new StaticInner("b");

        System.out.println(StaticInner.counter + " " + b.name);
        System.out.println(StaticInner.counter + " " + a.name);
        System.out.println(a.counter + " " + b.name);
    }

    void number1() {
        Initializer i = new Initializer();
        System.out.println(i.number);
    }

    void number2() {
        System.out.println(Initializer.number);
    }


    public static void main(String[] args) {
        Static s = new Static();
        s.counter1();
        s.counter2();
        s.number1();
        s.number2();
    }
}
