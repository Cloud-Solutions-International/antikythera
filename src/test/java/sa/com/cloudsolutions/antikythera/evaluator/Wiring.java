package sa.com.cloudsolutions.antikythera.evaluator;

import java.lang.reflect.Field;

public class Wiring {
    @Autowired
    First f;
    @Autowired
    Second s;

    public Wiring() {

    }

    void doStuff() {
        System.out.println(f.getName() + " " + s.getName());
    }

    public static void main(String[] args) {
        Wiring wiring = new Wiring();
        Field[] fields = Wiring.class.getDeclaredFields();

        try {
            for (Field field : fields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    field.setAccessible(true);
                    Class<?> type = field.getType();
                    Object instance = type.getDeclaredConstructor().newInstance();
                    field.set(wiring, instance);

                    // Handle nested autowired fields
                    Field[] nestedFields = type.getDeclaredFields();
                    for (Field nestedField : nestedFields) {
                        if (nestedField.isAnnotationPresent(Autowired.class)) {
                            nestedField.setAccessible(true);
                            Class<?> nestedType = nestedField.getType();
                            Object nestedInstance = nestedType.getDeclaredConstructor().newInstance();
                            nestedField.set(instance, nestedInstance);
                        }
                    }
                }
            }
            wiring.doStuff();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class First {
    @Autowired
    Second s;
    String name;

    public First() {
        name = "Fatty";
    }

    public String getName() {
        return name;
    }
}

class Second {
    /*
     * This is here to check for cycles
     */
    @Autowired
    First f;

    private String name;

    public Second() {
        this.name = "Bolgar";
    }
    public String getName() {
        return name;
    }
}
