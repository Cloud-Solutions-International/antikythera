package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.QueryType;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMappingResolver;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMetadata;
import sa.com.cloudsolutions.antikythera.parser.converter.HQLParserAdapter;
import sa.com.cloudsolutions.antikythera.parser.converter.TableMapping;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Generates SQL from Repository methods.</p>
 *
 * <p>The `BaseRepositoryParser` class is responsible for parsing repository classes and generating
 * repository queries based on the provided methods. It has functionality to handle custom
 * query annotations, inferred queries through method naming, and metadata extraction for entities.</p>
 *
 * <p>This class focuses on analyzing repository structures, interpreting query definitions, and
 * handling query conversion logic while accounting for specific database dialects.</p>
 */
public class BaseRepositoryParser extends AbstractCompiler {
    protected static final Logger logger = LoggerFactory.getLogger(BaseRepositoryParser.class);

    public static final String JPA_REPOSITORY = "JpaRepository";
    public static final String SELECT_STAR = "SELECT * FROM ";
    protected static final Pattern CAMEL_TO_SNAKE_PATTERN = Pattern.compile("([a-z])([A-Z]+)");

    protected static final String ORACLE = "oracle";
    protected static final String POSTGRESQL = "PG";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\?");
    public static final String NATIVE_QUERY = "nativeQuery";
    /**
     * SQL dialect, at the moment oracle or postgresql as identified from the connection url
     */
    protected static String dialect;

    /**
     * The JPA query converter for converting non-native queries to SQL
     */
    protected HQLParserAdapter queryConverter;

    /**
     * Entity mapping resolver for extracting metadata from JPA annotations
     */
    protected EntityMappingResolver entityMappingResolver;

    /**
     * The compilation unit or class associated with this entity.
     */
    TypeWrapper entity;

    /**
     * The table name associated with the entity
     */
    String table;
    /**
     * The java parser type associated with the entity.
     */
    Type entityType;

    /**
     * The queries that were identified in this repository
     */
    protected Map<Callable, RepositoryQuery> queries = new HashMap<>();

    Evaluator eval;

    protected static final Pattern KEYWORDS_PATTERN = Pattern.compile(
            "get|findBy|findFirstBy|findTopBy|And|OrderBy|NotIn|In|Desc|IsNotNull|IsNull|Not|Containing|Like|Or|Between|LessThanEqual|GreaterThanEqual|GreaterThan|LessThan"
    );

    public BaseRepositoryParser() throws IOException {
        super();
    }

