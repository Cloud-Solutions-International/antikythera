package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API Compatibility Baseline Test for TypeWrapper Migration.
 *
 * This test documents and verifies the public API signatures that MUST NOT CHANGE
 * during the TypeWrapper migration to ResolvedType.
 *
 * As per TYPE_WRAPPER_MIGRATION_PLAN.md § 4.2:
 * - External projects depend on Antikythera
 * - AbstractCompiler, TypeWrapper, and DepSolver public APIs are FROZEN
 *
 * These tests should pass BEFORE and AFTER the migration.
 */
class TypeWrapperApiBaselineTest {

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        AbstractCompiler.preProcess();
    }

    // ========================================================================
    // TypeWrapper Public API Baseline
    // ========================================================================

    @Nested
    @DisplayName("TypeWrapper Public API Signatures")
    class TypeWrapperApiTests {

        @Test
        @DisplayName("TypeWrapper has constructor(TypeDeclaration)")
        void hasTypeDeclarationConstructor() throws NoSuchMethodException {
            TypeWrapper.class.getConstructor(TypeDeclaration.class);
        }

        @Test
        @DisplayName("TypeWrapper has constructor(Class)")
        void hasClassConstructor() throws NoSuchMethodException {
            TypeWrapper.class.getConstructor(Class.class);
        }

        @Test
        @DisplayName("TypeWrapper has constructor(EnumConstantDeclaration)")
        void hasEnumConstantConstructor() throws NoSuchMethodException {
            TypeWrapper.class.getConstructor(EnumConstantDeclaration.class);
        }

        @Test
        @DisplayName("TypeWrapper has default constructor")
        void hasDefaultConstructor() throws NoSuchMethodException {
            TypeWrapper.class.getConstructor();
        }

        @Test
        @DisplayName("TypeWrapper.getType() returns TypeDeclaration")
        void getTypeReturnsTypeDeclaration() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getType");
            assertEquals(TypeDeclaration.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.getClazz() returns Class")
        void getClazzReturnsClass() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getClazz");
            assertEquals(Class.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.getEnumConstant() returns EnumConstantDeclaration")
        void getEnumConstantReturnsEnumConstantDeclaration() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getEnumConstant");
            assertEquals(EnumConstantDeclaration.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.getFullyQualifiedName() returns String")
        void getFullyQualifiedNameReturnsString() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getFullyQualifiedName");
            assertEquals(String.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.getName() returns String")
        void getNameReturnsString() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getName");
            assertEquals(String.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.isController() returns boolean")
        void isControllerReturnsBoolean() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("isController");
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.isService() returns boolean")
        void isServiceReturnsBoolean() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("isService");
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.isComponent() returns boolean")
        void isComponentReturnsBoolean() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("isComponent");
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.isEntity() returns boolean")
        void isEntityReturnsBoolean() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("isEntity");
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.isInterface() returns boolean")
        void isInterfaceReturnsBoolean() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("isInterface");
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.isAssignableFrom(TypeWrapper) returns boolean")
        void isAssignableFromReturnsBoolean() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("isAssignableFrom", TypeWrapper.class);
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.getEntityAnnotation() returns Optional<AnnotationExpr>")
        void getEntityAnnotationReturnsOptional() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getEntityAnnotation");
            assertEquals(Optional.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.getTableAnnotation() returns Optional<AnnotationExpr>")
        void getTableAnnotationReturnsOptional() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getTableAnnotation");
            assertEquals(Optional.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper.getInheritanceAnnotation() returns Optional<AnnotationExpr>")
        void getInheritanceAnnotationReturnsOptional() throws NoSuchMethodException {
            Method method = TypeWrapper.class.getMethod("getInheritanceAnnotation");
            assertEquals(Optional.class, method.getReturnType());
        }

        @Test
        @DisplayName("TypeWrapper has all required setters")
        void hasAllSetters() throws NoSuchMethodException {
            // These setters must exist for backward compatibility
            TypeWrapper.class.getMethod("setCu", TypeDeclaration.class);
            TypeWrapper.class.getMethod("setClass", Class.class);
            TypeWrapper.class.getMethod("setController", boolean.class);
            TypeWrapper.class.getMethod("setService", boolean.class);
            TypeWrapper.class.getMethod("setComponent", boolean.class);
            TypeWrapper.class.getMethod("setEntity", boolean.class);
            TypeWrapper.class.getMethod("setInterface", boolean.class);
            TypeWrapper.class.getMethod("setEnumConstant", EnumConstantDeclaration.class);
        }

        @Test
        @DisplayName("TypeWrapper public method count baseline")
        void publicMethodCountBaseline() {
            // Count public methods to detect accidental removals
            long publicMethodCount = Arrays.stream(TypeWrapper.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .count();

            // Current baseline: 21 public methods (getters, setters, isAssignableFrom)
            // This can INCREASE (new methods allowed) but should not DECREASE
            assertTrue(publicMethodCount >= 21,
                    "TypeWrapper should have at least 21 public methods, found: " + publicMethodCount);
        }
    }

    // ========================================================================
    // AbstractCompiler Public API Baseline
    // ========================================================================

    @Nested
    @DisplayName("AbstractCompiler Public API Signatures")
    class AbstractCompilerApiTests {

        @Test
        @DisplayName("AbstractCompiler.findType(CompilationUnit, String) returns TypeWrapper")
        void findTypeStringReturnsTypeWrapper() throws NoSuchMethodException {
            Method method = AbstractCompiler.class.getMethod("findType", CompilationUnit.class, String.class);
            assertEquals(TypeWrapper.class, method.getReturnType());
            assertTrue(Modifier.isStatic(method.getModifiers()), "findType should be static");
        }

        @Test
        @DisplayName("AbstractCompiler.findType(CompilationUnit, Type) returns TypeWrapper")
        void findTypeTypeReturnsTypeWrapper() throws NoSuchMethodException {
            Method method = AbstractCompiler.class.getMethod("findType", CompilationUnit.class, Type.class);
            assertEquals(TypeWrapper.class, method.getReturnType());
            assertTrue(Modifier.isStatic(method.getModifiers()), "findType should be static");
        }

        @Test
        @DisplayName("AbstractCompiler.findWrappedTypes(CompilationUnit, Type) returns List")
        void findWrappedTypesReturnsList() throws NoSuchMethodException {
            Method method = AbstractCompiler.class.getMethod("findWrappedTypes", CompilationUnit.class, Type.class);
            assertEquals(List.class, method.getReturnType());
            assertTrue(Modifier.isStatic(method.getModifiers()), "findWrappedTypes should be static");
        }

        @Test
        @DisplayName("AbstractCompiler.findFullyQualifiedName methods exist")
        void findFullyQualifiedNameMethodsExist() throws NoSuchMethodException {
            // These are key methods used by external consumers
            AbstractCompiler.class.getMethod("findFullyQualifiedName", CompilationUnit.class, Type.class);
            AbstractCompiler.class.getMethod("findFullyQualifiedName", CompilationUnit.class, String.class);
        }

        @Test
        @DisplayName("AbstractCompiler.preProcess() exists and is static")
        void preProcessExists() throws NoSuchMethodException {
            Method method = AbstractCompiler.class.getMethod("preProcess");
            assertTrue(Modifier.isStatic(method.getModifiers()), "preProcess should be static");
        }

        @Test
        @DisplayName("AbstractCompiler.reset() exists and is static")
        void resetExists() throws NoSuchMethodException {
            Method method = AbstractCompiler.class.getMethod("reset");
            assertTrue(Modifier.isStatic(method.getModifiers()), "reset should be static");
        }
    }

    // ========================================================================
    // AntikytheraRunTime Public API Baseline
    // ========================================================================

    @Nested
    @DisplayName("AntikytheraRunTime Public API Signatures")
    class AntikytheraRunTimeApiTests {

        @Test
        @DisplayName("AntikytheraRunTime.getCompilationUnit(String) returns CompilationUnit")
        void getCompilationUnitReturnsCompilationUnit() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("getCompilationUnit", String.class);
            assertEquals(CompilationUnit.class, method.getReturnType());
        }

        @Test
        @DisplayName("AntikytheraRunTime.getTypeDeclaration(String) returns Optional")
        void getTypeDeclarationReturnsOptional() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("getTypeDeclaration", String.class);
            assertEquals(Optional.class, method.getReturnType());
        }

        @Test
        @DisplayName("AntikytheraRunTime.addType(String, TypeWrapper) exists")
        void addTypeExists() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("addType", String.class, TypeWrapper.class);
            assertEquals(void.class, method.getReturnType());
        }

        @Test
        @DisplayName("AntikytheraRunTime.getResolvedTypes() returns Map")
        void getResolvedTypesReturnsMap() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("getResolvedTypes");
            assertEquals(java.util.Map.class, method.getReturnType());
        }

        @Test
        @DisplayName("AntikytheraRunTime.isServiceClass(String) returns boolean")
        void isServiceClassReturnsBoolean() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("isServiceClass", String.class);
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("AntikytheraRunTime.isControllerClass(String) returns boolean")
        void isControllerClassReturnsBoolean() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("isControllerClass", String.class);
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("AntikytheraRunTime.isComponentClass(String) returns boolean")
        void isComponentClassReturnsBoolean() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("isComponentClass", String.class);
            assertEquals(boolean.class, method.getReturnType());
        }

        @Test
        @DisplayName("AntikytheraRunTime.isInterface(String) returns boolean")
        void isInterfaceReturnsBoolean() throws NoSuchMethodException {
            Method method = AntikytheraRunTime.class.getMethod("isInterface", String.class);
            assertEquals(boolean.class, method.getReturnType());
        }
    }

    // ========================================================================
    // Behavioral Contract Tests
    // ========================================================================

    @Nested
    @DisplayName("Behavioral Contracts")
    class BehavioralContractTests {

        @Test
        @DisplayName("findType returns null for non-existent types")
        void findTypeReturnsNullForNonExistent() {
            TypeWrapper result = AbstractCompiler.findType(null, "NonExistentType12345XYZ");
            assertNull(result, "findType should return null for non-existent types");
        }

        @Test
        @DisplayName("TypeWrapper constructors create valid instances")
        void constructorsCreateValidInstances() {
            // Test each constructor creates a usable instance
            TypeWrapper tw1 = new TypeWrapper(String.class);
            assertNotNull(tw1.getClazz());

            TypeWrapper tw2 = new TypeWrapper();
            assertNotNull(tw2); // Default constructor works
        }

        @Test
        @DisplayName("getFullyQualifiedName returns null when both type and clazz are null")
        void getFqnReturnsNullWhenBothNull() {
            TypeWrapper empty = new TypeWrapper();
            assertNull(empty.getFullyQualifiedName());
        }

        @Test
        @DisplayName("isAssignableFrom returns false for null argument")
        void isAssignableFromReturnsFalseForNull() {
            TypeWrapper wrapper = new TypeWrapper(String.class);
            assertFalse(wrapper.isAssignableFrom(null));
        }

        @Test
        @DisplayName("Annotation getter methods return Optional.empty() when type is null")
        void annotationGettersReturnEmptyForNullType() {
            TypeWrapper wrapper = new TypeWrapper(String.class); // Has clazz but no type
            assertTrue(wrapper.getEntityAnnotation().isEmpty());
            assertTrue(wrapper.getTableAnnotation().isEmpty());
            assertTrue(wrapper.getInheritanceAnnotation().isEmpty());
        }
    }
}
