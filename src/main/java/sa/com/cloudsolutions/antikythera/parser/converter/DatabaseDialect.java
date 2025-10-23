package sa.com.cloudsolutions.antikythera.parser.converter;

/**
 * Enumeration of supported database dialects for query conversion.
 * 
 * This enum defines the database dialects that the JPA query converter
 * can target when generating native SQL. It provides dialect-specific
 * SQL generation rules and transformations.
 * 
 * Requirements addressed: 4.1, 4.2, 4.3
 */
public enum DatabaseDialect {
    
    /**
     * Oracle Database dialect.
     */
    ORACLE("oracle", "Oracle Database") {
        @Override
        public String transformBooleanValue(String value) {
            if ("true".equalsIgnoreCase(value)) {
                return "1";
            } else if ("false".equalsIgnoreCase(value)) {
                return "0";
            }
            return value;
        }
        
        @Override
        public String applyLimitClause(String sql, int limit) {
            if (limit == 1) {
                return sql + " AND ROWNUM = 1";
            } else {
                return sql + " AND ROWNUM <= " + limit;
            }
        }
        
        @Override
        public String getSequenceNextValueSyntax(String sequenceName) {
            return sequenceName + ".NEXTVAL";
        }
        
        @Override
        public String getConcatenationOperator() {
            return "||";
        }
        
        @Override
        public boolean supportsBoolean() {
            return false;
        }
    },
    
    /**
     * PostgreSQL Database dialect.
     */
    POSTGRESQL("postgresql", "PostgreSQL Database") {
        @Override
        public String transformBooleanValue(String value) {
            // PostgreSQL supports native boolean values
            return value;
        }
        
        @Override
        public String applyLimitClause(String sql, int limit) {
            return sql + " LIMIT " + limit;
        }
        
        @Override
        public String getSequenceNextValueSyntax(String sequenceName) {
            return "NEXTVAL('" + sequenceName + "')";
        }
        
        @Override
        public String getConcatenationOperator() {
            return "||";
        }
        
        @Override
        public boolean supportsBoolean() {
            return true;
        }
    };
    
    private final String identifier;
    private final String displayName;
    
    DatabaseDialect(String identifier, String displayName) {
        this.identifier = identifier;
        this.displayName = displayName;
    }
    
    /**
     * Gets the string identifier for this dialect.
     * 
     * @return The dialect identifier
     */
    public String getIdentifier() {
        return identifier;
    }
    
    /**
     * Gets the human-readable display name for this dialect.
     * 
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Transforms boolean values according to dialect-specific rules.
     * 
     * @param value The boolean value as a string
     * @return The transformed value for this dialect
     */
    public abstract String transformBooleanValue(String value);
    
    /**
     * Applies a LIMIT clause in dialect-specific syntax.
     * 
     * @param sql The base SQL query
     * @param limit The limit value
     * @return The SQL with the appropriate limit clause
     */
    public abstract String applyLimitClause(String sql, int limit);
    
    /**
     * Gets the syntax for retrieving the next value from a sequence.
     * 
     * @param sequenceName The name of the sequence
     * @return The dialect-specific sequence syntax
     */
    public abstract String getSequenceNextValueSyntax(String sequenceName);
    
    /**
     * Gets the string concatenation operator for this dialect.
     * 
     * @return The concatenation operator
     */
    public abstract String getConcatenationOperator();
    
    /**
     * Indicates whether this dialect supports native boolean types.
     * 
     * @return true if boolean is supported, false otherwise
     */
    public abstract boolean supportsBoolean();
    
    /**
     * Transforms SQL according to dialect-specific rules.
     * 
     * @param sql The original SQL
     * @return The transformed SQL
     */
    public String transformSql(String sql) {
        if (sql == null) {
            return null;
        }
        
        String transformed = sql;
        
        // Apply boolean transformations if needed
        if (!supportsBoolean()) {
            transformed = transformed.replaceAll("(?i)\\btrue\\b", transformBooleanValue("true"));
            transformed = transformed.replaceAll("(?i)\\bfalse\\b", transformBooleanValue("false"));
        }
        
        return transformed;
    }
    
    /**
     * Determines the database dialect from a JDBC URL.
     * 
     * @param jdbcUrl The JDBC connection URL
     * @return The detected dialect, or null if not recognized
     */
    public static DatabaseDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        
        String lowerUrl = jdbcUrl.toLowerCase();
        if (lowerUrl.contains("oracle")) {
            return ORACLE;
        } else if (lowerUrl.contains("postgresql") || lowerUrl.contains("postgres")) {
            return POSTGRESQL;
        }
        
        return null;
    }
    
    /**
     * Determines the database dialect from a dialect string identifier.
     * Compatible with existing RepositoryParser dialect constants.
     * 
     * @param dialectString The dialect string (case-insensitive)
     * @return The matching dialect, or null if not found
     */
    public static DatabaseDialect fromString(String dialectString) {
        if (dialectString == null) {
            return null;
        }
        
        String lower = dialectString.toLowerCase();
        
        // Handle existing RepositoryParser constants
        if ("oracle".equals(lower)) {
            return ORACLE;
        } else if ("pg".equals(lower) || "postgresql".equals(lower) || "postgres".equals(lower)) {
            return POSTGRESQL;
        }
        
        // Handle display names and identifiers
        for (DatabaseDialect dialect : values()) {
            if (dialect.identifier.equals(lower) || 
                dialect.displayName.toLowerCase().contains(lower)) {
                return dialect;
            }
        }
        
        return null;
    }
    
    /**
     * Creates a DatabaseDialect from the existing RepositoryParser dialect detection.
     * This method integrates with the existing dialect detection logic.
     * 
     * @param repositoryDialect The dialect string from RepositoryParser
     * @return The corresponding DatabaseDialect, or null if not found
     */
    public static DatabaseDialect fromRepositoryParser(String repositoryDialect) {
        return fromString(repositoryDialect);
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}