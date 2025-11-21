package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for QueryConversionException to verify exception handling.
 * 
 * Tests the exception hierarchy and error information handling which is
 * part of the core infrastructure for JPA query conversion.
 */
class QueryConversionExceptionTest {

    @Test
    void testQueryConversionExceptionWithMessage() {
        // Arrange
        String message = "Failed to convert query";
        String originalQuery = "SELECT u FROM User u WHERE u.name = :name";
        ConversionFailureReason reason = ConversionFailureReason.UNSUPPORTED_CONSTRUCT;
        
        // Act
        QueryConversionException exception = new QueryConversionException(message, originalQuery, reason);
        
        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(originalQuery, exception.getOriginalQuery());
        assertEquals(reason, exception.getReason());
        assertNull(exception.getCause());
    }

    @Test
    void testQueryConversionExceptionWithCause() {
        // Arrange
        String message = "Parser failed";
        String originalQuery = "SELECT u FROM User u";
        ConversionFailureReason reason = ConversionFailureReason.PARSER_ERROR;
        RuntimeException cause = new RuntimeException("Underlying parser error");
        
        // Act
        QueryConversionException exception = new QueryConversionException(message, originalQuery, reason, cause);
        
        // Assert
        assertEquals(message, exception.getMessage());
        assertEquals(originalQuery, exception.getOriginalQuery());
        assertEquals(reason, exception.getReason());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testToStringMethod() {
        // Arrange
        String message = "Test error";
        String originalQuery = "SELECT u FROM User u";
        ConversionFailureReason reason = ConversionFailureReason.MISSING_ENTITY_METADATA;
        
        // Act
        QueryConversionException exception = new QueryConversionException(message, originalQuery, reason);
        String toString = exception.toString();
        
        // Assert
        assertTrue(toString.contains("QueryConversionException"));
        assertTrue(toString.contains(message));
        assertTrue(toString.contains(originalQuery));
        assertTrue(toString.contains(reason.toString()));
    }
}