    public static BaseRepositoryParser create(CompilationUnit cu) throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        parser.cu = cu;
        return parser;
    }


    public RepositoryQuery getQueryFromRepositoryMethod(Callable repoMethod) {
        return queries.get(repoMethod);
    }

    /**
     * <p>Extract a query from a given method declaration.</p>
     *
     * <p>The method declaration should be a part of a JPARepository interface.
     * It may or may not have an @Query annotation. If the annotation is
     * present), it will either be a native query (in which case the nativeQuery
     * attribute will be true, or it maybe an HQL.</p>
     *
     * <p>In the case of an HQL query, we will use the hql-parser library to
     * convert it into an SQL which in turn will be parsed with the JSQL parser.
     * However that parsing takes place inside the BaseRepositoryQuery class</p>
     *
     * @param methodDeclaration the method declaration to be processed
     */
    void queryFromMethodDeclaration(MethodDeclaration methodDeclaration) {
        try {
            Optional<AnnotationExpr> annotationExpr = methodDeclaration.getAnnotationByName("Query");
            Callable callable = new Callable(methodDeclaration, null);

            if (annotationExpr.isPresent()) {
                Map<String, Expression> attr = AbstractCompiler.extractAnnotationAttributes(annotationExpr.get());
                Expression value = attr.get("value");
                Expression nt = attr.get(NATIVE_QUERY);
                Variable v = eval.evaluateExpression(value);

                if (nt != null && eval.evaluateExpression(nt).getValue().equals(true)) {
                    queries.put(callable, queryBuilder(v.getValue().toString(), QueryType.NATIVE_SQL, callable));
                }
                else {
                    queries.put(callable, queryBuilder(v.getValue().toString(), QueryType.HQL , callable));
                }
            }
            else {
                queries.put(callable, parseNonAnnotatedMethod(callable));
            }
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    /**
     * Parse a repository method that does not have a query annotation.
     * In these cases the naming convention of the method is used to infer the query.
     *
     * @param md the method declaration
     * @return the RepositoryQuery instance that as parsed from the callable
     */
    RepositoryQuery parseNonAnnotatedMethod(Callable md) {
        String methodName = md.getNameAsString();
        List<String> components = extractComponents(methodName);
        StringBuilder sql = new StringBuilder();
        boolean top = false;
        boolean ordering = false;
        String next = "";

        String tableName = findTableName(entity);
        if (tableName != null) {
            for (int i = 0; i < components.size(); i++) {
                String component = components.get(i);

                if (i < components.size() - 1) {
                    next = components.get(i + 1);
                } else {
                    next = "";
                }

                switch (component) {
                    case "findAll" -> sql.append(SELECT_STAR).append(tableName.replace("\"", ""));
                    case "findAllById" -> sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE id = ?");
                    case "findBy", "get" -> sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE ");
                    case "findFirstBy", "findTopBy" -> {
                        top = true;
                        sql.append(SELECT_STAR).append(tableName.replace("\"", "")).append(" WHERE ");
                    }
                    case "Between" -> sql.append(" BETWEEN ? AND ? ");
                    case "GreaterThan" -> sql.append(" > ? ");
                    case "LessThan" -> sql.append(" < ? ");
                    case "GreaterThanEqual" -> sql.append(" >= ? ");
                    case "LessThanEqual" -> sql.append(" <= ? ");
                    case "IsNull" -> sql.append(" IS NULL ");
                    case "IsNotNull" -> sql.append(" IS NOT NULL ");
                    case "And", "Or", "Not" -> sql.append(component).append(" ");
                    case "Containing", "Like" -> sql.append(" LIKE ? ");
                    case "OrderBy" -> {
                        ordering = true;
                        sql.append(" ORDER BY ");
                    }
                    default -> {
                        sql.append(camelToSnake(component));
                        if (!ordering) {
                            if (next.equals("In")) {
                                sql.append(" In  (?) ");
                                i++;
                            } else if (next.equals("NotIn")) {
                                sql.append(" NOT In (?) ");
                                i++;
                            } else {
                                // Add = ? if next is empty (last component) or next is not a special operator
                                if (next.isEmpty() || (!next.equals("Between") && !next.equals("GreaterThan")
                                        && !next.equals("LessThan") && !next.equals("LessThanEqual")
                                        && !next.equals("IsNotNull") && !next.equals("Like")
                                        && !next.equals("GreaterThanEqual") && !next.equals("IsNull")
                                        && !next.equals("Containing"))) {
                                    sql.append(" = ? ");
                                }
                            }
                        } else {
                            sql.append(" ");
                        }
                    }
                }
            }
        } else {
            logger.warn("Table name cannot be null");
        }

        if (top) {
            if (ORACLE.equals(dialect)) {
                sql.append(" AND ROWNUM = 1");
            } else {
                sql.append(" LIMIT 1");
            }
        }

        StringBuilder result = new StringBuilder();
        for(int i = 0, j = 1 ; i < sql.length() ; i++) {
            char c = sql.charAt(i);
            if(c == '?') {
                result.append('?').append(j++);
            }
            else {
                result.append(c);
            }
        }
        return queryBuilder(result.toString(), QueryType.DERIVED, md);
    }

    /**
     * Recursively search method names for sql components
     * @param methodName name of the method
     * @return a list of components
     */
    List<String> extractComponents(String methodName) {
        List<String> components = new ArrayList<>();
        Matcher matcher = KEYWORDS_PATTERN.matcher(methodName);

        // Add spaces around each keyword
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, " " + matcher.group() + " ");
        }
        matcher.appendTail(sb);

        // Split the modified method name by spaces
        String[] parts = sb.toString().split("\\s+");
        for (String part : parts) {
            if (!part.isEmpty()) {
                components.add(part);
            }
        }

        return components;
    }

    /**
     * Count the number of parameters to bind.
     *
     * @param sql the sql statement as a string in which we will count the number of placeholders
     * @return the number of placeholders. This can be 0
     */
    static int countPlaceholders(String sql) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static boolean isOracle() {
        return ORACLE.equals(dialect);
    }

    /**
     * Build a repository query object
     * @param query the query
     * @param qt what is the type of query we are dealing with
     * @return a repository query instance.
     */
    RepositoryQuery queryBuilder(String query, QueryType qt, Callable md) {
        RepositoryQuery rql = new RepositoryQuery();
        rql.setMethodDeclaration(md);
        rql.setEntityType(entityType);
        rql.setPrimaryTable(table);
        rql.setRepositoryClassName(className);
        rql.setQueryType(qt);

        // Use the new converter for non-native queries if enabled
        if (qt.equals(QueryType.HQL)) {
            try {
                EntityMetadata entityMetadata = buildEntityMetadata();
                rql.setConversionResult(queryConverter.convertToNativeSQL(query, entityMetadata));
                rql.setQuery(query);
            } catch (Exception e) {
                logger.warn("Exception during query conversion: {}. Falling back to existing logic.", e.getMessage());
                rql.setQuery(query);
            }
        } else {
            rql.setQuery(query);
        }

        return rql;
    }

    /**
     * Builds entity metadata for the current entity being processed.
     * Tries to use Antikythera's parsed source first, falls back to reflection.
     *
     * @return EntityMetadata containing mapping information for the entity
     */
    private EntityMetadata buildEntityMetadata() {
        // Try to build from Antikythera's parsed source first
        EntityMetadata metadata = buildMetadataFromSources();
        if (metadata != null && !metadata.entityToTableMappings().isEmpty()) {
            return metadata;
        }
        
        // Fallback to reflection-based approach
        if (entity != null && entity.getClazz() != null) {
            return entityMappingResolver.resolveEntityMetadata(entity.getClazz());
        }
        return EntityMetadata.empty(); // Return empty metadata if no entity context
    }

    /**
     * Builds entity metadata using Antikythera's TypeWrapper and AbstractCompiler.
     * This avoids reflection and works directly with parsed source code.
     *
     * @return EntityMetadata from a parsed source, or null if not available
     */
    private EntityMetadata buildMetadataFromSources() {
        if (entity == null || entity.getType() == null) {
            return null; // No parsed type available
        }

        com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl = entity.getType();
        
        // Check if this is a JPA entity
        if (typeDecl.getAnnotationByName("Entity").isEmpty()) {
            return null; // Not an entity
        }

        try {
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
            Map<String, String> propertyToColumnMappings =
                buildPropertyToColumnMappingsFromAST(typeDecl, tableMapping);

            // Relationship mappings not yet implemented for AST-based approach
            Map<String, sa.com.cloudsolutions.antikythera.parser.converter.JoinMapping> relationshipMappings = 
                new HashMap<>();

            return new EntityMetadata(entityToTableMappings, propertyToColumnMappings, relationshipMappings);
        } catch (Exception e) {
            logger.warn("Failed to build entity metadata from AST: {}", e.getMessage());
            return null; // Fall back to reflection
        }
    }

    /**
     * Builds property to column map from TypeDeclaration AST.
     */
    private Map<String, String> buildPropertyToColumnMapFromAST(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Map<String, String> propertyToColumnMap = new HashMap<>();

        // Get all fields from the entity
        for (FieldDeclaration field : typeDecl.getFields()) {
            if (!isTransientFieldFromAST(field)) {
                for (VariableDeclarator variable : field.getVariables()) {
                    String propertyName = variable.getNameAsString();
                    String columnName = getColumnNameFromAST(field);
                    if (columnName == null) {
                        // Default: convert camelCase to snake_case
                        columnName = camelToSnake(propertyName);
                    }
                    propertyToColumnMap.put(propertyName, columnName);
                }
            }
        }

        return propertyToColumnMap;
    }

    /**
     * Builds property to column mappings with full metadata.
     */
    private Map<String, String> buildPropertyToColumnMappingsFromAST(
            TypeDeclaration<?> typeDecl, TableMapping tableMapping) throws ReflectiveOperationException {
        Map<String, String> columnMappings = new HashMap<>();

        for (FieldDeclaration field : typeDecl.getFields()) {
            if (isTransientFieldFromAST(field) || isRelationshipFieldFromAST(field)) {
                continue;
            }

            for (VariableDeclarator variable : field.getVariables()) {
                String propertyName = variable.getNameAsString();
                String columnName = getColumnNameFromAST(field);
                if (columnName == null) {
                    columnName = camelToSnake(propertyName);
                }
                String fullPropertyName = tableMapping.entityName() + "." + propertyName;

                columnMappings.put(fullPropertyName, columnName);
            }
        }

        return columnMappings;
    }

    /**
     * Checks if a field is transient (should be skipped).
     */
    private boolean isTransientFieldFromAST(com.github.javaparser.ast.body.FieldDeclaration field) {
        // Check for @Transient annotation
        if (field.getAnnotationByName("Transient").isPresent()) {
            return true;
        }
        // Check for transient or static modifier
        return field.isTransient() || field.isStatic();
    }

    /**
     * Checks if a field is a relationship field (JPA relationships).
     */
    private boolean isRelationshipFieldFromAST(com.github.javaparser.ast.body.FieldDeclaration field) {
        return field.getAnnotationByName("OneToOne").isPresent() ||
               field.getAnnotationByName("OneToMany").isPresent() ||
               field.getAnnotationByName("ManyToOne").isPresent() ||
               field.getAnnotationByName("ManyToMany").isPresent();
    }

    /**
     * Gets column name from @Column annotation or returns null.
     */
    private String getColumnNameFromAST(com.github.javaparser.ast.body.FieldDeclaration field) throws ReflectiveOperationException {
        Optional<com.github.javaparser.ast.expr.AnnotationExpr> columnAnn = 
            field.getAnnotationByName("Column");
        
        if (columnAnn.isPresent()) {
            Map<String, Expression> attributes = AbstractCompiler.extractAnnotationAttributes(columnAnn.get());
            Expression name = attributes.get("name");
            if (name != null) {
                return eval.evaluateExpression(name).getValue().toString();
            }
        }
        return null; // No @Column annotation or no name specified
    }

    /**
     * Gets all the repository queries that have been built.
     * 
     * @return a collection of all RepositoryQuery objects
     */
    public Collection<RepositoryQuery> getAllQueries() {
        return queries.values();
    }
    
    /**
     * Clears all queries from the internal map.
     * This should be called before processing a new repository to prevent
     * accumulation of queries from previously analyzed repositories.
     */
    public void clearQueries() {
        queries.clear();
        logger.debug("Queries map cleared");
    }


    /**
     * Checks if fallback to existing logic is enabled on conversion failure.
     *
     * @return true if fallback is enabled, false otherwise
     */
    boolean isFallbackOnFailureEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("fallback_on_failure", "true").toString());
            }
        }
        return true; // Default to enabled for safety
    }


    /**
     * Checks if conversion result caching is enabled.
     *
     * @return true if caching is enabled, false otherwise
     */
    boolean isCachingEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("cache_results", "true").toString());
            }
        }
        return true; // Default to enabled
    }


    /**
     * Find the table name from the hibernate entity.
     * Usually the entity will have an annotation giving the actual name of the table.
     *
     * This method is made static because when processing joins there are multiple entities
     * and there by multiple table names involved.
     *
     * @param entity a TypeWrapper representing the entity
     * @return the table name as a string.
     */
    public static String findTableName(TypeWrapper entity) {
        String table = null;
        if(entity != null) {
            if (entity.getType() != null) {
                return getNameFromType(entity, table);
            }
            else if (entity.getClazz() != null){
                Class<?> cls = entity.getClazz();
                for (Annotation ann : cls.getAnnotations()) {
                    if (ann instanceof javax.persistence.Table t) {
                        table = t.name();
                    }
                }
            }
        }
        else {
            logger.warn("Compilation unit is null");
        }
        return table;
    }

    static String getNameFromType(TypeWrapper entity, String table) {
        Optional<AnnotationExpr> ann = entity.getType().getAnnotationByName("Table");
        if (ann.isPresent()) {
            if (ann.get().isNormalAnnotationExpr()) {
                for (var pair : ann.get().asNormalAnnotationExpr().getPairs()) {
                    if (pair.getNameAsString().equals("name")) {
                        table = pair.getValue().toString().replace("\"", "");
                    }
                }
            } else {
                table = ann.get().asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
            }
            return table;
        } else {
            return camelToSnake(entity.getType().getNameAsString());
        }
    }

    /**
     * Find and parse the given entity.
     *
     * @param fd FieldDeclaration for which we need to find the compilation unit
     * @return a compilation unit
     */
    public static TypeWrapper findEntity(Type fd) {
        Optional<CompilationUnit> cu = fd.findCompilationUnit();
        if (cu.isPresent()) {
            for (ImportWrapper wrapper : AbstractCompiler.findImport(cu.get(), fd)) {
                if (wrapper.getType() != null) {
                    return new TypeWrapper(wrapper.getType());
                }
                else if(!wrapper.getImport().getNameAsString().startsWith("java.util")) {
                    try {
                        Class<?> cls = AbstractCompiler.loadClass(wrapper.getImport().getNameAsString());
                        return new TypeWrapper(cls);

                    } catch (ClassNotFoundException e) {
                        // can be ignored, we are trying to check for the existence of the class
                        logger.debug(e.getMessage());
                    }
                }
            }
            return new TypeWrapper(AbstractCompiler.findInSamePackage(cu.get(), fd).orElse(null));
        }
        return null;
    }

    /**
     * Converts the fields in an Entity to snake case which is the usual pattern for columns
     * @param str A camel cased variable
     * @return a snake cased variable
     */
    public static String camelToSnake(String str) {
        return CAMEL_TO_SNAKE_PATTERN.matcher(str).replaceAll("$1_$2").toLowerCase();
    }
    public void buildQueries() {
        if (cu != null && entity != null) {
            cu.accept(new Visitor(), null);
        }
    }

    /**
     * Visitor to iterate through the methods in the repository
     */
    class Visitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            queryFromMethodDeclaration(n);
        }
    }

    /**
     * Process the CompilationUnit to identify all the queries.
     */
    public void processTypes()  {
        /*
         * TODO : get the isJpaRepository stuff from examples into this project and use it here.
         * this method is now being called multiple times for a repository, fix that
         */
        for(var tp : cu.getTypes()) {
            if(tp.isClassOrInterfaceDeclaration()) {
                var cls = tp.asClassOrInterfaceDeclaration();

                for(var parent : cls.getExtendedTypes()) {
                    if (parent.toString().startsWith(JPA_REPOSITORY)) {

                        parent.getTypeArguments().ifPresent(t -> {
                            entityType = t.getFirst().orElseThrow();
                            entity = findEntity(entityType);
                            table = findTableName(entity);
                            eval = EvaluatorFactory.create(cls.getFullyQualifiedName().orElseThrow(), Evaluator.class);
                        });
                    }
                }
            }
        }
    }

    String getAnnotationValue(AnnotationExpr expr, String name) throws ReflectiveOperationException {
        Map<String, Expression> attributes = extractAnnotationAttributes(expr);
        Expression value = attributes.get(name);
        if (name != null) {
            Variable v = eval.evaluateExpression(value);
            return v.getValue().toString();
        }
        return null;
    }

    /**
     * Gets the table name from @Table annotation or derives from entity name.
     *
     * @param typeDecl The entity type declaration
     * @return Table name
     */
    public String getTableName(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Optional<AnnotationExpr> tableAnn = typeDecl.getAnnotationByName("Table");

        if (tableAnn.isPresent()) {
            String s = getAnnotationValue(tableAnn.get(), "name");
            if (s != null) {
                return s;
            }
        }

        // Default: convert entity name to snake_case
        return camelToSnakeCase(typeDecl.getNameAsString());
    }

    /**
     * Gets the entity name from @Entity annotation or class name.
     *
     * @param typeDecl The entity type declaration
     * @return Entity name
     */
    public  String getEntityName(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
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
    public  String getDiscriminatorColumn(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
        Optional<AnnotationExpr> discAnn = typeDecl.getAnnotationByName("DiscriminatorColumn");

        if (discAnn.isPresent()) {
            String s = getAnnotationValue(discAnn.get(), "name");
            if (s != null) {
                return s;
            }
        }

        return "dtype"; // Default discriminator column
    }

    /**
     * Gets the discriminator value from @DiscriminatorValue.
     *
     * @param typeDecl The entity type declaration
     * @return Discriminator value, or null if not specified
     */
    public  String getDiscriminatorValue(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
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
    public String getInheritanceStrategy(TypeDeclaration<?> typeDecl) throws ReflectiveOperationException {
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

}
