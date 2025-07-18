package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class Variable {
    /**
     * Represents a java parser type
     */
    private Type type;
    /**
     * Represents the type as it was identified by reflection.
     * This is needed because sometimes the value maybe null because it's supposed to hold null, but when
     * that happens, you cannot call value.getClass() because it will throw a null pointer exception.
     *
     */
    private Class<?> clazz;

    /**
     * The value of this variable.
     * The value could be intentionally null, when the variable is supposed to be null.
     */
    private Object value;

    private List<Expression> initializer = new ArrayList<>();

    @SuppressWarnings("java:S2245")
    private static final Random random = new Random();

    /**
     * Represents the name of a parameter, field or local variable that this may represent
     */
    private String name;

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
        this.type = Objects.requireNonNullElseGet(type, VoidType::new);
    }

    /**
     * <p>Create an instance with the given value.</p>
     *
     * if the value is not null, its class will be detected and saved in the class field.
     * @param value the initial value for the Variable
     */
    public Variable(Object value) {
        this.value = value;
        if (value != null) {
            this.setClazz(value.getClass());
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
            try {
                this.clazz = Reflect.getComponentClass(type.asString());
            } catch (ClassNotFoundException e) {
                // can be silently ignored
            }
        }
    }

    @Override
    public String toString() {
        if (value == null) {
            return "null";
        }

        if (value instanceof Evaluator eval) {
            return "Evaluator for " + eval.getClassName();
        }
        return value.toString();
    }

    public Class<?> getClazz() {
        return clazz == null && value != null ? value.getClass() : clazz;
    }

    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
        if (this.type == null) {
            this.type = Reflect.getComponentType(clazz);
        }
    }

    public List<Expression> getInitializer() {
        return initializer;
    }

    public void setInitializer(List<Expression> initializer) {
        this.initializer = initializer;
    }

    public static String generateVariableName(TypeWrapper type) {
        if (type.getClazz() == null) {
            return generateVariableName(type.getType().getNameAsString());
        }
        return generateVariableName(type.getClazz());
    }

    public static String generateVariableName(Type type) {
        return generateVariableName(type.asString());
    }

    public static String generateVariableName(Class<?> clazz) {
        return generateVariableName(clazz.getSimpleName());
    }

    public static String generateVariableName(String className) {
        char a = (char)( 'A' + random.nextInt(26));
        char b = (char)( 'a' + random.nextInt(26));
        char c = (char)( 'a' + random.nextInt(26));

        return AbstractCompiler.classToInstanceName(className) + a + b + c;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}
