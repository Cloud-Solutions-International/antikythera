package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.converter.ConversionResult;
import sa.com.cloudsolutions.antikythera.parser.converter.DatabaseDialect;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMappingResolver;
import sa.com.cloudsolutions.antikythera.parser.converter.EntityMetadata;
import sa.com.cloudsolutions.antikythera.parser.converter.JpaQueryConverter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseRepositoryParser extends AbstractCompiler {
    protected static final Logger logger = LoggerFactory.getLogger(BaseRepositoryParser.class);

    protected static final String ORACLE = "oracle";
    protected static final String POSTGRESQL = "PG";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\?");
    /**
     * SQL dialect, at the moment oracle or postgresql as identified from the connection url
     */
    protected static String dialect;
    /**
     * Cache for conversion results to avoid re-converting the same queries.
     * Key is generated from query string and entity metadata.
     */
    private final Map<String, ConversionResult> conversionCache = new HashMap<>();

    /**
     * The JPA query converter for converting non-native queries to SQL
     */
    protected JpaQueryConverter queryConverter;

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

    public BaseRepositoryParser() throws IOException {
        super();
    }

    public static BaseRepositoryParser create(CompilationUnit cu) throws IOException {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        parser.cu = cu;
        return parser;
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
     * @param isNative will be true if an annotation says a native query
     * @return a repository query instance.
     */
    RepositoryQuery queryBuilder(String query, boolean isNative, Callable md) {
        RepositoryQuery rql = new RepositoryQuery();
        rql.setMethodDeclaration(md);
        rql.setEntityType(entityType);
        rql.setTable(table);

        // Use the new converter for non-native queries if enabled
        if (!isNative && isQueryConversionEnabled()) {
            try {
                EntityMetadata entityMetadata = buildEntityMetadata();
                DatabaseDialect targetDialect = detectDatabaseDialect();

                // Check cache first
                String cacheKey = generateCacheKey(query, entityMetadata, targetDialect);
                ConversionResult result = getCachedConversionResult(cacheKey);

                if (result == null) {
                    // Not in cache, perform conversion
                    result = queryConverter.convertToNativeSQL(query, entityMetadata, targetDialect);

                    // Cache the result (both successful and failed results)
                    cacheConversionResult(cacheKey, result);
                } else {
                    logger.debug("Using cached conversion result for query");
                }

                if (result.isSuccessful()) {
                    logger.debug("Successfully converted JPA query to native SQL: {}", result.getNativeSql());
                    rql.setQuery(result.getNativeSql());
                    rql.setIsNative(true); // Mark as native after successful conversion
                } else {
                    logger.debug("Falling back to existing logic for query conversion failure");
                    rql.setQuery(query);
                    rql.setIsNative(isNative);
                }
            } catch (Exception e) {
                if (isConversionFailureLoggingEnabled()) {
                    logger.warn("Exception during query conversion: {}. Falling back to existing logic.", e.getMessage());
                }
                rql.setQuery(query);
                rql.setIsNative(isNative);
            }
        } else {
            // Use original query for native queries or when conversion is disabled
            rql.setQuery(query);
            rql.setIsNative(isNative);
        }

        return rql;
    }

    /**
     * Builds entity metadata for the current entity being processed.
     *
     * @return EntityMetadata containing mapping information for the entity
     */
    private EntityMetadata buildEntityMetadata() {
        if (entity != null && entity.getClazz() != null) {
            return entityMappingResolver.resolveEntityMetadata(entity.getClazz());
        }
        return EntityMetadata.empty(); // Return empty metadata if no entity context
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
     * Gets a cached conversion result if available.
     *
     * @param cacheKey The cache key
     * @return The cached ConversionResult, or null if not found
     */
    ConversionResult getCachedConversionResult(String cacheKey) {
        if (isCachingEnabled()) {
            return conversionCache.get(cacheKey);
        }
        return null;
    }

    /**
     * Caches a conversion result.
     *
     * @param cacheKey The cache key
     * @param result The conversion result to cache
     */
    void cacheConversionResult(String cacheKey, ConversionResult result) {
        if (isCachingEnabled()) {
            conversionCache.put(cacheKey, result);
            logger.debug("Cached conversion result for key: {}", cacheKey);
        }
    }

    /**
     * Clears the conversion cache. Useful for testing or when entity metadata changes.
     */
    public void clearConversionCache() {
        conversionCache.clear();
        logger.debug("Conversion cache cleared");
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
}
