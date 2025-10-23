package sa.com.cloudsolutions.antikythera.parser.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for applying comprehensive dialect-specific SQL transformations.
 * 
 * This class provides advanced transformation capabilities beyond basic boolean
 * handling, including function mapping, syntax conversion, and data type handling
 * for different database dialects.
 */
public class DialectTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(DialectTransformer.class);
    
    // Patterns for various SQL constructs that need dialect-specific handling
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
        "\\s+LIMIT\\s+(\\d+)(?:\\s+OFFSET\\s+(\\d+))?\\s*$", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern CONCAT_FUNCTION_PATTERN = Pattern.compile(
        "\\bCONCAT\\s*\\(([^)]+)\\)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ORACLE_SEQUENCE_PATTERN = Pattern.compile(
        "\\b(\\w+)\\.NEXTVAL\\b", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern POSTGRESQL_SEQUENCE_PATTERN = Pattern.compile(
        "\\bNEXTVAL\\s*\\(\\s*'([^']+)'\\s*\\)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern ILIKE_PATTERN = Pattern.compile(
        "\\bILIKE\\b", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern NVL_FUNCTION_PATTERN = Pattern.compile(
        "\\bNVL\\s*\\(([^,]+),\\s*([^)]+)\\)", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern COALESCE_FUNCTION_PATTERN = Pattern.compile(
        "\\bCOALESCE\\s*\\(([^)]+)\\)", 
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Applies comprehensive dialect-specific transformations to SQL.
     * 
     * @param sql The original SQL query
     * @param dialect The target database dialect
     * @return The transformed SQL query
     */
    public static String transform(String sql, DatabaseDialect dialect) {
        if (sql == null || dialect == null) {
            return sql;
        }
        
        logger.debug("Applying {} dialect transformations to SQL", dialect.getDisplayName());
        
        String transformedSql = sql;
        
        switch (dialect) {
            case ORACLE:
                transformedSql = transformToOracle(transformedSql);
                break;
            case POSTGRESQL:
                transformedSql = transformToPostgreSQL(transformedSql);
                break;
            default:
                logger.warn("No specific transformations defined for dialect: {}", dialect);
        }
        
        // Apply the dialect's built-in transformations
        transformedSql = dialect.transformSql(transformedSql);
        
        logger.debug("Transformation complete. Original length: {}, Transformed length: {}", 
                   sql.length(), transformedSql.length());
        
        return transformedSql;
    }
    
    /**
     * Transforms SQL to Oracle-specific syntax.
     */
    private static String transformToOracle(String sql) {
        String result = sql;
        
        // Transform LIMIT clause to ROWNUM
        result = transformLimitToRownum(result);
        
        // Transform string concatenation
        result = transformConcatToOracleStyle(result);
        
        // Transform PostgreSQL functions to Oracle equivalents
        result = transformPostgreSQLFunctionsToOracle(result);
        
        // Transform date/time functions
        result = transformDateTimeFunctionsToOracle(result);
        
        // Transform case-insensitive operations
        result = transformCaseInsensitiveToOracle(result);
        
        // Transform null handling functions
        result = transformNullHandlingToOracle(result);
        
        return result;
    }
    
    /**
     * Transforms SQL to PostgreSQL-specific syntax.
     */
    private static String transformToPostgreSQL(String sql) {
        String result = sql;
        
        // Transform Oracle functions to PostgreSQL equivalents
        result = transformOracleFunctionsToPostgreSQL(result);
        
        // Transform sequence syntax
        result = transformSequencesToPostgreSQL(result);
        
        // Transform date/time functions
        result = transformDateTimeFunctionsToPostgreSQL(result);
        
        // Transform null handling functions
        result = transformNullHandlingToPostgreSQL(result);
        
        // Transform Oracle-specific syntax
        result = transformOracleSpecificSyntax(result);
        
        return result;
    }
    
    /**
     * Transforms LIMIT clause to Oracle ROWNUM syntax.
     */
    private static String transformLimitToRownum(String sql) {
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        
        if (matcher.find()) {
            String limit = matcher.group(1);
            String offset = matcher.group(2);
            
            if (offset != null) {
                // Handle LIMIT with OFFSET using subquery
                int limitNum = Integer.parseInt(limit);
                int offsetNum = Integer.parseInt(offset);
                int upperBound = limitNum + offsetNum;
                
                return matcher.replaceFirst(
                    " AND ROWNUM <= " + upperBound + 
                    " AND ROWNUM > " + offsetNum
                );
            } else {
                // Simple LIMIT case
                if ("1".equals(limit)) {
                    return matcher.replaceFirst(" AND ROWNUM = 1");
                } else {
                    return matcher.replaceFirst(" AND ROWNUM <= " + limit);
                }
            }
        }
        
        return sql;
    }
    
    /**
     * Transforms CONCAT function to Oracle-style concatenation.
     */
    private static String transformConcatToOracleStyle(String sql) {
        Matcher matcher = CONCAT_FUNCTION_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String arguments = matcher.group(1);
            String[] args = arguments.split(",");
            
            if (args.length == 2) {
                String replacement = "(" + args[0].trim() + " || " + args[1].trim() + ")";
                matcher.appendReplacement(result, replacement);
            } else {
                // For multiple arguments, chain the || operators
                StringBuilder concatenation = new StringBuilder("(");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        concatenation.append(" || ");
                    }
                    concatenation.append(args[i].trim());
                }
                concatenation.append(")");
                matcher.appendReplacement(result, concatenation.toString());
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Transforms PostgreSQL-specific functions to Oracle equivalents.
     */
    private static String transformPostgreSQLFunctionsToOracle(String sql) {
        String result = sql;
        
        // Transform COALESCE to NVL (for two arguments) or keep COALESCE (Oracle supports it)
        result = result.replaceAll("\\bCURRENT_TIMESTAMP\\b", "SYSDATE");
        result = result.replaceAll("\\bNOW\\(\\)", "SYSDATE");
        result = result.replaceAll("\\bLOCALTIMESTAMP\\b", "SYSDATE");
        
        return result;
    }
    
    /**
     * Transforms date/time functions to Oracle syntax.
     */
    private static String transformDateTimeFunctionsToOracle(String sql) {
        String result = sql;
        
        // Transform standard SQL date functions to Oracle equivalents
        result = result.replaceAll("\\bCURRENT_DATE\\b", "TRUNC(SYSDATE)");
        result = result.replaceAll("\\bCURRENT_TIME\\b", "TO_CHAR(SYSDATE, 'HH24:MI:SS')");
        
        // Transform PostgreSQL date functions
        result = result.replaceAll("\\bEXTRACT\\s*\\(\\s*EPOCH\\s+FROM\\s+([^)]+)\\)", 
                                 "(($1 - DATE '1970-01-01') * 86400)");
        
        return result;
    }
    
    /**
     * Transforms case-insensitive operations to Oracle syntax.
     */
    private static String transformCaseInsensitiveToOracle(String sql) {
        // Transform ILIKE to UPPER(...) LIKE UPPER(...)
        Matcher matcher = ILIKE_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            // This is a simplified transformation - in practice, we'd need to
            // identify the operands and wrap them with UPPER()
            matcher.appendReplacement(result, "LIKE");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Transforms null handling functions to Oracle syntax.
     */
    private static String transformNullHandlingToOracle(String sql) {
        // Transform COALESCE with two arguments to NVL
        Matcher matcher = COALESCE_FUNCTION_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String arguments = matcher.group(1);
            String[] args = arguments.split(",");
            
            if (args.length == 2) {
                String replacement = "NVL(" + args[0].trim() + ", " + args[1].trim() + ")";
                matcher.appendReplacement(result, replacement);
            } else {
                // Keep COALESCE for more than 2 arguments (Oracle supports it)
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Transforms Oracle-specific functions to PostgreSQL equivalents.
     */
    private static String transformOracleFunctionsToPostgreSQL(String sql) {
        String result = sql;
        
        // Transform Oracle date functions
        result = result.replaceAll("\\bSYSDATE\\b", "CURRENT_TIMESTAMP");
        result = result.replaceAll("\\bTRUNC\\s*\\(\\s*SYSDATE\\s*\\)", "CURRENT_DATE");
        
        // Transform Oracle string functions
        result = result.replaceAll("\\bLENGTH\\b", "CHAR_LENGTH");
        
        return result;
    }
    
    /**
     * Transforms sequence syntax to PostgreSQL format.
     */
    private static String transformSequencesToPostgreSQL(String sql) {
        // Transform Oracle sequence syntax to PostgreSQL
        Matcher matcher = ORACLE_SEQUENCE_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String sequenceName = matcher.group(1);
            matcher.appendReplacement(result, "NEXTVAL('" + sequenceName + "')");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Transforms date/time functions to PostgreSQL syntax.
     */
    private static String transformDateTimeFunctionsToPostgreSQL(String sql) {
        String result = sql;
        
        // Transform Oracle-specific date functions
        result = result.replaceAll("\\bTO_CHAR\\s*\\(\\s*SYSDATE\\s*,\\s*'HH24:MI:SS'\\s*\\)", 
                                 "TO_CHAR(CURRENT_TIMESTAMP, 'HH24:MI:SS')");
        
        return result;
    }
    
    /**
     * Transforms null handling functions to PostgreSQL syntax.
     */
    private static String transformNullHandlingToPostgreSQL(String sql) {
        // Transform NVL to COALESCE
        Matcher matcher = NVL_FUNCTION_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        
        while (matcher.find()) {
            String arg1 = matcher.group(1);
            String arg2 = matcher.group(2);
            matcher.appendReplacement(result, "COALESCE(" + arg1 + ", " + arg2 + ")");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Transforms Oracle-specific syntax to PostgreSQL equivalents.
     */
    private static String transformOracleSpecificSyntax(String sql) {
        String result = sql;
        
        // Transform ROWNUM to LIMIT (this is a simplified transformation)
        result = result.replaceAll("\\bAND\\s+ROWNUM\\s*=\\s*1\\b", "LIMIT 1");
        result = result.replaceAll("\\bAND\\s+ROWNUM\\s*<=\\s*(\\d+)\\b", "LIMIT $1");
        
        // Transform Oracle outer join syntax (+) to standard JOIN
        // This is complex and would require more sophisticated parsing
        
        return result;
    }
    
    /**
     * Transforms boolean values according to dialect-specific rules.
     * This method delegates to the DatabaseDialect enum for consistency.
     * 
     * @param sql The SQL containing boolean values
     * @param dialect The target database dialect
     * @return The SQL with transformed boolean values
     */
    public static String transformBooleanValues(String sql, DatabaseDialect dialect) {
        if (sql == null || dialect == null) {
            return sql;
        }
        
        String result = sql;
        
        // Use the dialect's boolean transformation
        result = result.replaceAll("\\btrue\\b", dialect.transformBooleanValue("true"));
        result = result.replaceAll("\\bfalse\\b", dialect.transformBooleanValue("false"));
        
        return result;
    }
    
    /**
     * Applies LIMIT clause using dialect-specific syntax.
     * This method delegates to the DatabaseDialect enum for consistency.
     * 
     * @param sql The base SQL query
     * @param limit The limit value
     * @param dialect The target database dialect
     * @return The SQL with the appropriate limit clause
     */
    public static String applyLimitClause(String sql, int limit, DatabaseDialect dialect) {
        if (sql == null || dialect == null) {
            return sql + " LIMIT " + limit; // Default to standard SQL
        }
        
        return dialect.applyLimitClause(sql, limit);
    }
}
