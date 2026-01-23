package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.types.ResolvedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Wrapper for Java types that bridges AST-based types (TypeDeclaration),
 * reflection-based types (Class), and JavaParser's ResolvedType.
 *
 * <p>This class is undergoing migration to use ResolvedType as the primary
 * internal representation. During the migration period, all three representations
 * may coexist.</p>
 *
 * <h3>Factory Methods (Preferred)</h3>
 * <ul>
 *   <li>{@link #fromTypeDeclaration(TypeDeclaration)} - Create from AST type</li>
 *   <li>{@link #fromClass(Class)} - Create from reflection type</li>
 *   <li>{@link #fromEnumConstant(EnumConstantDeclaration)} - Create from enum constant</li>
 *   <li>{@link #fromResolvedType(ResolvedType)} - Create directly from ResolvedType</li>
 * </ul>
 *
 * <h3>Legacy Constructors (Deprecated)</h3>
 * <p>The single-argument constructors are deprecated but still functional for
 * backward compatibility. New code should use factory methods.</p>
 */
public class TypeWrapper {

    private static final Logger logger = LoggerFactory.getLogger(TypeWrapper.class);

    /**
     * Sentinel value for unresolved types.
     * Use this instead of null when a type cannot be resolved.
     */
    public static final TypeWrapper UNKNOWN = new TypeWrapper();

    // ========================================================================
    // Fields
    // ========================================================================

    /**
     * Primary type representation (Phase 1+).
     * Will become the single source of truth after migration.
     */
    private ResolvedType resolvedType;

    /**
     * AST-based type representation.
     * @deprecated Will be lazily derived from resolvedType in future versions.
     */
    TypeDeclaration<?> type;

    /**
     * Reflection-based type representation.
     * @deprecated Will be lazily derived from resolvedType in future versions.
     */
    Class<?> clazz;

    /**
     * Special case: enum constant (cannot be represented as ResolvedType).
     * This field is NOT deprecated as enum constants require special handling.
     */
    EnumConstantDeclaration enumConstant;

    // Annotation flags (set during preprocessing)
    private boolean isController;
    private boolean isService;
    private boolean component;
    private boolean isInterface;
    private boolean isEntity;

    // ========================================================================
    // Factory Methods (Preferred Creation Path)
    // ========================================================================

    /**
     * Create TypeWrapper from AST TypeDeclaration.
     * <p>
     * Note: ResolvedType derivation from TypeDeclaration is not yet implemented.
     * This will be enhanced in a future phase of the migration.
     * </p>
     *
     * @param type the AST type declaration
     * @return TypeWrapper instance
     */
    public static TypeWrapper fromTypeDeclaration(TypeDeclaration<?> type) {
        if (type == null) {
            return UNKNOWN;
        }
        // ResolvedType derivation from TypeDeclaration requires resolving a Type reference,
        // not the declaration itself. This will be enhanced in Phase 2.
        return new TypeWrapper(type, null);
    }

    /**
     * Create TypeWrapper from reflection Class.
     * <p>
     * Note: ResolvedType derivation from Class is not yet implemented.
     * This will be enhanced in a future phase of the migration.
     * </p>
     *
     * @param clazz the reflection class
     * @return TypeWrapper instance
     */
    public static TypeWrapper fromClass(Class<?> clazz) {
        if (clazz == null) {
            return UNKNOWN;
        }
        // ResolvedType derivation from Class will be enhanced in Phase 2.
        return new TypeWrapper(clazz, null);
    }

    /**
     * Create TypeWrapper from EnumConstantDeclaration.
     * <p><strong>SPECIAL CASE:</strong> ResolvedType cannot represent enum constants,
     * so this factory method does not attempt resolution.</p>
     *
     * @param constant the enum constant declaration
     * @return TypeWrapper instance (without ResolvedType)
     */
    public static TypeWrapper fromEnumConstant(EnumConstantDeclaration constant) {
        if (constant == null) {
            return UNKNOWN;
        }
        return new TypeWrapper(constant);
    }

    /**
     * Create TypeWrapper directly from ResolvedType.
     * Use when symbol solver already resolved the type.
     *
     * @param resolved the resolved type from JavaParser symbol solver
     * @return TypeWrapper instance
     */
    public static TypeWrapper fromResolvedType(ResolvedType resolved) {
        if (resolved == null) {
            return UNKNOWN;
        }
        return new TypeWrapper(resolved);
    }

    // ========================================================================
    // Internal Constructors (Package-Private for Migration)
    // ========================================================================

    /**
     * Internal constructor for TypeDeclaration with optional ResolvedType.
     */
    TypeWrapper(TypeDeclaration<?> type, ResolvedType resolved) {
        this.type = type;
        this.resolvedType = resolved;
        this.clazz = null;
        this.enumConstant = null;
    }

    /**
     * Internal constructor for Class with optional ResolvedType.
     */
    TypeWrapper(Class<?> clazz, ResolvedType resolved) {
        this.clazz = clazz;
        this.resolvedType = resolved;
        this.type = null;
        this.enumConstant = null;
    }

    /**
     * Internal constructor for ResolvedType only.
     */
    TypeWrapper(ResolvedType resolved) {
        this.resolvedType = resolved;
        this.type = null;
        this.clazz = null;
        this.enumConstant = null;
    }

    // ========================================================================
    // Deprecated Constructors (For Backward Compatibility)
    // ========================================================================

    /**
     * @deprecated Use {@link #fromTypeDeclaration(TypeDeclaration)} instead.
     *             This constructor does not populate ResolvedType.
     */
    @Deprecated
    public TypeWrapper(TypeDeclaration<?> type) {
        this.type = type;
    }

    /**
     * @deprecated Use {@link #fromClass(Class)} instead.
     *             This constructor does not populate ResolvedType.
     */
    @Deprecated
    public TypeWrapper(Class<?> cls) {
        this.clazz = cls;
    }

    /**
     * @deprecated Use {@link #fromEnumConstant(EnumConstantDeclaration)} instead.
     */
    @Deprecated
    public TypeWrapper(EnumConstantDeclaration enumConstant) {
        this.enumConstant = enumConstant;
    }

    /**
     * @deprecated Default constructor creates an empty wrapper.
     *             Consider using {@link #UNKNOWN} sentinel instead.
     */
    @Deprecated
    public TypeWrapper() {
        // Empty constructor for backward compatibility
    }

    // ========================================================================
    // ResolvedType Access (New API)
    // ========================================================================

    /**
     * Get the underlying ResolvedType if available.
     *
     * @return the ResolvedType, or null if not resolved
     */
    public ResolvedType getResolvedType() {
        return resolvedType;
    }

    /**
     * Check if this TypeWrapper has a resolved type.
     *
     * @return true if ResolvedType is available
     */
    public boolean isResolved() {
        return resolvedType != null;
    }

    /**
     * Check if this represents a primitive type.
     *
     * @return true if the type is a primitive (int, boolean, etc.)
     */
    public boolean isPrimitive() {
        if (resolvedType != null) {
            return resolvedType.isPrimitive();
        }
        if (clazz != null) {
            return clazz.isPrimitive();
        }
        return false;
    }

    /**
     * Check if this represents an array type.
     *
     * @return true if the type is an array
     */
    public boolean isArray() {
        if (resolvedType != null) {
            return resolvedType.isArray();
        }
        if (clazz != null) {
            return clazz.isArray();
        }
        return false;
    }

    /**
     * Get the component type if this is an array.
     *
     * @return TypeWrapper for the component type, or null if not an array
     */
    public TypeWrapper getComponentType() {
        if (resolvedType != null && resolvedType.isArray()) {
            return new TypeWrapper(resolvedType.asArrayType().getComponentType());
        }
        if (clazz != null && clazz.isArray()) {
            return fromClass(clazz.getComponentType());
        }
        return null;
    }

    // ========================================================================
    // Legacy Getters (Preserved for Backward Compatibility)
    // ========================================================================

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
        // Try ResolvedType first (new path)
        if (resolvedType != null) {
            try {
                if (resolvedType.isReferenceType()) {
                    return resolvedType.asReferenceType().getQualifiedName();
                } else if (resolvedType.isPrimitive()) {
                    return resolvedType.asPrimitive().name().toLowerCase();
                } else if (resolvedType.isArray()) {
                    TypeWrapper component = getComponentType();
                    if (component != null) {
                        return component.getFullyQualifiedName() + "[]";
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not get FQN from ResolvedType: {}", e.getMessage());
            }
        }

        // Fall back to legacy fields
        if (clazz != null) {
            return clazz.getName();
        }
        if (type != null) {
            return type.getFullyQualifiedName().orElse(null);
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
        if (resolvedType != null) {
            return resolvedType.describe();
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

        // 1. Try ResolvedType first (if both have it)
        if (this.resolvedType != null && other.resolvedType != null) {
            try {
                return this.resolvedType.isAssignableBy(other.resolvedType);
            } catch (Exception e) {
                // Fall through to legacy logic on resolution failure
                logger.debug("ResolvedType.isAssignableBy failed: {}", e.getMessage());
            }
        }

        // 2. If both are reflection-based, use standard reflection
        if (this.clazz != null && other.clazz != null) {
            return this.clazz.isAssignableFrom(other.clazz);
        }

        // 3. If both are AST-based or Mixed - Check FQN equality first
        String fqn1 = this.getFullyQualifiedName();
        String fqn2 = other.getFullyQualifiedName();

        if (fqn1 != null && fqn1.equals(fqn2)) {
            return true;
        }

        // 4. AST Inheritance Check
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
