package sa.com.cloudsolutions.antikythera.parser.converter;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolver that extracts entity metadata from JPA annotations.
 * 
 * This class analyzes JPA entity classes and builds EntityMetadata objects
 * containing mappings between entities and tables, properties and columns,
 * and relationship information for joins.
 */
public class EntityMappingResolver {
    
    private final Map<Class<?>, EntityMetadata> metadataCache = new ConcurrentHashMap<>();
    
    /**
     * Resolves entity metadata for the given entity class.
     * 
     * @param entityClass The JPA entity class to analyze
     * @return EntityMetadata containing all mapping information
     */
    public EntityMetadata resolveEntityMetadata(Class<?> entityClass) {
        return metadataCache.computeIfAbsent(entityClass, this::buildEntityMetadata);
    }
    
    /**
     * Resolves entity metadata for multiple entity classes.
     * 
     * @param entityClasses Collection of entity classes to analyze
     * @return Combined EntityMetadata for all entities
     */
    public EntityMetadata resolveEntityMetadata(Collection<Class<?>> entityClasses) {
        Map<String, TableMapping> entityToTableMappings = new HashMap<>();
        Map<String, ColumnMapping> propertyToColumnMappings = new HashMap<>();
        Map<String, JoinMapping> relationshipMappings = new HashMap<>();
        
        for (Class<?> entityClass : entityClasses) {
            EntityMetadata metadata = resolveEntityMetadata(entityClass);
            entityToTableMappings.putAll(metadata.getEntityToTableMappings());
            propertyToColumnMappings.putAll(metadata.getPropertyToColumnMappings());
            relationshipMappings.putAll(metadata.getRelationshipMappings());
        }
        
        return new EntityMetadata(entityToTableMappings, propertyToColumnMappings, relationshipMappings);
    }
    
    /**
     * Clears the metadata cache.
     */
    public void clearCache() {
        metadataCache.clear();
    }
    
    /**
     * Gets the size of the metadata cache.
     * 
     * @return The number of cached entity metadata objects
     */
    public int getCacheSize() {
        return metadataCache.size();
    }
    
    private EntityMetadata buildEntityMetadata(Class<?> entityClass) {
        if (!isJpaEntity(entityClass)) {
            return EntityMetadata.empty();
        }
        
        String entityName = getEntityName(entityClass);
        TableMapping tableMapping = buildTableMapping(entityClass, entityName);
        
        Map<String, TableMapping> entityToTableMappings = Map.of(entityName, tableMapping);
        Map<String, ColumnMapping> propertyToColumnMappings = buildPropertyToColumnMappings(entityClass, tableMapping);
        Map<String, JoinMapping> relationshipMappings = buildRelationshipMappings(entityClass, tableMapping);
        
        return new EntityMetadata(entityToTableMappings, propertyToColumnMappings, relationshipMappings);
    }
    
    private boolean isJpaEntity(Class<?> clazz) {
        return clazz.isAnnotationPresent(Entity.class);
    }
    
    private String getEntityName(Class<?> entityClass) {
        Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
        String name = entityAnnotation.name();
        return name.isEmpty() ? entityClass.getSimpleName() : name;
    }
    
    private TableMapping buildTableMapping(Class<?> entityClass, String entityName) {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        
        String tableName;
        String schema = null;
        
        if (tableAnnotation != null) {
            tableName = tableAnnotation.name().isEmpty() ? 
                convertCamelCaseToSnakeCase(entityName) : tableAnnotation.name();
            schema = tableAnnotation.schema().isEmpty() ? null : tableAnnotation.schema();
        } else {
            tableName = convertCamelCaseToSnakeCase(entityName);
        }
        
        Map<String, String> propertyToColumnMap = buildPropertyToColumnMap(entityClass);
        
        return new TableMapping(entityName, tableName, schema, propertyToColumnMap);
    }
    
    private Map<String, String> buildPropertyToColumnMap(Class<?> entityClass) {
        Map<String, String> propertyToColumnMap = new HashMap<>();
        
        for (Field field : getAllFields(entityClass)) {
            if (isTransientField(field)) {
                continue;
            }
            
            String propertyName = field.getName();
            String columnName = getColumnName(field);
            propertyToColumnMap.put(propertyName, columnName);
        }
        
        return propertyToColumnMap;
    }
    
    private Map<String, ColumnMapping> buildPropertyToColumnMappings(Class<?> entityClass, TableMapping tableMapping) {
        Map<String, ColumnMapping> columnMappings = new HashMap<>();
        
        for (Field field : getAllFields(entityClass)) {
            if (isTransientField(field) || isRelationshipField(field)) {
                continue;
            }
            
            String propertyName = field.getName();
            String columnName = getColumnName(field);
            String fullPropertyName = tableMapping.entityName() + "." + propertyName;
            
            ColumnMapping columnMapping = new ColumnMapping(
                fullPropertyName,
                columnName,
                tableMapping.tableName(),
                field.getType(),
                getSqlType(field.getType()),
                isNullable(field)
            );
            
            columnMappings.put(fullPropertyName, columnMapping);
        }
        
        return columnMappings;
    }
    
