package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Map;
import java.util.HashMap;

/**
 * Context object that holds information needed during SQL conversion.
 * <p>
 * This class encapsulates the entity metadata and database dialect information
 * required during the HQL to SQL conversion process. It provides a clean way
 * to pass conversion context between different components of the converter.
 * <p>
 * Also tracks alias-to-entity mappings to properly handle inheritance and discriminator filtering.
 */
public record SqlConversionContext(EntityMetadata entityMetadata, DatabaseDialect dialect, 
                                   Map<String, TableMapping> aliasToEntityMap) {

    /**
     * Constructs a new SqlConversionContext.
     *
     * @param entityMetadata   The entity metadata containing table and column mappings
     * @param dialect          The target database dialect for SQL generation
     * @param aliasToEntityMap Map of alias names to their table mappings
     */
    public SqlConversionContext {
        if (entityMetadata == null) {
            throw new IllegalArgumentException("Entity metadata cannot be null");
        }
        if (dialect == null) {
            throw new IllegalArgumentException("Database dialect cannot be null");
        }
        if (aliasToEntityMap == null) {
            throw new IllegalArgumentException("Alias to entity map cannot be null");
        }
    }

    /**
     * Constructs a new SqlConversionContext without alias mapping (for backwards compatibility).
     * The alias map can be populated later during conversion.
     *
     * @param entityMetadata The entity metadata containing table and column mappings
     * @param dialect        The target database dialect for SQL generation
     */
    public SqlConversionContext(EntityMetadata entityMetadata, DatabaseDialect dialect) {
        this(entityMetadata, dialect, new HashMap<>());
    }

    /**
     * Gets the table mapping for a given alias.
     *
     * @param alias The alias to look up
     * @return The table mapping, or null if not found
     */
    public TableMapping getTableMappingForAlias(String alias) {
        return aliasToEntityMap.get(alias);
    }

    /**
     * Registers an alias to entity mapping.
     * Pre-extraction ensures that aliases are registered in the correct order,
     * so we simply store the mapping.
     *
     * @param alias The alias from the query
     * @param tableMapping The table mapping this alias refers to
     */
    public void registerAlias(String alias, TableMapping tableMapping) {
        aliasToEntityMap.put(alias, tableMapping);
    }

    /**
     * Gets the internal alias to entity mapping map.
     * 
     * @return The internal map (modifiable)
     */
    public Map<String, TableMapping> getAliasToEntityMap() {
        return aliasToEntityMap;
    }

    @Override
    public String toString() {
        return "SqlConversionContext{" +
                "dialect=" + dialect +
                ", entityCount=" + (entityMetadata != null ? entityMetadata.getAllTableMappings().size() : 0) +
                ", aliasCount=" + aliasToEntityMap.size() +
                '}';
    }
}
