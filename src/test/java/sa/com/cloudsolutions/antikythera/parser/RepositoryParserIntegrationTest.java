package sa.com.cloudsolutions.antikythera.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMetadata;

import java.io.File;
import java.io.IOException;
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
}
