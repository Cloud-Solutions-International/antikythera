package sa.com.cloudsolutions.antikythera.evaluator;


public class Arithmetic {
    private final String a = "number10";

    public int calculate(int b) {
        String c = a.toLowerCase().substring(6);
        return Integer.parseInt(c) + b;
    }

    public static void main(String args[]) {
        Arithmetic arithmetic = new Arithmetic();
        doStuff(arithmetic);
    }


    public void additionViaStrings() {
        String a = "10";
        String b = "20";
        int c = Integer.parseInt(a) + Integer.parseInt(b);
        System.out.println(c);
    }

    public void assignments() {
        int a = 1;
        int b = 1;

        a *= 2; // a = 2
        if (a == b) {
            b += 2;
        }
        a = 10; // a = 10 but b still is 1
        System.out.println(100 + a * b);
    }
    public void simpleAddition() {
        int a = 10;
        int b = 20;
        int c = a + b;
        System.out.println(c);
    }

    private static void doStuff(Arithmetic arithmetic) {
        int a = arithmetic.calculate(Integer.parseInt("10"));
        System.out.println(a);
    }
}
