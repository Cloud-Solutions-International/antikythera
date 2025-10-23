package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Context object that holds information needed during SQL conversion.
 * <p>
 * This class encapsulates the entity metadata and database dialect information
 * required during the HQL to SQL conversion process. It provides a clean way
 * to pass conversion context between different components of the converter.
 */
public record SqlConversionContext(EntityMetadata entityMetadata, DatabaseDialect dialect) {

    /**
     * Constructs a new SqlConversionContext.
     *
     * @param entityMetadata The entity metadata containing table and column mappings
     * @param dialect        The target database dialect for SQL generation
     */
    public SqlConversionContext {
        if (entityMetadata == null) {
            throw new IllegalArgumentException("Entity metadata cannot be null");
        }
        if (dialect == null) {
            throw new IllegalArgumentException("Database dialect cannot be null");
        }

    }

    @Override
    public String toString() {
        return "SqlConversionContext{" +
                "dialect=" + dialect +
                ", entityCount=" + (entityMetadata != null ? entityMetadata.getAllTableMappings().size() : 0) +
                '}';
    }
}
