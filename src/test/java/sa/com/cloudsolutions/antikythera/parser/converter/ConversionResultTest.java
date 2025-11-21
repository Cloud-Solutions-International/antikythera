package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for ConversionResult to verify core functionality.
 * 
 * Tests the basic functionality of the ConversionResult class which is
 * part of the core infrastructure for JPA query conversion.
 */
class ConversionResultTest {

    @Test
    void testSuccessfulConversionResult() {
        // Arrange
        String nativeSql = "SELECT * FROM users WHERE username = ?";
        List<ParameterMapping> parameterMappings = Arrays.asList(
            new ParameterMapping("username", 1, String.class, "username")
        );
        Set<String> referencedTables = new HashSet<>(Arrays.asList("users"));
        
        // Act
        ConversionResult result = new ConversionResult(nativeSql, parameterMappings, referencedTables);
        
        // Assert
        assertTrue(result.isSuccessful());
        assertEquals(nativeSql, result.getNativeSql());
        assertEquals(1, result.getParameterMappings().size());
        assertEquals(1, result.getReferencedTables().size());
        assertTrue(result.getReferencedTables().contains("users"));
        assertNull(result.getErrorMessage());
        assertNull(result.getFailureReason());
    }
}
