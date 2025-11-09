package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Resolver that extracts entity metadata from JPA annotations.
 * 
 * This class analyzes JPA entity classes and builds EntityMetadata objects
 * containing mappings between entities and tables, properties and columns,
 * and relationship information for joins.
 */
public class EntityMappingResolver {
    
    private static final Map<String, EntityMetadata> mapping = new HashMap<>();

    public static void build() throws ReflectiveOperationException {
        for (TypeWrapper type : AntikytheraRunTime.getResolvedTypes().values()) {
            TypeDeclaration<?> typeDecl = type.getType();
            if (typeDecl != null && typeDecl.getAnnotationByName("Entity").isPresent()) {
                mapping.put(type.getFullyQualifiedName(), buildMetadataFromSources(typeDecl));
            }
            else {
                mapping.put(type.getFullyQualifiedName(), buildEntityMetadata(type.getClazz()));
            }
        }
    }

    /**
     * Builds property to column map from TypeDeclaration.
     */
    private static Map<String, String> buildPropertyToColumnMapFromAST(TypeDeclaration<?> typeDecl)  {
        Map<String, String> propertyToColumnMap = new HashMap<>();

        // Get all fields from the entity
        for (FieldDeclaration field : typeDecl.getFields()) {
            if (!isTransientFieldFromAST(field)) {
                for (VariableDeclarator variable : field.getVariables()) {
                    String propertyName = variable.getNameAsString();
                    String columnName = getColumnNameFromAST(field);
                    if (columnName == null) {
                        // Default: convert camelCase to snake_case
                        columnName = BaseRepositoryParser.camelToSnake(propertyName);
                    }
                    propertyToColumnMap.put(propertyName, columnName);
                }
            }
        }

        return propertyToColumnMap;
    }

    /**
     * Checks if a field is transient (should be skipped).
     */
    private static boolean isTransientFieldFromAST(com.github.javaparser.ast.body.FieldDeclaration field) {
        return field.isTransient() || field.isStatic() || field.getAnnotationByName("Transient").isPresent();
    }

    /**
     * Checks if a field is a relationship field (JPA relationships).
     */
    private static boolean isRelationshipFieldFromAST(com.github.javaparser.ast.body.FieldDeclaration field) {
        return field.getAnnotationByName("OneToOne").isPresent() ||
                field.getAnnotationByName("OneToMany").isPresent() ||
                field.getAnnotationByName("ManyToOne").isPresent() ||
                field.getAnnotationByName("ManyToMany").isPresent();
    }


    /**
     * Gets column name from @Column annotation or returns null.
     */
    private static String getColumnNameFromAST(com.github.javaparser.ast.body.FieldDeclaration field)  {
        Optional<com.github.javaparser.ast.expr.AnnotationExpr> columnAnn =
                field.getAnnotationByName("Column");

        if (columnAnn.isPresent()) {
            Map<String, Expression> attributes = AbstractCompiler.extractAnnotationAttributes(columnAnn.get());
            Expression name = attributes.get("name");
            return name.toString().replace("\"", ""); // Remove quotes
        }
        return null; // No @Column annotation or no name specified
    }


    /**
     * Builds entity metadata using Antikythera's TypeWrapper and AbstractCompiler.
     * This avoids reflection and works directly with parsed source code.
     *
     * @return EntityMetadata from a parsed source, or null if not available
     */
    private static EntityMetadata buildMetadataFromSources(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        // Extract entity information using AbstractCompiler helpers
        String entityName = getEntityName(typeDecl);
        String tableName = getTableName(typeDecl);
        String discriminatorColumn = getDiscriminatorColumn(typeDecl);
        String discriminatorValue = getDiscriminatorValue(typeDecl);
        String inheritanceType = getInheritanceStrategy(typeDecl);

        // Build property to column map
        Map<String, String> propertyToColumnMap = buildPropertyToColumnMapFromAST(typeDecl);

        // For now, no parent table support (could be added later)
        TableMapping tableMapping = new TableMapping(
                entityName, tableName, null, propertyToColumnMap,
                discriminatorColumn, discriminatorValue, inheritanceType, null
        );

        Map<String, TableMapping> entityToTableMappings = Map.of(entityName, tableMapping);

        // Build property to column mappings
        Map<String, String> propertyToColumnMappings = buildPropertyToColumnMapFromAST(typeDecl);

        // Relationship mappings not yet implemented for AST-based approach
        Map<String, sa.com.cloudsolutions.antikythera.parser.converter.JoinMapping> relationshipMappings =
                new HashMap<>();

        return new EntityMetadata(entityToTableMappings, propertyToColumnMappings, relationshipMappings);
    }


