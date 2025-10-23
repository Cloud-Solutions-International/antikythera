package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Collections;
import java.util.Map;

/**
 * Represents the mapping between a JPA entity and its corresponding database table.
 * 
 * This class contains information about how an entity maps to a table,
 * including the table name, schema, and property-to-column mappings.
 * 
 * Requirements addressed: 1.4, 1.5
 */
public class TableMapping {
    
    private final String entityName;
    private final String tableName;
    private final String schema;
    private final Map<String, String> propertyToColumnMap;
    
    /**
     * Creates a new table mapping.
     * 
     * @param entityName The name of the JPA entity
     * @param tableName The name of the database table
     * @param schema The database schema (can be null)
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
     * Gets the entity name.
     * 
     * @return The entity name
     */
    public String getEntityName() {
        return entityName;
    }
    
    /**
     * Gets the table name.
     * 
     * @return The table name
     */
    public String getTableName() {
        return tableName;
    }
    
    /**
     * Gets the schema name.
     * 
     * @return The schema name, or null if not specified
     */
    public String getSchema() {
        return schema;
    }
    
    /**
     * Gets the full table name including schema if present.
     * 
     * @return The full table name (schema.table or just table)
     */
    public String getFullTableName() {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema + "." + tableName;
        }
        return tableName;
    }
    
    /**
     * Gets the column name for the specified property.
     * 
     * @param propertyName The entity property name
     * @return The corresponding column name, or null if not found
     */
    public String getColumnName(String propertyName) {
        return propertyToColumnMap.get(propertyName);
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
     * Gets all property to column mappings.
     * 
     * @return Immutable map of property names to column names
     */
    public Map<String, String> getPropertyToColumnMap() {
        return propertyToColumnMap;
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
        
        if (entityName != null ? !entityName.equals(that.entityName) : that.entityName != null) return false;
        if (tableName != null ? !tableName.equals(that.tableName) : that.tableName != null) return false;
        if (schema != null ? !schema.equals(that.schema) : that.schema != null) return false;
        return propertyToColumnMap != null ? propertyToColumnMap.equals(that.propertyToColumnMap) : that.propertyToColumnMap == null;
    }
    
    @Override
    public int hashCode() {
        int result = entityName != null ? entityName.hashCode() : 0;
        result = 31 * result + (tableName != null ? tableName.hashCode() : 0);
        result = 31 * result + (schema != null ? schema.hashCode() : 0);
        result = 31 * result + (propertyToColumnMap != null ? propertyToColumnMap.hashCode() : 0);
        return result;
    }
}