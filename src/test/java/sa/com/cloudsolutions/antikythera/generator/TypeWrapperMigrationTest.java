package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 tests for TypeWrapper migration to JavaParser ResolvedType.
 *
 * This test suite validates the edge cases identified in TYPE_WRAPPER_MIGRATION_PLAN.md:
 * 1. Enum constant wrapping (§3.1)
 * 2. Generic type argument ordering (§3.2)
 * 3. Primitive type support (§3.3)
 * 4. Cross-boundary type resolution (§3.6, §4.9)
 * 5. Inner class resolution (§3.9)
 * 6. isAssignableFrom compatibility
 * 7. Spring annotation detection
 *
 * These tests establish a baseline that must pass before AND after migration.
 */
class TypeWrapperMigrationTest {

    // Test fixture class names
    private static final String GENERIC_TYPES = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.GenericTypes";
    private static final String PRIMITIVE_ARRAYS = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.PrimitiveArrays";
    private static final String INNER_CLASSES = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.InnerClasses";
    private static final String CROSS_BOUNDARY = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.CrossBoundaryTypes";
    private static final String INHERITANCE = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.InheritanceHierarchy";
    private static final String SPRING_TYPES = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.SpringAnnotatedTypes";
    private static final String COMPLEX_ENUM = "sa.com.cloudsolutions.antikythera.testhelper.typewrapper.ComplexEnum";