    private static EntityMetadata buildEntityMetadata(Class<?> entityClass) {
        if (!isJpaEntity(entityClass)) {
            return EntityMetadata.empty();
        }
        
        String entityName = getEntityName(entityClass);
        TableMapping tableMapping = buildTableMapping(entityClass, entityName);
        
        Map<String, TableMapping> entityToTableMappings = Map.of(entityName, tableMapping);
        Map<String, String> propertyToColumnMappings = buildPropertyToColumnMappings(entityClass, tableMapping);
        Map<String, JoinMapping> relationshipMappings = buildRelationshipMappings(entityClass, tableMapping);
        
        return new EntityMetadata(entityToTableMappings, propertyToColumnMappings, relationshipMappings);
    }


    /**
     * Gets the discriminator value from @DiscriminatorValue.
     *
     * @param typeDecl The entity type declaration
     * @return Discriminator value, or null if not specified
     */
    public static String getDiscriminatorValue(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Optional<AnnotationExpr> discAnn = typeDecl.getAnnotationByName("DiscriminatorValue");

        if (discAnn.isPresent()) {
            String s = getAnnotationValue(discAnn.get(), "name");
            if (s != null) {
                return s;
            }
        }

        // Default: entity name
        return getEntityName(typeDecl);
    }

    /**
     * Gets the inheritance strategy from @Inheritance annotation.
     *
     * @param typeDecl The entity type declaration
     * @return Inheritance strategy ("SINGLE_TABLE", "JOINED", "TABLE_PER_CLASS"), or null
     */
    public static String getInheritanceStrategy(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Optional<AnnotationExpr> inhAnn = typeDecl.getAnnotationByName("Inheritance");

        if (inhAnn.isPresent()) {
            String strategy = getAnnotationValue(inhAnn.get(), "name");

            if (strategy != null) {
                // Extract enum value: InheritanceType.SINGLE_TABLE -> SINGLE_TABLE
                if (strategy.contains(".")) {
                    return strategy.substring(strategy.lastIndexOf('.') + 1);
                }
                return strategy;
            }
        }

        return null; // No inheritance specified
    }


    static String  getAnnotationValue(AnnotationExpr expr, String name)  {
        Map<String, Expression> attributes = AbstractCompiler.extractAnnotationAttributes(expr);
        Expression value = attributes.get(name);
        return value.toString().replace("\"", ""); // Remove quotes
    }

    /**
     * Gets the table name from @Table annotation or derives from entity name.
     *
     * @param typeDecl The entity type declaration
     * @return Table name
     */
    public static String getTableName(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Optional<AnnotationExpr> tableAnn = typeDecl.getAnnotationByName("Table");

        if (tableAnn.isPresent()) {
            String s = getAnnotationValue(tableAnn.get(), "name");
            if (s != null) {
                return s;
            }
        }

        // Default: convert entity name to snake_case
        return BaseRepositoryParser.camelToSnakeCase(typeDecl.getNameAsString());
    }

    /**
     * Gets the entity name from @Entity annotation or class name.
     *
     * @param typeDecl The entity type declaration
     * @return Entity name
     */
    public static String getEntityName(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Optional<AnnotationExpr> entityAnn = typeDecl.getAnnotationByName("Entity");

        if (entityAnn.isPresent()) {
            String s = getAnnotationValue(entityAnn.get(), "name");
            if (s != null) {
                return s;
            }
        }

        return typeDecl.getNameAsString();
    }

    /**
     * Gets the discriminator column name from @DiscriminatorColumn.
     *
     * @param typeDecl The entity type declaration
     * @return Discriminator column name, or "dtype" if not specified
     */
    public static String getDiscriminatorColumn(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Optional<AnnotationExpr> discAnn = typeDecl.getAnnotationByName("DiscriminatorColumn");

        if (discAnn.isPresent()) {
            String s = getAnnotationValue(discAnn.get(), "name");
            if (s != null) {
                return s;
            }
        }

        return "dtype"; // Default discriminator column
    }


    private static boolean isJpaEntity(Class<?> clazz) {
        return clazz.isAnnotationPresent(Entity.class);
    }
    
    private static String getEntityName(Class<?> entityClass) {
        Entity entityAnnotation = entityClass.getAnnotation(Entity.class);
        String name = entityAnnotation.name();
        return name.isEmpty() ? entityClass.getSimpleName() : name;
    }
    
