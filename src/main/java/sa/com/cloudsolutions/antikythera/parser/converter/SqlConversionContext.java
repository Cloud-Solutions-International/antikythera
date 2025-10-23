package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Context object that holds information needed during SQL conversion.
 * 
 * This class encapsulates the entity metadata and database dialect information
 * required during the HQL to SQL conversion process. It provides a clean way
 * to pass conversion context between different components of the converter.
 * 
 * Requirements addressed: 1.1, 1.3
 */
public class SqlConversionContext {
    
    private final EntityMetadata entityMetadata;
    private final DatabaseDialect dialect;
    
    /**
     * Constructs a new SqlConversionContext.
     * 
     * @param entityMetadata The entity metadata containing table and column mappings
     * @param dialect The target database dialect for SQL generation
     */
    public SqlConversionContext(EntityMetadata entityMetadata, DatabaseDialect dialect) {
        if (entityMetadata == null) {
            throw new IllegalArgumentException("Entity metadata cannot be null");
        }
        if (dialect == null) {
            throw new IllegalArgumentException("Database dialect cannot be null");
        }
        
        this.entityMetadata = entityMetadata;
        this.dialect = dialect;
    }
    
    /**
     * Gets the entity metadata.
     * 
     * @return The entity metadata containing table and column mappings
     */
    public EntityMetadata getEntityMetadata() {
        return entityMetadata;
    }
    
    /**
     * Gets the target database dialect.
     * 
     * @return The database dialect for SQL generation
     */
    public DatabaseDialect getDialect() {
        return dialect;
    }
    
    /**
     * Checks if the context is valid for conversion.
     * 
     * @return true if the context has all required information, false otherwise
     */
    public boolean isValid() {
        return entityMetadata != null && dialect != null;
    }
    
    @Override
    public String toString() {
        return "SqlConversionContext{" +
                "dialect=" + dialect +
                ", entityCount=" + (entityMetadata != null ? entityMetadata.getAllTableMappings().size() : 0) +
                '}';
    }
}
