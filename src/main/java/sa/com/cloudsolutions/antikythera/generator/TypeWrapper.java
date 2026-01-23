package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    // Legacy Getters (Preserved for Backward Compatibility with Lazy Derivation)
    // ========================================================================

    /**
     * Get the AST TypeDeclaration if available.
     * <p>
     * Implements lazy derivation: if type is null but resolvedType is available,
     * attempts to retrieve the TypeDeclaration from AntikytheraRunTime cache.
     * </p>
     *
     * @return TypeDeclaration or null if not available
     */
    @SuppressWarnings("java:S1452")
    public TypeDeclaration<?> getType() {
        if (type == null && resolvedType != null && resolvedType.isReferenceType()) {
            try {
                String fqn = resolvedType.asReferenceType().getQualifiedName();
                type = AntikytheraRunTime.getTypeDeclaration(fqn).orElse(null);
            } catch (Exception e) {
                logger.debug("Could not derive TypeDeclaration from ResolvedType: {}", e.getMessage());
            }
        }
        return type;
    }

    public void setCu(TypeDeclaration<?> type) {
        this.type = type;
    }

    /**
     * Get the reflection Class if available.
     * <p>
     * Implements lazy derivation: if clazz is null but resolvedType is available,
     * attempts to load the Class using Class.forName().
     * </p>
     *
     * @return Class or null if not available (e.g., source-only types)
     */
    public Class<?> getClazz() {
        if (clazz == null && resolvedType != null && resolvedType.isReferenceType()) {
            try {
                String fqn = resolvedType.asReferenceType().getQualifiedName();
                clazz = Class.forName(fqn);
            } catch (ClassNotFoundException e) {
                // Source-only type, no Class available - this is expected
                logger.debug("Class not found for {}", e.getMessage());
            } catch (Exception e) {
                logger.debug("Could not derive Class from ResolvedType: {}", e.getMessage());
            }
        }
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

    /**
     * Check if this type is annotated with @Controller or @RestController.
     * <p>
     * Uses dynamic annotation checking via reflection when possible,
     * falling back to the cached flag.
     * </p>
     *
     * @return true if the type is a Spring controller
     */
    public boolean isController() {
        // Try dynamic checking via reflection first
        if (clazz != null || (resolvedType != null && resolvedType.isReferenceType())) {
            Class<?> c = getClazz();
            if (c != null) {
                return hasAnnotation(c, "org.springframework.stereotype.Controller")
                        || hasAnnotation(c, "org.springframework.web.bind.annotation.RestController");
            }
        }
        // Fall back to cached flag (set during preprocessing)
        return isController;
    }

    public void setController(boolean isController) {
        this.isController = isController;
    }

    /**
     * Check if this type is annotated with @Service.
     * <p>
     * Uses dynamic annotation checking via reflection when possible,
     * falling back to the cached flag.
     * </p>
     *
     * @return true if the type is a Spring service
     */
    public boolean isService() {
        // Try dynamic checking via reflection first
        if (clazz != null || (resolvedType != null && resolvedType.isReferenceType())) {
            Class<?> c = getClazz();
            if (c != null) {
                return hasAnnotation(c, "org.springframework.stereotype.Service");
            }
        }
        // Fall back to cached flag
        return isService;
    }

    public void setService(boolean isService) {
        this.isService = isService;
    }

    /**
     * Check if this type is annotated with @Component.
     * <p>
     * Uses dynamic annotation checking via reflection when possible,
     * falling back to the cached flag.
     * </p>
     *
     * @return true if the type is a Spring component
     */
    public boolean isComponent() {
        // Try dynamic checking via reflection first
        if (clazz != null || (resolvedType != null && resolvedType.isReferenceType())) {
            Class<?> c = getClazz();
            if (c != null) {
                return hasAnnotation(c, "org.springframework.stereotype.Component");
            }
        }
        // Fall back to cached flag
        return component;
    }

    public void setComponent(boolean component) {
        this.component = component;
    }

    /**
     * Check if this type is an interface.
     * <p>
     * Uses reflection or AST inspection when possible,
     * falling back to the cached flag.
     * </p>
     *
     * @return true if the type is an interface
     */
    public boolean isInterface() {
        if (clazz != null) {
            return clazz.isInterface();
        }
        if (type != null && type.isClassOrInterfaceDeclaration()) {
            return type.asClassOrInterfaceDeclaration().isInterface();
        }
        // Fall back to cached flag
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

    /**
     * Check if this type is annotated with @Entity.
     * <p>
     * Uses dynamic annotation checking via reflection when possible,
     * falling back to the cached flag.
     * </p>
     *
     * @return true if the type is a JPA entity
     */
    public boolean isEntity() {
        // Try dynamic checking via reflection first
        if (clazz != null || (resolvedType != null && resolvedType.isReferenceType())) {
            Class<?> c = getClazz();
            if (c != null) {
                return hasAnnotation(c, "jakarta.persistence.Entity")
                        || hasAnnotation(c, "javax.persistence.Entity");
            }
        }
        // Fall back to cached flag
        return isEntity;
    }

    public void setEntity(boolean isEntity) {
        this.isEntity = isEntity;
    }

    /**
     * Helper method to check if a class has a specific annotation.
     *
     * @param c the class to check
     * @param annotationFqn the fully qualified name of the annotation
     * @return true if the annotation is present
     */
    private boolean hasAnnotation(Class<?> c, String annotationFqn) {
        for (java.lang.annotation.Annotation ann : c.getAnnotations()) {
            if (ann.annotationType().getName().equals(annotationFqn)) {
                return true;
            }
        }
        return false;
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
     * <p>
     * Implements multi-stage fallback:
     * <ol>
     *   <li>ResolvedType.isAssignableBy() if both have ResolvedType</li>
     *   <li>FQN equality check (fast path)</li>
     *   <li>Cross-boundary resolution (source ↔ JAR)</li>
     *   <li>AST inheritance traversal</li>
     *   <li>Reflection fallback</li>
     * </ol>
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

        // 2. FQN equality check (fast path)
        String fqn1 = this.getFullyQualifiedName();
        String fqn2 = other.getFullyQualifiedName();

        if (fqn1 != null && fqn1.equals(fqn2)) {
            return true;
        }

        // 3. Cross-boundary resolution (source ↔ JAR)
        // When one type is from source and other from JAR
        if (this.resolvedType != null && other.clazz != null) {
            if (isAssignableFromMixed(fqn1, other.clazz)) {
                return true;
            }
        }
        if (this.clazz != null && other.resolvedType != null) {
            if (isAssignableFromMixed(fqn2, this.clazz)) {
                // Note: reversed - checking if other's type is assignable from this
                return false; // This check doesn't make sense for assignability direction
            }
        }

        // 4. AST Inheritance Check
        // If the OTHER type is an AST type, we can check its ancestors to see if THIS
        // (ancestor) is one of them.
        if (other.type != null && other.type.isClassOrInterfaceDeclaration()) {
            if (isAssignableFromAST(other, fqn1)) {
                return true;
            }
        }

        // 5. Reflection fallback
        if (this.clazz != null && other.clazz != null) {
            return this.clazz.isAssignableFrom(other.clazz);
        }

        // 6. Try to derive clazz for both and use reflection
        Class<?> thisClass = this.getClazz();
        Class<?> otherClass = other.getClazz();
        if (thisClass != null && otherClass != null) {
            return thisClass.isAssignableFrom(otherClass);
        }

        return false;
    }

    /**
     * Handle cross-boundary type compatibility when one type is from ResolvedType
     * and the other is from reflection.
     *
     * @param resolvedFqn the FQN of the resolved type
     * @param otherClass the Class to check
     * @return true if otherClass implements/extends the resolved type
     */
    private boolean isAssignableFromMixed(String resolvedFqn, Class<?> otherClass) {
        if (resolvedFqn == null || otherClass == null) {
            return false;
        }

        // Direct match
        if (resolvedFqn.equals(otherClass.getName())) {
            return true;
        }

        // Check if otherClass implements the resolved type (interface check)
        for (Class<?> iface : otherClass.getInterfaces()) {
            if (iface.getName().equals(resolvedFqn)) {
                return true;
            }
            // Recursive check for interface hierarchy
            if (isAssignableFromMixed(resolvedFqn, iface)) {
                return true;
            }
        }

        // Check superclass hierarchy
        Class<?> superclass = otherClass.getSuperclass();
        while (superclass != null) {
            if (superclass.getName().equals(resolvedFqn)) {
                return true;
            }
            // Also check superclass interfaces
            for (Class<?> iface : superclass.getInterfaces()) {
                if (iface.getName().equals(resolvedFqn)) {
                    return true;
                }
            }
            superclass = superclass.getSuperclass();
        }

        return false;
    }

    /**
     * AST-based inheritance check.
     */
    private static boolean isAssignableFromAST(TypeWrapper other, String fqn1) {
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

    // ========================================================================
    // Generic Type Support (New API)
    // ========================================================================

    /**
     * Get the type arguments if this is a parameterized type.
     * <p>
     * For example, for {@code List<String>}, returns a list containing
     * the TypeWrapper for String.
     * </p>
     *
     * @return list of type argument TypeWrappers, or empty list if not parameterized
     */
    public List<TypeWrapper> getTypeArguments() {
        if (resolvedType != null && resolvedType.isReferenceType()) {
            try {
                var refType = resolvedType.asReferenceType();
                var typeParams = refType.getTypeParametersMap();
                if (!typeParams.isEmpty()) {
                    List<TypeWrapper> result = new ArrayList<>();
                    for (var pair : typeParams) {
                        result.add(fromResolvedType(pair.b));
                    }
                    return result;
                }
            } catch (Exception e) {
                logger.debug("Could not get type arguments: {}", e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    /**
     * Get the raw type (erased type) for this TypeWrapper.
     * <p>
     * For a parameterized type like {@code List<String>}, returns the TypeWrapper
     * for the raw type {@code List}.
     * </p>
     *
     * @return this TypeWrapper (which represents the raw type)
     */
    public TypeWrapper getRawType() {
        // For a TypeWrapper, this IS the raw type
        // The type arguments are accessed separately via getTypeArguments()
        return this;
    }

    // ========================================================================
    // Field Access Support (New API)
    // ========================================================================

    /**
     * Get the declared fields of this type.
     * <p>
     * Works with both AST-based and reflection-based types through the
     * ResolvedFieldAdapter abstraction.
     * </p>
     *
     * @return list of field adapters, or empty list if fields cannot be accessed
     */
    public List<ResolvedFieldAdapter> getFields() {
        // Try ResolvedType first
        if (resolvedType != null && resolvedType.isReferenceType()) {
            try {
                Optional<ResolvedReferenceTypeDeclaration> declOpt =
                        resolvedType.asReferenceType().getTypeDeclaration();
                if (declOpt.isPresent()) {
                    var decl = declOpt.get();
                    List<ResolvedFieldAdapter> result = new ArrayList<>();
                    for (var field : decl.getDeclaredFields()) {
                        result.add(new ResolvedFieldAdapter(field));
                    }
                    return result;
                }
            } catch (Exception e) {
                logger.debug("Could not get fields from ResolvedType: {}", e.getMessage());
            }
        }

        // Fall back to reflection
        Class<?> c = getClazz();
        if (c != null) {
            List<ResolvedFieldAdapter> result = new ArrayList<>();
            for (java.lang.reflect.Field field : c.getDeclaredFields()) {
                result.add(new ResolvedFieldAdapter(field));
            }
            return result;
        }

        return Collections.emptyList();
    }
}
