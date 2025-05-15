package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.ArrayList;

public class Locals {
    public static void main(String[] args) {
        Locals l = new Locals();
        l.doStuff();
        l.arrayAccess();
        l.mce();
        l.people();
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

    private void mce() {
        String s = new ArrayList<String>().toString();
        System.out.print(s);
    }

    private void arrayAccess() {
        String[] a = {"Hello", "World"};
        System.out.println(a[0].toUpperCase() + a[1].toUpperCase() + 9.1);
    }

    private void people() {
        IPerson[] a = {new Person("Bertie"), new Person("Biggles")};
        System.out.println(a[0].getName() + " and " + a[1].getName());
    }

}
