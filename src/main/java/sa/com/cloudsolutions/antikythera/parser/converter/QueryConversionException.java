package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

/**
 * Exception thrown when JPA query conversion fails.
 * 
 * This exception provides detailed information about conversion failures,
 * including the original query and the specific reason for failure.
 * 
 */
public class QueryConversionException extends AntikytheraException {
    
    private final String originalQuery;
    private final ConversionFailureReason reason;
    
    /**
     * Creates a new QueryConversionException.
     * 
     * @param message Detailed error message
     * @param originalQuery The original JPA query that failed to convert
     * @param reason Categorized reason for the conversion failure
     */
    public QueryConversionException(String message, String originalQuery, ConversionFailureReason reason) {
        super(message);
        this.originalQuery = originalQuery;
        this.reason = reason;
    }
    
    /**
     * Creates a new QueryConversionException with a cause.
     * 
     * @param message Detailed error message
     * @param originalQuery The original JPA query that failed to convert
     * @param reason Categorized reason for the conversion failure
     * @param cause The underlying cause of the conversion failure
     */
    public QueryConversionException(String message, String originalQuery, ConversionFailureReason reason, Throwable cause) {
        super(message, cause);
        this.originalQuery = originalQuery;
        this.reason = reason;
    }
    
    /**
     * Gets the original JPA query that failed to convert.
     * 
     * @return The original query string
     */
    public String getOriginalQuery() {
        return originalQuery;
    }
    
    /**
     * Gets the categorized reason for the conversion failure.
     * 
     * @return The failure reason
     */
    public ConversionFailureReason getReason() {
        return reason;
    }
    
    @Override
    public String toString() {
        return "QueryConversionException{" +
                "message='" + getMessage() + '\'' +
                ", originalQuery='" + originalQuery + '\'' +
                ", reason=" + reason +
                '}';
    }
}
