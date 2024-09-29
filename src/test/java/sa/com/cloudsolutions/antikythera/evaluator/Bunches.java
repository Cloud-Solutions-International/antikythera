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
}
