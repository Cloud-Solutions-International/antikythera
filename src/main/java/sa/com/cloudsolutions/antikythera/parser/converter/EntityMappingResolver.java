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
    public static final String ENTITY = "Entity";

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
        if (typeDecl != null && typeDecl.getAnnotationByName(ENTITY).isPresent()) {
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

        // Build relationship mappings from AST
        Map<String, JoinMapping> relationshipMappings = buildRelationshipMappingsFromAST(typeDecl, tableName);

        return new EntityMetadata(new TypeWrapper(typeDecl), tableName,
                propertyToColumnMappings, relationshipMappings);
    }

    /**
     * Builds relationship mappings from AST by analyzing JPA relationship annotations.
     */
    private static Map<String, JoinMapping> buildRelationshipMappingsFromAST(TypeDeclaration<?> typeDecl, String sourceTableName) {
        Map<String, JoinMapping> joinMappings = new HashMap<>();

        for (FieldDeclaration field : typeDecl.getFields()) {
            if (isTransientFieldFromAST(field) || !isRelationshipFieldFromAST(field)) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                String propertyName = variable.getNameAsString();
                JoinMapping joinMapping = buildJoinMappingFromAST(field, variable, propertyName, sourceTableName);
                if (joinMapping != null) {
                    joinMappings.put(propertyName, joinMapping);
                }
            }
        }

        return joinMappings;
    }

    private static boolean isRelationshipFieldFromAST(FieldDeclaration field) {
        return field.getAnnotationByName("OneToOne").isPresent() ||
                field.getAnnotationByName("OneToMany").isPresent() ||
                field.getAnnotationByName("ManyToOne").isPresent() ||
                field.getAnnotationByName("ManyToMany").isPresent();
    }

    /**
     * Builds a JoinMapping from AST field information.
     */
    private static JoinMapping buildJoinMappingFromAST(FieldDeclaration field, VariableDeclarator variable,
            String propertyName, String sourceTableName) {
        TypeWrapper targetType = getTargetTypeFromAST(field, variable);
        if (targetType == null || targetType.getName() == null) {
            return null;
        }

        String targetEntityName = targetType.getName();
        String targetTableName = resolveTargetTableName(targetEntityName);
        String[] joinColumnInfo = extractJoinColumnInfoFromAST(field, propertyName);

        return new JoinMapping(
                propertyName,
                targetEntityName,
                joinColumnInfo[0],
                joinColumnInfo[1],
                determineJoinTypeFromAST(field),
                sourceTableName,
                targetTableName);
    }

    private static TypeWrapper getTargetTypeFromAST(FieldDeclaration field, VariableDeclarator variable) {
        List<TypeWrapper> types = AbstractCompiler.findTypesInVariable(variable);
        if (types.isEmpty()) {
            return null;
        }

        // For collection types (OneToMany, ManyToMany), the target is the first type (generic argument)
        // For single types (OneToOne, ManyToOne), the target is the last type
        boolean isCollection = field.getAnnotationByName("OneToMany").isPresent() ||
                field.getAnnotationByName("ManyToMany").isPresent();

        if (isCollection && types.size() > 1) {
            return types.getFirst();
        }
        return types.getLast();
    }

    private static String resolveTargetTableName(String targetEntityName) {
        Set<String> fullNames = getFullNamesForEntity(targetEntityName);
        for (String fullName : fullNames) {
            EntityMetadata meta = mapping.get(fullName);
            if (meta != null) {
                return meta.tableName();
            }
        }
        return AbstractCompiler.camelToSnakeCase(targetEntityName);
    }

    private static String[] extractJoinColumnInfoFromAST(FieldDeclaration field, String propertyName) {
        String joinColumnName = AbstractCompiler.camelToSnakeCase(propertyName) + "_id";
        String referencedColumnName = "id";

        Optional<AnnotationExpr> joinColumnAnn = field.getAnnotationByName("JoinColumn");
        if (joinColumnAnn.isPresent()) {
            Map<String, Expression> attrs = AbstractCompiler.extractAnnotationAttributes(joinColumnAnn.get());
            Expression nameExpr = attrs.get("name");
            if (nameExpr != null) {
                joinColumnName = nameExpr.toString().replace("\"", "");
            }
            Expression refExpr = attrs.get("referencedColumnName");
            if (refExpr != null) {
                referencedColumnName = refExpr.toString().replace("\"", "");
            }
        }

        return new String[] { joinColumnName, referencedColumnName };
    }

    /**
     * Determines the join type from AST annotations.
     */
    private static JoinType determineJoinTypeFromAST(FieldDeclaration field) {
        // Check ManyToOne and OneToOne - these can have optional=false for INNER join
        for (String annotationName : List.of("ManyToOne", "OneToOne")) {
            Optional<AnnotationExpr> annotation = field.getAnnotationByName(annotationName);
            if (annotation.isPresent()) {
                Map<String, Expression> attrs = AbstractCompiler.extractAnnotationAttributes(annotation.get());
                Expression optional = attrs.get("optional");
                if (optional != null && "false".equals(optional.toString())) {
                    return JoinType.INNER;
                }
                return JoinType.LEFT;
            }
        }

        // OneToMany and ManyToMany are typically LEFT joins
        return JoinType.LEFT;
    }

    private static EntityMetadata buildEntityMetadata(Class<?> entityClass) {
        String entityName = getEntityName(entityClass);
        TableMapping tableMapping = buildTableMapping(entityClass, entityName);

        Map<String, String> propertyToColumnMappings = buildPropertyToColumnMappings(entityClass);
        Map<String, JoinMapping> relationshipMappings = buildRelationshipMappings(entityClass, tableMapping);

        return new EntityMetadata(new TypeWrapper(entityClass), getTableName(entityClass),
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
        Optional<AnnotationExpr> entityAnn = typeDecl.getAnnotationByName(ENTITY);

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
        return buildPropertyToColumnMap(entityClass, false, false);
    }

    private static Map<String, String> buildPropertyToColumnMappings(Class<?> entityClass) {
        return buildPropertyToColumnMap(entityClass, true, true);
    }

    private static Map<String, String> buildPropertyToColumnMap(Class<?> entityClass, boolean useFullyQualifiedKey, boolean skipRelationships) {
        Map<String, String> propertyToColumnMap = new HashMap<>();

        for (Field field : getAllFields(entityClass)) {
            if (isTransientField(field) || (skipRelationships && isRelationshipField(field))) {
                continue;
            }
            String propertyName = field.getName();
            String columnName = getColumnName(field);
            String key = useFullyQualifiedKey ? entityClass.getName() + "." + propertyName : propertyName;
            propertyToColumnMap.put(key, columnName);
        }

        return propertyToColumnMap;
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
            if (m == null) {
                // FQN exists in shortNames but no metadata (not an entity or couldn't be parsed)
                return AbstractCompiler.camelToSnakeCase(entityName);
            }
            return m.tableName();
        }
        return meta.tableName();
    }

    /**
     * Attempts to resolve an entity by its simple name or suffix.
     * Iterates through all resolved types to find a match.
     *
     * @param suffix The simple name or suffix of the entity class
     * @return Optional containing EntityMetadata if found, empty otherwise
     */
    public static Optional<EntityMetadata> resolveBySuffix(String suffix) {
        for (Map.Entry<String, TypeWrapper> entry : AntikytheraRunTime.getResolvedTypes().entrySet()) {
            String fqn = entry.getKey();
            if (fqn.endsWith("." + suffix)) {
                TypeWrapper tw = entry.getValue();
                if (isEntity(tw)) {
                    return Optional.ofNullable(buildOnTheFly(tw));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if a TypeWrapper represents a JPA entity.
     *
     * @param tw The TypeWrapper to check
     * @return true if the type is annotated with @Entity, false otherwise
     */
    public static boolean isEntity(TypeWrapper tw) {
        if (tw.getType() != null) {
            return tw.getType().getAnnotationByName(ENTITY).isPresent();
        } else if (tw.getClazz() != null) {
            return tw.getClazz().isAnnotationPresent(javax.persistence.Entity.class);
        }
        return false;
    }

    /**
     * Attempts to resolve an entity by its name, checking full names mapping first,
     * then falling back to suffix search.
     *
     * @param name The entity name or suffix
     * @return Optional containing EntityMetadata if found
     */
    public static Optional<EntityMetadata> resolveEntity(String name) {
        Optional<String> n = getFullNamesForEntity(name).stream().findFirst();
        if (n.isPresent()) {
            EntityMetadata meta = mapping.get(n.get());
            return Optional.ofNullable(meta);
        }
        return resolveBySuffix(name);
    }

    /**
     * Resolves an entity name within the context of another entity (e.g. the primary
     * entity of a query).
     * This handles cases where the entity might be:
     * 1. The context entity itself
     * 2. A related entity (field) of the context entity
     * 3. In the same package as the context entity
     *
     * @param context The context entity (e.g. FROM User)
     * @param name    The name to resolve
     * @return The fully qualified name of the resolved entity, or null if not found
     */
    public static String resolveRelatedEntity(TypeWrapper context, String name) {
        // Check 1: Is it the context entity?
        if (name.equals(context.getName()) || name.equals(context.getFullyQualifiedName())) {
            return context.getFullyQualifiedName();
        }

        // Check 2: Global resolution
        Optional<EntityMetadata> global = resolveEntity(name);
        if (global.isPresent()) {
            return global.get().entity().getFullyQualifiedName();
        }

        // Check 3: Search in the context entity's fields (relationships)
        if (context.getClazz() == null && context.getType() != null) {
            for (FieldDeclaration f : context.getType().getFields()) {
                for (TypeWrapper tw : AbstractCompiler.findTypesInVariable(f.getVariable(0))) {
                    if (tw.getFullyQualifiedName() != null &&
                            (tw.getFullyQualifiedName().equals(name) || tw.getName().equals(name))) {
                        return tw.getFullyQualifiedName();
                    }
                }
            }
        } else if (context.getClazz() != null && context.getClazz().getName().equals(name)) {
            return context.getFullyQualifiedName();
        }

        // Check 4: Try same package
        String contextFqn = context.getFullyQualifiedName();
        if (contextFqn != null && contextFqn.contains(".")) {
            String packageName = contextFqn.substring(0, contextFqn.lastIndexOf('.'));
            String samePackageFqn = packageName + "." + name;

            TypeWrapper resolved = AntikytheraRunTime.getResolvedTypes().get(samePackageFqn);
            if (resolved != null) {
                // Build metadata on-the-fly if needed
                buildOnTheFly(resolved);
                return samePackageFqn;
            }
        }

        return null;
    }
}
