package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.raditha.hql.parser.HQLParser;
import com.raditha.hql.parser.ParseException;
import com.raditha.hql.model.MetaData;
import com.raditha.hql.converter.HQLToPostgreSQLConverter;
import com.raditha.hql.converter.ConversionException;
import com.raditha.hql.converter.JoinMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.*;

/**
 * Adapter that bridges antikythera's converter interface with raditha's hql-parser.
 * Replaces the mock implementation in HibernateQueryConverter with real ANTLR4-based parsing.
 */
public class HQLParserAdapter  {
    
    private static final Logger logger = LoggerFactory.getLogger(HQLParserAdapter.class);
    private final CompilationUnit cu;
    private final HQLParser hqlParser;
    private final HQLToPostgreSQLConverter sqlConverter;
    private final Set<DatabaseDialect> supportedDialects;
    TypeWrapper entity;

    public HQLParserAdapter(CompilationUnit cu, TypeWrapper entity) {
        this.hqlParser = new HQLParser();
        this.sqlConverter = new HQLToPostgreSQLConverter();
        this.supportedDialects = EnumSet.of(DatabaseDialect.POSTGRESQL);
        this.cu = cu;
        this.entity = entity;
    }

    /**
     * Converts a JPA/HQL query to native SQL.
     *
     * @param jpaQuery The original JPA/HQL query string to convert
     * @return ConversionResult containing the native SQL and conversion metadata
     * @throws QueryConversionException if the conversion fails
     */
    public ConversionResult convertToNativeSQL(String jpaQuery) throws ParseException, ConversionException {
        MetaData analysis = hqlParser.analyze(jpaQuery);

        Set<String> referencedTables = registerMappings(analysis);
        String nativeSql = sqlConverter.convert(jpaQuery, analysis);

        List<ParameterMapping> parameterMappings = extractParameterMappings(analysis);
        ConversionResult result = new ConversionResult(nativeSql, parameterMappings, referencedTables);
        result.setMetaData(analysis);
        return result;
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
    private Set<String> registerMappings(MetaData analysis) {
        Set<String> tables = new HashSet<>();
        Map<String, Map<String, JoinMapping>> relationshipMetadata = new HashMap<>();

        for (String name : analysis.getEntityNames()) {
            TypeWrapper typeWrapper = AbstractCompiler.findType(cu, name);
            String fullName = null;
            if (typeWrapper == null) {
                fullName = getEntiyNameForEntity(name);
            }
            else {
                fullName = typeWrapper.getFullyQualifiedName();
            }

            EntityMetadata meta = EntityMappingResolver.getMapping().get(fullName);
            if (meta == null) {
                logger.warn("No metadata found for entity: {} (fullName: {})", name, fullName);
                continue;
            }
            
            tables.add(meta.tableName());
            sqlConverter.registerEntityMapping(name, meta.tableName());

            if (meta.propertyToColumnMap() != null) {
                for (var entry : meta.propertyToColumnMap().entrySet()) {
                    String propertyName = entry.getKey();
                    String columnName = entry.getValue();
                    sqlConverter.registerFieldMapping(
                            name,
                            propertyName,
                            columnName
                    );
                }
            }
            
            // Collect relationship mappings for implicit join ON clause generation
            if (meta.relationshipMap() != null && !meta.relationshipMap().isEmpty()) {
                Map<String, JoinMapping> propertyMappings = new HashMap<>();
                for (var entry : meta.relationshipMap().entrySet()) {
                    propertyMappings.put(entry.getKey(), entry.getValue());
                    // Also register the joined entity's table mapping
                    JoinMapping joinMapping = entry.getValue();
                    sqlConverter.registerEntityMapping(
                            joinMapping.targetEntity(),
                            joinMapping.targetTable()
                    );
                    logger.debug("Registered join entity mapping: {} -> {}",
                            joinMapping.targetEntity(), joinMapping.targetTable());
                }
                // Use entity name as it appears in HQL (not FQN) as the key
                relationshipMetadata.put(name, propertyMappings);
            }
        }
        
        // Pass relationship metadata to converter for implicit join ON clause generation
        sqlConverter.setRelationshipMetadata(relationshipMetadata);
        
        return tables;
    }

    String getEntiyNameForEntity(String name) {
        if (name.equals(entity.getName()) || name.equals(entity.getFullyQualifiedName())) {
            return entity.getFullyQualifiedName();
        }
        Optional<String> n = EntityMappingResolver.getFullNamesForEntity(name).stream().findFirst();
        if (n.isPresent()) {
            return n.stream().findFirst().get();
        }

        if (entity.getClazz() == null) {
            for (FieldDeclaration f : entity.getType().getFields()) {
                for (TypeWrapper tw : AbstractCompiler.findTypesInVariable(f.getVariable(0))) {
                    if (tw.getFullyQualifiedName().equals(name) || tw.getName().equals(name)) {
                        return tw.getFullyQualifiedName();
                    }
                }
            }
        }
        else if (entity.getClazz().getName().equals(name)) {
            return entity.getFullyQualifiedName();
        }
        return null;
    }
    /**
     * Extracts parameter mappings from the query analysis.
     */
    private List<ParameterMapping> extractParameterMappings(MetaData analysis) {
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
}
