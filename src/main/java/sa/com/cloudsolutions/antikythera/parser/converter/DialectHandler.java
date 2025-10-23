package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Handles database dialect detection and SQL transformations.
 * 
 * This class provides integration between the existing RepositoryParser
 * dialect detection logic and the new DatabaseDialect enum, enabling
 * dialect-specific SQL generation and transformations.
 * 
 * Requirements addressed: 4.1, 4.2, 4.3
 */
public class DialectHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(DialectHandler.class);
    
    private DatabaseDialect currentDialect;
    
    /**
     * Creates a new DialectHandler and detects the current database dialect
     * from the configuration settings.
     */
    public DialectHandler() {
        this.currentDialect = detectDialectFromConfiguration();
    }
    
    /**
     * Creates a new DialectHandler with a specific dialect.
     * 
     * @param dialect The database dialect to use
     */
    public DialectHandler(DatabaseDialect dialect) {
        this.currentDialect = dialect;
    }
    
    /**
     * Gets the current database dialect.
     * 
     * @return The current DatabaseDialect, or null if not detected
     */
    public DatabaseDialect getCurrentDialect() {
        return currentDialect;
    }
    
    /**
     * Sets the current database dialect.
     * 
     * @param dialect The dialect to set
     */
    public void setCurrentDialect(DatabaseDialect dialect) {
        this.currentDialect = dialect;
    }
    
    /**
     * Detects the database dialect from the application configuration.
     * This method integrates with the existing RepositoryParser logic.
     * 
     * @return The detected DatabaseDialect, or null if not found
     */
    @SuppressWarnings("unchecked")
    public static DatabaseDialect detectDialectFromConfiguration() {
        try {
            Map<String, Object> db = (Map<String, Object>) Settings.getProperty("database");
            if (db != null) {
                Object urlObj = db.get("url");
                if (urlObj != null) {
                    String url = urlObj.toString();
                    DatabaseDialect dialect = DatabaseDialect.fromJdbcUrl(url);
                    if (dialect != null) {
                        logger.debug("Detected database dialect: {}", dialect.getDisplayName());
                        return dialect;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to detect database dialect from configuration: {}", e.getMessage());
        }
        
        logger.warn("Could not detect database dialect from configuration");
        return null;
    }
    
    /**
     * Detects the database dialect from a JDBC URL.
     * 
     * @param jdbcUrl The JDBC connection URL
     * @return The detected DatabaseDialect, or null if not recognized
     */
    public static DatabaseDialect detectDialectFromUrl(String jdbcUrl) {
        return DatabaseDialect.fromJdbcUrl(jdbcUrl);
    }
    
    /**
     * Applies dialect-specific transformations to SQL.
     * 
     * @param sql The original SQL query
     * @return The transformed SQL query
     */
    public String transformSql(String sql) {
        if (currentDialect == null || sql == null) {
            return sql;
        }
        
        return currentDialect.transformSql(sql);
    }
    
    /**
     * Transforms boolean values according to the current dialect.
     * 
     * @param value The boolean value as a string
     * @return The transformed value
     */
    public String transformBooleanValue(String value) {
        if (currentDialect == null) {
            return value;
        }
        
        return currentDialect.transformBooleanValue(value);
    }
    
    /**
     * Applies a LIMIT clause using dialect-specific syntax.
     * 
     * @param sql The base SQL query
     * @param limit The limit value
     * @return The SQL with the appropriate limit clause
     */
    public String applyLimitClause(String sql, int limit) {
        if (currentDialect == null) {
            return sql + " LIMIT " + limit; // Default to standard SQL
        }
        
        return currentDialect.applyLimitClause(sql, limit);
    }
    
    /**
     * Gets the sequence next value syntax for the current dialect.
     * 
     * @param sequenceName The name of the sequence
     * @return The dialect-specific sequence syntax
     */
    public String getSequenceNextValueSyntax(String sequenceName) {
        if (currentDialect == null) {
            return "NEXTVAL('" + sequenceName + "')"; // Default to PostgreSQL syntax
        }
        
        return currentDialect.getSequenceNextValueSyntax(sequenceName);
    }
    
    /**
     * Gets the string concatenation operator for the current dialect.
     * 
     * @return The concatenation operator
     */
    public String getConcatenationOperator() {
        if (currentDialect == null) {
            return "||"; // Default to standard SQL
        }
        
        return currentDialect.getConcatenationOperator();
    }
    
    /**
     * Checks if the current dialect supports native boolean types.
     * 
     * @return true if boolean is supported, false otherwise
     */
    public boolean supportsBoolean() {
        if (currentDialect == null) {
            return true; // Default to true
        }
        
        return currentDialect.supportsBoolean();
    }
    
    /**
     * Checks if the current dialect is Oracle.
     * This method provides compatibility with existing RepositoryParser logic.
     * 
     * @return true if the current dialect is Oracle
     */
    public boolean isOracle() {
        return currentDialect == DatabaseDialect.ORACLE;
    }
    
    /**
     * Checks if the current dialect is PostgreSQL.
     * 
     * @return true if the current dialect is PostgreSQL
     */
    public boolean isPostgreSQL() {
        return currentDialect == DatabaseDialect.POSTGRESQL;
    }
    
    /**
     * Creates a DialectHandler from the existing RepositoryParser dialect string.
     * This provides backward compatibility with the existing dialect detection.
     * 
     * @param repositoryDialect The dialect string from RepositoryParser
     * @return A new DialectHandler with the appropriate dialect
     */
    public static DialectHandler fromRepositoryParser(String repositoryDialect) {
        DatabaseDialect dialect = DatabaseDialect.fromRepositoryParser(repositoryDialect);
        return new DialectHandler(dialect);
    }
}