package sa.com.cloudsolutions.antikythera.evaluator;

public class Overlord {

    public void print(String input) {
        System.out.println("String input: " + input);
    }

    public void print(int input) {
        System.out.println("Int input: " + input);
    }

    public void print(int input1, int input2) {
        System.out.println("First int: " + input1 + ", Second int: " + input2);
    }

    public static void main(String[] args) {
        Overlord ov = new Overlord();
        ov.print(args[0]);
        ov.print(1);
        ov.print(1,2);
    }
}

