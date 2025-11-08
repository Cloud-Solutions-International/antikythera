package sa.com.cloudsolutions.antikythera.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic compatibility tests that verify core functionality works
 * without breaking existing behavior.
 */
class BasicCompatibilityTest {
    
    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }
    
    /**
     * Test that existing utility methods still work correctly
     */
    @Test
    void testExistingUtilityMethods() {
        // Test camelToSnake conversion
        assertEquals("user_name", RepositoryParser.camelToSnake("userName"));
        assertEquals("first_name", RepositoryParser.camelToSnake("firstName"));
        assertEquals("id", RepositoryParser.camelToSnake("id"));
        assertEquals("user_id", RepositoryParser.camelToSnake("userID"));
        assertEquals("", RepositoryParser.camelToSnake(""));
        
        // Test SQL beautification
        String result1 = RepositoryParser.beautify("SELECT * FROM users WHERE id = ?1");
        assertEquals("SELECT * FROM users WHERE id = ?", result1);
        
        String result2 = RepositoryParser.beautify("SELECT * FROM users WHERE id = ?1 AND name = ?2");
        assertEquals("SELECT * FROM users WHERE id = ? AND name = ?", result2);
        
        // Test placeholder counting
        assertEquals(0, RepositoryParser.countPlaceholders("SELECT * FROM users"));
        assertEquals(1, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ?"));
        assertEquals(2, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ? AND name = ?"));
        assertEquals(3, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ? AND name = ? AND age > ?"));
        
        // Test trueFalseCheck
        String input = "SELECT * FROM users WHERE active = true AND deleted = false";
        String result = RepositoryParser.trueFalseCheck(input);
        assertNotNull(result);
        assertTrue(result.contains("SELECT * FROM users WHERE active ="));
        assertTrue(result.contains("AND deleted ="));
    }
    
    /**
     * Test that RepositoryParser can be instantiated with different configurations
     */
    @Test
    void testRepositoryParserInstantiation() throws IOException {
        // Test with query conversion disabled
        disableQueryConversion();
        RepositoryParser parser1 = new RepositoryParser();
        assertNotNull(parser1);
        assertFalse(parser1.isQueryConversionEnabled());
        
        // Test with query conversion enabled
        enableQueryConversion();
        RepositoryParser parser2 = new RepositoryParser();
        assertNotNull(parser2);
        assertTrue(parser2.isQueryConversionEnabled());
    }
    
    /**
     * Test that configuration methods work correctly
     */
    @Test
    void testConfigurationMethods() throws IOException {
        // Test with all features enabled
        enableAllFeatures();
        RepositoryParser parser = new RepositoryParser();
        
        assertTrue(parser.isQueryConversionEnabled());
        assertTrue(parser.isFallbackOnFailureEnabled());
        assertTrue(parser.isCachingEnabled());
        
        // Test with all features disabled
        disableAllFeatures();
        RepositoryParser parser2 = new RepositoryParser();
        
        assertFalse(parser2.isQueryConversionEnabled());
        assertFalse(parser2.isFallbackOnFailureEnabled());
        assertFalse(parser2.isCachingEnabled());
    }
    
    /**
     * Test that the system doesn't crash with various configurations
     */
    @Test
    void testSystemStability() throws IOException {
        // Test multiple configuration changes
        for (int i = 0; i < 5; i++) {
            if (i % 2 == 0) {
                enableQueryConversion();
            } else {
                disableQueryConversion();
            }
            
            // Should not throw exception
            assertDoesNotThrow(() -> {
                RepositoryParser parser = new RepositoryParser();
                assertNotNull(parser);
            });
        }
    }
    
    // Helper methods for configuration
    
    private void enableQueryConversion() {
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", true);
        queryConversion.put("fallback_on_failure", false);
        queryConversion.put("log_conversion_failures", false);
        queryConversion.put("cache_results", false);
        
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
    }
    
    private void disableQueryConversion() {
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", false);
        queryConversion.put("fallback_on_failure", false);
        queryConversion.put("log_conversion_failures", false);
        queryConversion.put("cache_results", false);
        
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
    }
    
    private void enableAllFeatures() {
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", true);
        queryConversion.put("fallback_on_failure", true);
        queryConversion.put("log_conversion_failures", true);
        queryConversion.put("cache_results", true);
        
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
    }
    
    private void disableAllFeatures() {
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", false);
        queryConversion.put("fallback_on_failure", false);
        queryConversion.put("log_conversion_failures", false);
        queryConversion.put("cache_results", false);
        
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
    }
}
