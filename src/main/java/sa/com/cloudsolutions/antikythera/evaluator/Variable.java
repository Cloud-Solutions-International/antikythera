package sa.com.cloudsolutions.antikythera.evaluator;

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
     * It maybe null and that maybe intentional because the variable is supposed to be null.
     */
    private Object value;
    private boolean primitive;

    public Variable (Type type, Object value) {
        setValue(value);
        setType(type);
    }

    public Variable(Type type) {
        this.type = type;
    }

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

    public Class getClazz() {
        return clazz;
    }

    public void setClazz(Class clazz) {
        this.clazz = clazz;
    }
}
