package com.cloud.api.evaluator;


public class Arithmetic {
    private String a = "number10";

    private int calculate(int b) {
        String c = a.toLowerCase().substring(6);
        return Integer.parseInt(c) + b;
    }

    public static void main(String args[]) {
        Arithmetic arithmetic = new Arithmetic();
        doStuff(arithmetic);
    }

    private static void doStuff(Arithmetic arithmetic) {
        int a = arithmetic.calculate(Integer.parseInt("10"));
        System.out.println(a);
    }
}
