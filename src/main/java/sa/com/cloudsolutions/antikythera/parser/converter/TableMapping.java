package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Collections;
import java.util.Map;

/**
 * Represents the mapping between a JPA entity and its corresponding database table.
 * <p>
 * This class contains information about how an entity maps to a table,
 * including the table name, schema, property-to-column mappings, and inheritance information.
 * <p>
 */
public record TableMapping(String entityName, String tableName, String schema,
                           Map<String, String> propertyToColumnMap,
                           String discriminatorColumn, String discriminatorValue,
                           String inheritanceType, TableMapping parentTable) {

    /**
     * Creates a new table mapping with inheritance information.
     *
     * @param entityName          The name of the JPA entity
     * @param tableName           The name of the database table
     * @param schema              The database schema (can be null)
     * @param propertyToColumnMap Map of entity property names to column names
     * @param discriminatorColumn Discriminator column name (for SINGLE_TABLE inheritance)
     * @param discriminatorValue  Discriminator value for this entity
     * @param inheritanceType     Inheritance strategy (SINGLE_TABLE, JOINED, TABLE_PER_CLASS, or null)
     * @param parentTable         Parent table mapping (for JOINED inheritance)
     */
    public TableMapping(String entityName, String tableName, String schema, 
                       Map<String, String> propertyToColumnMap,
                       String discriminatorColumn, String discriminatorValue,
                       String inheritanceType, TableMapping parentTable) {
        this.entityName = entityName;
        this.tableName = tableName;
        this.schema = schema;
        this.propertyToColumnMap = propertyToColumnMap != null ?
                Map.copyOf(propertyToColumnMap) : Collections.emptyMap();
        this.discriminatorColumn = discriminatorColumn;
        this.discriminatorValue = discriminatorValue;
        this.inheritanceType = inheritanceType;
        this.parentTable = parentTable;
    }

    /**
     * Gets the column mapping for the specified property.
     *
     * @param propertyName The entity property name
     * @return The corresponding column mapping, or null if not found
     */
    public String getColumnMapping(String propertyName) {
        return propertyToColumnMap.get(propertyName);
    }
}
