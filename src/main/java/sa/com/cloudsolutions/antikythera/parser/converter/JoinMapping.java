package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Represents the mapping information for entity relationships and joins.
 * 
 * This class contains information about how entity relationships should be
 * converted to SQL joins, including join columns and join types.
 * 
 * Requirements addressed: 1.4, 3.2
 */
public class JoinMapping {
    
    private final String propertyName;
    private final String targetEntity;
    private final String joinColumn;
    private final String referencedColumn;
    private final JoinType joinType;
    private final String sourceTable;
    private final String targetTable;
    
    /**
     * Creates a new join mapping.
     * 
     * @param propertyName The name of the relationship property
     * @param targetEntity The name of the target entity
     * @param joinColumn The join column in the source table
     * @param referencedColumn The referenced column in the target table
     * @param joinType The type of join to perform
     * @param sourceTable The source table name
     * @param targetTable The target table name
     */
    public JoinMapping(String propertyName, String targetEntity, String joinColumn, 
                      String referencedColumn, JoinType joinType, String sourceTable, String targetTable) {
        this.propertyName = propertyName;
        this.targetEntity = targetEntity;
        this.joinColumn = joinColumn;
        this.referencedColumn = referencedColumn;
        this.joinType = joinType;
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
    }
    
    /**
     * Gets the relationship property name.
     * 
     * @return The property name
     */
    public String getPropertyName() {
        return propertyName;
    }
    
    /**
     * Gets the target entity name.
     * 
     * @return The target entity name
     */
    public String getTargetEntity() {
        return targetEntity;
    }
    
    /**
     * Gets the join column name.
     * 
     * @return The join column name
     */
    public String getJoinColumn() {
        return joinColumn;
    }
    
    /**
     * Gets the referenced column name.
     * 
     * @return The referenced column name
     */
    public String getReferencedColumn() {
        return referencedColumn;
    }
    
    /**
     * Gets the join type.
     * 
     * @return The join type
     */
    public JoinType getJoinType() {
        return joinType;
    }
    
    /**
     * Gets the source table name.
     * 
     * @return The source table name
     */
    public String getSourceTable() {
        return sourceTable;
    }
    
    /**
     * Gets the target table name.
     * 
     * @return The target table name
     */
    public String getTargetTable() {
        return targetTable;
    }
    
    /**
     * Generates the SQL join clause for this mapping.
     * 
     * @return The SQL join clause
     */
    public String toSqlJoinClause() {
        return String.format("%s JOIN %s ON %s.%s = %s.%s",
                joinType.getSqlKeyword(),
                targetTable,
                sourceTable,
                joinColumn,
                targetTable,
                referencedColumn);
    }
    
    @Override
    public String toString() {
        return "JoinMapping{" +
                "propertyName='" + propertyName + '\'' +
                ", targetEntity='" + targetEntity + '\'' +
                ", joinColumn='" + joinColumn + '\'' +
                ", referencedColumn='" + referencedColumn + '\'' +
                ", joinType=" + joinType +
                ", sourceTable='" + sourceTable + '\'' +
                ", targetTable='" + targetTable + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        JoinMapping that = (JoinMapping) o;
        
        if (propertyName != null ? !propertyName.equals(that.propertyName) : that.propertyName != null) return false;
        if (targetEntity != null ? !targetEntity.equals(that.targetEntity) : that.targetEntity != null) return false;
        if (joinColumn != null ? !joinColumn.equals(that.joinColumn) : that.joinColumn != null) return false;
        if (referencedColumn != null ? !referencedColumn.equals(that.referencedColumn) : that.referencedColumn != null) return false;
        if (joinType != that.joinType) return false;
        if (sourceTable != null ? !sourceTable.equals(that.sourceTable) : that.sourceTable != null) return false;
        return targetTable != null ? targetTable.equals(that.targetTable) : that.targetTable == null;
    }
    
    @Override
    public int hashCode() {
        int result = propertyName != null ? propertyName.hashCode() : 0;
        result = 31 * result + (targetEntity != null ? targetEntity.hashCode() : 0);
        result = 31 * result + (joinColumn != null ? joinColumn.hashCode() : 0);
        result = 31 * result + (referencedColumn != null ? referencedColumn.hashCode() : 0);
        result = 31 * result + (joinType != null ? joinType.hashCode() : 0);
        result = 31 * result + (sourceTable != null ? sourceTable.hashCode() : 0);
        result = 31 * result + (targetTable != null ? targetTable.hashCode() : 0);
        return result;
    }
}