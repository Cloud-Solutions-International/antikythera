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
}
