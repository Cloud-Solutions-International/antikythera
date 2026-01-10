package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.util.Optional;

public class TypeWrapper {
    TypeDeclaration<?> type;
    Class<?> clazz;
    EnumConstantDeclaration enumConstant;

    private boolean isController;
    private boolean isService;
    private boolean component;
    private boolean isInterface;
    private boolean isEntity;

    public TypeWrapper(TypeDeclaration<?> type) {
        this.type = type;
    }

    public TypeWrapper(Class<?> cls) {
        this.clazz = cls;
    }

    public TypeWrapper(EnumConstantDeclaration enumConstant) {
        this.enumConstant = enumConstant;
    }

    public TypeWrapper() {

    }

    @SuppressWarnings("java:S1452")
    public TypeDeclaration<?> getType() {
        return type;
    }

    public void setCu(TypeDeclaration<?> type) {
        this.type = type;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public void setClass(Class<?> cls) {
        this.clazz = cls;
    }

    public String getFullyQualifiedName() {
        if (clazz == null) {
            if (type != null) {
                return type.getFullyQualifiedName().orElseThrow();
            }
        } else {
            return clazz.getName();
        }
        return null;
    }

    public boolean isController() {
        return isController;
    }

    public void setController(boolean isController) {
        this.isController = isController;
    }

    public boolean isService() {
        return isService;
    }

    public void setService(boolean isService) {
        this.isService = isService;
    }

    public boolean isComponent() {
        return component;
    }

    public void setComponent(boolean component) {
        this.component = component;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }

    public EnumConstantDeclaration getEnumConstant() {
        return enumConstant;
    }

    public void setEnumConstant(EnumConstantDeclaration enumConstant) {
        this.enumConstant = enumConstant;
    }

    public boolean isEntity() {
        return isEntity;
    }

    public void setEntity(boolean isEntity) {
        this.isEntity = isEntity;
    }

    /**
     * Gets the @Entity annotation if present on the type.
     *
     * @return Optional containing the annotation, or empty if not found
     */
    public Optional<AnnotationExpr> getEntityAnnotation() {
        if (type != null) {
            return type.getAnnotationByName("Entity");
        }
        return Optional.empty();
    }

    /**
     * Gets the @Table annotation if present on the type.
     *
     * @return Optional containing the annotation, or empty if not found
     */
    public Optional<AnnotationExpr> getTableAnnotation() {
        if (type != null) {
            return type.getAnnotationByName("Table");
        }
        return Optional.empty();
    }

    /**
     * Gets the @Inheritance annotation if present on the type.
     *
     * @return Optional containing the annotation, or empty if not found
     */
    public Optional<AnnotationExpr> getInheritanceAnnotation() {
        if (type != null) {
            return type.getAnnotationByName("Inheritance");
        }
        return Optional.empty();
    }

    /**
     * Gets the @DiscriminatorColumn annotation if present on the type.
     *
     * @return Optional containing the annotation, or empty if not found
     */
    public Optional<AnnotationExpr> getDiscriminatorColumnAnnotation() {
        if (type != null) {
            return type.getAnnotationByName("DiscriminatorColumn");
        }
        return Optional.empty();
    }

    /**
     * Gets the @DiscriminatorValue annotation if present on the type.
     *
     * @return Optional containing the annotation, or empty if not found
     */
    public Optional<AnnotationExpr> getDiscriminatorValueAnnotation() {
        if (type != null) {
            return type.getAnnotationByName("DiscriminatorValue");
        }
        return Optional.empty();
    }

    public String getName() {
        if (clazz != null) {
            return clazz.getName();
        }
        if (type != null) {
            return type.getNameAsString();
        }
        return null;
    }

    /**
     * Check if this type can be assigned from the other type.
     * Handles both reflection-based and AST-based types.
     * 
     * @param other the type wrapper to check compatibility with
     * @return true if compatible
     */
    public boolean isAssignableFrom(TypeWrapper other) {
        if (other == null)
            return false;

        // 1. If both are reflection-based, use standard reflection
        if (this.clazz != null && other.clazz != null) {
            return this.clazz.isAssignableFrom(other.clazz);
        }

        // 2. If both are AST-based or Mixed - Check FQN equality first
        String fqn1 = this.getFullyQualifiedName();
        String fqn2 = other.getFullyQualifiedName();

        if (fqn1 != null && fqn1.equals(fqn2)) {
            return true;
        }

        // 3. AST Inheritance Check
        // If the OTHER type is an AST type, we can check its ancestors to see if THIS
        // (ancestor) is one of them.
        if (other.type != null && other.type.isClassOrInterfaceDeclaration()) {
            return isAssignableFrom(other, fqn1);
        }

        return false;
    }

    private static boolean isAssignableFrom(TypeWrapper other, String fqn1) {
        var typeDecl = other.type.asClassOrInterfaceDeclaration();

        // Check extended types (superclasses)
        if (typeDecl.getExtendedTypes() != null) {
            for (var extended : typeDecl.getExtendedTypes()) {
                String extendedFQN = sa.com.cloudsolutions.antikythera.parser.AbstractCompiler
                        .findFullyQualifiedName(
                                other.type.findCompilationUnit().orElse(null), extended);
                if (extendedFQN != null && extendedFQN.equals(fqn1)) {
                    return true;
                }
            }
        }

        // Check implemented interfaces
        if (typeDecl.getImplementedTypes() != null) {
            for (var implemented : typeDecl.getImplementedTypes()) {
                String implFQN = sa.com.cloudsolutions.antikythera.parser.AbstractCompiler.findFullyQualifiedName(
                        other.type.findCompilationUnit().orElse(null), implemented);
                if (implFQN != null && implFQN.equals(fqn1)) {
                    return true;
                }
            }
        }
        return false;
    }
}
