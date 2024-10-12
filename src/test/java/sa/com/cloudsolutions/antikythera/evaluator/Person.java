package sa.com.cloudsolutions.antikythera.evaluator;

public class Person {
    private int id;
    private String name;
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

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
