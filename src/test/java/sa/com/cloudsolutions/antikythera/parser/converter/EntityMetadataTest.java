package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic test for EntityMetadata functionality.
 * 
 * This test validates that the EntityMetadata class works correctly
 * for storing and retrieving entity mapping information.
 */
class EntityMetadataTest {

    @Test
    void testEmptyEntityMetadata() {
        EntityMetadata metadata = EntityMetadata.empty();
        
        assertNotNull(metadata, "Empty metadata should not be null");
        assertTrue(metadata.entityToTableMappings().isEmpty(),
                  "Empty metadata should have no entity mappings");
        assertTrue(metadata.propertyToColumnMappings().isEmpty(),
                  "Empty metadata should have no property mappings");
        assertTrue(metadata.relationshipMappings().isEmpty(),
                  "Empty metadata should have no relationship mappings");
    }

    @Test
    void testEntityMetadataWithMappings() {
        // Create test mappings
        Map<String, TableMapping> entityMappings = new HashMap<>();
        Map<String, String> propertyMappings = new HashMap<>();
        Map<String, JoinMapping> relationshipMappings = new HashMap<>();
        
        // Create a simple table mapping
        TableMapping userTable = new TableMapping("User", "users", null, null, null, null, null, null);
        entityMappings.put("User", userTable);
        
        EntityMetadata metadata = new EntityMetadata(entityMappings, propertyMappings, relationshipMappings);
        
        assertNotNull(metadata, "Metadata should not be null");
        assertEquals(1, metadata.entityToTableMappings().size(),
                    "Should have one entity mapping");
        assertTrue(metadata.hasEntityMetadata("User"), 
                  "Should have metadata for User entity");
        assertFalse(metadata.hasEntityMetadata("NonExistent"), 
                   "Should not have metadata for non-existent entity");
        
        TableMapping retrieved = metadata.getTableMapping("User");
        assertNotNull(retrieved, "Should retrieve table mapping for User");
        assertEquals("users", retrieved.tableName(), "Table name should match");
    }

    @Test
    void testGetAllTableMappings() {
        Map<String, TableMapping> entityMappings = new HashMap<>();
        entityMappings.put("User", new TableMapping("User", "users", null, null, null, null, null, null));
        entityMappings.put("Product", new TableMapping("Product", "products", null, null, null, null, null, null));
        
        EntityMetadata metadata = new EntityMetadata(entityMappings, null, null);
        
        assertEquals(2, metadata.getAllTableMappings().size(), 
                    "Should return all table mappings");
    }

    @Test
    void testNullSafety() {
        // Test that null inputs are handled safely
        EntityMetadata metadata = new EntityMetadata(null, null, null);
        
        assertNotNull(metadata.entityToTableMappings(),
                     "Should return empty map for null input");
        assertNotNull(metadata.propertyToColumnMappings(),
                     "Should return empty map for null input");
        assertNotNull(metadata.relationshipMappings(),
                     "Should return empty map for null input");
        
        assertNull(metadata.getTableMapping("NonExistent"), 
                  "Should return null for non-existent mapping");
        assertNull(metadata.getColumnMapping("NonExistent"), 
                  "Should return null for non-existent mapping");
        assertNull(metadata.getJoinMapping("NonExistent"), 
                  "Should return null for non-existent mapping");
    }
}
