package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Represents the mapping between a JPA entity property and its corresponding database column.
 * 
 * This class contains detailed information about how an entity property maps to a column,
 * including type information and constraints.
 * 
 * Requirements addressed: 1.4, 1.5
 */
public class ColumnMapping {
    
    private final String propertyName;
    private final String columnName;
    private final String tableName;
    private final Class<?> javaType;
    private final String sqlType;
    private final boolean nullable;
    
    /**
     * Creates a new column mapping.
     * 
     * @param propertyName The name of the entity property
     * @param columnName The name of the database column
     * @param tableName The name of the table containing the column
     * @param javaType The Java type of the property
     * @param sqlType The SQL type of the column
     * @param nullable Whether the column allows null values
     */
    public ColumnMapping(String propertyName, String columnName, String tableName, 
                        Class<?> javaType, String sqlType, boolean nullable) {
        this.propertyName = propertyName;
        this.columnName = columnName;
        this.tableName = tableName;
        this.javaType = javaType;
        this.sqlType = sqlType;
        this.nullable = nullable;
    }
    
    /**
     * Creates a new column mapping with default values.
     * 
     * @param propertyName The name of the entity property
     * @param columnName The name of the database column
     * @param tableName The name of the table containing the column
     */
    public ColumnMapping(String propertyName, String columnName, String tableName) {
        this(propertyName, columnName, tableName, Object.class, "VARCHAR", true);
    }
    
    /**
     * Gets the property name.
     * 
     * @return The entity property name
     */
    public String getPropertyName() {
        return propertyName;
    }
    
    /**
     * Gets the column name.
     * 
     * @return The database column name
     */
    public String getColumnName() {
        return columnName;
    }
    
    /**
     * Gets the table name.
     * 
     * @return The name of the table containing this column
     */
    public String getTableName() {
        return tableName;
    }
    
    /**
     * Gets the Java type of the property.
     * 
     * @return The Java type
     */
    public Class<?> getJavaType() {
        return javaType;
    }
    
    /**
     * Gets the SQL type of the column.
     * 
     * @return The SQL type
     */
    public String getSqlType() {
        return sqlType;
    }
    
    /**
     * Checks if the column allows null values.
     * 
     * @return true if nullable, false otherwise
     */
    public boolean isNullable() {
        return nullable;
    }
    
    /**
     * Gets the fully qualified column name (table.column).
     * 
     * @return The fully qualified column name
     */
    public String getFullyQualifiedColumnName() {
        return tableName + "." + columnName;
    }
    
    @Override
    public String toString() {
        return "ColumnMapping{" +
                "propertyName='" + propertyName + '\'' +
                ", columnName='" + columnName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", javaType=" + (javaType != null ? javaType.getSimpleName() : "null") +
                ", sqlType='" + sqlType + '\'' +
                ", nullable=" + nullable +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ColumnMapping that = (ColumnMapping) o;
        
        if (nullable != that.nullable) return false;
        if (propertyName != null ? !propertyName.equals(that.propertyName) : that.propertyName != null) return false;
        if (columnName != null ? !columnName.equals(that.columnName) : that.columnName != null) return false;
        if (tableName != null ? !tableName.equals(that.tableName) : that.tableName != null) return false;
        if (javaType != null ? !javaType.equals(that.javaType) : that.javaType != null) return false;
        return sqlType != null ? sqlType.equals(that.sqlType) : that.sqlType == null;
    }
    
    @Override
    public int hashCode() {
        int result = propertyName != null ? propertyName.hashCode() : 0;
        result = 31 * result + (columnName != null ? columnName.hashCode() : 0);
        result = 31 * result + (tableName != null ? tableName.hashCode() : 0);
        result = 31 * result + (javaType != null ? javaType.hashCode() : 0);
        result = 31 * result + (sqlType != null ? sqlType.hashCode() : 0);
        result = 31 * result + (nullable ? 1 : 0);
        return result;
    }
}