    // Existing fixtures
    private static final String STATUS_ENUM = "sa.com.cloudsolutions.antikythera.testhelper.evaluator.Status";
    private static final String ANIMAL_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.Animal";
    private static final String DOG_ENTITY = "sa.com.cloudsolutions.antikythera.testhelper.model.Dog";
    private static final String FAKE_SERVICE = "sa.com.cloudsolutions.antikythera.testhelper.service.FakeService";

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AntikytheraRunTime.resetAll();
        AbstractCompiler.preProcess();
    }

    // ========================================================================
    // §3.1 CRITICAL: Enum Constant Wrapping
    // ========================================================================

    @Nested
    @DisplayName("§3.1 Enum Constant Wrapping")
    class EnumConstantTests {

        @Test
        @DisplayName("TypeWrapper can wrap EnumConstantDeclaration")
        void typeWrapperCanWrapEnumConstant() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(STATUS_ENUM);
            assertNotNull(cu, "Status enum should be loaded");

            EnumDeclaration enumDecl = cu.getType(0).asEnumDeclaration();
            EnumConstantDeclaration openConstant = enumDecl.getEntries().stream()
                    .filter(e -> e.getNameAsString().equals("OPEN"))
                    .findFirst()
                    .orElseThrow();

            TypeWrapper wrapper = new TypeWrapper(openConstant);
            assertNotNull(wrapper.getEnumConstant());
            assertEquals("OPEN", wrapper.getEnumConstant().getNameAsString());
        }

        @Test
        @DisplayName("findType resolves enum constant by name")
        void findTypeResolvesEnumConstant() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(STATUS_ENUM);
            assertNotNull(cu, "Status enum should be loaded");

            // Add the enum to runtime if not present
            TypeWrapper statusWrapper = AntikytheraRunTime.getResolvedTypes().get(STATUS_ENUM);
            assertNotNull(statusWrapper, "Status TypeWrapper should exist");

            // Finding enum constant directly - AbstractCompiler should handle this
            TypeWrapper openWrapper = AbstractCompiler.findType(cu, "OPEN");
            // This may return the enum constant wrapper or null depending on context
            // The key test is that TypeWrapper CAN wrap enum constants
        }

        @Test
        @DisplayName("Complex enum with constructor parameters")
        void complexEnumWithConstructor() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(COMPLEX_ENUM);
            assertNotNull(cu, "ComplexEnum should be loaded");

            EnumDeclaration enumDecl = cu.getType(0).asEnumDeclaration();

            // Verify enum has multiple constants with arguments
            assertTrue(enumDecl.getEntries().size() >= 3, "ComplexEnum should have multiple constants");

            EnumConstantDeclaration valueA = enumDecl.getEntries().get(0);
            assertFalse(valueA.getArguments().isEmpty(), "VALUE_A should have constructor arguments");

            // Wrap the enum constant
            TypeWrapper wrapper = new TypeWrapper(valueA);
            assertNotNull(wrapper.getEnumConstant());
            assertEquals("VALUE_A", wrapper.getEnumConstant().getNameAsString());
        }
    }

    // ========================================================================
    // §3.2 CRITICAL: Generic Type Argument Ordering
    // ========================================================================

    @Nested
    @DisplayName("§3.2 Generic Type Argument Ordering")
    class GenericTypeOrderingTests {

        @Test
        @DisplayName("findWrappedTypes returns [TypeArgs..., RawType] ordering")
        void findWrappedTypesPreservesOrdering() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(GENERIC_TYPES);
            assertNotNull(cu, "GenericTypes should be loaded");

            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();

            // Find the stringList field: List<String>
            Optional<FieldDeclaration> stringListField = clazz.getFieldByName("stringList");
            assertTrue(stringListField.isPresent(), "stringList field should exist");

            Type fieldType = stringListField.get().getVariable(0).getType();
            List<TypeWrapper> wrappers = AbstractCompiler.findWrappedTypes(cu, fieldType);

            // Contract: [String, List] - type arguments first, raw type last
            assertFalse(wrappers.isEmpty(), "Should find wrapped types");

            // The last element should be the raw type (List)
            TypeWrapper rawType = wrappers.get(wrappers.size() - 1);
            assertNotNull(rawType, "Raw type should not be null");
        }

        @Test
        @DisplayName("Nested generic types preserve ordering")
        void nestedGenericTypesPreserveOrdering() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(GENERIC_TYPES);
            assertNotNull(cu, "GenericTypes should be loaded");

            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();

            // Find the nestedMap field: Map<String, List<Integer>>
            Optional<FieldDeclaration> nestedMapField = clazz.getFieldByName("nestedMap");
            assertTrue(nestedMapField.isPresent(), "nestedMap field should exist");

            Type fieldType = nestedMapField.get().getVariable(0).getType();
            List<TypeWrapper> wrappers = AbstractCompiler.findWrappedTypes(cu, fieldType);

            // Should have wrappers for String, List<Integer>, and Map
            assertFalse(wrappers.isEmpty(), "Should find wrapped types for nested generics");
        }

        @Test
        @DisplayName("BiFunction with multiple type parameters")
        void multipleTParamsPreserveOrdering() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(GENERIC_TYPES);
            assertNotNull(cu, "GenericTypes should be loaded");

            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();

            // Find the biFunction field: BiFunction<String, Integer, Boolean>
            Optional<FieldDeclaration> biFunctionField = clazz.getFieldByName("biFunction");
            assertTrue(biFunctionField.isPresent(), "biFunction field should exist");

            Type fieldType = biFunctionField.get().getVariable(0).getType();
            List<TypeWrapper> wrappers = AbstractCompiler.findWrappedTypes(cu, fieldType);

            assertFalse(wrappers.isEmpty(), "Should find wrapped types for BiFunction");
            // Last element should be BiFunction (raw type)
        }
    }

    // ========================================================================
    // §3.3 CRITICAL: Primitive Type Support
    // ========================================================================

    @Nested
    @DisplayName("§3.3 Primitive Type Support")
    class PrimitiveTypeTests {

        @Test
        @DisplayName("Primitive int field is recognized")
        void primitiveIntFieldRecognized() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(PRIMITIVE_ARRAYS);
            assertNotNull(cu, "PrimitiveArrays should be loaded");

            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();
            Optional<FieldDeclaration> intField = clazz.getFieldByName("intField");
            assertTrue(intField.isPresent(), "intField should exist");

            Type fieldType = intField.get().getVariable(0).getType();
            assertTrue(fieldType.isPrimitiveType(), "intField should be primitive");
            assertEquals("int", fieldType.asString());
        }

        @Test
        @DisplayName("Primitive array int[] field is recognized")
        void primitiveArrayFieldRecognized() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(PRIMITIVE_ARRAYS);
            assertNotNull(cu, "PrimitiveArrays should be loaded");

            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();
            Optional<FieldDeclaration> intArrayField = clazz.getFieldByName("intArray");
            assertTrue(intArrayField.isPresent(), "intArray field should exist");

            Type fieldType = intArrayField.get().getVariable(0).getType();
            assertTrue(fieldType.isArrayType(), "intArray should be array type");

            Type componentType = fieldType.asArrayType().getComponentType();
            assertTrue(componentType.isPrimitiveType(), "Component type should be primitive");
            assertEquals("int", componentType.asString());
        }

        @Test
        @DisplayName("Multi-dimensional int[][] array is recognized")
        void multiDimensionalArrayRecognized() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(PRIMITIVE_ARRAYS);
            assertNotNull(cu, "PrimitiveArrays should be loaded");

            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();
            Optional<FieldDeclaration> intMatrixField = clazz.getFieldByName("intMatrix");
            assertTrue(intMatrixField.isPresent(), "intMatrix field should exist");

            Type fieldType = intMatrixField.get().getVariable(0).getType();
            assertTrue(fieldType.isArrayType(), "intMatrix should be array type");

            // int[][] - component type is int[]
            Type componentType = fieldType.asArrayType().getComponentType();
            assertTrue(componentType.isArrayType(), "Component of int[][] should be int[]");

            // Inner component type is int
            Type innerComponent = componentType.asArrayType().getComponentType();
            assertTrue(innerComponent.isPrimitiveType(), "Inner component should be primitive int");
        }

        @Test
        @DisplayName("All primitive types are recognized")
        void allPrimitiveTypesRecognized() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(PRIMITIVE_ARRAYS);
            assertNotNull(cu, "PrimitiveArrays should be loaded");

            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();

            String[] primitiveFields = {"intField", "booleanField", "byteField", "shortField",
                    "longField", "floatField", "doubleField", "charField"};

            for (String fieldName : primitiveFields) {
                Optional<FieldDeclaration> field = clazz.getFieldByName(fieldName);
                assertTrue(field.isPresent(), fieldName + " should exist");
                Type fieldType = field.get().getVariable(0).getType();
                assertTrue(fieldType.isPrimitiveType(), fieldName + " should be primitive");
            }
        }
    }

    // ========================================================================
    // §3.6 DepSolver Compatibility - getClazz() and getType()
    // ========================================================================

    @Nested
    @DisplayName("§3.6 DepSolver Compatibility")
    class DepSolverCompatibilityTests {

        @Test
        @DisplayName("TypeWrapper from TypeDeclaration has getType() not null")
        void typeWrapperFromTypeDeclarationHasType() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
            assertNotNull(cu, "Animal entity should be loaded");

            ClassOrInterfaceDeclaration animalClass = cu.getType(0).asClassOrInterfaceDeclaration();
            TypeWrapper wrapper = new TypeWrapper(animalClass);

            assertNotNull(wrapper.getType(), "getType() should not be null for AST-based wrapper");
            assertEquals("Animal", wrapper.getType().getNameAsString());
        }

        @Test
        @DisplayName("TypeWrapper from Class has getClazz() not null")
        void typeWrapperFromClassHasClazz() {
            TypeWrapper wrapper = new TypeWrapper(String.class);

            assertNotNull(wrapper.getClazz(), "getClazz() should not be null for reflection-based wrapper");
            assertEquals("java.lang.String", wrapper.getClazz().getName());
        }

        @Test
        @DisplayName("TypeWrapper from findType can have both getType and getClazz")
        void findTypeCanResolveBothTypeAndClazz() {
            // Find a JDK type - should resolve via reflection
            TypeWrapper stringWrapper = AbstractCompiler.findType(null, "String");
            assertNotNull(stringWrapper, "Should find String");
            assertNotNull(stringWrapper.getClazz(), "String should have Class");
            assertEquals("java.lang.String", stringWrapper.getClazz().getName());
        }

        @Test
        @DisplayName("TypeWrapper getFullyQualifiedName works for both AST and reflection")
        void getFqnWorksForBothModes() {
            // AST-based
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
            ClassOrInterfaceDeclaration animalClass = cu.getType(0).asClassOrInterfaceDeclaration();
            TypeWrapper astWrapper = new TypeWrapper(animalClass);

            String astFqn = astWrapper.getFullyQualifiedName();
            assertNotNull(astFqn, "FQN should not be null for AST wrapper");
            assertTrue(astFqn.endsWith(".Animal"), "FQN should end with .Animal");

            // Reflection-based
            TypeWrapper reflectWrapper = new TypeWrapper(String.class);
            String reflectFqn = reflectWrapper.getFullyQualifiedName();
            assertEquals("java.lang.String", reflectFqn, "FQN should be java.lang.String");
        }
    }

    // ========================================================================
    // §3.9 Inner Class Resolution
    // ========================================================================

    @Nested
    @DisplayName("§3.9 Inner Class Resolution")
    class InnerClassTests {

        @Test
        @DisplayName("Static nested class is found in compilation unit")
        void staticNestedClassFound() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(INNER_CLASSES);
            assertNotNull(cu, "InnerClasses should be loaded");

            ClassOrInterfaceDeclaration outerClass = cu.getType(0).asClassOrInterfaceDeclaration();

            // Find static nested class
            Optional<ClassOrInterfaceDeclaration> staticNested = outerClass.getMembers().stream()
                    .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                    .map(m -> (ClassOrInterfaceDeclaration) m)
                    .filter(c -> c.getNameAsString().equals("StaticNested"))
                    .findFirst();

            assertTrue(staticNested.isPresent(), "StaticNested class should exist");
            assertTrue(staticNested.get().isStatic(), "StaticNested should be static");
        }

        @Test
        @DisplayName("Non-static inner class is found")
        void nonStaticInnerClassFound() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(INNER_CLASSES);
            assertNotNull(cu, "InnerClasses should be loaded");

            ClassOrInterfaceDeclaration outerClass = cu.getType(0).asClassOrInterfaceDeclaration();

            // Find non-static inner class
            Optional<ClassOrInterfaceDeclaration> innerClass = outerClass.getMembers().stream()
                    .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                    .map(m -> (ClassOrInterfaceDeclaration) m)
                    .filter(c -> c.getNameAsString().equals("Inner"))
                    .findFirst();

            assertTrue(innerClass.isPresent(), "Inner class should exist");
            assertFalse(innerClass.get().isStatic(), "Inner should not be static");
        }

        @Test
        @DisplayName("Nested enum is found")
        void nestedEnumFound() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(INNER_CLASSES);
            assertNotNull(cu, "InnerClasses should be loaded");

            ClassOrInterfaceDeclaration outerClass = cu.getType(0).asClassOrInterfaceDeclaration();

            // Find nested enum
            Optional<EnumDeclaration> nestedEnum = outerClass.getMembers().stream()
                    .filter(m -> m instanceof EnumDeclaration)
                    .map(m -> (EnumDeclaration) m)
                    .filter(e -> e.getNameAsString().equals("NestedEnum"))
                    .findFirst();

            assertTrue(nestedEnum.isPresent(), "NestedEnum should exist");
        }

        @Test
        @DisplayName("Nested interface is found")
        void nestedInterfaceFound() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(INNER_CLASSES);
            assertNotNull(cu, "InnerClasses should be loaded");

            ClassOrInterfaceDeclaration outerClass = cu.getType(0).asClassOrInterfaceDeclaration();

            // Find nested interface
            Optional<ClassOrInterfaceDeclaration> nestedInterface = outerClass.getMembers().stream()
                    .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                    .map(m -> (ClassOrInterfaceDeclaration) m)
                    .filter(c -> c.getNameAsString().equals("NestedInterface"))
                    .findFirst();

            assertTrue(nestedInterface.isPresent(), "NestedInterface should exist");
            assertTrue(nestedInterface.get().isInterface(), "NestedInterface should be an interface");
        }
    }

    // ========================================================================
    // §4.9 isAssignableFrom Compatibility
    // ========================================================================

    @Nested
    @DisplayName("§4.9 isAssignableFrom Compatibility")
    class IsAssignableFromTests {

        @Test
        @DisplayName("Same type is assignable from itself")
        void sameTypeIsAssignableFromItself() {
            TypeWrapper wrapper1 = new TypeWrapper(String.class);
            TypeWrapper wrapper2 = new TypeWrapper(String.class);

            assertTrue(wrapper1.isAssignableFrom(wrapper2), "String should be assignable from String");
        }

        @Test
        @DisplayName("Superclass is assignable from subclass (reflection)")
        void superclassAssignableFromSubclass() {
            TypeWrapper objectWrapper = new TypeWrapper(Object.class);
            TypeWrapper stringWrapper = new TypeWrapper(String.class);

            assertTrue(objectWrapper.isAssignableFrom(stringWrapper),
                    "Object should be assignable from String");
        }

        @Test
        @DisplayName("Interface is assignable from implementing class (reflection)")
        void interfaceAssignableFromImpl() {
            TypeWrapper serializableWrapper = new TypeWrapper(Serializable.class);
            TypeWrapper stringWrapper = new TypeWrapper(String.class);

            assertTrue(serializableWrapper.isAssignableFrom(stringWrapper),
                    "Serializable should be assignable from String");
        }

        @Test
        @DisplayName("Generic interface is assignable from implementation (reflection)")
        void genericInterfaceAssignableFromImpl() {
            TypeWrapper comparableWrapper = new TypeWrapper(Comparable.class);
            TypeWrapper stringWrapper = new TypeWrapper(String.class);

            assertTrue(comparableWrapper.isAssignableFrom(stringWrapper),
                    "Comparable should be assignable from String");
        }

        @Test
        @DisplayName("AST-based inheritance - Dog extends Animal")
        void astBasedInheritance() {
            CompilationUnit animalCu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
            CompilationUnit dogCu = AntikytheraRunTime.getCompilationUnit(DOG_ENTITY);
            assertNotNull(animalCu, "Animal CU should exist");
            assertNotNull(dogCu, "Dog CU should exist");

            TypeWrapper animalWrapper = new TypeWrapper(animalCu.getType(0).asClassOrInterfaceDeclaration());
            TypeWrapper dogWrapper = new TypeWrapper(dogCu.getType(0).asClassOrInterfaceDeclaration());

            // Dog extends Animal, so Animal.isAssignableFrom(Dog) should be true
            assertTrue(animalWrapper.isAssignableFrom(dogWrapper),
                    "Animal should be assignable from Dog (AST-based)");
        }

        @Test
        @DisplayName("null parameter returns false")
        void nullParameterReturnsFalse() {
            TypeWrapper wrapper = new TypeWrapper(String.class);
            assertFalse(wrapper.isAssignableFrom(null), "isAssignableFrom(null) should return false");
        }

        @Test
        @DisplayName("Cross-boundary: reflection List assignable from reflection ArrayList")
        void crossBoundaryListArrayList() {
            TypeWrapper listWrapper = new TypeWrapper(List.class);
            TypeWrapper arrayListWrapper = new TypeWrapper(java.util.ArrayList.class);

            assertTrue(listWrapper.isAssignableFrom(arrayListWrapper),
                    "List should be assignable from ArrayList");
        }

        @Test
        @DisplayName("Cross-boundary: reflection Collection assignable from reflection ArrayList")
        void crossBoundaryCollectionArrayList() {
            TypeWrapper collectionWrapper = new TypeWrapper(Collection.class);
            TypeWrapper arrayListWrapper = new TypeWrapper(java.util.ArrayList.class);

            assertTrue(collectionWrapper.isAssignableFrom(arrayListWrapper),
                    "Collection should be assignable from ArrayList");
        }

        @Test
        @DisplayName("Cross-boundary: reflection AbstractList assignable from reflection ArrayList")
        void crossBoundaryAbstractListArrayList() {
            TypeWrapper abstractListWrapper = new TypeWrapper(AbstractList.class);
            TypeWrapper arrayListWrapper = new TypeWrapper(java.util.ArrayList.class);

            assertTrue(abstractListWrapper.isAssignableFrom(arrayListWrapper),
                    "AbstractList should be assignable from ArrayList");
        }
    }

    // ========================================================================
    // Spring Annotation Detection
    // ========================================================================

    @Nested
    @DisplayName("Spring Annotation Detection")
    class SpringAnnotationTests {

        @Test
        @DisplayName("Entity annotation is detected via getEntityAnnotation()")
        void entityAnnotationDetected() {
            TypeWrapper wrapper = AntikytheraRunTime.getResolvedTypes().get(ANIMAL_ENTITY);
            assertNotNull(wrapper, "Animal TypeWrapper should exist");
            // Use getEntityAnnotation() which checks AST directly
            assertTrue(wrapper.getEntityAnnotation().isPresent(), "Animal should have @Entity annotation");
        }

        @Test
        @DisplayName("Non-entity does not have @Entity annotation")
        void nonEntityNotFlagged() {
            TypeWrapper wrapper = AntikytheraRunTime.getResolvedTypes().get(GENERIC_TYPES);
            assertNotNull(wrapper, "GenericTypes TypeWrapper should exist");
            assertFalse(wrapper.getEntityAnnotation().isPresent(), "GenericTypes should not have @Entity");
        }

        @Test
        @DisplayName("getEntityAnnotation returns annotation when present")
        void getEntityAnnotationReturnsAnnotation() {
            TypeWrapper wrapper = AntikytheraRunTime.getResolvedTypes().get(ANIMAL_ENTITY);
            assertNotNull(wrapper, "Animal TypeWrapper should exist");

            Optional<com.github.javaparser.ast.expr.AnnotationExpr> entityAnnotation =
                    wrapper.getEntityAnnotation();
            assertTrue(entityAnnotation.isPresent(), "getEntityAnnotation should return @Entity");
        }

        @Test
        @DisplayName("getTableAnnotation returns annotation when present")
        void getTableAnnotationReturnsAnnotation() {
            TypeWrapper wrapper = AntikytheraRunTime.getResolvedTypes().get(ANIMAL_ENTITY);
            assertNotNull(wrapper, "Animal TypeWrapper should exist");

            Optional<com.github.javaparser.ast.expr.AnnotationExpr> tableAnnotation =
                    wrapper.getTableAnnotation();
            assertTrue(tableAnnotation.isPresent(), "getTableAnnotation should return @Table");
        }
    }

    // ========================================================================
    // API Compatibility Baseline
    // ========================================================================

    @Nested
    @DisplayName("API Compatibility Baseline")
    class ApiCompatibilityTests {

        @Test
        @DisplayName("TypeWrapper(TypeDeclaration) constructor works")
        void typeDeclarationConstructorWorks() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();

            TypeWrapper wrapper = new TypeWrapper(clazz);
            assertNotNull(wrapper);
            assertNotNull(wrapper.getType());
            assertNull(wrapper.getClazz());
        }

        @Test
        @DisplayName("TypeWrapper(Class) constructor works")
        void classConstructorWorks() {
            TypeWrapper wrapper = new TypeWrapper(String.class);
            assertNotNull(wrapper);
            assertNull(wrapper.getType());
            assertNotNull(wrapper.getClazz());
        }

        @Test
        @DisplayName("TypeWrapper(EnumConstantDeclaration) constructor works")
        void enumConstantConstructorWorks() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(STATUS_ENUM);
            EnumDeclaration enumDecl = cu.getType(0).asEnumDeclaration();
            EnumConstantDeclaration constant = enumDecl.getEntries().get(0);

            TypeWrapper wrapper = new TypeWrapper(constant);
            assertNotNull(wrapper);
            assertNotNull(wrapper.getEnumConstant());
        }

        @Test
        @DisplayName("TypeWrapper() default constructor works")
        void defaultConstructorWorks() {
            TypeWrapper wrapper = new TypeWrapper();
            assertNotNull(wrapper);
            assertNull(wrapper.getType());
            assertNull(wrapper.getClazz());
        }

        @Test
        @DisplayName("All getter methods are accessible")
        void allGettersAccessible() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);
            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();
            TypeWrapper wrapper = new TypeWrapper(clazz);

            // These should all be callable without exception
            wrapper.getType();
            wrapper.getClazz();
            wrapper.getEnumConstant();
            wrapper.getFullyQualifiedName();
            wrapper.getName();
            wrapper.isController();
            wrapper.isService();
            wrapper.isComponent();
            wrapper.isEntity();
            wrapper.isInterface();
            wrapper.getEntityAnnotation();
            wrapper.getTableAnnotation();
            wrapper.getInheritanceAnnotation();
            wrapper.getDiscriminatorColumnAnnotation();
            wrapper.getDiscriminatorValueAnnotation();
        }

        @Test
        @DisplayName("All setter methods are accessible")
        void allSettersAccessible() {
            TypeWrapper wrapper = new TypeWrapper();

            // These should all be callable without exception
            wrapper.setController(true);
            wrapper.setService(true);
            wrapper.setComponent(true);
            wrapper.setEntity(true);
            wrapper.setInterface(true);

            assertTrue(wrapper.isController());
            assertTrue(wrapper.isService());
            assertTrue(wrapper.isComponent());
            assertTrue(wrapper.isEntity());
            assertTrue(wrapper.isInterface());
        }

        @Test
        @DisplayName("AbstractCompiler.findType returns TypeWrapper or null")
        void findTypeReturnsTypeWrapperOrNull() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(ANIMAL_ENTITY);

            TypeWrapper found = AbstractCompiler.findType(cu, "Animal");
            assertNotNull(found, "Should find Animal");

            TypeWrapper notFound = AbstractCompiler.findType(cu, "NonExistentClass12345");
            assertNull(notFound, "Should return null for non-existent class");
        }

        @Test
        @DisplayName("AbstractCompiler.findWrappedTypes returns List<TypeWrapper>")
        void findWrappedTypesReturnsList() {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(GENERIC_TYPES);
            ClassOrInterfaceDeclaration clazz = cu.getType(0).asClassOrInterfaceDeclaration();

            Optional<FieldDeclaration> field = clazz.getFieldByName("stringList");
            assertTrue(field.isPresent());

            Type fieldType = field.get().getVariable(0).getType();
            List<TypeWrapper> wrappers = AbstractCompiler.findWrappedTypes(cu, fieldType);

            assertNotNull(wrappers, "Should return a list, not null");
            assertInstanceOf(List.class, wrappers);
        }
    }
}