    private Map<String, JoinMapping> buildRelationshipMappings(Class<?> entityClass, TableMapping tableMapping) {
        Map<String, JoinMapping> joinMappings = new HashMap<>();
        
        for (Field field : getAllFields(entityClass)) {
            if (!isRelationshipField(field)) {
                continue;
            }
            
            JoinMapping joinMapping = buildJoinMapping(field, tableMapping);
            joinMappings.put(field.getName(), joinMapping);
        }
        
        return joinMappings;
    }
    
    private JoinMapping buildJoinMapping(Field field, TableMapping sourceTableMapping) {
        String propertyName = field.getName();
        Class<?> targetEntityClass = getTargetEntityClass(field);
        String targetEntityName = getEntityName(targetEntityClass);
        String targetTableName = getTableName(targetEntityClass);
        
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        String joinColumnName;
        String referencedColumnName = "id"; // Default assumption
        
        if (joinColumn != null) {
            joinColumnName = joinColumn.name().isEmpty() ? 
                convertCamelCaseToSnakeCase(propertyName) + "_id" : joinColumn.name();
            referencedColumnName = joinColumn.referencedColumnName().isEmpty() ? 
                "id" : joinColumn.referencedColumnName();
        } else {
            joinColumnName = convertCamelCaseToSnakeCase(propertyName) + "_id";
        }
        
        JoinType joinType = determineJoinType(field);
        
        return new JoinMapping(
            propertyName,
            targetEntityName,
            joinColumnName,
            referencedColumnName,
            joinType,
            sourceTableMapping.tableName(),
            targetTableName
        );
    }
    
    private Class<?> getTargetEntityClass(Field field) {
        if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
            return field.getType();
        } else if (field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)) {
            // For collections, we need to get the generic type
            if (Collection.class.isAssignableFrom(field.getType())) {
                java.lang.reflect.Type genericType = field.getGenericType();
                if (genericType instanceof java.lang.reflect.ParameterizedType paramType) {
                    return (Class<?>) paramType.getActualTypeArguments()[0];
                }
            }
        }
        return field.getType();
    }
    
    private JoinType determineJoinType(Field field) {
        // For now, default to LEFT join for optional relationships, INNER for required
        if (field.isAnnotationPresent(ManyToOne.class)) {
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
            return manyToOne.optional() ? JoinType.LEFT : JoinType.INNER;
        } else if (field.isAnnotationPresent(OneToOne.class)) {
            OneToOne oneToOne = field.getAnnotation(OneToOne.class);
            return oneToOne.optional() ? JoinType.LEFT : JoinType.INNER;
        }
        // OneToMany and ManyToMany are typically LEFT joins
        return JoinType.LEFT;
    }
    
    private String getTableName(Class<?> entityClass) {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            return tableAnnotation.name();
        }
        return convertCamelCaseToSnakeCase(getEntityName(entityClass));
    }
    
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }
    
    private boolean isTransientField(Field field) {
        return field.isAnnotationPresent(Transient.class) ||
               java.lang.reflect.Modifier.isTransient(field.getModifiers()) ||
               java.lang.reflect.Modifier.isStatic(field.getModifiers());
    }
    
    private boolean isRelationshipField(Field field) {
        return field.isAnnotationPresent(OneToOne.class) ||
               field.isAnnotationPresent(OneToMany.class) ||
               field.isAnnotationPresent(ManyToOne.class) ||
               field.isAnnotationPresent(ManyToMany.class);
    }
    
    private String getColumnName(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            return columnAnnotation.name();
        }
        return convertCamelCaseToSnakeCase(field.getName());
    }
    
    private boolean isNullable(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null) {
            return columnAnnotation.nullable();
        }
        // Default to nullable unless it's an ID field
        return !field.isAnnotationPresent(Id.class);
    }
    
    private String getSqlType(Class<?> javaType) {
        if (javaType == String.class) {
            return "VARCHAR";
        } else if (javaType == Integer.class || javaType == int.class) {
            return "INTEGER";
        } else if (javaType == Long.class || javaType == long.class) {
            return "BIGINT";
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return "BOOLEAN";
        } else if (javaType == Double.class || javaType == double.class) {
            return "DOUBLE";
        } else if (javaType == Float.class || javaType == float.class) {
            return "FLOAT";
        } else if (javaType == java.util.Date.class || javaType == java.sql.Date.class) {
            return "DATE";
        } else if (javaType == java.sql.Timestamp.class) {
            return "TIMESTAMP";
        } else if (javaType == java.math.BigDecimal.class) {
            return "DECIMAL";
        }
        return "VARCHAR"; // Default fallback
    }
    
    private String convertCamelCaseToSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(camelCase.charAt(0)));
        
        for (int i = 1; i < camelCase.length(); i++) {
            char ch = camelCase.charAt(i);
            if (Character.isUpperCase(ch)) {
                result.append('_');
                result.append(Character.toLowerCase(ch));
            } else {
                result.append(ch);
            }
        }
        
        return result.toString();
    }
}
