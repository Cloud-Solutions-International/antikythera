package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.generator.QueryType;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.converter.ColumnMapping;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
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
        RepositoryQuery q = queries.get(repoMethod);
        if (q == null) {
            if (repoMethod.isMethodDeclaration()) {
                queryFromMethodDeclaration(repoMethod.asMethodDeclaration());
            }
            else {
                /*
                 * TODO : THis has to be changed
                 */
                parseNonAnnotatedMethod(repoMethod);
            }
            q = queries.get(repoMethod);
        }
        return q;
    }

    void queryFromMethodDeclaration(MethodDeclaration n) {
        Optional<AnnotationExpr> annotationExpr = n.getAnnotationByName("Query");
        Callable callable = new Callable(n, null);

        if (annotationExpr.isPresent()) {
            Map<String, String> attr = AbstractCompiler.extractAnnotationAttributes(annotationExpr.get());
            if (Boolean.parseBoolean(attr.getOrDefault(NATIVE_QUERY,"false"))) {
                queries.put(callable, queryBuilder(attr.get("value"), QueryType.HQL, callable));
            }
            else {
                queries.put(callable, queryBuilder(attr.get("value"), QueryType.NATIVE_SQL, callable));
            }
        }
        else {
            parseNonAnnotatedMethod(callable);
        }
    }

    /**
     * Parse a repository method that does not have a query annotation.
     * In these cases the naming convention of the method is used to infer the query.
     * @param md the method declaration
     */
    void parseNonAnnotatedMethod(Callable md) {
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
        Optional<AnnotationExpr> ann = md.asMethodDeclaration().getAnnotationByName("Query");
        if (ann.isPresent()) {
            Map<String, String> a = AbstractCompiler.extractAnnotationAttributes(ann.get());
            if (a.get(NATIVE_QUERY) != null && Boolean.parseBoolean(a.get(NATIVE_QUERY))) {
                /*
                 * TODO : fix null
                 */
                queries.put(md, queryBuilder(result.toString(), null, md));
            }
        }
        /*
         * TODO : fix null
         */
        queries.put(md, queryBuilder(result.toString(), null, md));
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
     * Checks if query conversion is enabled in the configuration.
     *
     * @return true if query conversion is enabled, false otherwise
     */
    boolean isQueryConversionEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("enabled", "false").toString());
            }
        }
        return false; // Default to disabled
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

        // Use the new converter for non-native queries if enabled
        if (qt.equals(QueryType.HQL)) {
            try {
                EntityMetadata entityMetadata = buildEntityMetadata();
                DatabaseDialect targetDialect = detectDatabaseDialect();

                ConversionResult result = queryConverter.convertToNativeSQL(query, entityMetadata, targetDialect);

                if (result.isSuccessful()) {
                    logger.debug("Successfully converted JPA query to native SQL: {}", result.getNativeSql());
                    rql.setQuery(result.getNativeSql());
                } else {
                    logger.debug("Falling back to existing logic for query conversion failure");
                    rql.setQuery(query);
                }
            } catch (Exception e) {
                if (isConversionFailureLoggingEnabled()) {
                    logger.warn("Exception during query conversion: {}. Falling back to existing logic.", e.getMessage());
                }
                rql.setQuery(query);
            }
        } else {
            // Use original query for native queries or when conversion is disabled
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
        EntityMetadata metadata = buildEntityMetadataFromAntikythera();
        if (metadata != null && !metadata.getEntityToTableMappings().isEmpty()) {
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
     * @return EntityMetadata from parsed source, or null if not available
     */
    private EntityMetadata buildEntityMetadataFromAntikythera() {
        if (entity == null || entity.getType() == null) {
            return null; // No parsed type available
        }

        com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl = entity.getType();
        
        // Check if this is a JPA entity
        if (!typeDecl.getAnnotationByName("Entity").isPresent()) {
            return null; // Not an entity
        }

        try {
            // Extract entity information using AbstractCompiler helpers
            String entityName = AbstractCompiler.getEntityName(typeDecl);
            String tableName = AbstractCompiler.getTableName(typeDecl);
            String discriminatorColumn = AbstractCompiler.getDiscriminatorColumn(typeDecl);
            String discriminatorValue = AbstractCompiler.getDiscriminatorValue(typeDecl);
            String inheritanceType = AbstractCompiler.getInheritanceStrategy(typeDecl);

            // Build property to column map
            Map<String, String> propertyToColumnMap = buildPropertyToColumnMapFromAST(typeDecl);

            // For now, no parent table support (could be added later)
            TableMapping tableMapping = new TableMapping(
                entityName, tableName, null, propertyToColumnMap,
                discriminatorColumn, discriminatorValue, inheritanceType, null
            );

            Map<String, TableMapping> entityToTableMappings = Map.of(entityName, tableMapping);
            
            // Build property to column mappings
            Map<String, ColumnMapping> propertyToColumnMappings = 
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
    private Map<String, String> buildPropertyToColumnMapFromAST(
            com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl) {
        Map<String, String> propertyToColumnMap = new HashMap<>();

        // Get all fields from the entity
        typeDecl.getFields().forEach(field -> {
            if (isTransientFieldFromAST(field)) {
                return; // Skip transient fields
            }

            field.getVariables().forEach(variable -> {
                String propertyName = variable.getNameAsString();
                String columnName = getColumnNameFromAST(field);
                if (columnName == null) {
                    // Default: convert camelCase to snake_case
                    columnName = camelToSnake(propertyName);
                }
                propertyToColumnMap.put(propertyName, columnName);
            });
        });

        return propertyToColumnMap;
    }

    /**
     * Builds property to column mappings with full metadata.
     */
    private Map<String, sa.com.cloudsolutions.antikythera.parser.converter.ColumnMapping> 
            buildPropertyToColumnMappingsFromAST(
                com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl,
                sa.com.cloudsolutions.antikythera.parser.converter.TableMapping tableMapping) {
        Map<String, sa.com.cloudsolutions.antikythera.parser.converter.ColumnMapping> columnMappings = 
            new HashMap<>();

        typeDecl.getFields().forEach(field -> {
            if (isTransientFieldFromAST(field) || isRelationshipFieldFromAST(field)) {
                return; // Skip transient and relationship fields
            }

            field.getVariables().forEach(variable -> {
                String propertyName = variable.getNameAsString();
                String columnName = getColumnNameFromAST(field);
                if (columnName == null) {
                    columnName = camelToSnake(propertyName);
                }
                String fullPropertyName = tableMapping.entityName() + "." + propertyName;

                sa.com.cloudsolutions.antikythera.parser.converter.ColumnMapping columnMapping = 
                    new sa.com.cloudsolutions.antikythera.parser.converter.ColumnMapping(
                        fullPropertyName,
                        columnName,
                        tableMapping.tableName()
                    );

                columnMappings.put(fullPropertyName, columnMapping);
            });
        });

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
    private String getColumnNameFromAST(com.github.javaparser.ast.body.FieldDeclaration field) {
        Optional<com.github.javaparser.ast.expr.AnnotationExpr> columnAnn = 
            field.getAnnotationByName("Column");
        
        if (columnAnn.isPresent()) {
            Map<String, String> attributes = AbstractCompiler.extractAnnotationAttributes(columnAnn.get());
            String name = attributes.get("name");
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return null; // No @Column annotation or no name specified
    }

    /**
     * Detects the database dialect from the current configuration.
     *
     * @return DatabaseDialect enum value for the configured database
     */
    private DatabaseDialect detectDatabaseDialect() {
        if (ORACLE.equals(dialect)) {
            return DatabaseDialect.ORACLE;
        } else if (POSTGRESQL.equals(dialect)) {
            return DatabaseDialect.POSTGRESQL;
        }
        return DatabaseDialect.POSTGRESQL; // Default to PostgreSQL
    }


    /**
     * Generates a cache key for the given query and entity metadata.
     *
     * @param query The JPA query string
     * @param entityMetadata The entity metadata
     * @param dialect The database dialect
     * @return A unique cache key string
     */
    String generateCacheKey(String query, EntityMetadata entityMetadata, DatabaseDialect dialect) {
        // Create a simple hash-based key combining query, entity info, and dialect
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(query.trim().replaceAll("\\s+", " ")); // Normalize whitespace
        keyBuilder.append("|");
        keyBuilder.append(entityMetadata.hashCode());
        keyBuilder.append("|");
        keyBuilder.append(dialect.name());

        return String.valueOf(keyBuilder.toString().hashCode());
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
     * Checks if conversion failure logging is enabled.
     *
     * @return true if logging conversion failures is enabled, false otherwise
     */
    boolean isConversionFailureLoggingEnabled() {
        Map<String, Object> db = (Map<String, Object>) Settings.getProperty(Settings.DATABASE);
        if (db != null) {
            Map<String, Object> queryConversion = (Map<String, Object>) db.get(Settings.SQL_QUERY_CONVERSION);
            if (queryConversion != null) {
                return Boolean.parseBoolean(queryConversion.getOrDefault("log_conversion_failures", "true").toString());
            }
        }
        return true; // Default to enabled
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
        for(var tp : cu.getTypes()) {
            if(tp.isClassOrInterfaceDeclaration()) {
                var cls = tp.asClassOrInterfaceDeclaration();

                for(var parent : cls.getExtendedTypes()) {
                    if (parent.toString().startsWith(JPA_REPOSITORY)) {

                        parent.getTypeArguments().ifPresent(t -> {
                            entityType = t.getFirst().orElseThrow();
                            entity = findEntity(entityType);
                            table = findTableName(entity);
                        });
                    }
                }
            }
        }
    }

}
