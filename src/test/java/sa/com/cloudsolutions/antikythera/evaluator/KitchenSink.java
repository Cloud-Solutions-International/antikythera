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
    private int i = 43;

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
            case "i":
                return i;
            default:
                return null;
        }
    }
}