    private static TableMapping buildTableMapping(Class<?> entityClass, String entityName) {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        
        String tableName;
        String schema = null;
        
        if (tableAnnotation != null) {
            tableName = tableAnnotation.name().isEmpty() ?
                 AbstractCompiler.camelToSnakeCase(entityName) : tableAnnotation.name();
            schema = tableAnnotation.schema().isEmpty() ? null : tableAnnotation.schema();
        } else {
            tableName = AbstractCompiler.camelToSnakeCase(entityName);
        }
        
        Map<String, String> propertyToColumnMap = buildPropertyToColumnMap(entityClass);
        
        // Extract inheritance information
        String discriminatorColumn = null;
        String discriminatorValue = null;
        String inheritanceType = null;
        TableMapping parentTable = null;
        
        Inheritance inheritanceAnnotation = entityClass.getAnnotation(Inheritance.class);
        if (inheritanceAnnotation != null) {
            inheritanceType = inheritanceAnnotation.strategy().name();
        }
        
        DiscriminatorColumn discriminatorColumnAnnotation = 
            entityClass.getAnnotation(DiscriminatorColumn.class);
        if (discriminatorColumnAnnotation != null) {
            discriminatorColumn = discriminatorColumnAnnotation.name();
            if (discriminatorColumn.isEmpty()) {
                discriminatorColumn = "dtype"; // Default
            }
        } else if (inheritanceType != null && "SINGLE_TABLE".equals(inheritanceType)) {
            discriminatorColumn = "dtype"; // Default for SINGLE_TABLE
        }
        
        DiscriminatorValue discriminatorValueAnnotation = 
            entityClass.getAnnotation(DiscriminatorValue.class);
        if (discriminatorValueAnnotation != null) {
            discriminatorValue = discriminatorValueAnnotation.value();
        }
        
        // For JOINED strategy, build parent table mapping if exists
        if ("JOINED".equals(inheritanceType)) {
            Class<?> superclass = entityClass.getSuperclass();
            if (superclass != null && superclass.isAnnotationPresent(Entity.class)) {
                String parentEntityName = getEntityName(superclass);
                parentTable = buildTableMapping(superclass, parentEntityName);
            }
        }
        
        return new TableMapping(entityName, tableName, schema, propertyToColumnMap,
                                discriminatorColumn, discriminatorValue, 
                                inheritanceType, parentTable);
    }
    
    private static Map<String, String> buildPropertyToColumnMap(Class<?> entityClass) {
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
    
    private static Map<String, String> buildPropertyToColumnMappings(Class<?> entityClass, TableMapping tableMapping) {
        Map<String, String> columnMappings = new HashMap<>();
        
        for (Field field : getAllFields(entityClass)) {
            if (isTransientField(field) || isRelationshipField(field)) {
                continue;
            }
            
            String propertyName = field.getName();
            String columnName = getColumnName(field);
            String fullPropertyName = tableMapping.entityName() + "." + propertyName;

            columnMappings.put(fullPropertyName, columnName);
        }
        
        return columnMappings;
    }
    
    private static Map<String, JoinMapping> buildRelationshipMappings(Class<?> entityClass, TableMapping tableMapping) {
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
    
    private static JoinMapping buildJoinMapping(Field field, TableMapping sourceTableMapping) {
        String propertyName = field.getName();
        Class<?> targetEntityClass = getTargetEntityClass(field);
        String targetEntityName = getEntityName(targetEntityClass);
        String targetTableName = getTableName(targetEntityClass);
        
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
        String joinColumnName;
        String referencedColumnName = "id"; // Default assumption
        
        if (joinColumn != null) {
            joinColumnName = joinColumn.name().isEmpty() ?
                AbstractCompiler.camelToSnakeCase(propertyName) + "_id" : joinColumn.name();
            referencedColumnName = joinColumn.referencedColumnName().isEmpty() ? 
                "id" : joinColumn.referencedColumnName();
        } else {
            joinColumnName = AbstractCompiler.camelToSnakeCase(propertyName) + "_id";
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
    
    private static Class<?> getTargetEntityClass(Field field) {
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
    
    private static JoinType determineJoinType(Field field) {
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
    
    private static String getTableName(Class<?> entityClass) {
        Table tableAnnotation = entityClass.getAnnotation(Table.class);
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            return tableAnnotation.name();
        }
        return AbstractCompiler.camelToSnakeCase(getEntityName(entityClass));
    }
    
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;
        
        while (currentClass != null && currentClass != Object.class) {
            fields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }
    
    private static boolean isTransientField(Field field) {
        return field.isAnnotationPresent(Transient.class) ||
               java.lang.reflect.Modifier.isTransient(field.getModifiers()) ||
               java.lang.reflect.Modifier.isStatic(field.getModifiers());
    }
    
    private static boolean isRelationshipField(Field field) {
        return field.isAnnotationPresent(OneToOne.class) ||
               field.isAnnotationPresent(OneToMany.class) ||
               field.isAnnotationPresent(ManyToOne.class) ||
               field.isAnnotationPresent(ManyToMany.class);
    }
    
    private static String getColumnName(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
            return columnAnnotation.name();
        }
        return AbstractCompiler.camelToSnakeCase(field.getName());
    }

    public static Map<String, EntityMetadata> getMapping() {
        return mapping;
    }
}
