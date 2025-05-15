package sa.com.cloudsolutions.antikythera.evaluator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@SuppressWarnings("unused")
public class KitchenSink {
    private List<String> stringList;
    private ArrayList<Integer> intList;
    private Map<Long, Person> people;
    private HashMap<String, LinkedHashMap<Person, Person>> itsComplicated;
    private String text = "test";
    private Integer number = 42;
    private int id = 43;

    Object getSomething(String thing) {
        switch (thing) {
            case "stringList":
                return stringList;
            case "intList":
                return intList;
            case "people":
                return people;
            case "text":
                return text;
            case "number":
                return number;
            case "id":
                return id;
            default:
                return null;
        }
    }

    public void print(IPerson person) {
        System.out.println("Person name: " + person.getName());
    }

}
