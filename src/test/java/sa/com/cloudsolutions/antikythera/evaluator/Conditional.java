package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Conditional {
    @SuppressWarnings("unused")
    public void conditional1(Person person) {
        if (person.getName() != null) {
            System.out.print(person.getName() +"!");
        }
        else {
            System.out.print("The name is null!");
        }
    }

    @SuppressWarnings("unused")
    public void conditional2(Person person) {
        if (person.getName() == null) {
            System.out.print("The name is null!");
        }
        else {
            System.out.print(person.getName() + "!");
        }
    }

    @SuppressWarnings("unused")
    public void conditional3(Person person) {
        if (person.getId() == 0) {
            System.out.print("ZERO!");
        }
        else {
            System.out.print(person.getId());
        }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void smallDiff(Double a) {
        if (a > 1.1) {
            System.out.print("Nearly 2!");
        }
        else if (a > 1.0) {
            System.out.print("One!");
        }
    }

    @SuppressWarnings("unused")
    public void booleanWorks(Boolean b) {
        if(b) {
            System.out.print("True!");
        }
        else {
            System.out.print("False!");
        }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public void emptiness1(List<Integer> list) {
        if (list.isEmpty()) {
            System.out.print("List is empty!");
        }
        else {
            System.out.print("List is not empty!");
        }
    }

    @SuppressWarnings("unused")
    public void emptiness2(List<Integer> list) {
        if (list == null || list.isEmpty()) {
            System.out.print("List is empty!");
        }
        else {
            System.out.print("List is not empty!");
        }
    }

    @SuppressWarnings("unused")
    public void emptiness3(List<Integer> list) {
        if (list != null && !list.isEmpty()) {
            System.out.print("List is not empty!");
        }
        else {
            System.out.print("List is empty!");
        }
    }

    @SuppressWarnings("unused")
    public void emptiness4(Set<Integer> set) {
        if (set != null && !set.isEmpty()) {
            System.out.print("Set is not empty!");
        }
        else {
            System.out.print("Set is empty!");
        }
    }

    @SuppressWarnings("unused")
    public void emptiness5(Map<Integer, Integer> map) {
        if (map != null && !map.isEmpty()) {
            System.out.print("Map is not empty!");
        }
        else {
            System.out.print("Map is empty!");
        }
    }

    @SuppressWarnings("unused")
    public static void printMap(Map<Integer, Person> map) {
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<Integer, Person> entry : map.entrySet()) {
                System.out.print("Key: " + entry.getKey() + " -> Value: " + entry.getValue());
            }
        }
        else {
            System.out.print("Map is empty!");
        }
    }

    @SuppressWarnings("unused")
    public String nested(int a) {
        if (a >= 0) {
            if (a == 0) {
                return "Zero";
            }
            else {
                return "Positive";
            }
        }
        return "Negative";
    }

    @SuppressWarnings("unused")
    public String ternary1(String a) {
        return null == a ? "It is null!" : "It is not null!";
    }

    @SuppressWarnings("unused")
    public String ternary2(String a) {
        return a == null ? "It is null!" : "It is not null!";
    }

    @SuppressWarnings("unused")
    public void ternary3(String a) {
        System.out.println(a == null ? "It is null!" : "It is not null!");
    }

    @SuppressWarnings("unused")
    public void ternary4(int a) {
        System.out.println(a == 1 || a == 2 ? "Small!" : "Big!");
    }

    @SuppressWarnings("unused")
    public void ternary5(boolean a) {
        System.out.println(a ? "True!" : "False!");
    }

    @SuppressWarnings("unused")
    public void multiVariate(int a, int b) {
        if (a == 0) {
            if (b == 0) {
                System.out.println("Zero!");
            } else {
                System.out.println("Bee!");
            }
        } else {
            if (b == 0) {
                System.out.println("Aargh!");
            }
            else {
                System.out.println("Antikythera!");
            }
        }
    }

    @SuppressWarnings("unused")
    public void multiVariateDeep(int a, int b) {
        if (a == 0) { // 0
            System.out.println("A0!");
            if (b == 0) { // 1
                System.out.println("B0!");
            } else {
                if (b == 1) { // 2
                    System.out.println("Bee!");
                }
                else {
                    System.out.println("Queen Bee!");
                }
            }
        } else {
            if (b == 0) { // 3
                System.out.println("Aargh!");
            }
            else {
                System.out.println("Antikythera!");
            }
        }
    }

    @SuppressWarnings("unused")
    public void animalFarm(Person a, Person b) {
        if (a.equals(b)) {
            System.out.print("All animals are equal!");
        }
        else {
            System.out.print("Some animals are more equal!");
        }
    }

    @SuppressWarnings("unused")
    public void cannotControl(int a, double b, String c) {
        if (System.currentTimeMillis() == 10000) {
            System.out.println("This condition cannot be met");
        }
    }

    public static void main(String[] args) {
        Person p = new Person("Hello");
        p.setId(1);

        Map<Integer, Person> map = new HashMap<>();
        map.put(1, p);
        printMap(map);

    }
}
