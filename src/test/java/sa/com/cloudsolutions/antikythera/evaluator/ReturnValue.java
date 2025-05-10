package sa.com.cloudsolutions.antikythera.evaluator;

import java.io.Serializable;

public class ReturnValue implements Serializable {
    private int number = 10;

    @SuppressWarnings("unused")
    public void printName() {
        System.out.println(getName());
    }

    public String getName() {
        return "John";
    }

    @SuppressWarnings("unused")
    public void printNumberField() {
        System.out.println(getNumber());
    }

    public int getNumber() {
        return number;
    }

    @SuppressWarnings("unused")
    public int returnConditionally() {
        if (number == 10) {
            return 10;
        } else if (number == 20){
            return 20;
        }
        System.out.println("THIS SHOULD NOT BE PRINTED");

        return 11;
    }

    @SuppressWarnings("unused")
    public int deepReturn() {
        if (number == 10) {
            number += 2;
            if(number == 12) {
                number--;
                --number;
                ++number;
                number++;
                if(number == 12) {
                    return number;
                }
                System.out.println("A");
            }
            System.out.println("B");
        }
        System.out.println("C");
        return 10;
    }
}


