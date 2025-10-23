package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the mapping between a JPA entity and its corresponding database table.
 * <p>
 * This class contains information about how an entity maps to a table,
 * including the table name, schema, and property-to-column mappings.
 * <p>
 */
public record TableMapping(String entityName, String tableName, String schema,
                           Map<String, String> propertyToColumnMap) {

    /**
     * Creates a new table mapping.
     *
     * @param entityName          The name of the JPA entity
     * @param tableName           The name of the database table
     * @param schema              The database schema (can be null)
     * @param propertyToColumnMap Map of entity property names to column names
     */
    public TableMapping(String entityName, String tableName, String schema, Map<String, String> propertyToColumnMap) {
        this.entityName = entityName;
        this.tableName = tableName;
        this.schema = schema;
        this.propertyToColumnMap = propertyToColumnMap != null ?
                Map.copyOf(propertyToColumnMap) : Collections.emptyMap();
    }

    /**
     * Gets the column mapping for the specified property.
     *
     * @param propertyName The entity property name
     * @return The corresponding column mapping, or null if not found
     */
    public ColumnMapping getColumnMapping(String propertyName) {
        String columnName = propertyToColumnMap.get(propertyName);
        if (columnName != null) {
            return new ColumnMapping(propertyName, columnName, tableName);
        }
        return null;
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

    @Override
    public String toString() {
        return "TableMapping{" +
                "entityName='" + entityName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", schema='" + schema + '\'' +
                ", propertyMappings=" + propertyToColumnMap.size() +
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
        return Objects.equals(propertyToColumnMap, that.propertyToColumnMap);
    }

}
