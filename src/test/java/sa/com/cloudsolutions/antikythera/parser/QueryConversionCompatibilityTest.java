package sa.com.cloudsolutions.antikythera.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMetadata;

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
     * Test that query conversion can be enabled and disabled
     */
    @Test
    void testQueryConversionToggle() throws IOException {
        // Test with conversion enabled
        enableQueryConversion();
        RepositoryParser parser1 = new RepositoryParser();
        assertTrue(parser1.isQueryConversionEnabled());
        
        // Test with conversion disabled
        disableQueryConversion();
        RepositoryParser parser2 = new RepositoryParser();
        assertFalse(parser2.isQueryConversionEnabled());
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
        enableQueryConversionWithoutFallback();
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
        enableQueryConversionWithoutCaching();
        RepositoryParser parser2 = new RepositoryParser();
        assertFalse(parser2.isCachingEnabled());
    }

    /**
     * Test cache key generation
     */
    @Test
    void testCacheKeyGeneration() throws IOException {
        enableQueryConversion();
        RepositoryParser parser = new RepositoryParser();
        
        String query1 = "SELECT u FROM User u WHERE u.name = :name";
        String query2 = "SELECT u FROM User u WHERE u.name = :name";
        String query3 = "SELECT u FROM User u WHERE u.email = :email";
        
        // Same query should generate same cache key
        String key1 = parser.generateCacheKey(query1, EntityMetadata.empty(), DatabaseDialect.POSTGRESQL);
        String key2 = parser.generateCacheKey(query2, EntityMetadata.empty(), DatabaseDialect.POSTGRESQL);
        assertEquals(key1, key2, "Same queries should generate the same cache key");
        
        // Different query should generate different cache key
        String key3 = parser.generateCacheKey(query3, EntityMetadata.empty(), DatabaseDialect.POSTGRESQL);
        assertNotEquals(key1, key3, "Different queries should generate different cache keys");
        
        // Test that cache keys are not null or empty
        assertNotNull(key1, "Cache key should not be null");
        assertFalse(key1.isEmpty(), "Cache key should not be empty");
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
    
    private void enableQueryConversionWithoutFallback() {
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
    
    private void enableQueryConversionWithoutCaching() {
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
    
    private void enableQueryConversionWithLogging() {
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", true);
        queryConversion.put("fallback_on_failure", false);
        queryConversion.put("log_conversion_failures", true);
        queryConversion.put("cache_results", false);
        
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
    }
    
    private void enableQueryConversionWithoutLogging() {
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
}
