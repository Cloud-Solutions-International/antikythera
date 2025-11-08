package sa.com.cloudsolutions.antikythera.parser.converter;

import com.raditha.hql.parser.HQLParser;
import com.raditha.hql.parser.ParseException;
import com.raditha.hql.model.QueryAnalysis;
import com.raditha.hql.converter.HQLToPostgreSQLConverter;
import com.raditha.hql.converter.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Adapter that bridges antikythera's converter interface with raditha's hql-parser.
 * Replaces the mock implementation in HibernateQueryConverter with real ANTLR4-based parsing.
 */
public class HQLParserAdapter  {
    
    private static final Logger logger = LoggerFactory.getLogger(HQLParserAdapter.class);
    
    private final HQLParser hqlParser;
    private final HQLToPostgreSQLConverter sqlConverter;
    private final Set<DatabaseDialect> supportedDialects;
    
    public HQLParserAdapter() {
        this.hqlParser = new HQLParser();
        this.sqlConverter = new HQLToPostgreSQLConverter();
        this.supportedDialects = EnumSet.of(DatabaseDialect.POSTGRESQL);
        
        logger.info("HQLParserAdapter initialized with ANTLR4-based parser");
    }

    /**
     * Converts a JPA/HQL query to native SQL.
     *
     * @param jpaQuery The original JPA/HQL query string to convert
     * @param entityMetadata Metadata about entities involved in the query
     * @return ConversionResult containing the native SQL and conversion metadata
     * @throws QueryConversionException if the conversion fails
     */
    public ConversionResult convertToNativeSQL(String jpaQuery, EntityMetadata entityMetadata) throws ParseException, ConversionException {
        // Step 1: Analyze the HQL query using hql-parser
        QueryAnalysis analysis = hqlParser.analyze(jpaQuery);

        // Step 2: Register entity and field mappings from EntityMetadata
        registerMappings(entityMetadata, analysis);

        // Step 3: Convert to PostgreSQL using hql-parser converter
        String nativeSql = sqlConverter.convert(jpaQuery);

        // Step 4: Extract parameter mappings
        List<ParameterMapping> parameterMappings = extractParameterMappings(analysis);

        // Step 5: Get referenced tables
        Set<String> referencedTables = extractReferencedTables(entityMetadata, analysis);

        return new ConversionResult(nativeSql, parameterMappings, referencedTables);
    }


    /**
     * Validates if a query can be converted by this converter.
     *
     * @param jpaQuery The JPA/HQL query to validate
     * @return true if the query can be converted, false otherwise
     */
    public boolean canConvert(String jpaQuery) {
        if (jpaQuery == null || jpaQuery.trim().isEmpty()) {
            return false;
        }
        
        // Use hql-parser's validation
        return hqlParser.isValid(jpaQuery);
    }

    /**
     * Checks if the converter supports the specified database dialect.
     *
     * @param dialect The database dialect to check
     * @return true if the dialect is supported, false otherwise
     */
    public boolean supportsDialect(DatabaseDialect dialect) {
        return supportedDialects.contains(dialect);
    }
    
    /**
     * Registers entity and field mappings from EntityMetadata into the hql-parser converter.
     */
    private void registerMappings(EntityMetadata entityMetadata, QueryAnalysis analysis) {
        // Register entity-to-table mappings
        for (TableMapping tableMapping : entityMetadata.getAllTableMappings()) {
            String entityName = tableMapping.entityName();
            String tableName = tableMapping.tableName();
            
            if (entityName != null && tableName != null) {
                sqlConverter.registerEntityMapping(entityName, tableName);
                logger.debug("Registered entity mapping: {} -> {}", entityName, tableName);
                
                // Register field-to-column mappings for this entity
                if (tableMapping.propertyToColumnMap() != null) {
                    for (var entry : tableMapping.propertyToColumnMap().entrySet()) {
                        String propertyName = entry.getKey();
                        String columnName = entry.getValue();
                        sqlConverter.registerFieldMapping(
                            entityName,
                            propertyName,
                            columnName
                        );
                        logger.debug("Registered field mapping: {}.{} -> {}",
                            entityName, propertyName, columnName);
                    }
                }
            }
        }
        
        // Register mappings for joined entities (if any)
        for (JoinMapping joinMapping : entityMetadata.relationshipMappings().values()) {
            // Register the joined entity's table mapping
            sqlConverter.registerEntityMapping(
                joinMapping.targetEntity(),
                joinMapping.targetTable()
            );
            logger.debug("Registered join entity mapping: {} -> {}",
                joinMapping.targetEntity(), joinMapping.targetTable());
        }
    }
    
    /**
     * Extracts parameter mappings from the query analysis.
     */
    private List<ParameterMapping> extractParameterMappings(QueryAnalysis analysis) {
        List<ParameterMapping> mappings = new ArrayList<>();
        
        // hql-parser provides parameter names from the query
        Set<String> parameters = analysis.getParameters();
        
        int position = 1;
        for (String paramName : parameters) {
            // ParameterMapping requires: originalName, position, type, columnName
            ParameterMapping mapping = new ParameterMapping(paramName, position++, Object.class, null);
            mappings.add(mapping);
        }
        
        return mappings;
    }
    
    /**
     * Extracts referenced table names from entity metadata and query analysis.
     */
    private Set<String> extractReferencedTables(EntityMetadata entityMetadata, QueryAnalysis analysis) {
        Set<String> tables = new HashSet<>();
        
        // Add all tables from entity-to-table mappings
        for (TableMapping tableMapping : entityMetadata.getAllTableMappings()) {
            if (tableMapping.tableName() != null) {
                tables.add(tableMapping.tableName());
            }
        }
        
        // Add joined tables
        for (JoinMapping join : entityMetadata.relationshipMappings().values()) {
            if (join.targetTable() != null) {
                tables.add(join.targetTable());
            }
        }
        
        return tables;
    }
}
