package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Represents the mapping between a JPA entity property and its corresponding database column.
 * <p>
 * This class contains detailed information about how an entity property maps to a column,
 * including type information and constraints.
 */
public record ColumnMapping(String propertyName, String columnName, String tableName, Class<?> javaType, String sqlType,
                            boolean nullable) {
    /**
     * Creates a new column mapping with default values.
     *
     * @param propertyName The name of the entity property
     * @param columnName   The name of the database column
     * @param tableName    The name of the table containing the column
     */
    public ColumnMapping(String propertyName, String columnName, String tableName) {
        this(propertyName, columnName, tableName, Object.class, "VARCHAR", true);
    }
}
