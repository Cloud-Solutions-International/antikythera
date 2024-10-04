package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.platform.commons.function.Try;

public class TryCatch {
    TryCatch() {

    }

    public static void main(String[] args) {
        TryCatch tc = new TryCatch();
        tc.doStuff();
    }

    public void doStuff() {
        try {
            String s = null;
            int c = s.length();
            System.out.println("This bit of code shold not be executed");
        } catch (NullPointerException e) {
            System.out.println("Caught an exception");
        } finally {
            System.out.println("Finally block");
        }
    }
}
