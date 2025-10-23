package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates the result of a JPA query conversion operation.
 * 
 * This class contains the converted native SQL, parameter mappings,
 * and metadata about the conversion process.
 * 
 * Requirements addressed: 1.1, 2.1, 2.5
 */
public class ConversionResult {
    
    private final String nativeSql;
    private final List<ParameterMapping> parameterMappings;
    private final Set<String> referencedTables;
    private final boolean successful;
    private final String errorMessage;
    private final ConversionFailureReason failureReason;
    
    /**
     * Creates a successful conversion result.
     * 
     * @param nativeSql The converted native SQL query
     * @param parameterMappings List of parameter mappings from original to converted query
     * @param referencedTables Set of table names referenced in the converted query
     */
    public ConversionResult(String nativeSql, List<ParameterMapping> parameterMappings, Set<String> referencedTables) {
        this.nativeSql = nativeSql;
        this.parameterMappings = parameterMappings != null ? List.copyOf(parameterMappings) : Collections.emptyList();
        this.referencedTables = referencedTables != null ? Set.copyOf(referencedTables) : Collections.emptySet();
        this.successful = true;
        this.errorMessage = null;
        this.failureReason = null;
    }
    
    /**
     * Creates a failed conversion result.
     * 
     * @param errorMessage Description of the conversion failure
     * @param failureReason Categorized reason for the failure
     */
    public ConversionResult(String errorMessage, ConversionFailureReason failureReason) {
        this.nativeSql = null;
        this.parameterMappings = Collections.emptyList();
        this.referencedTables = Collections.emptySet();
        this.successful = false;
        this.errorMessage = errorMessage;
        this.failureReason = failureReason;
    }
    
    /**
     * Gets the converted native SQL query.
     * 
     * @return The native SQL string, or null if conversion failed
     */
    public String getNativeSql() {
        return nativeSql;
    }
    
    /**
     * Gets the parameter mappings from the original query to the converted query.
     * 
     * @return Immutable list of parameter mappings
     */
    public List<ParameterMapping> getParameterMappings() {
        return parameterMappings;
    }
    
    /**
     * Gets the set of table names referenced in the converted query.
     * 
     * @return Immutable set of table names
     */
    public Set<String> getReferencedTables() {
        return referencedTables;
    }
    
    /**
     * Indicates whether the conversion was successful.
     * 
     * @return true if conversion succeeded, false otherwise
     */
    public boolean isSuccessful() {
        return successful;
    }
    
    /**
     * Gets the error message if conversion failed.
     * 
     * @return Error message, or null if conversion succeeded
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    /**
     * Gets the categorized reason for conversion failure.
     * 
     * @return Failure reason, or null if conversion succeeded
     */
    public ConversionFailureReason getFailureReason() {
        return failureReason;
    }
    
    /**
     * Creates a successful conversion result with minimal information.
     * 
     * @param nativeSql The converted native SQL query
     * @return A successful ConversionResult
     */
    public static ConversionResult success(String nativeSql) {
        return new ConversionResult(nativeSql, null, null);
    }
    
    /**
     * Creates a failed conversion result.
     * 
     * @param errorMessage Description of the failure
     * @param failureReason Categorized reason for the failure
     * @return A failed ConversionResult
     */
    public static ConversionResult failure(String errorMessage, ConversionFailureReason failureReason) {
        return new ConversionResult(errorMessage, failureReason);
    }
    
    @Override
    public String toString() {
        if (successful) {
            return "ConversionResult{successful=true, nativeSql='" + nativeSql + "', parameterCount=" + parameterMappings.size() + "}";
        } else {
            return "ConversionResult{successful=false, errorMessage='" + errorMessage + "', failureReason=" + failureReason + "}";
        }
    }
}