package sa.com.cloudsolutions.antikythera.evaluator;

public class Locals {
    public static void main(String[] args) {
        Locals l = new Locals();
        l.doStuff();
    }

    private void doStuff() {
        int c = 100;
        {
            int a = 10;
            int b = 20;
            System.out.println(a + "," + b + "," + c);
            c = 200;
        }
        int a = 20;
        int b = 30;
        System.out.println(a +"," + b + "," + c);
    }
}
