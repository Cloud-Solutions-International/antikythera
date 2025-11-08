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
 * Tests to verify that the new query conversion functionality works correctly
 * and doesn't break existing behavior.
 */
class QueryConversionCompatibilityTest {
    
    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }

    /**
     * Test that fallback configuration works
     */
    @Test
    void testFallbackConfiguration() throws IOException {
        // Test with fallback enabled
        enableQueryConversionWithFallback();
        RepositoryParser parser1 = new RepositoryParser();
        assertTrue(parser1.isFallbackOnFailureEnabled());
        
        // Test with fallback disabled
        enableQueryConversion();
        RepositoryParser parser2 = new RepositoryParser();
        assertFalse(parser2.isFallbackOnFailureEnabled());
    }
    
    /**
     * Test that caching configuration works
     */
    @Test
    void testCachingConfiguration() throws IOException {
        // Test with caching enabled
        enableQueryConversionWithCaching();
        RepositoryParser parser1 = new RepositoryParser();
        assertTrue(parser1.isCachingEnabled());
        
        // Test with caching disabled
        enableQueryConversion();
        RepositoryParser parser2 = new RepositoryParser();
        assertFalse(parser2.isCachingEnabled());
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
    
    private void enableQueryConversionWithFallback() {
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", true);
        queryConversion.put("fallback_on_failure", true);
        queryConversion.put("log_conversion_failures", false);
        queryConversion.put("cache_results", false);
        
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
    }

    
    private void enableQueryConversionWithCaching() {
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", true);
        queryConversion.put("fallback_on_failure", false);
        queryConversion.put("log_conversion_failures", false);
        queryConversion.put("cache_results", true);
        
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
    }
}
