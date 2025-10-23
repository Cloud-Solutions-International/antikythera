package sa.com.cloudsolutions.antikythera.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMetadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for RepositoryParser with JpaQueryConverter integration.
 * 
 * Tests the integration between RepositoryParser and the new query conversion functionality.
 */
class RepositoryParserIntegrationTest {
    
    @TempDir
    Path tempDir;
    
    private RepositoryParser repositoryParser;
    
    @BeforeEach
    void setUp() throws IOException {
        // Initialize Settings with the test configuration file
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        
        // Create a test configuration
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        database.put("run_queries", false);
        
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", true);
        queryConversion.put("fallback_on_failure", true);
        queryConversion.put("log_conversion_failures", true);
        queryConversion.put("cache_results", true);
        
        database.put("query_conversion", queryConversion);
        
        // Set the configuration
        Settings.setProperty("database", database);
        
        repositoryParser = new RepositoryParser();
    }
    
    @Test
    void testQueryConversionConfigurationLoading() {
        // Test that configuration methods work correctly
        assertTrue(repositoryParser.isQueryConversionEnabled());
        assertTrue(repositoryParser.isFallbackOnFailureEnabled());
        assertTrue(repositoryParser.isConversionFailureLoggingEnabled());
        assertTrue(repositoryParser.isCachingEnabled());
    }
    
    @Test
    void testCacheKeyGeneration() {
        // Test cache key generation with different inputs
        String query1 = "SELECT u FROM User u WHERE u.name = :name";
        String query2 = "SELECT u FROM User u WHERE u.name = :name";
        String query3 = "SELECT u FROM User u WHERE u.email = :email";
        
        // Same query should generate same cache key
        String key1 = repositoryParser.generateCacheKey(query1, EntityMetadata.empty(), DatabaseDialect.POSTGRESQL);
        String key2 = repositoryParser.generateCacheKey(query2, EntityMetadata.empty(), DatabaseDialect.POSTGRESQL);
        assertEquals(key1, key2, "Same queries should generate the same cache key");
        
        // Different query should generate different cache key
        String key3 = repositoryParser.generateCacheKey(query3, EntityMetadata.empty(), DatabaseDialect.POSTGRESQL);
        assertNotEquals(key1, key3, "Different queries should generate different cache keys");
        
        // Test that cache keys are not null or empty
        assertNotNull(key1, "Cache key should not be null");
        assertFalse(key1.isEmpty(), "Cache key should not be empty");
    }
    
    @Test
    void testConversionCacheOperations() {
        // Test cache operations
        String cacheKey = "test-key";
        ConversionResult result = ConversionResult.success("SELECT * FROM users");
        
        // Initially should be null
        assertNull(repositoryParser.getCachedConversionResult(cacheKey));
        
        // Cache the result
        repositoryParser.cacheConversionResult(cacheKey, result);
        
        // Should now return the cached result
        ConversionResult cached = repositoryParser.getCachedConversionResult(cacheKey);
        assertNotNull(cached);
        assertEquals(result.getNativeSql(), cached.getNativeSql());
        
        // Clear cache
        repositoryParser.clearConversionCache();
        assertNull(repositoryParser.getCachedConversionResult(cacheKey));
    }
    
    @Test
    void testConfigurationMethods() {
        // Test that configuration methods work correctly with disabled conversion
        Map<String, Object> database = new HashMap<>();
        database.put("url", "jdbc:postgresql://localhost:5432/test");
        Map<String, Object> queryConversion = new HashMap<>();
        queryConversion.put("enabled", false);
        queryConversion.put("fallback_on_failure", false);
        queryConversion.put("log_conversion_failures", false);
        queryConversion.put("cache_results", false);
        database.put("query_conversion", queryConversion);
        Settings.setProperty("database", database);
        
        // Create a new parser with disabled conversion
        try {
            RepositoryParser parser = new RepositoryParser();
            
            // Test configuration methods
            assertFalse(parser.isQueryConversionEnabled(), "Query conversion should be disabled");
            assertFalse(parser.isFallbackOnFailureEnabled(), "Fallback should be disabled");
            assertFalse(parser.isConversionFailureLoggingEnabled(), "Logging should be disabled");
            assertFalse(parser.isCachingEnabled(), "Caching should be disabled");
        } catch (IOException e) {
            fail("Failed to create RepositoryParser: " + e.getMessage());
        }
    }
}