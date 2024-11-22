package sa.com.cloudsolutions.antikythera.evaluator;

public class Person implements IPerson {
    private int id;
    String name;
    private String address;
    private String phone;
    private String email;

    public Person(String name) {
        this.name = name;
    }

    public Person(int id, String name, String address, String phone, String email) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    public static Person personBuilder() {
        Person p = new Person("Hornblower");
        p.setId("200");
        return p;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public void setId(String id) {
        this.setId(Integer.parseInt(id));
    }
}
