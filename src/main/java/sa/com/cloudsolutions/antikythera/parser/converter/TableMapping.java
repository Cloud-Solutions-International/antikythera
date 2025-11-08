package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

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

    /**
     * Checks if a mapping exists for the specified property.
     *
     * @param propertyName The property name to check
     * @return true if a mapping exists, false otherwise
     */
    public boolean hasPropertyMapping(String propertyName) {
        return propertyToColumnMap.containsKey(propertyName);
    }

    /**
     * Checks if this entity uses inheritance.
     *
     * @return true if inheritanceType is not null
     */
    public boolean hasInheritance() {
        return inheritanceType != null;
    }

    /**
     * Checks if this entity uses SINGLE_TABLE inheritance strategy.
     *
     * @return true if inheritance type is SINGLE_TABLE
     */
    public boolean isSingleTableInheritance() {
        return "SINGLE_TABLE".equals(inheritanceType);
    }

    /**
     * Checks if this entity uses JOINED inheritance strategy.
     *
     * @return true if inheritance type is JOINED
     */
    public boolean isJoinedInheritance() {
        return "JOINED".equals(inheritanceType);
    }

    /**
     * Checks if this entity uses TABLE_PER_CLASS inheritance strategy.
     *
     * @return true if inheritance type is TABLE_PER_CLASS
     */
    public boolean isTablePerClassInheritance() {
        return "TABLE_PER_CLASS".equals(inheritanceType);
    }

    @Override
    public String toString() {
        return "TableMapping{" +
                "entityName='" + entityName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", schema='" + schema + '\'' +
                ", propertyMappings=" + propertyToColumnMap.size() +
                ", inheritanceType='" + inheritanceType + '\'' +
                ", discriminatorColumn='" + discriminatorColumn + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TableMapping that = (TableMapping) o;

        if (!Objects.equals(entityName, that.entityName)) return false;
        if (!Objects.equals(tableName, that.tableName)) return false;
        if (!Objects.equals(schema, that.schema)) return false;
        if (!Objects.equals(discriminatorColumn, that.discriminatorColumn)) return false;
        if (!Objects.equals(discriminatorValue, that.discriminatorValue)) return false;
        if (!Objects.equals(inheritanceType, that.inheritanceType)) return false;
        if (!Objects.equals(parentTable, that.parentTable)) return false;
        return Objects.equals(propertyToColumnMap, that.propertyToColumnMap);
    }

}
