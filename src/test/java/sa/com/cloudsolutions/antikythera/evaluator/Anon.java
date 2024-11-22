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

            @Override
            public void setId(int id) {

            }

            @Override
            public void setId(String id) {

            }
        };

        System.out.println(p.getName());
    }
    public static void main(String[] args) {
        Anon anon = new Anon();
        anon.anonPerson();

    }

}
