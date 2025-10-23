package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Represents the mapping of a parameter from the original JPA query to the converted native SQL.
 * 
 * This class tracks how named parameters in JPA queries are converted to positional
 * parameters in native SQL, along with type and column information.
 * 
 * Requirements addressed: 3.5
 */
public class ParameterMapping {
    
    private final String originalName;
    private final int position;
    private final Class<?> type;
    private final String columnName;
    
    /**
     * Creates a new parameter mapping.
     * 
     * @param originalName The original parameter name from the JPA query (e.g., "username")
     * @param position The position in the converted SQL (1-based index)
     * @param type The Java type of the parameter
     * @param columnName The database column name this parameter maps to
     */
    public ParameterMapping(String originalName, int position, Class<?> type, String columnName) {
        this.originalName = originalName;
        this.position = position;
        this.type = type;
        this.columnName = columnName;
    }
    
    /**
     * Gets the original parameter name from the JPA query.
     * 
     * @return The original parameter name
     */
    public String getOriginalName() {
        return originalName;
    }
    
    /**
     * Gets the position of this parameter in the converted SQL.
     * 
     * @return The 1-based position index
     */
    public int getPosition() {
        return position;
    }
    
    /**
     * Gets the Java type of the parameter.
     * 
     * @return The parameter type
     */
    public Class<?> getType() {
        return type;
    }
    
    /**
     * Gets the database column name this parameter maps to.
     * 
     * @return The column name
     */
    public String getColumnName() {
        return columnName;
    }
    
    @Override
    public String toString() {
        return "ParameterMapping{" +
                "originalName='" + originalName + '\'' +
                ", position=" + position +
                ", type=" + (type != null ? type.getSimpleName() : "null") +
                ", columnName='" + columnName + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ParameterMapping that = (ParameterMapping) o;
        
        if (position != that.position) return false;
        if (originalName != null ? !originalName.equals(that.originalName) : that.originalName != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        return columnName != null ? columnName.equals(that.columnName) : that.columnName == null;
    }
    
    @Override
    public int hashCode() {
        int result = originalName != null ? originalName.hashCode() : 0;
        result = 31 * result + position;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (columnName != null ? columnName.hashCode() : 0);
        return result;
    }
}