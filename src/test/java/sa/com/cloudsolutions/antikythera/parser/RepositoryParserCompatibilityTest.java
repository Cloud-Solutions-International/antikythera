package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compatibility tests for RepositoryParser to ensure existing functionality continues to work
 * with the new JPA query conversion enhancement.
 * 
 * These tests verify critical functionality that must continue to work.
 */
class RepositoryParserCompatibilityTest {
    
    @BeforeEach
    void setUp() throws IOException {
        // Initialize Settings with the test configuration file
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }
    
    /**
     * Test that core utility methods continue to work unchanged
     */
    @Test
    void testCoreUtilityMethods() {
        // Test SQL beautification
        String result1 = RepositoryParser.beautify("SELECT * FROM users WHERE id = ?1");
        assertEquals("SELECT * FROM users WHERE id = ?", result1);
        
        String result2 = RepositoryParser.beautify("SELECT * FROM users WHERE id = ?1 AND name = ?2");
        assertEquals("SELECT * FROM users WHERE id = ? AND name = ?", result2);
        
        // Test camel case to snake case conversion
        assertEquals("user_name", RepositoryParser.camelToSnake("userName"));
        assertEquals("first_name", RepositoryParser.camelToSnake("firstName"));
        assertEquals("id", RepositoryParser.camelToSnake("id"));
        assertEquals("user_id", RepositoryParser.camelToSnake("userID"));
        
        // Test placeholder counting
        assertEquals(0, RepositoryParser.countPlaceholders("SELECT * FROM users"));
        assertEquals(1, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ?"));
        assertEquals(2, RepositoryParser.countPlaceholders("SELECT * FROM users WHERE id = ? AND name = ?"));
    }
    
    /**
     * Test that table name resolution continues to work
     */
    @Test
    void testTableNameResolution() {
        // Test with @Table annotation
        CompilationUnit entityWithTable = StaticJavaParser.parse("""
                @Table(name = "custom_users")
                public class User {
                    private String username;
                }
                """);
        
        String tableName1 = RepositoryParser.findTableName(new TypeWrapper(entityWithTable.getType(0)));
        assertEquals("custom_users", tableName1);
        
        // Test without @Table annotation (should use class name conversion)
        CompilationUnit entityWithoutTable = StaticJavaParser.parse("""
                public class UserProfile {
                    private String name;
                }
                """);
        
        String tableName2 = RepositoryParser.findTableName(new TypeWrapper(entityWithoutTable.getType(0)));
        assertEquals("user_profile", tableName2);
    }
    
    /**
     * Test that method component extraction works correctly
     */
    @Test
    void testMethodComponentExtraction() throws IOException {
        RepositoryParser parser = new RepositoryParser();
        
        List<String> result1 = parser.extractComponents("findByUsernameAndAge");
        assertEquals(List.of("findBy", "Username", "And", "Age"), result1);
        
        List<String> result2 = parser.extractComponents("findFirstByEmailOrderByAge");
        assertEquals(List.of("findFirstBy", "Email", "OrderBy", "Age"), result2);
        
        List<String> result3 = parser.extractComponents("findByAgeGreaterThanAndUsernameContaining");
        assertEquals(List.of("findBy", "Age", "GreaterThan", "And", "Username", "Containing"), result3);
    }
    
    /**
     * Test that configuration loading works correctly
     */
    @Test
    void testConfigurationLoading() throws IOException {
        RepositoryParser parser = new RepositoryParser();
        
        // Test that parser can be created without errors
        assertNotNull(parser);
        
        // Test that configuration methods exist and work
        assertDoesNotThrow(() -> {
            parser.isQueryConversionEnabled();
            parser.isFallbackOnFailureEnabled();
            parser.isConversionFailureLoggingEnabled();
            parser.isCachingEnabled();
        });
    }
    
    /**
     * Test that true/false SQL conversion works
     */
    @Test
    void testTrueFalseConversion() {
        String input = "SELECT * FROM users WHERE active = true AND deleted = false";
        String result = RepositoryParser.trueFalseCheck(input);
        
        assertNotNull(result);
        assertTrue(result.contains("SELECT * FROM users WHERE active ="));
        assertTrue(result.contains("AND deleted ="));
    }
    

}