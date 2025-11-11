package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Collections;
import java.util.Map;

/**
 * Contains metadata about JPA entities required for query conversion.
 * <p>
 * This class provides mappings between entity names and table names,
 * property names and column names, and relationship information needed
 * to convert JPA queries to native SQL.
 */
public record EntityMetadata(Map<String, TableMapping> entityToTableMappings,
                             Map<String, String> propertyToColumnMappings,
                             Map<String, JoinMapping> relationshipMappings) {

    /**
     * Creates new entity metadata.
     *
     * @param entityToTableMappings    Map of entity names to table mappings
     * @param propertyToColumnMappings Map of property names to column mappings
     * @param relationshipMappings     Map of relationship property names to join mappings
     */
    public EntityMetadata(Map<String, TableMapping> entityToTableMappings,
                          Map<String, String> propertyToColumnMappings,
                          Map<String, JoinMapping> relationshipMappings) {
        this.entityToTableMappings = entityToTableMappings != null ?
                Map.copyOf(entityToTableMappings) : Collections.emptyMap();
        this.propertyToColumnMappings = propertyToColumnMappings != null ?
                Map.copyOf(propertyToColumnMappings) : Collections.emptyMap();
        this.relationshipMappings = relationshipMappings != null ?
                Map.copyOf(relationshipMappings) : Collections.emptyMap();
    }

    /**
     * Gets the table mapping for the specified entity name.
     *
     * @param entityName The name of the entity
     * @return The table mapping, or null if not found
     */
    public TableMapping getTableMapping(String entityName) {
        return entityToTableMappings.get(entityName);
    }

    /**
     * Gets the column mapping for the specified property name.
     *
     * @param propertyName The name of the property (e.g., "user.username")
     * @return The column mapping, or null if not found
     */
    public String getColumnMapping(String propertyName) {
        return propertyToColumnMappings.get(propertyName);
    }

    /**
     * Gets the join mapping for the specified relationship property.
     *
     * @param relationshipProperty The name of the relationship property
     * @return The join mapping, or null if not found
     */
    public JoinMapping getJoinMapping(String relationshipProperty) {
        return relationshipMappings.get(relationshipProperty);
    }

    /**
     * Checks if metadata exists for the specified entity.
     *
     * @param entityName The name of the entity to check
     * @return true if metadata exists, false otherwise
     */
    public boolean hasEntityMetadata(String entityName) {
        return entityToTableMappings.containsKey(entityName);
    }

    /**
     * Gets all table mappings.
     *
     * @return Collection of all table mappings
     */
    public java.util.Collection<TableMapping> getAllTableMappings() {
        return entityToTableMappings.values();
    }
}
