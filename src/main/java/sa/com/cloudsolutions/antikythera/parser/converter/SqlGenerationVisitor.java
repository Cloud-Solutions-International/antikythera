package sa.com.cloudsolutions.antikythera.parser.converter;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import net.sf.jsqlparser.expression.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Visitor implementation for converting HQL AST nodes to SQL.
 * 
 * This class implements the visitor pattern to traverse HQL Abstract Syntax Tree
 * nodes and generate corresponding SQL statements. It handles entity-to-table
 * mapping, property-to-column conversion, and dialect-specific SQL generation.
 * 
 * Requirements addressed: 1.1, 1.3, 3.1
 */
public class SqlGenerationVisitor {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlGenerationVisitor.class);
    
    // Pattern to match entity references in FROM and JOIN clauses
    private static final Pattern ENTITY_REFERENCE_PATTERN = Pattern.compile(
        "\\b(FROM|JOIN)\\s+([A-Z][a-zA-Z0-9_]*(?:\\.[A-Z][a-zA-Z0-9_]*)*)\\s+([a-zA-Z_][a-zA-Z0-9_]*)?",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern to match property references (alias.property)
    private static final Pattern PROPERTY_REFERENCE_PATTERN = Pattern.compile(
        "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b"
    );
    
    // Pattern to match named parameters
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":(\\w+)");
    
    // Pattern to match aggregate functions
    private static final Pattern AGGREGATE_FUNCTION_PATTERN = Pattern.compile(
        "\\b(COUNT|SUM|AVG|MIN|MAX)\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern to match GROUP BY clause
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(
        "\\bGROUP\\s+BY\\s+([^\\s]+(?:\\s*,\\s*[^\\s]+)*)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Pattern to match HAVING clause
    private static final Pattern HAVING_PATTERN = Pattern.compile(
        "(HAVING\\s+)(.+?)(?=\\s+(?:ORDER\\s+BY|LIMIT|$))",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Pattern to match subqueries (nested SELECT statements)
    private static final Pattern SUBQUERY_PATTERN = Pattern.compile(
        "\\(\\s*(SELECT\\s+.+?)\\s*\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Pattern to match EXISTS subqueries
    private static final Pattern EXISTS_SUBQUERY_PATTERN = Pattern.compile(
        "\\b(NOT\\s+)?EXISTS\\s*\\(\\s*(SELECT\\s+.+?)\\s*\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // Pattern to match IN subqueries
    private static final Pattern IN_SUBQUERY_PATTERN = Pattern.compile(
        "\\bIN\\s*\\(\\s*(SELECT\\s+.+?)\\s*\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    /**
     * Converts an HQL query node to native SQL.
     * 
     * @param queryNode The root query node from HQL parsing
     * @param context The conversion context containing entity metadata and dialect info
     * @return The generated native SQL string
     */
    public String convertToSql(QueryNode queryNode, SqlConversionContext context) {
        if (queryNode == null) {
            throw new IllegalArgumentException("Query node cannot be null");
        }
        
        logger.debug("Converting query node to SQL for dialect: {}", context.dialect());
        
        // For the initial implementation, we'll work with the original query text
        // In a full implementation, this would traverse the actual AST
        String originalQuery = queryNode.getText();
        
        try {
            // Use JSQLParser to parse and convert the query properly
            Statement statement = CCJSqlParserUtil.parse(originalQuery);
            
            if (statement instanceof Select select) {
                // Convert the parsed statement using proper AST traversal
                convertSelectStatement(select, context);
                return statement.toString();
            } else if (statement instanceof net.sf.jsqlparser.statement.update.Update update) {
                // Convert UPDATE statement
                convertUpdateStatement(update, context);
                return statement.toString();
            } else if (statement instanceof net.sf.jsqlparser.statement.delete.Delete delete) {
                // Convert DELETE statement
                convertDeleteStatement(delete, context);
                return statement.toString();
            } else {
                // Fallback to string-based conversion for other statement types
                return convertQueryStringBased(originalQuery, context);
            }
        } catch (JSQLParserException e) {
            logger.warn("Failed to parse query with JSQLParser, falling back to string-based conversion: {}", e.getMessage());
            // Fallback to the original string-based approach
            return convertQueryStringBased(originalQuery, context);
        }
    }
    
    /**
     * String-based query conversion (fallback method).
     */
    private String convertQueryStringBased(String originalQuery, SqlConversionContext context) {
        String trimmedQuery = originalQuery.trim().toLowerCase();
        
        // Determine query type and apply appropriate conversions
        if (trimmedQuery.startsWith("select") || trimmedQuery.startsWith("from")) {
            return convertSelectQueryStringBased(originalQuery, context);
        } else if (trimmedQuery.startsWith("update")) {
            return convertUpdateQueryStringBased(originalQuery, context);
        } else if (trimmedQuery.startsWith("delete")) {
            return convertDeleteQueryStringBased(originalQuery, context);
        } else {
            // Unknown query type, apply basic conversions
            return convertGenericQueryStringBased(originalQuery, context);
        }
    }
    
    /**
     * String-based conversion for SELECT queries.
     */
    private String convertSelectQueryStringBased(String originalQuery, SqlConversionContext context) {
        // Step 1: Process SELECT statement conversion
        String sqlWithSelect = convertSelectStatement(originalQuery, context);
        
        // Step 2: Convert entity references to table references
        String sqlWithTables = convertEntityReferences(sqlWithSelect, context);
        
        // Step 3: Convert property references to column references
        String sqlWithColumns = convertPropertyReferences(sqlWithTables, context);
        
        // Step 4: Process JOIN operations conversion
        String sqlWithJoins = convertJoinOperations(sqlWithColumns, context);
        
        // Step 5: Process WHERE clause conversion with proper column mapping
        String sqlWithWhere = convertWhereClause(sqlWithJoins, context);
        
        // Step 6: Process aggregate functions (COUNT, SUM, AVG, MIN, MAX)
        String sqlWithAggregates = convertAggregateFunctions(sqlWithWhere, context);
        
        // Step 7: Process GROUP BY clause conversion
        String sqlWithGroupBy = convertGroupByClause(sqlWithAggregates, context);
        
        // Step 8: Process HAVING clause conversion with proper column mapping
        String sqlWithHaving = convertHavingClause(sqlWithGroupBy, context);
        
        // Step 9: Process subqueries (nested SELECT statements)
        String sqlWithSubqueries = convertSubqueries(sqlWithHaving, context);
        
        // Step 10: Convert named parameters to positional parameters
        String sqlWithPositionalParams = convertNamedParameters(sqlWithSubqueries);
        
        // Step 11: Apply dialect-specific transformations
        String finalSql = DialectTransformer.transform(sqlWithPositionalParams, context.dialect());
        
        logger.debug("Converted SELECT SQL: {}", finalSql);
        return finalSql;
    }
    
    /**
     * String-based conversion for UPDATE queries.
     */
    private String convertUpdateQueryStringBased(String originalQuery, SqlConversionContext context) {
        // Step 1: Convert entity references to table references
        String sqlWithTables = convertEntityReferences(originalQuery, context);
        
        // Step 2: Convert property references to column references (in SET and WHERE clauses)
        String sqlWithColumns = convertPropertyReferences(sqlWithTables, context);
        
        // Step 3: Process JOIN operations conversion (if any)
        String sqlWithJoins = convertJoinOperations(sqlWithColumns, context);
        
        // Step 4: Process WHERE clause conversion
        String sqlWithWhere = convertWhereClause(sqlWithJoins, context);
        
        // Step 5: Convert SET clause property references
        String sqlWithSetClause = convertUpdateSetClause(sqlWithWhere, context);
        
        // Step 6: Convert named parameters to positional parameters
        String sqlWithPositionalParams = convertNamedParameters(sqlWithSetClause);
        
        // Step 7: Apply dialect-specific transformations
        String finalSql = DialectTransformer.transform(sqlWithPositionalParams, context.dialect());
        
        logger.debug("Converted UPDATE SQL: {}", finalSql);
        return finalSql;
    }
    
    /**
     * String-based conversion for DELETE queries.
     */
    private String convertDeleteQueryStringBased(String originalQuery, SqlConversionContext context) {
        // Step 1: Convert entity references to table references
        String sqlWithTables = convertEntityReferences(originalQuery, context);
        
        // Step 2: Convert property references to column references
        String sqlWithColumns = convertPropertyReferences(sqlWithTables, context);
        
        // Step 3: Process JOIN operations conversion (if any)
        String sqlWithJoins = convertJoinOperations(sqlWithColumns, context);
        
        // Step 4: Process WHERE clause conversion
        String sqlWithWhere = convertWhereClause(sqlWithJoins, context);
        
        // Step 5: Convert named parameters to positional parameters
        String sqlWithPositionalParams = convertNamedParameters(sqlWithWhere);
        
        // Step 6: Apply dialect-specific transformations
        String finalSql = DialectTransformer.transform(sqlWithPositionalParams, context.dialect());
        
        logger.debug("Converted DELETE SQL: {}", finalSql);
        return finalSql;
    }
    
    /**
     * String-based conversion for generic/unknown query types.
     */
    private String convertGenericQueryStringBased(String originalQuery, SqlConversionContext context) {
        // Apply basic conversions for unknown query types
        
        // Step 1: Convert entity references to table references
        String sqlWithTables = convertEntityReferences(originalQuery, context);
        
        // Step 2: Convert property references to column references
        String sqlWithColumns = convertPropertyReferences(sqlWithTables, context);
        
        // Step 3: Process WHERE clause conversion (if any)
        String sqlWithWhere = convertWhereClause(sqlWithColumns, context);
        
        // Step 4: Convert named parameters to positional parameters
        String sqlWithPositionalParams = convertNamedParameters(sqlWithWhere);
        
        // Step 5: Apply dialect-specific transformations
        String finalSql = DialectTransformer.transform(sqlWithPositionalParams, context.dialect());
        
        logger.debug("Converted generic SQL: {}", finalSql);
        return finalSql;
    }
    
    /**
     * Converts SET clause in UPDATE statements, handling property to column mapping.
     */
    private String convertUpdateSetClause(String query, SqlConversionContext context) {
        // Pattern to match SET clause: SET property = value, property2 = value2
        Pattern setPattern = Pattern.compile(
            "(SET\\s+)([^\\s]+(?:\\s*,\\s*[^\\s]+)*?)(?=\\s+WHERE|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher matcher = setPattern.matcher(query);
        if (matcher.find()) {
            String setKeyword = matcher.group(1);
            String setClause = matcher.group(2);
            
            // Convert property references in SET clause
            String convertedSetClause = convertSetClauseProperties(setClause, context);
            
            return matcher.replaceFirst(setKeyword + convertedSetClause);
        }
        
        return query;
    }
    
    /**
     * Converts property references within SET clause assignments.
     */
    private String convertSetClauseProperties(String setClause, SqlConversionContext context) {
        // Pattern to match property assignments: property = value
        Pattern assignmentPattern = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*=",
            Pattern.CASE_INSENSITIVE
        );
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = assignmentPattern.matcher(setClause);
        
        while (matcher.find()) {
            String propertyName = matcher.group(1);
            
            // Find column mapping for the property
            String columnName = findColumnMapping(propertyName, context);
            
            if (columnName != null) {
                String replacement = columnName + " =";
                matcher.appendReplacement(result, replacement);
                logger.debug("Converted SET clause property: {} -> {}", propertyName, columnName);
            } else {
                // If no mapping found, keep original
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts a parsed SELECT statement using proper AST traversal.
     */
    private void convertSelectStatement(Select select, SqlConversionContext context) {
        PlainSelect plainSelect = select.getPlainSelect();
        
        // Convert SELECT items
        if (plainSelect.getSelectItems() != null) {
            for (int i = 0; i < plainSelect.getSelectItems().size(); i++) {
                SelectItem<?> item = plainSelect.getSelectItems().get(i);
                if (item.getExpression() != null) {
                    net.sf.jsqlparser.expression.Expression convertedExpr = 
                        convertExpressionToSnakeCase(item.getExpression(), context);
                    SelectItem<?> newItem = SelectItem.from(convertedExpr);
                    if (item.getAlias() != null) {
                        newItem.setAlias(item.getAlias());
                    }
                    plainSelect.getSelectItems().set(i, newItem);
                }
            }
        }
        
        // Convert FROM item
        if (plainSelect.getFromItem() != null) {
            convertFromItem(plainSelect.getFromItem(), context);
        }
        
        // Convert JOINs
        if (plainSelect.getJoins() != null) {
            for (Join join : plainSelect.getJoins()) {
                convertJoin(join, context);
            }
        }
        
        // Convert WHERE clause
        if (plainSelect.getWhere() != null) {
            plainSelect.setWhere(convertExpressionToSnakeCase(plainSelect.getWhere(), context));
        }
        
        // Convert GROUP BY
        if (plainSelect.getGroupBy() != null) {
            GroupByElement groupBy = plainSelect.getGroupBy();
            if (groupBy.getGroupByExpressions() != null) {
                ExpressionList expressions = new ExpressionList();
                for (Object obj : groupBy.getGroupByExpressions()) {
                    if (obj instanceof net.sf.jsqlparser.expression.Expression) {
                        net.sf.jsqlparser.expression.Expression expr = (net.sf.jsqlparser.expression.Expression) obj;
                        expressions.add(convertExpressionToSnakeCase(expr, context));
                    } else {
                        expressions.add(obj);
                    }
                }
                groupBy.setGroupByExpressions(expressions);
            }
        }
        
        // Convert HAVING clause
        if (plainSelect.getHaving() != null) {
            plainSelect.setHaving(convertExpressionToSnakeCase(plainSelect.getHaving(), context));
        }
        
        // Convert ORDER BY
        if (plainSelect.getOrderByElements() != null) {
            for (OrderByElement orderBy : plainSelect.getOrderByElements()) {
                orderBy.setExpression(convertExpressionToSnakeCase(orderBy.getExpression(), context));
            }
        }
    }
    
    /**
     * Converts a parsed UPDATE statement using proper AST traversal.
     */
    private void convertUpdateStatement(Update update, SqlConversionContext context) {
        // Convert table reference
        if (update.getTable() != null) {
            convertFromItem(update.getTable(), context);
        }
        
        // Convert SET clauses
        if (update.getUpdateSets() != null) {
            for (UpdateSet updateSet : update.getUpdateSets()) {
                // Convert columns in SET clause
                if (updateSet.getColumns() != null) {
                    for (int i = 0; i < updateSet.getColumns().size(); i++) {
                        Column column = updateSet.getColumns().get(i);
                        updateSet.getColumns().set(i, (Column) convertExpressionToSnakeCase(column, context));
                    }
                }
                
                // Convert values in SET clause
                if (updateSet.getValues() != null) {
                    ExpressionList newValues = new ExpressionList();
                    for (Object value : updateSet.getValues()) {
                        if (value instanceof net.sf.jsqlparser.expression.Expression) {
                            newValues.add(convertExpressionToSnakeCase((net.sf.jsqlparser.expression.Expression) value, context));
                        } else {
                            newValues.add(value);
                        }
                    }
                    updateSet.setValues(newValues);
                }
            }
        }
        
        // Convert WHERE clause
        if (update.getWhere() != null) {
            update.setWhere(convertExpressionToSnakeCase(update.getWhere(), context));
        }
        
        // Convert JOINs if present
        if (update.getJoins() != null) {
            for (Join join : update.getJoins()) {
                convertJoin(join, context);
            }
        }
        
        logger.debug("Converted UPDATE statement for table: {}", 
                    update.getTable() != null ? update.getTable().getName() : "unknown");
    }
    
    /**
     * Converts a parsed DELETE statement using proper AST traversal.
     */
    private void convertDeleteStatement(Delete delete, SqlConversionContext context) {
        // Convert table reference
        if (delete.getTable() != null) {
            convertFromItem(delete.getTable(), context);
        }
        
        // Convert WHERE clause
        if (delete.getWhere() != null) {
            delete.setWhere(convertExpressionToSnakeCase(delete.getWhere(), context));
        }
        
        // Convert JOINs if present (for DELETE with JOINs)
        if (delete.getJoins() != null) {
            for (Join join : delete.getJoins()) {
                convertJoin(join, context);
            }
        }
        
        // Convert ORDER BY if present (MySQL supports ORDER BY in DELETE)
        if (delete.getOrderByElements() != null) {
            for (OrderByElement orderBy : delete.getOrderByElements()) {
                orderBy.setExpression(convertExpressionToSnakeCase(orderBy.getExpression(), context));
            }
        }
        
        logger.debug("Converted DELETE statement for table: {}", 
                    delete.getTable() != null ? delete.getTable().getName() : "unknown");
    }
    
    /**
     * Enhanced expression conversion that uses the conversion context.
     */
    private net.sf.jsqlparser.expression.Expression convertExpressionToSnakeCase(
            net.sf.jsqlparser.expression.Expression expr, SqlConversionContext context) {
        
        if (expr instanceof Column column) {
            // Convert column names to snake_case
            String columnName = column.getColumnName();
            if (columnName != null && !columnName.equals("*")) {
                column.setColumnName(camelToSnake(columnName));
            }
        } else if (expr instanceof Function function) {
            // Handle function parameters
            if (function.getParameters() != null && function.getParameters().getExpressions() != null) {
                ExpressionList params = (ExpressionList) function.getParameters().getExpressions();
                for (int i = 0; i < params.size(); i++) {
                    params.getExpressions().set(i, convertExpressionToSnakeCase(
                        (net.sf.jsqlparser.expression.Expression) params.get(i), context));
                }
            }
        } else if (expr instanceof CaseExpression ce) {
            // Convert switch expression if present
            if (ce.getSwitchExpression() != null) {
                ce.setSwitchExpression(convertExpressionToSnakeCase(ce.getSwitchExpression(), context));
            }
            
            // Convert WHEN clauses
            for (WhenClause when : ce.getWhenClauses()) {
                when.setWhenExpression(convertExpressionToSnakeCase(when.getWhenExpression(), context));
                when.setThenExpression(convertExpressionToSnakeCase(when.getThenExpression(), context));
            }
            
            // Convert ELSE expression if present
            if (ce.getElseExpression() != null) {
                ce.setElseExpression(convertExpressionToSnakeCase(ce.getElseExpression(), context));
            }
        } else if (expr instanceof BinaryExpression binaryExpr) {
            binaryExpr.setLeftExpression(convertExpressionToSnakeCase(binaryExpr.getLeftExpression(), context));
            binaryExpr.setRightExpression(convertExpressionToSnakeCase(binaryExpr.getRightExpression(), context));
        }
        
        return expr;
    }
    
    /**
     * Converts FROM item (table references).
     */
    private void convertFromItem(FromItem fromItem, SqlConversionContext context) {
        if (fromItem instanceof Table table) {
            // Convert entity name to table name if needed
            String tableName = table.getName();
            TableMapping tableMapping = context.entityMetadata().getTableMapping(tableName);
            if (tableMapping != null) {
                table.setName(tableMapping.tableName());
            }
        }
    }
    
    /**
     * Converts JOIN clauses.
     */
    private void convertJoin(Join join, SqlConversionContext context) {
        // Convert the right item (joined table)
        if (join.getRightItem() != null) {
            convertFromItem(join.getRightItem(), context);
        }
        
        // Convert ON expressions
        if (join.getOnExpressions() != null) {
            List<net.sf.jsqlparser.expression.Expression> onExpressions = 
                new java.util.ArrayList<>();
            for (net.sf.jsqlparser.expression.Expression expr : join.getOnExpressions()) {
                onExpressions.add(convertExpressionToSnakeCase(expr, context));
            }
            join.setOnExpressions(onExpressions);
        }
    }
    
    /**
     * Simple camelCase to snake_case conversion.
     */
    private String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                result.append('_');
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }
    
    /**
     * Converts entity references in FROM and JOIN clauses to table references.
     */
    private String convertEntityReferences(String query, SqlConversionContext context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = ENTITY_REFERENCE_PATTERN.matcher(query);
        
        while (matcher.find()) {
            String clause = matcher.group(1); // FROM or JOIN
            String entityName = matcher.group(2); // Entity name
            String alias = matcher.group(3); // Optional alias
            
            // Get table mapping for the entity
            TableMapping tableMapping = context.entityMetadata().getTableMapping(entityName);
            if (tableMapping != null) {
                String tableName = tableMapping.tableName();
                String replacement = clause + " " + tableName;
                if (alias != null && !alias.isEmpty()) {
                    replacement += " " + alias;
                }
                matcher.appendReplacement(result, replacement);
                
                logger.debug("Converted entity reference: {} -> {}", entityName, tableName);
            } else {
                // If no mapping found, keep original
                logger.warn("No table mapping found for entity: {}", entityName);
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts property references (alias.property) to column references (alias.column).
     */
    private String convertPropertyReferences(String query, SqlConversionContext context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PROPERTY_REFERENCE_PATTERN.matcher(query);
        
        while (matcher.find()) {
            String alias = matcher.group(1);
            String property = matcher.group(2);
            
            // Try to find column mapping for the property
            // For now, we'll use a simple approach - in full implementation,
            // we'd need to track which alias corresponds to which entity
            String columnName = findColumnMapping(property, context);
            
            if (columnName != null) {
                String replacement = alias + "." + columnName;
                matcher.appendReplacement(result, replacement);
                logger.debug("Converted property reference: {}.{} -> {}.{}", alias, property, alias, columnName);
            } else {
                // If no mapping found, keep original
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts named parameters (:param) to positional parameters (?).
     * Maintains parameter order for proper binding.
     */
    private String convertNamedParameters(String query) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(query);
        Map<String, Integer> parameterPositions = new HashMap<>();
        int position = 1;
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            
            // Assign position if not already assigned (handles duplicate parameters)
            if (!parameterPositions.containsKey(paramName)) {
                parameterPositions.put(paramName, position++);
            }
            
            // Replace with positional parameter
            matcher.appendReplacement(result, "?");
            
            logger.debug("Converted named parameter: :{} -> ? (position {})", 
                       paramName, parameterPositions.get(paramName));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Applies database dialect-specific transformations using the DatabaseDialect enum.
     */
    private String applyDialectTransformations(String sql, DatabaseDialect dialect) {
        if (dialect == null) {
            return sql;
        }
        
        // Use the dialect's built-in transformation method
        String transformedSql = dialect.transformSql(sql);
        
        // Apply additional dialect-specific transformations
        transformedSql = applyAdvancedDialectTransformations(transformedSql, dialect);
        
        return transformedSql;
    }
    
    /**
     * Applies advanced dialect-specific transformations beyond basic boolean handling.
     */
    private String applyAdvancedDialectTransformations(String sql, DatabaseDialect dialect) {
        switch (dialect) {
            case ORACLE:
                return applyOracleAdvancedTransformations(sql);
            case POSTGRESQL:
                return applyPostgreSQLAdvancedTransformations(sql);
            default:
                return sql;
        }
    }
    
    /**
     * Applies Oracle-specific advanced SQL transformations.
     */
    private String applyOracleAdvancedTransformations(String sql) {
        // Oracle-specific transformations beyond boolean handling
        
        // Handle string concatenation (use || operator)
        sql = sql.replaceAll("\\bCONCAT\\s*\\(([^,]+),\\s*([^)]+)\\)", "($1 || $2)");
        
        // Handle LIMIT clause conversion to ROWNUM
        sql = convertLimitToRownum(sql);
        
        // Handle date/time functions
        sql = sql.replaceAll("\\bCURRENT_TIMESTAMP\\b", "SYSDATE");
        sql = sql.replaceAll("\\bNOW\\(\\)", "SYSDATE");
        
        // Handle case-insensitive comparisons
        sql = sql.replaceAll("\\bILIKE\\b", "LIKE");
        
        return sql;
    }
    
    /**
     * Applies PostgreSQL-specific advanced SQL transformations.
     */
    private String applyPostgreSQLAdvancedTransformations(String sql) {
        // PostgreSQL-specific transformations
        
        // Handle Oracle-style date functions
        sql = sql.replaceAll("\\bSYSDATE\\b", "CURRENT_TIMESTAMP");
        
        // Handle string functions
        sql = sql.replaceAll("\\bNVL\\s*\\(([^,]+),\\s*([^)]+)\\)", "COALESCE($1, $2)");
        
        // Handle sequence syntax
        sql = convertOracleSequenceToPostgreSQL(sql);
        
        return sql;
    }
    
    /**
     * Converts LIMIT clause to Oracle ROWNUM syntax.
     */
    private String convertLimitToRownum(String sql) {
        // Pattern to match LIMIT clause at the end of query
        Pattern limitPattern = Pattern.compile("\\s+LIMIT\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = limitPattern.matcher(sql);
        
        if (matcher.find()) {
            String limitValue = matcher.group(1);
            if ("1".equals(limitValue)) {
                return matcher.replaceFirst(" AND ROWNUM = 1");
            } else {
                return matcher.replaceFirst(" AND ROWNUM <= " + limitValue);
            }
        }
        
        return sql;
    }
    
    /**
     * Converts Oracle sequence syntax to PostgreSQL syntax.
     */
    private String convertOracleSequenceToPostgreSQL(String sql) {
        // Pattern to match Oracle sequence syntax: sequence_name.NEXTVAL
        Pattern oracleSeqPattern = Pattern.compile("\\b(\\w+)\\.NEXTVAL\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = oracleSeqPattern.matcher(sql);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String sequenceName = matcher.group(1);
            matcher.appendReplacement(result, "NEXTVAL('" + sequenceName + "')");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts SELECT statement from HQL to SQL format.
     * Handles entity selections and property selections.
     */
    private String convertSelectStatement(String query, SqlConversionContext context) {
        // Pattern to match SELECT clauses with entity or property selections
        Pattern selectPattern = Pattern.compile(
            "SELECT\\s+(DISTINCT\\s+)?([^\\s]+(?:\\s*,\\s*[^\\s]+)*)\\s+FROM",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = selectPattern.matcher(query);
        if (matcher.find()) {
            String distinct = matcher.group(1) != null ? matcher.group(1) : "";
            String selectClause = matcher.group(2);
            
            // Convert entity selections (e.g., "u" -> "u.*")
            String convertedSelect = convertSelectClause(selectClause, context);
            
            String replacement = "SELECT " + distinct + convertedSelect + " FROM";
            return matcher.replaceFirst(replacement);
        }
        
        return query;
    }
    
    /**
     * Converts the SELECT clause, handling entity and property selections.
     */
    private String convertSelectClause(String selectClause, SqlConversionContext context) {
        // Split by comma to handle multiple selections
        String[] selections = selectClause.split("\\s*,\\s*");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < selections.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            
            String selection = selections[i].trim();
            
            // Check if it's a simple entity alias (e.g., "u")
            if (selection.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                // Convert entity alias to table.* selection
                result.append(selection).append(".*");
            } else {
                // Keep property selections as-is for now (will be converted later)
                result.append(selection);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Converts JOIN operations from entity joins to table joins.
     * Handles different join types (INNER, LEFT, RIGHT) and implicit joins.
     */
    private String convertJoinOperations(String query, SqlConversionContext context) {
        // Pattern to match explicit JOIN clauses
        Pattern explicitJoinPattern = Pattern.compile(
            "\\b((?:INNER\\s+|LEFT\\s+(?:OUTER\\s+)?|RIGHT\\s+(?:OUTER\\s+)?|FULL\\s+(?:OUTER\\s+)?)?JOIN)\\s+" +
            "([A-Z][a-zA-Z0-9_]*(?:\\.[A-Z][a-zA-Z0-9_]*)*)\\s+" +
            "([a-zA-Z_][a-zA-Z0-9_]*)\\s+" +
            "(?:ON\\s+(.+?))?(?=\\s+(?:WHERE|ORDER|GROUP|HAVING|JOIN|$))",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = explicitJoinPattern.matcher(query);
        
        while (matcher.find()) {
            String joinType = matcher.group(1);
            String entityName = matcher.group(2);
            String alias = matcher.group(3);
            String onClause = matcher.group(4);
            
            // Convert entity join to table join
            String convertedJoin = convertEntityJoin(joinType, entityName, alias, onClause, context);
            matcher.appendReplacement(result, convertedJoin);
        }
        matcher.appendTail(result);
        
        // Handle implicit joins (entity.property references that need JOIN clauses)
        return handleImplicitJoins(result.toString(), context);
    }
    
    /**
     * Converts an entity JOIN to a table JOIN with proper ON clause.
     */
    private String convertEntityJoin(String joinType, String entityName, String alias, String onClause, SqlConversionContext context) {
        // Get table mapping for the entity
        TableMapping tableMapping = context.entityMetadata().getTableMapping(entityName);
        if (tableMapping == null) {
            logger.warn("No table mapping found for entity: {}", entityName);
            return joinType + " " + entityName + " " + alias + (onClause != null ? " ON " + onClause : "");
        }
        
        String tableName = tableMapping.tableName();
        StringBuilder joinBuilder = new StringBuilder();
        joinBuilder.append(joinType).append(" ").append(tableName).append(" ").append(alias);
        
        if (onClause != null) {
            // Convert property references in ON clause
            String convertedOnClause = convertJoinOnClause(onClause, context);
            joinBuilder.append(" ON ").append(convertedOnClause);
        } else {
            // Try to generate ON clause from relationship mappings
            String generatedOnClause = generateJoinOnClause(entityName, alias, context);
            if (generatedOnClause != null) {
                joinBuilder.append(" ON ").append(generatedOnClause);
            }
        }
        
        logger.debug("Converted entity join: {} {} -> {} {}", entityName, alias, tableName, alias);
        return joinBuilder.toString();
    }
    
    /**
     * Converts property references in JOIN ON clauses.
     */
    private String convertJoinOnClause(String onClause, SqlConversionContext context) {
        // Use the same property reference conversion as WHERE clauses
        return convertWhereConditions(onClause, context);
    }
    
    /**
     * Generates JOIN ON clause from relationship mappings.
     */
    private String generateJoinOnClause(String entityName, String alias, SqlConversionContext context) {
        EntityMetadata metadata = context.entityMetadata();
        
        // Look for relationship mappings that involve this entity
        for (JoinMapping joinMapping : metadata.getRelationshipMappings().values()) {
            if (entityName.equals(joinMapping.targetEntity())) {
                // Generate ON clause based on join mapping
                String joinColumn = joinMapping.joinColumn();
                String referencedColumn = joinMapping.referencedColumn();
                
                // This is a simplified approach - in practice, we'd need to track
                // the source alias from the query context
                return "source_alias." + referencedColumn + " = " + alias + "." + joinColumn;
            }
        }
        
        return null; // No relationship mapping found
    }
    
    /**
     * Handles implicit joins by detecting entity.property references that require JOIN clauses.
     */
    private String handleImplicitJoins(String query, SqlConversionContext context) {
        // Pattern to detect potential implicit joins (entity.relationship.property)
        Pattern implicitJoinPattern = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b"
        );
        
        Matcher matcher = implicitJoinPattern.matcher(query);
        Set<String> processedJoins = new HashSet<>();
        StringBuilder additionalJoins = new StringBuilder();
        
        while (matcher.find()) {
            String sourceAlias = matcher.group(1);
            String relationshipProperty = matcher.group(2);
            String targetProperty = matcher.group(3);
            
            // Check if this relationship needs a JOIN
            JoinMapping joinMapping = context.entityMetadata().getJoinMapping(relationshipProperty);
            if (joinMapping != null && !processedJoins.contains(relationshipProperty)) {
                String implicitJoin = generateImplicitJoin(sourceAlias, relationshipProperty, joinMapping, context);
                if (implicitJoin != null) {
                    additionalJoins.append(" ").append(implicitJoin);
                    processedJoins.add(relationshipProperty);
                }
            }
        }
        
        // Insert additional JOINs after the FROM clause
        if (additionalJoins.length() > 0) {
            Pattern fromPattern = Pattern.compile("(FROM\\s+\\w+\\s+\\w+)", Pattern.CASE_INSENSITIVE);
            Matcher fromMatcher = fromPattern.matcher(query);
            if (fromMatcher.find()) {
                return fromMatcher.replaceFirst(fromMatcher.group(1) + additionalJoins.toString());
            }
        }
        
        return query;
    }
    
    /**
     * Generates an implicit JOIN clause from a relationship mapping.
     */
    private String generateImplicitJoin(String sourceAlias, String relationshipProperty, JoinMapping joinMapping, SqlConversionContext context) {
        String targetEntity = joinMapping.targetEntity();
        TableMapping targetTable = context.entityMetadata().getTableMapping(targetEntity);
        
        if (targetTable == null) {
            return null;
        }
        
        String joinTypeStr = convertJoinType(joinMapping.joinType());
        String targetAlias = relationshipProperty; // Use relationship property as alias
        String joinColumn = joinMapping.joinColumn();
        String referencedColumn = joinMapping.referencedColumn();
        
        return String.format("%s %s %s ON %s.%s = %s.%s",
                joinTypeStr,
                targetTable.tableName(),
                targetAlias,
                sourceAlias,
                referencedColumn,
                targetAlias,
                joinColumn);
    }
    
    /**
     * Converts JoinType enum to SQL JOIN string.
     */
    private String convertJoinType(JoinType joinType) {
        switch (joinType) {
            case INNER:
                return "INNER JOIN";
            case LEFT:
                return "LEFT JOIN";
            case RIGHT:
                return "RIGHT JOIN";
            case FULL:
                return "FULL OUTER JOIN";
            default:
                return "INNER JOIN"; // Default to INNER JOIN
        }
    }
    
    /**
     * Converts WHERE clause with proper column mapping.
     * Ensures that property references in conditions are properly mapped to columns.
     */
    private String convertWhereClause(String query, SqlConversionContext context) {
        // Pattern to match WHERE clause
        Pattern wherePattern = Pattern.compile(
            "(WHERE\\s+)(.+?)(?=\\s+(?:ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT|$))",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        
        Matcher matcher = wherePattern.matcher(query);
        if (matcher.find()) {
            String whereKeyword = matcher.group(1);
            String whereConditions = matcher.group(2);
            
            // Convert property references in WHERE conditions
            String convertedConditions = convertWhereConditions(whereConditions, context);
            
            return matcher.replaceFirst(whereKeyword + convertedConditions);
        }
        
        return query;
    }
    
    /**
     * Converts property references within WHERE conditions.
     */
    private String convertWhereConditions(String conditions, SqlConversionContext context) {
        // Pattern to match property references in conditions (more specific than general property pattern)
        Pattern conditionPropertyPattern = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\.([a-zA-Z_][a-zA-Z0-9_]*)\\b"
        );
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = conditionPropertyPattern.matcher(conditions);
        
        while (matcher.find()) {
            String alias = matcher.group(1);
            String property = matcher.group(2);
            
            // Find column mapping for the property
            String columnName = findColumnMapping(property, context);
            
            if (columnName != null) {
                String replacement = alias + "." + columnName;
                matcher.appendReplacement(result, replacement);
                logger.debug("Converted WHERE condition: {}.{} -> {}.{}", alias, property, alias, columnName);
            } else {
                // If no mapping found, keep original
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Finds column mapping for a given property name.
     * This is a simplified implementation - in practice, we'd need to track
     * entity-alias relationships to determine the correct mapping.
     */
    private String findColumnMapping(String propertyName, SqlConversionContext context) {
        EntityMetadata metadata = context.entityMetadata();
        
        // Try to find the property in any of the table mappings
        for (TableMapping tableMapping : metadata.getAllTableMappings()) {
            ColumnMapping columnMapping = tableMapping.getColumnMapping(propertyName);
            if (columnMapping != null) {
                return columnMapping.getColumnName();
            }
        }
        
        return null; // No mapping found
    }
    
    /**
     * Converts aggregate functions (COUNT, SUM, AVG, MIN, MAX) with proper column mapping.
     * Handles property references within aggregate function arguments.
     * 
     * Requirements addressed: 3.3
     */
    private String convertAggregateFunctions(String query, SqlConversionContext context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = AGGREGATE_FUNCTION_PATTERN.matcher(query);
        
        while (matcher.find()) {
            String functionName = matcher.group(1).toUpperCase();
            String argument = matcher.group(2).trim();
            
            // Convert property references in the argument
            String convertedArgument = convertAggregateArgument(argument, context);
            
            // Handle special cases for different aggregate functions
            String convertedFunction = convertSpecificAggregateFunction(functionName, convertedArgument, context);
            
            matcher.appendReplacement(result, convertedFunction);
            
            logger.debug("Converted aggregate function: {}({}) -> {}", 
                       functionName, argument, convertedFunction);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts property references within aggregate function arguments.
     */
    private String convertAggregateArgument(String argument, SqlConversionContext context) {
        // Handle special cases
        if ("*".equals(argument.trim())) {
            return "*"; // COUNT(*) remains as-is
        }
        
        if ("DISTINCT".equals(argument.trim().toUpperCase())) {
            return "DISTINCT"; // Handle DISTINCT keyword
        }
        
        // Handle DISTINCT with property reference
        if (argument.trim().toUpperCase().startsWith("DISTINCT ")) {
            String distinctPart = "DISTINCT ";
            String propertyPart = argument.trim().substring(9); // Remove "DISTINCT "
            String convertedProperty = convertPropertyReferencesInArgument(propertyPart, context);
            return distinctPart + convertedProperty;
        }
        
        // Convert regular property references
        return convertPropertyReferencesInArgument(argument, context);
    }
    
    /**
     * Converts property references within function arguments.
     */
    private String convertPropertyReferencesInArgument(String argument, SqlConversionContext context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PROPERTY_REFERENCE_PATTERN.matcher(argument);
        
        while (matcher.find()) {
            String alias = matcher.group(1);
            String property = matcher.group(2);
            
            // Find column mapping for the property
            String columnName = findColumnMapping(property, context);
            
            if (columnName != null) {
                String replacement = alias + "." + columnName;
                matcher.appendReplacement(result, replacement);
                logger.debug("Converted aggregate argument property: {}.{} -> {}.{}", 
                           alias, property, alias, columnName);
            } else {
                // If no mapping found, keep original
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Handles specific aggregate function conversions and dialect-specific transformations.
     */
    private String convertSpecificAggregateFunction(String functionName, String argument, SqlConversionContext context) {
        DatabaseDialect dialect = context.dialect();
        
        switch (functionName) {
            case "COUNT":
                return handleCountFunction(argument, dialect);
            case "SUM":
                return handleSumFunction(argument, dialect);
            case "AVG":
                return handleAvgFunction(argument, dialect);
            case "MIN":
                return handleMinFunction(argument, dialect);
            case "MAX":
                return handleMaxFunction(argument, dialect);
            default:
                return functionName + "(" + argument + ")";
        }
    }
    
    /**
     * Handles COUNT function conversion with dialect-specific considerations.
     */
    private String handleCountFunction(String argument, DatabaseDialect dialect) {
        // COUNT function is generally consistent across dialects
        return "COUNT(" + argument + ")";
    }
    
    /**
     * Handles SUM function conversion with dialect-specific considerations.
     */
    private String handleSumFunction(String argument, DatabaseDialect dialect) {
        // Handle potential null handling differences
        if (dialect == DatabaseDialect.ORACLE) {
            // Oracle SUM returns null for empty sets, which is standard behavior
            return "SUM(" + argument + ")";
        } else if (dialect == DatabaseDialect.POSTGRESQL) {
            // PostgreSQL SUM also returns null for empty sets
            return "SUM(" + argument + ")";
        }
        
        return "SUM(" + argument + ")";
    }
    
    /**
     * Handles AVG function conversion with dialect-specific considerations.
     */
    private String handleAvgFunction(String argument, DatabaseDialect dialect) {
        // AVG function behavior is generally consistent
        return "AVG(" + argument + ")";
    }
    
    /**
     * Handles MIN function conversion with dialect-specific considerations.
     */
    private String handleMinFunction(String argument, DatabaseDialect dialect) {
        // MIN function is consistent across dialects
        return "MIN(" + argument + ")";
    }
    
    /**
     * Handles MAX function conversion with dialect-specific considerations.
     */
    private String handleMaxFunction(String argument, DatabaseDialect dialect) {
        // MAX function is consistent across dialects
        return "MAX(" + argument + ")";
    }
    
    /**
     * Converts GROUP BY clause with proper column mapping.
     * Ensures that property references in GROUP BY are properly mapped to columns.
     * 
     * Requirements addressed: 3.3
     */
    private String convertGroupByClause(String query, SqlConversionContext context) {
        Matcher matcher = GROUP_BY_PATTERN.matcher(query);
        
        if (matcher.find()) {
            String groupByColumns = matcher.group(1);
            
            // Convert property references in GROUP BY columns
            String convertedColumns = convertGroupByColumns(groupByColumns, context);
            
            String replacement = "GROUP BY " + convertedColumns;
            return matcher.replaceFirst(replacement);
        }
        
        return query;
    }
    
    /**
     * Converts property references within GROUP BY columns.
     */
    private String convertGroupByColumns(String columns, SqlConversionContext context) {
        // Split by comma to handle multiple columns
        String[] columnArray = columns.split("\\s*,\\s*");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < columnArray.length; i++) {
            if (i > 0) {
                result.append(", ");
            }
            
            String column = columnArray[i].trim();
            
            // Check if it's a property reference (alias.property)
            Matcher propertyMatcher = PROPERTY_REFERENCE_PATTERN.matcher(column);
            if (propertyMatcher.matches()) {
                String alias = propertyMatcher.group(1);
                String property = propertyMatcher.group(2);
                
                // Find column mapping for the property
                String columnName = findColumnMapping(property, context);
                
                if (columnName != null) {
                    result.append(alias).append(".").append(columnName);
                    logger.debug("Converted GROUP BY column: {}.{} -> {}.{}", 
                               alias, property, alias, columnName);
                } else {
                    // If no mapping found, keep original
                    result.append(column);
                }
            } else {
                // Not a property reference, keep as-is (could be a column index or expression)
                result.append(column);
            }
        }
        
        return result.toString();
    }
    
    /**
     * Converts HAVING clause with proper column mapping.
     * Ensures that property references in HAVING conditions are properly mapped to columns.
     * 
     * Requirements addressed: 3.3
     */
    private String convertHavingClause(String query, SqlConversionContext context) {
        Matcher matcher = HAVING_PATTERN.matcher(query);
        
        if (matcher.find()) {
            String havingKeyword = matcher.group(1);
            String havingConditions = matcher.group(2);
            
            // Convert property references in HAVING conditions
            String convertedConditions = convertHavingConditions(havingConditions, context);
            
            return matcher.replaceFirst(havingKeyword + convertedConditions);
        }
        
        return query;
    }
    
    /**
     * Converts property references within HAVING conditions.
     * Similar to WHERE clause conversion but handles aggregate function contexts.
     */
    private String convertHavingConditions(String conditions, SqlConversionContext context) {
        // First, convert any aggregate functions in the HAVING clause
        String conditionsWithAggregates = convertAggregateFunctions(conditions, context);
        
        // Then convert regular property references (similar to WHERE clause conversion)
        StringBuffer result = new StringBuffer();
        Matcher matcher = PROPERTY_REFERENCE_PATTERN.matcher(conditionsWithAggregates);
        
        while (matcher.find()) {
            String alias = matcher.group(1);
            String property = matcher.group(2);
            
            // Find column mapping for the property
            String columnName = findColumnMapping(property, context);
            
            if (columnName != null) {
                String replacement = alias + "." + columnName;
                matcher.appendReplacement(result, replacement);
                logger.debug("Converted HAVING condition: {}.{} -> {}.{}", 
                           alias, property, alias, columnName);
            } else {
                // If no mapping found, keep original
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts subqueries (nested SELECT statements) with proper scoping and entity mapping.
     * Handles both correlated and non-correlated subqueries while maintaining proper
     * entity reference scoping.
     * 
     * Requirements addressed: 3.4
     */
    private String convertSubqueries(String query, SqlConversionContext context) {
        // Handle different types of subqueries in order of complexity
        
        // 1. Handle EXISTS subqueries first (they often contain correlated references)
        String queryWithExists = convertExistsSubqueries(query, context);
        
        // 2. Handle IN subqueries
        String queryWithIn = convertInSubqueries(queryWithExists, context);
        
        // 3. Handle general subqueries (in SELECT, FROM, etc.)
        String queryWithGeneral = convertGeneralSubqueries(queryWithIn, context);
        
        return queryWithGeneral;
    }
    
    /**
     * Converts EXISTS subqueries with proper correlation handling.
     */
    private String convertExistsSubqueries(String query, SqlConversionContext context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = EXISTS_SUBQUERY_PATTERN.matcher(query);
        
        while (matcher.find()) {
            String notKeyword = matcher.group(1) != null ? matcher.group(1) : "";
            String subqueryText = matcher.group(2);
            
            // Create a new context for the subquery with inherited scope
            SqlConversionContext subqueryContext = createSubqueryContext(context);
            
            // Convert the subquery recursively
            String convertedSubquery = convertSubqueryRecursively(subqueryText, subqueryContext);
            
            String replacement = notKeyword + "EXISTS (" + convertedSubquery + ")";
            matcher.appendReplacement(result, replacement);
            
            logger.debug("Converted EXISTS subquery: {} -> {}", subqueryText, convertedSubquery);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts IN subqueries with proper entity mapping.
     */
    private String convertInSubqueries(String query, SqlConversionContext context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = IN_SUBQUERY_PATTERN.matcher(query);
        
        while (matcher.find()) {
            String subqueryText = matcher.group(1);
            
            // Create a new context for the subquery
            SqlConversionContext subqueryContext = createSubqueryContext(context);
            
            // Convert the subquery recursively
            String convertedSubquery = convertSubqueryRecursively(subqueryText, subqueryContext);
            
            String replacement = "IN (" + convertedSubquery + ")";
            matcher.appendReplacement(result, replacement);
            
            logger.debug("Converted IN subquery: {} -> {}", subqueryText, convertedSubquery);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Converts general subqueries (in SELECT, FROM clauses, etc.).
     */
    private String convertGeneralSubqueries(String query, SqlConversionContext context) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = SUBQUERY_PATTERN.matcher(query);
        
        while (matcher.find()) {
            String subqueryText = matcher.group(1);
            
            // Skip if this subquery was already processed by EXISTS or IN handlers
            if (isAlreadyProcessedSubquery(subqueryText, query, matcher.start())) {
                matcher.appendReplacement(result, matcher.group(0));
                continue;
            }
            
            // Create a new context for the subquery
            SqlConversionContext subqueryContext = createSubqueryContext(context);
            
            // Convert the subquery recursively
            String convertedSubquery = convertSubqueryRecursively(subqueryText, subqueryContext);
            
            String replacement = "(" + convertedSubquery + ")";
            matcher.appendReplacement(result, replacement);
            
            logger.debug("Converted general subquery: {} -> {}", subqueryText, convertedSubquery);
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Checks if a subquery was already processed by EXISTS or IN handlers.
     */
    private boolean isAlreadyProcessedSubquery(String subqueryText, String fullQuery, int position) {
        // Look backwards from the current position to see if this is part of EXISTS or IN
        String beforeSubquery = fullQuery.substring(Math.max(0, position - 20), position);
        
        return beforeSubquery.toUpperCase().contains("EXISTS") || 
               beforeSubquery.toUpperCase().contains("IN");
    }
    
    /**
     * Creates a new conversion context for subquery processing.
     * Inherits entity metadata and dialect from parent context while maintaining
     * proper scoping for entity references.
     */
    private SqlConversionContext createSubqueryContext(SqlConversionContext parentContext) {
        // Create a new context that inherits from the parent but allows for
        // independent entity alias resolution within the subquery scope
        return new SqlConversionContext(
            parentContext.entityMetadata(),
            parentContext.dialect()
        );
    }
    
    /**
     * Recursively converts a subquery by applying the full conversion process.
     * This ensures that subqueries are processed with the same logic as main queries.
     */
    private String convertSubqueryRecursively(String subqueryText, SqlConversionContext context) {
        // Create a temporary QueryNode for the subquery
        QueryNode subqueryNode = createTemporaryQueryNode(subqueryText);
        
        // Apply the conversion process recursively (but avoid infinite recursion)
        String convertedSubquery = convertSubqueryInternal(subqueryText, context);
        
        return convertedSubquery;
    }
    
    /**
     * Internal method for converting subqueries without full recursive processing
     * to avoid infinite recursion.
     */
    private String convertSubqueryInternal(String subqueryText, SqlConversionContext context) {
        // Apply the same conversion steps as the main query, but without subquery processing
        // to avoid infinite recursion
        
        // Step 1: Convert SELECT statement
        String sqlWithSelect = convertSelectStatement(subqueryText, context);
        
        // Step 2: Convert entity references to table references
        String sqlWithTables = convertEntityReferences(sqlWithSelect, context);
        
        // Step 3: Convert property references to column references
        String sqlWithColumns = convertPropertyReferences(sqlWithTables, context);
        
        // Step 4: Process JOIN operations conversion
        String sqlWithJoins = convertJoinOperations(sqlWithColumns, context);
        
        // Step 5: Process WHERE clause conversion
        String sqlWithWhere = convertWhereClause(sqlWithJoins, context);
        
        // Step 6: Process aggregate functions
        String sqlWithAggregates = convertAggregateFunctions(sqlWithWhere, context);
        
        // Step 7: Process GROUP BY clause
        String sqlWithGroupBy = convertGroupByClause(sqlWithAggregates, context);
        
        // Step 8: Process HAVING clause
        String sqlWithHaving = convertHavingClause(sqlWithGroupBy, context);
        
        // Note: We skip subquery processing here to avoid infinite recursion
        // Note: We also skip parameter conversion as it will be done at the top level
        
        return sqlWithHaving;
    }
    
    /**
     * Creates a temporary QueryNode for subquery processing.
     */
    private QueryNode createTemporaryQueryNode(String queryText) {
        return new QueryNode() {
            private String text = queryText;
            private int type = 0;
            private QueryNode parent;
            private List<QueryNode> children = new java.util.ArrayList<>();
            
            @Override
            public int getType() { return type; }
            
            @Override
            public void setType(int type) { this.type = type; }
            
            @Override
            public String getText() { return text; }
            
            @Override
            public void setText(String text) { this.text = text; }
            
            @Override
            public int getLine() { return 0; }
            
            @Override
            public int getColumn() { return 0; }
            
            @Override
            public QueryNode getParent() { return parent; }
            
            @Override
            public void setParent(QueryNode parent) { this.parent = parent; }
            
            @Override
            public QueryNode getNextSibling() { return null; }
            
            @Override
            public QueryNode getFirstChild() { 
                return children.isEmpty() ? null : children.get(0); 
            }
            
            @Override
            public void addChild(QueryNode child) { 
                children.add(child);
                child.setParent(this);
            }
            
            @Override
            public boolean hasChildren() { return !children.isEmpty(); }
            
            @Override
            public int getNumberOfChildren() { return children.size(); }
            
            @Override
            public List<QueryNode> getChildren() { return new java.util.ArrayList<>(children); }
            
            @Override
            public QueryNode getChild(int index) { 
                return index < children.size() ? children.get(index) : null; 
            }
            
            @Override
            public void insertChild(int index, QueryNode child) { 
                children.add(index, child);
                child.setParent(this);
            }
            
            @Override
            public void removeChild(QueryNode child) { 
                children.remove(child);
                child.setParent(null);
            }
            
            @Override
            public void replaceChild(QueryNode oldChild, QueryNode newChild) { 
                int index = children.indexOf(oldChild);
                if (index >= 0) {
                    children.set(index, newChild);
                    oldChild.setParent(null);
                    newChild.setParent(this);
                }
            }
        };
    }
    
    /**
     * Handles correlated subqueries by maintaining proper entity reference scoping.
     * Ensures that references to outer query entities are preserved correctly.
     */
    private String handleCorrelatedSubquery(String subqueryText, SqlConversionContext context, 
                                          Map<String, String> outerAliases) {
        // Track which aliases refer to outer query entities vs subquery entities
        
        // First, identify aliases used in the subquery FROM clause (these are local)
        Set<String> localAliases = extractLocalAliases(subqueryText);
        
        // Convert the subquery, but preserve outer alias references
        String convertedSubquery = convertSubqueryWithCorrelation(subqueryText, context, 
                                                                 localAliases, outerAliases);
        
        return convertedSubquery;
    }
    
    /**
     * Extracts local aliases defined within the subquery.
     */
    private Set<String> extractLocalAliases(String subqueryText) {
        Set<String> localAliases = new HashSet<>();
        
        // Pattern to match FROM and JOIN clauses to find local aliases
        Pattern aliasPattern = Pattern.compile(
            "\\b(?:FROM|JOIN)\\s+\\w+\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = aliasPattern.matcher(subqueryText);
        while (matcher.find()) {
            localAliases.add(matcher.group(1));
        }
        
        return localAliases;
    }
    
    /**
     * Converts a subquery while handling correlation with outer query.
     */
    private String convertSubqueryWithCorrelation(String subqueryText, SqlConversionContext context,
                                                Set<String> localAliases, Map<String, String> outerAliases) {
        // This is a simplified implementation. In a full implementation, we would need
        // to track alias scoping more carefully and handle correlation properly.
        
        // For now, we'll use the standard conversion but be aware of the correlation context
        return convertSubqueryInternal(subqueryText, context);
    }
}
