package sa.com.cloudsolutions.antikythera.parser.converter;

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
}
