package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic test for ParameterMapping functionality.
 * 
 * This test validates that the ParameterMapping class works correctly
 * for storing parameter conversion information.
 */
class ParameterMappingTest {

    @Test
    void testParameterMappingCreation() {
        ParameterMapping mapping = new ParameterMapping("username", 1, String.class, "username");
        
        assertNotNull(mapping, "Parameter mapping should not be null");
        assertEquals("username", mapping.originalName(), "Original name should match");
        assertEquals(1, mapping.position(), "Position should match");
        assertEquals(String.class, mapping.type(), "Type should match");
        assertEquals("username", mapping.columnName(), "Column name should match");
    }

    @Test
    void testParameterMappingEquality() {
        ParameterMapping mapping1 = new ParameterMapping("username", 1, String.class, "username");
        ParameterMapping mapping2 = new ParameterMapping("username", 1, String.class, "username");
        ParameterMapping mapping3 = new ParameterMapping("email", 2, String.class, "email");
        
        assertEquals(mapping1, mapping2, "Identical mappings should be equal");
        assertNotEquals(mapping1, mapping3, "Different mappings should not be equal");
        assertEquals(mapping1.hashCode(), mapping2.hashCode(), "Equal mappings should have same hash code");
    }

    @Test
    void testParameterMappingToString() {
        ParameterMapping mapping = new ParameterMapping("userId", 2, Long.class, "user_id");
        String toString = mapping.toString();
        
        assertNotNull(toString, "toString should not be null");
        assertTrue(toString.contains("userId"), "toString should contain original name");
        assertTrue(toString.contains("2"), "toString should contain position");
        assertTrue(toString.contains("Long"), "toString should contain type");
        assertTrue(toString.contains("user_id"), "toString should contain column name");
    }

    @Test
    void testParameterMappingWithNullValues() {
        ParameterMapping mapping = new ParameterMapping(null, 0, null, null);
        
        assertNull(mapping.originalName(), "Original name can be null");
        assertEquals(0, mapping.position(), "Position should be 0");
        assertNull(mapping.type(), "Type can be null");
        assertNull(mapping.columnName(), "Column name can be null");
        
        // Test toString with null values
        String toString = mapping.toString();
        assertNotNull(toString, "toString should handle null values");
        assertTrue(toString.contains("null"), "toString should show null values");
    }
}
