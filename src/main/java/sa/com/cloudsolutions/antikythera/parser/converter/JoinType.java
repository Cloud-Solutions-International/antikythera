package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Enumeration of SQL join types supported by the query converter.
 * 
 * This enum defines the different types of joins that can be used
 * when converting entity relationships to SQL joins.
 * 
 * Requirements addressed: 3.2
 */
public enum JoinType {
    
    /**
     * Inner join - returns only matching records from both tables.
     */
    INNER("INNER"),
    
    /**
     * Left outer join - returns all records from the left table and matching records from the right table.
     */
    LEFT("LEFT"),
    
    /**
     * Right outer join - returns all records from the right table and matching records from the left table.
     */
    RIGHT("RIGHT"),
    
    /**
     * Full outer join - returns all records from both tables.
     */
    FULL("FULL OUTER");
    
    private final String sqlKeyword;
    
    JoinType(String sqlKeyword) {
        this.sqlKeyword = sqlKeyword;
    }
    
    /**
     * Gets the SQL keyword for this join type.
     * 
     * @return The SQL keyword
     */
    public String getSqlKeyword() {
        return sqlKeyword;
    }
    
    /**
     * Parses a join type from a string representation.
     * 
     * @param joinTypeString The string representation (case-insensitive)
     * @return The matching join type, or INNER if not found
     */
    public static JoinType fromString(String joinTypeString) {
        if (joinTypeString == null) {
            return INNER;
        }
        
        String upper = joinTypeString.toUpperCase().trim();
        for (JoinType joinType : values()) {
            if (joinType.sqlKeyword.equals(upper) || joinType.name().equals(upper)) {
                return joinType;
            }
        }
        
        return INNER; // Default to INNER join
    }
    
    @Override
    public String toString() {
        return sqlKeyword;
    }
}