package sa.com.cloudsolutions.antikythera.evaluator;

public class ReturnValue {
    private int number = 10;

    public void printName() {
        System.out.println(getName());
    }

    public String getName() {
        return "John";
    }

    public void printNumberField() {
        System.out.println(getNumber());
    }

    public int getNumber() {
        return number;
    }

}


