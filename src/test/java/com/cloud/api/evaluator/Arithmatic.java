package com.cloud.api.evaluator;


public class Arithmatic {
    private String a = "number10";

    private int calculate(int b) {
        String c = a.toLowerCase().substring(6);
        return Integer.parseInt(c) + b;
    }

    public static void main(String args[]) {
        Arithmatic arithmatic = new Arithmatic();
        doStuff(arithmatic);
    }

    private static void doStuff(Arithmatic arithmatic) {
        int a = arithmatic.calculate(Integer.parseInt("10"));
        System.out.println(a);
    }
}
