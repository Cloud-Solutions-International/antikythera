package sa.com.cloudsolutions.antikythera.evaluator;

public class Anon {
    public void anonPerson() {
        IPerson p = new IPerson() {
            @Override
            public String getName() {
                return "Bush";
            }

            @Override
            public void setName(String name) {

            }
        };

        System.out.println(p.getName());
    }
    public static void main(String[] args) {
        Anon anon = new Anon();
        anon.anonPerson();

    }

}
