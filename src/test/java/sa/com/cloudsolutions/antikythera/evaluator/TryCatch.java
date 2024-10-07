package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.platform.commons.function.Try;

public class TryCatch {
    TryCatch() {

    }

    public static void main(String[] args) {
        TryCatch tc = new TryCatch();
        tc.tryNPE();
        tc.nested();
    }

    public void nested() {
        try {
            try {
                String s = null;
                int c = s.length();
                System.out.println("This bit of code should not be executed");

            } catch (NullPointerException e) {
                System.out.println("Caught an exception");
            } finally {
                System.out.println("The first finally block");
            }

            String t = null;
            int d = t.length();
            System.out.println("This bit of code should not be executed");

        } catch (NullPointerException e) {
            System.out.println("Caught another exception");
        } finally {
            System.out.println("The second finally block");
        }
    }

    public void throwTantrum(int a) {
        if(a == 1) {
            throw new RuntimeException("Throwing a tantrum");
        }
        else {
            System.out.println("No tantrum thrown");
        }
        System.out.println("Bye");
    }

    public void tryNPE() {
        try {
            String s = null;
            int c = s.length();
            System.out.println("This bit of code should not be executed");
        } catch (NullPointerException e) {
            System.out.println("Caught an exception");
        } finally {
            System.out.println("Finally block");
        }
    }
}
