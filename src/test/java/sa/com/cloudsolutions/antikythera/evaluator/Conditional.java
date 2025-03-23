package sa.com.cloudsolutions.antikythera.evaluator;

public class Conditional {
    public void conditional1(Person person) {
        if (person.getName() != null) {
            System.out.print(person.getName());
        }
        else {
            System.out.print("The name is null");
        }
    }

    public void conditional2(Person person) {
        if (person.getName() == null) {
            System.out.print("The name is null");
        }
        else {
            System.out.print(person.getName());
        }
    }

    public void conditional3(Person person) {
        if (person.getId() == 0) {
            System.out.print("ZERO!");
        }
        else {
            System.out.print(person.getId());
        }
    }

    public void conditional4(Person person) {
        if (person.getId() < 0) {
            System.out.print("Negative!");
        }
        else if (person.getId() > 1) {
            System.out.print("Positive!");
        }
        else {
            System.out.print("ZERO!");
        }
    }

    public void conditional5(int a) {
        if (a == 1) {
            System.out.print("One!");
        }
        else if (a == 2) {
            System.out.print("Two!");
        }
        else if (a == 3) {
            System.out.print("Three!");
        }
        else {
            System.out.print("ZERO!");
        }
    }

    public void conditional6(double a) {
        if (a == 1.0) {
            System.out.print("One!");
        }
        else if (a == 2.0) {
            System.out.print("Two!");
        }
        else if (a == 3.0) {
            System.out.print("Three!");
        }
        else {
            System.out.print("ZERO!");
        }
    }

    public void conditional7(Long a) {
        if (a == 1) {
            System.out.print("One!");
        }
        else if (a == 2) {
            System.out.print("Two!");
        }
        else if (a == 3) {
            System.out.print("Three!");
        }
        else {
            System.out.print("ZERO!");
        }
    }

    public void conditional8(String a) {
        if (a.equals("1")) {
            System.out.print("One!");
        }
        else if (a.equals("2")) {
            System.out.print("Two!");
        }
        else if (a.equals("3")) {
            System.out.print("Three!");
        }
        else {
            System.out.print("ZERO!");
        }
    }

    public void smallDiff(Double a) {
        if (a > 1.1) {
            System.out.print("Nearly 2!");
        }
        else if (a > 1.0) {
            System.out.print("One!");
        }
    }

    public void booleanWorks(Boolean b) {
        if(b) {
            System.out.println("True!");
        }
        else {
            System.out.println("False!");
        }
    }

    public void switchCase1(int a) {
        switch(a) {
            case 1:
                System.out.print("One!");
                break;
            case 2:
                System.out.print("Two!");
                break;
            case 3:
                System.out.print("Three!");
                break;
            default:
                System.out.print("Guess!");
        }
    }


    public static void main(String[] args) {
        Person p = new Person("Hello");
        Conditional c = new Conditional();
        c.conditional1(p);
        p.setName(null);
        c.conditional1(p);
    }
}
