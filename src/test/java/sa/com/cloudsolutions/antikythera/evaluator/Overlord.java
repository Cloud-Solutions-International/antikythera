package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.ArrayList;
import java.util.List;

public class Overlord {

    public void print(String input) {
        System.out.println("String input: " + input);
    }

    public void print(int input) {
        System.out.println("Int input: " + input);
    }

    public void print(int input1, int input2) {
        System.out.println("First int: " + input1 + " Second int: " + input2);
    }

    public void print(List<Integer> input) {
        for (Integer i : input) {
            System.out.print(i + " ");
        }
    }

    @SuppressWarnings("unused")
    public void p1() {
        print("a");
    }

    @SuppressWarnings("unused")
    public void p2() {
        print(1);
    }

    @SuppressWarnings("unused")
    public void p3() {
        print(1, 2);
    }

    @SuppressWarnings("unused")
    public void p4() {
        ArrayList<Integer> a = new ArrayList<>();
        a.add(1);
        a.add(2);
        a.add(3);
        print(a);
    }

    public static void main(String[] args) {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);

        Overlord ov = new Overlord();
        ov.print(args[0]);
        ov.print(1);
        ov.print(1,2);
        ov.print(list);
    }
}

