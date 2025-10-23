package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Objects;

/**
 * Represents the mapping information for entity relationships and joins.
 * This class contains information about how entity relationships should be
 * converted to SQL joins, including join columns and join types.
 */
public record JoinMapping(String propertyName, String targetEntity, String joinColumn, String referencedColumn,
                          JoinType joinType, String sourceTable, String targetTable) {

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

        if (!Objects.equals(propertyName, that.propertyName)) return false;
        if (!Objects.equals(targetEntity, that.targetEntity)) return false;
        if (!Objects.equals(joinColumn, that.joinColumn)) return false;
        if (!Objects.equals(referencedColumn, that.referencedColumn))
            return false;
        if (joinType != that.joinType) return false;
        if (!Objects.equals(sourceTable, that.sourceTable)) return false;
        return Objects.equals(targetTable, that.targetTable);
    }

}
