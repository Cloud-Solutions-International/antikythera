package sa.com.cloudsolutions.antikythera.evaluator;
import java.util.*;
import sa.com.cloudsolutions.antikythera.evaluator.*;

public class Nesting {
    String outerField = "Hello";

    private static String outerMethod() {
        return "from outer method";
    }

    class Inner {
        String innerField = "World";

        public String innerMethod() {
            return outerField + " " + innerField;
        }

        public String outerMethodCall() {
            return outerMethod();
        }
    }

    private void t1() {
        Inner inner = new Inner();
        System.out.println(inner.innerMethod());
    }

    public static void main(String[] args) {
        Nesting n = new Nesting();
        n.t1();
    }
}
