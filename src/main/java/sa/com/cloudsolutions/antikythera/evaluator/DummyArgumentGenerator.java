package sa.com.cloudsolutions.antikythera.evaluator;

public class DummyArgumentGenerator extends ArgumentGenerator {

     public Variable mockParameter(String typeName) {
        return new Variable(switch (typeName) {
            case "Boolean" -> false;
            case "float", "Float", "double", "Double" -> 0.0;
            case "Integer", "int" -> 0;
            case "Long", "long" -> 0L;
            case "String" -> "Ibuprofen";
            default -> "0";
        });
    }
}
