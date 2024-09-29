package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bunches {

    public static void main(String[] args) {
        Bunches bunches = new Bunches();
        bunches.printList();
        bunches.printMap();
        bunches.methodCall();
        bunches.withDTO();
    }


    public void printList() {
        List<String> list = new ArrayList<>();
        list.add("one");
        list.add("two");
        System.out.println(list);
    }

    public void printMap() {
        Map<String, Integer> list = new HashMap<>();
        list.put("one", 1);
        list.put("two", 2);
        System.out.println(list);
    }

    public void methodCall() {
        List<Integer> ints = new ArrayList<>();
        ints.add(15);

        Arithmetic arithmetic = new Arithmetic();
        int result = arithmetic.calculate(20);
        ints.add(result);
        System.out.println(ints);
    }

    public void withDTO() {
        List<DTO> dtos = new ArrayList<>();
        DTO dto = new DTO();
        dto.age = 10;
        dto.name = "Biggles";

        dtos.add(dto);
        System.out.println(dtos);
    }

    class DTO {
        private String name;
        private int age;

        public String toString() {
            return name + " " + age;
        }
    }
}
