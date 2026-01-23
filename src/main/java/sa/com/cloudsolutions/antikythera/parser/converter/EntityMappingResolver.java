package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;
import com.raditha.hql.converter.JoinType;
import com.raditha.hql.converter.JoinMapping;

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
    private static final Map<String, Set<String>> shortNames = new HashMap<>();
    public static final String DTYPE = "dtype";

    /**
     * Private constructor to prevent instantiation.
     */
    private EntityMappingResolver() {
    }

    /**
     * Reset the entity mapping resolver, clearing all cached mappings.
     * This is useful for tests that need to rebuild the mappings.
     */
    public static void reset() {
        mapping.clear();
        shortNames.clear();
    }

    public static void build() {
        if (!mapping.isEmpty()) {
            throw new AntikytheraException("Already built");
        }
        for (TypeWrapper type : AntikytheraRunTime.getResolvedTypes().values()) {
            buildOnTheFly(type);
        }
    }

    /**
     * Builds entity metadata for a type on the fly.
     * This is useful for entities found in JARs that were not part of the initial
     * scan.
     *
     * @param type the type wrapper for the entity
     * @return the built EntityMetadata, or null if it's not an entity
     */
    public static EntityMetadata buildOnTheFly(TypeWrapper type) {
        TypeDeclaration<?> typeDecl = type.getType();
        String fullyQualifiedName = type.getFullyQualifiedName();
        String name = type.getName();

        if (fullyQualifiedName == null) {
            return null;
        }

        if (mapping.containsKey(fullyQualifiedName)) {
            return mapping.get(fullyQualifiedName);
        }

        shortNames.computeIfAbsent(name, k -> new HashSet<>()).add(fullyQualifiedName);

        EntityMetadata meta = null;
        if (typeDecl != null && typeDecl.getAnnotationByName("Entity").isPresent()) {
            meta = buildMetadataFromSources(typeDecl);
        } else if (type.getClazz() != null && type.getClazz().isAnnotationPresent(Entity.class)) {
            meta = buildEntityMetadata(type.getClazz());
        }

        if (meta != null) {
            mapping.put(fullyQualifiedName, meta);
        }
        return meta;
    }

    /**
     * Builds property to column map from TypeDeclaration.
     */
    private static Map<String, String> buildPropertyToColumnMapFromAST(TypeDeclaration<?> typeDecl) {
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
     * Gets column name from @Column annotation or returns null.
     */
    private static String getColumnNameFromAST(FieldDeclaration field) {
        Optional<com.github.javaparser.ast.expr.AnnotationExpr> columnAnn = field.getAnnotationByName("Column");

        if (columnAnn.isPresent()) {
            Map<String, Expression> attributes = AbstractCompiler.extractAnnotationAttributes(columnAnn.get());
            Expression name = attributes.get("name");
            if (name != null) {
                return name.toString().replace("\"", ""); // Remove quotes
            }
        }
        return null; // No @Column annotation or no name specified
    }

    /**
     * Builds entity metadata using Antikythera's TypeWrapper and AbstractCompiler.
     * This avoids reflection and works directly with parsed source code.
     *
     * @return EntityMetadata from a parsed source, or null if not available
     */
    private static EntityMetadata buildMetadataFromSources(TypeDeclaration<?> typeDecl) {
        String tableName = getTableName(typeDecl);
        Map<String, String> propertyToColumnMappings = buildPropertyToColumnMapFromAST(typeDecl);

        // Relationship mappings not yet implemented for AST-based approach
        Map<String, JoinMapping> relationshipMappings = new HashMap<>();

        return new EntityMetadata(TypeWrapper.fromTypeDeclaration(typeDecl), tableName,
                propertyToColumnMappings, relationshipMappings);
    }

    private static EntityMetadata buildEntityMetadata(Class<?> entityClass) {
        String entityName = getEntityName(entityClass);
        TableMapping tableMapping = buildTableMapping(entityClass, entityName);

        Map<String, String> propertyToColumnMappings = buildPropertyToColumnMappings(entityClass);
        Map<String, JoinMapping> relationshipMappings = buildRelationshipMappings(entityClass, tableMapping);

        return new EntityMetadata(TypeWrapper.fromClass(entityClass), getTableName(entityClass),
                propertyToColumnMappings, relationshipMappings);
    }

    /**
     * Gets the discriminator value from @DiscriminatorValue.
     *
     * @param typeDecl The entity type declaration
     * @return Discriminator value, or null if not specified
     */
    public static String getDiscriminatorValue(TypeDeclaration<?> typeDecl) {
        Optional<AnnotationExpr> discAnn = typeDecl.getAnnotationByName("DiscriminatorValue");

        if (discAnn.isPresent()) {
            Optional<String> s = getAnnotationValue(discAnn.get(), "name");
            if (s.isPresent()) {
                return s.get();
            }
        }

        // Default: entity name
        return getEntityName(typeDecl);
    }

    /**
     * Gets the inheritance strategy from @Inheritance annotation.
     *
     * @param typeDecl The entity type declaration
     * @return Inheritance strategy ("SINGLE_TABLE", "JOINED", "TABLE_PER_CLASS"),
     *         or null
     */
    public static String getInheritanceStrategy(TypeDeclaration<?> typeDecl) {
        Optional<AnnotationExpr> inhAnn = typeDecl.getAnnotationByName("Inheritance");

        if (inhAnn.isPresent()) {
            Optional<String> strategy = getAnnotationValue(inhAnn.get(), "name");

            if (strategy.isPresent()) {
                // Extract enum value: InheritanceType.SINGLE_TABLE -> SINGLE_TABLE
                if (strategy.get().contains(".")) {
                    return strategy.get().substring(strategy.get().lastIndexOf('.') + 1);
                }
                return strategy.get();
            }
        }

        return null; // No inheritance specified
    }

    static Optional<String> getAnnotationValue(AnnotationExpr expr, String name) {
        Map<String, Expression> attributes = AbstractCompiler.extractAnnotationAttributes(expr);
        Expression value = attributes.get(name);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value.toString().replace("\"", "")); // Remove quotes
    }

    /**
     * Gets the table name from @Table annotation or derives from entity name.
     *
     * @param typeDecl The entity type declaration
     * @return Table name
     */
    public static String getTableName(TypeDeclaration<?> typeDecl) {
        Optional<AnnotationExpr> tableAnn = typeDecl.getAnnotationByName("Table");

        if (tableAnn.isPresent()) {
            Optional<String> s = getAnnotationValue(tableAnn.get(), "name");
            if (s.isPresent()) {
                return s.get();
            }
        }

        // Default: convert entity name to snake_case
        return AbstractCompiler.camelToSnakeCase(typeDecl.getNameAsString());
    }

    /**
     * Gets the entity name from @Entity annotation or class name.
     *
     * @param typeDecl The entity type declaration
     * @return Entity name
     */
    public static String getEntityName(TypeDeclaration<?> typeDecl) {
        Optional<AnnotationExpr> entityAnn = typeDecl.getAnnotationByName("Entity");

        if (entityAnn.isPresent()) {
            Optional<String> s = getAnnotationValue(entityAnn.get(), "name");
            if (s.isPresent()) {
                return s.get();
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
    public static String getDiscriminatorColumn(TypeDeclaration<?> typeDecl) {
        Optional<AnnotationExpr> discAnn = typeDecl.getAnnotationByName("DiscriminatorColumn");

        if (discAnn.isPresent()) {
            Optional<String> s = getAnnotationValue(discAnn.get(), "name");
            if (s.isPresent()) {
                return s.get();
            }
        }

        return DTYPE; // Default discriminator column
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
            tableName = tableAnnotation.name().isEmpty() ? AbstractCompiler.camelToSnakeCase(entityName)
                    : tableAnnotation.name();
            schema = tableAnnotation.schema().isEmpty() ? null : tableAnnotation.schema();
        } else {
            tableName = AbstractCompiler.camelToSnakeCase(entityName);
        }

        return buildTableMapping(entityClass, tableName, schema);
    }

    private static TableMapping buildTableMapping(Class<?> entityClass, String tableName, String schema) {
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

        DiscriminatorColumn discriminatorColumnAnnotation = entityClass.getAnnotation(DiscriminatorColumn.class);
        if (discriminatorColumnAnnotation != null) {
            discriminatorColumn = discriminatorColumnAnnotation.name();
            if (discriminatorColumn.isEmpty()) {
                discriminatorColumn = DTYPE; // Default
            }
        } else if (inheritanceType != null && "SINGLE_TABLE".equals(inheritanceType)) {
            discriminatorColumn = DTYPE; // Default for SINGLE_TABLE
        }

        DiscriminatorValue discriminatorValueAnnotation = entityClass.getAnnotation(DiscriminatorValue.class);
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

        return new TableMapping(tableName, schema, propertyToColumnMap,
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

    private static Map<String, String> buildPropertyToColumnMappings(Class<?> entityClass) {
        Map<String, String> columnMappings = new HashMap<>();

        for (Field field : getAllFields(entityClass)) {
            if (isTransientField(field) || isRelationshipField(field)) {
                continue;
            }

            String propertyName = field.getName();
            String columnName = getColumnName(field);
            String fullPropertyName = entityClass.getName() + "." + propertyName;

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
            joinColumnName = joinColumn.name().isEmpty() ? AbstractCompiler.camelToSnakeCase(propertyName) + "_id"
                    : joinColumn.name();
            referencedColumnName = joinColumn.referencedColumnName().isEmpty() ? "id"
                    : joinColumn.referencedColumnName();
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
                targetTableName);
    }

    private static Class<?> getTargetEntityClass(Field field) {
        if (field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)) {
            return field.getType();
        }
        if ((field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class)) &&
                Collection.class.isAssignableFrom(field.getType())) {
            // For collections, we need to get the generic type
            java.lang.reflect.Type genericType = field.getGenericType();
            if (genericType instanceof java.lang.reflect.ParameterizedType paramType) {
                return (Class<?>) paramType.getActualTypeArguments()[0];
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

    public static Set<String> getFullNamesForEntity(String name) {
        return shortNames.getOrDefault(name, Collections.emptySet());
    }

    public static String getTableNameForEntity(String entityName) {
        EntityMetadata meta = mapping.get(entityName);
        if (meta == null) {
            Set<String> n = EntityMappingResolver.getFullNamesForEntity(entityName);
            if (n.size() != 1) {
                // Fallback: assume snake_case of the entity name
                return AbstractCompiler.camelToSnakeCase(entityName);
            }
            EntityMetadata m = mapping.get(n.stream().findFirst().orElseThrow());
            return m.tableName();
        }
        return meta.tableName();
    }
}
