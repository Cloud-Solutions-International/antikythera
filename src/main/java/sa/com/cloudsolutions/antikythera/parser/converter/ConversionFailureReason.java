package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Enumeration of possible reasons for JPA query conversion failures.
 * 
 * This enum categorizes different types of conversion failures to help
 * with error handling and troubleshooting.
 * 
 * Requirements addressed: 2.5
 */
public enum ConversionFailureReason {
    
    /**
     * The query contains HQL/JPQL constructs that are not supported by the converter.
     */
    UNSUPPORTED_CONSTRUCT("Query contains unsupported HQL/JPQL constructs"),
    
    /**
     * Required entity metadata is missing or incomplete.
     */
    MISSING_ENTITY_METADATA("Required entity metadata is missing or incomplete"),
    
    /**
     * The target database dialect is not supported or incompatible.
     */
    DIALECT_INCOMPATIBILITY("Target database dialect is not supported or incompatible"),
    
    /**
     * The query parser encountered a syntax error or parsing failure.
     */
    PARSER_ERROR("Query parser encountered a syntax error or parsing failure"),
    
    /**
     * An unexpected internal error occurred during conversion.
     */
    INTERNAL_ERROR("An unexpected internal error occurred during conversion"),
    
    /**
     * The query is malformed or contains invalid syntax.
     */
    INVALID_QUERY_SYNTAX("The query is malformed or contains invalid syntax");
    
    private final String description;
    
    ConversionFailureReason(String description) {
        this.description = description;
    }
    
    /**
     * Gets a human-readable description of the failure reason.
     * 
     * @return Description of the failure reason
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return name() + ": " + description;
    }
}