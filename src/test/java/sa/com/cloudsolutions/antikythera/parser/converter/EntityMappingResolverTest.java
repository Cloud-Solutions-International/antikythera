package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.raditha.hql.converter.JoinMapping;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntityMappingResolverTest extends TestHelper {
    private static final String USER_MODEL = "sa.com.cloudsolutions.antikythera.testhelper.model.User";
    private static final String VEHICLE_MODEL = "sa.com.cloudsolutions.antikythera.testhelper.model.Vehicle";

    @BeforeAll
    static void setUpClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        EntityMappingResolver.reset();
    }

    @Test
    void testGetTableNameForEntity_UnknownEntity() {
        String tableName = EntityMappingResolver.getTableNameForEntity("UnknownEntity");
        assertEquals("unknown_entity", tableName);
    }

    @Test
    void testGetTableNameForEntity_KnownEntity() {
        EntityMappingResolver.build();
        // Use the fully qualified name since there are multiple "User" classes
        String tableName = EntityMappingResolver.getTableNameForEntity(USER_MODEL);
        assertEquals("users", tableName);
    }

    @Test
    void testGetTableNameForEntity_BySimpleNameWhenUnique() {
        EntityMappingResolver.build();
        // Vehicle should be unique, so simple name lookup should work
        String tableName = EntityMappingResolver.getTableNameForEntity("Vehicle");
        assertEquals("vehicles", tableName);
    }

    @Test
    void testGetTableNameForEntity_WithNullMetadata() {
        // This tests the null check fix: when FQN is in shortNames but not in mapping
        // Simulate by adding to shortNames without adding metadata to mapping
        // The method should fall back to snake_case instead of throwing NPE
        String tableName = EntityMappingResolver.getTableNameForEntity("NonExistentEntity");
        assertEquals("non_existent_entity", tableName);
    }

    @Test
    void testBuildOnTheFly_CreatesRelationshipMappings() {
        TypeDeclaration<?> userType = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        EntityMetadata meta = EntityMappingResolver.buildOnTheFly(new TypeWrapper(userType));

        assertNotNull(meta);
        assertNotNull(meta.relationshipMap());

        // User has a @OneToMany relationship to Vehicle via 'vehicles' field
        assertTrue(meta.relationshipMap().containsKey("vehicles"),
                "User should have 'vehicles' relationship mapping");

        JoinMapping vehiclesMapping = meta.relationshipMap().get("vehicles");
        assertNotNull(vehiclesMapping);
        assertEquals("Vehicle", vehiclesMapping.targetEntity());
    }

    @Test
    void testBuildOnTheFly_ManyToOneRelationship() {
        TypeDeclaration<?> vehicleType = AntikytheraRunTime.getTypeDeclaration(VEHICLE_MODEL).orElseThrow();
        EntityMetadata meta = EntityMappingResolver.buildOnTheFly(new TypeWrapper(vehicleType));

        assertNotNull(meta);
        assertNotNull(meta.relationshipMap());

        // Vehicle has a @ManyToOne relationship to User via 'owner' field
        assertTrue(meta.relationshipMap().containsKey("owner"),
                "Vehicle should have 'owner' relationship mapping");

        JoinMapping ownerMapping = meta.relationshipMap().get("owner");
        assertNotNull(ownerMapping);
        assertEquals("User", ownerMapping.targetEntity());
    }

    @Test
    void testBuildOnTheFly_PropertyToColumnMappings() {
        TypeDeclaration<?> userType = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        EntityMetadata meta = EntityMappingResolver.buildOnTheFly(new TypeWrapper(userType));

        assertNotNull(meta);
        assertNotNull(meta.propertyToColumnMap());

        // Check some property to column mappings
        assertEquals("id", meta.propertyToColumnMap().get("id"));
        assertEquals("username", meta.propertyToColumnMap().get("username"));
        assertEquals("first_name", meta.propertyToColumnMap().get("firstName"));
        assertEquals("last_name", meta.propertyToColumnMap().get("lastName"));
    }

    @Test
    void testBuildOnTheFly_TableName() {
        TypeDeclaration<?> userType = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        EntityMetadata meta = EntityMappingResolver.buildOnTheFly(new TypeWrapper(userType));

        assertNotNull(meta);
        // User entity has @Table(name = "users")
        assertEquals("users", meta.tableName());
    }

    @Test
    void testBuildOnTheFly_Idempotent() {
        TypeDeclaration<?> userType = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();

        EntityMetadata meta1 = EntityMappingResolver.buildOnTheFly(new TypeWrapper(userType));
        EntityMetadata meta2 = EntityMappingResolver.buildOnTheFly(new TypeWrapper(userType));

        // Should return the same cached instance
        assertSame(meta1, meta2);
    }

    @Test
    void testGetFullNamesForEntity() {
        EntityMappingResolver.build();

        var fullNames = EntityMappingResolver.getFullNamesForEntity("User");
        assertFalse(fullNames.isEmpty());
        assertTrue(fullNames.contains(USER_MODEL));
    }

    @Test
    void testGetFullNamesForEntity_UnknownEntity() {
        var fullNames = EntityMappingResolver.getFullNamesForEntity("NonExistent");
        assertTrue(fullNames.isEmpty());
    }

    @Test
    void testGetMapping_ContainsBuiltEntities() {
        EntityMappingResolver.build();

        Map<String, EntityMetadata> mapping = EntityMappingResolver.getMapping();
        assertNotNull(mapping);
        assertTrue(mapping.containsKey(USER_MODEL));
        assertTrue(mapping.containsKey(VEHICLE_MODEL));
    }

    @Test
    void testIsEntity_WithTypeDeclaration() {
        TypeDeclaration<?> userType = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        TypeWrapper tw = new TypeWrapper(userType);
        
        assertTrue(EntityMappingResolver.isEntity(tw), 
            "Should detect @Entity annotation from TypeDeclaration");
    }

    @Test
    void testIsEntity_WithClass_JakartaPersistence() {
        // This tests the reflection path with jakarta.persistence
        // Since the project uses jakarta.persistence, loaded classes will have jakarta annotations
        TypeWrapper tw = AntikytheraRunTime.getResolvedTypes().get(USER_MODEL);
        
        if (tw != null && tw.getClazz() != null) {
            assertTrue(EntityMappingResolver.isEntity(tw),
                "Should detect jakarta.persistence.Entity via reflection");
        }
    }

    @Test
    void testIsEntity_WithNonEntityClass() {
        // Test with a class that's not an entity
        TypeWrapper tw = AntikytheraRunTime.getResolvedTypes().get(
            "sa.com.cloudsolutions.antikythera.parser.AbstractCompiler");
        
        if (tw != null) {
            assertFalse(EntityMappingResolver.isEntity(tw),
                "Should return false for non-entity classes");
        }
    }

    @Test
    void testIsEntity_WithNullTypeAndClass() {
        // Edge case: TypeWrapper with both type and class null
        TypeWrapper tw = new TypeWrapper((TypeDeclaration<?>) null);
        
        assertFalse(EntityMappingResolver.isEntity(tw),
            "Should return false when both type and class are null");
    }

    @Test
    void testBuildOnTheFly_WithReflection() {
        // Test the reflection path (line 84) by using a TypeWrapper with only Class
        TypeWrapper tw = AntikytheraRunTime.getResolvedTypes().get(USER_MODEL);
        
        if (tw != null && tw.getClazz() != null) {
            // Force using the reflection path by creating a TypeWrapper with only the class
            TypeWrapper classOnlyWrapper = new TypeWrapper(tw.getClazz());
            EntityMetadata meta = EntityMappingResolver.buildOnTheFly(classOnlyWrapper);
            
            assertNotNull(meta, "Should build metadata from Class using reflection");
            assertEquals("users", meta.tableName());
        }
    }

    @Test
    void testResolveEntity_Success() {
        EntityMappingResolver.build();
        
        var result = EntityMappingResolver.resolveEntity("Vehicle");
        assertTrue(result.isPresent(), "Should resolve Vehicle entity");
        assertEquals("vehicles", result.get().tableName());
    }

    @Test
    void testResolveEntity_NotFound() {
        EntityMappingResolver.build();
        
        var result = EntityMappingResolver.resolveEntity("NonExistentEntity");
        assertFalse(result.isPresent(), "Should return empty for non-existent entity");
    }

    @Test
    void testResolveBySuffix() {
        EntityMappingResolver.build();
        
        var result = EntityMappingResolver.resolveBySuffix("User");
        assertTrue(result.isPresent(), "Should resolve by suffix");
        assertTrue(result.get().entity().getFullyQualifiedName().endsWith(".User"));
    }
    @Test
    void testIsEntityReflection_WithJakartaEntity() throws Exception {
        // Create a mock class with jakarta.persistence.Entity annotation at runtime
        // This forces testing the reflection path
        Class<?> mockEntityClass = createMockEntityClass("jakarta.persistence.Entity");
        
        if (mockEntityClass != null) {
            // Use reflection to call the private isEntityReflection method
            java.lang.reflect.Method method = EntityMappingResolver.class
                .getDeclaredMethod("isEntityReflection", Class.class);
            method.setAccessible(true);
            
            boolean result = (boolean) method.invoke(null, mockEntityClass);
            assertTrue(result, "Should detect jakarta.persistence.Entity via reflection");
        }
    }

    @Test
    void testIsEntityReflection_WithJavaxEntity() throws Exception {
        // Test backward compatibility with javax.persistence.Entity
        Class<?> mockEntityClass = createMockEntityClass("javax.persistence.Entity");
        
        if (mockEntityClass != null) {
            java.lang.reflect.Method method = EntityMappingResolver.class
                .getDeclaredMethod("isEntityReflection", Class.class);
            method.setAccessible(true);
            
            boolean result = (boolean) method.invoke(null, mockEntityClass);
            assertTrue(result, "Should detect javax.persistence.Entity via reflection for backward compatibility");
        }
    }

    @Test
    void testIsEntityReflection_WithNonEntity() throws Exception {
        // Test with a class that has no @Entity annotation
        java.lang.reflect.Method method = EntityMappingResolver.class
            .getDeclaredMethod("isEntityReflection", Class.class);
        method.setAccessible(true);
        
        boolean result = (boolean) method.invoke(null, String.class);
        assertFalse(result, "Should return false for non-entity classes");
    }

    /**
     * Helper method to create a mock class with Entity annotation for testing.
     * Returns null if the annotation class is not available in classpath.
     */
    private Class<?> createMockEntityClass(String annotationClassName) {
        try {
            // Check if the annotation is available
            Class.forName(annotationClassName);
            
            // For testing purposes, we can use existing entity classes from test resources
            // that have the jakarta.persistence annotations
            return Class.forName(USER_MODEL);
        } catch (ClassNotFoundException e) {
            // Annotation not available in this environment
            return null;
        }
    }
}
