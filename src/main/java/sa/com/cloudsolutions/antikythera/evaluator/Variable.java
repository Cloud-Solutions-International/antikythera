package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;

public class Variable {
    /**
     * Represents a java parser type
     */
    private Type type;
    /**
     * Represents the type as it was identified by reflection.
     * This is needed because sometimes the value maybe null because it's supposed to hold null, but when
     * that happens you cannot call value.getClass() because it will throw a null pointer exception.
     *
     */
    private Class clazz;

    /**
     * The value of this variable.
     * The value could be intentionally null, when the variable is supposed to be null.
     */
    private Object value;

    private Expression initializer;

    /**
     * True if this represents a primitive type as the value
     */
    private boolean primitive;

    /**
     * Create an object having the given value and java parser type.
     * @param type the identified java parser type
     * @param value the value to hole
     */
    public Variable (Type type, Object value) {
        setType(type);
        setValue(value);
    }

    /**
     * Creates an instance with null as the value and of the given java parser type.
     * @param type the identified java parser type.
     */
    public Variable(Type type) {
        this.type = type;
    }

    /**
     * Create an instance with the given value.
     *
     * if the value is not null, it's class will be detected and saved in the class field.
     * @param value
     */
    public Variable(Object value) {
        this.value = value;
        if (value != null) {
            clazz = value.getClass();
        }
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
        if (value != null) {
            clazz = value.getClass();
        }
    }

    public void setType(Type type) {
        this.type = type;
        if (this.clazz == null) {
            this.clazz = switch (type.asString()) {
                case "String" -> String.class;
                case "Double" -> Double.class;
                case "Integer" -> Integer.class;
                case "Boolean" -> Boolean.class;
                case "Long" -> Long.class;
                case "Float" -> Float.class;
                case "Short" -> Short.class;
                case "Byte" -> Byte.class;
                case "Character" -> Character.class;
                default -> {
                    yield null;
                }
            };
        }
    }

    public boolean isPrimitive() {
        return primitive;
    }

    public void setPrimitive(boolean primitive) {
        this.primitive = primitive;
    }

    public String toString() {
        return value == null ? "null" : value.toString();
    }

    public Class<?> getClazz() {
        return clazz == null && value != null ? value.getClass() : clazz;
    }

    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }

    public Expression getInitializer() {
        return initializer;
    }

    public void setInitializer(Expression initializer) {
        this.initializer = initializer;
    }
}
