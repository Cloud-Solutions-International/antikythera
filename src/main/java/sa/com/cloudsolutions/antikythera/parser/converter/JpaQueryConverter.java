package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Interface for converting JPA/HQL queries to native SQL queries.
 * 
 * This interface provides the contract for converting JPA queries (HQL/JPQL) 
 * to native SQL that can be executed directly against the database.
 * 
 * Requirements addressed: 1.1, 2.1, 2.5
 */
public interface JpaQueryConverter {
    
    /**
     * Converts a JPA/HQL query to native SQL.
     * 
     * @param jpaQuery The original JPA/HQL query string to convert
     * @param entityMetadata Metadata about entities involved in the query
     * @param dialect The target database dialect for SQL generation
     * @return ConversionResult containing the native SQL and conversion metadata
     * @throws QueryConversionException if the conversion fails
     */
    ConversionResult convertToNativeSQL(String jpaQuery, EntityMetadata entityMetadata, DatabaseDialect dialect);
    
    /**
     * Validates if a query can be converted by this converter.
     * 
     * @param jpaQuery The JPA/HQL query to validate
     * @return true if the query can be converted, false otherwise
     */
    boolean canConvert(String jpaQuery);
    
    /**
     * Checks if the converter supports the specified database dialect.
     * 
     * @param dialect The database dialect to check
     * @return true if the dialect is supported, false otherwise
     */
    boolean supportsDialect(DatabaseDialect dialect);
}