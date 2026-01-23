package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Abstraction for field access that works with both AST-based (ResolvedFieldDeclaration)
 * and reflection-based (java.lang.reflect.Field) types.
 * <p>
 * Named ResolvedFieldAdapter to avoid conflict with javassist.bytecode.FieldInfo.
 * </p>
 * <p>
 * This adapter provides a unified API for accessing field metadata regardless of
 * whether the field information comes from JavaParser's symbol solver or Java reflection.
 * </p>
 */
public class ResolvedFieldAdapter {

    private final ResolvedFieldDeclaration resolvedField;
    private final Field reflectionField;

    /**
     * Create adapter from JavaParser's ResolvedFieldDeclaration.
     *
     * @param field the resolved field declaration from symbol solver
     */
    public ResolvedFieldAdapter(ResolvedFieldDeclaration field) {
        this.resolvedField = field;
        this.reflectionField = null;
    }

    /**
     * Create adapter from Java reflection Field.
     *
     * @param field the reflection field
     */
    public ResolvedFieldAdapter(Field field) {
        this.reflectionField = field;
        this.resolvedField = null;
    }

    /**
     * Get the name of the field.
     *
     * @return field name
     */
    public String getName() {
        if (resolvedField != null) {
            return resolvedField.getName();
        }
        if (reflectionField != null) {
            return reflectionField.getName();
        }
        return null;
    }

    /**
     * Get the type of the field as a TypeWrapper.
     *
     * @return TypeWrapper for the field type
     */
    public TypeWrapper getType() {
        if (resolvedField != null) {
            return TypeWrapper.fromResolvedType(resolvedField.getType());
        }
        if (reflectionField != null) {
            return TypeWrapper.fromClass(reflectionField.getType());
        }
        return TypeWrapper.UNKNOWN;
    }

    /**
     * Check if the field has a specific annotation.
     *
     * @param annotationFqn the fully qualified name of the annotation
     * @return true if the annotation is present
     */
    public boolean hasAnnotation(String annotationFqn) {
        if (resolvedField != null) {
            // ResolvedFieldDeclaration doesn't have direct annotation access in all versions
            // Fall back to checking if we can get the underlying AST node
            try {
                // Try to check via the declaring type's field
                return false; // Limited support in ResolvedFieldDeclaration
            } catch (Exception e) {
                return false;
            }
        }
        if (reflectionField != null) {
            for (Annotation ann : reflectionField.getAnnotations()) {
                if (ann.annotationType().getName().equals(annotationFqn)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the field is static.
     *
     * @return true if the field is static
     */
    public boolean isStatic() {
        if (resolvedField != null) {
            return resolvedField.isStatic();
        }
        if (reflectionField != null) {
            return java.lang.reflect.Modifier.isStatic(reflectionField.getModifiers());
        }
        return false;
    }

    /**
     * Check if the field is final.
     *
     * @return true if the field is final
     */
    public boolean isFinal() {
        if (reflectionField != null) {
            return java.lang.reflect.Modifier.isFinal(reflectionField.getModifiers());
        }
        // ResolvedFieldDeclaration doesn't expose final modifier directly
        return false;
    }

    /**
     * Get the fully qualified name of the field type.
     *
     * @return the FQN of the field type
     */
    public String getTypeName() {
        if (resolvedField != null) {
            return resolvedField.getType().describe();
        }
        if (reflectionField != null) {
            return reflectionField.getType().getName();
        }
        return null;
    }

    /**
     * Check if this adapter was created from a ResolvedFieldDeclaration.
     *
     * @return true if backed by ResolvedFieldDeclaration
     */
    public boolean isResolved() {
        return resolvedField != null;
    }

    /**
     * Check if this adapter was created from reflection.
     *
     * @return true if backed by reflection Field
     */
    public boolean isReflection() {
        return reflectionField != null;
    }

    /**
     * Get the underlying ResolvedFieldDeclaration if available.
     *
     * @return the ResolvedFieldDeclaration or null
     */
    public ResolvedFieldDeclaration getResolvedField() {
        return resolvedField;
    }

    /**
     * Get the underlying reflection Field if available.
     *
     * @return the reflection Field or null
     */
    public Field getReflectionField() {
        return reflectionField;
    }

    @Override
    public String toString() {
        String name = getName();
        String typeName = getTypeName();
        return "ResolvedFieldAdapter[" + typeName + " " + name + "]";
    }
}
