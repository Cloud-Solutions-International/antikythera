package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Map;
import java.util.HashMap;

/**
 * Context object that holds information needed during SQL conversion.
 * <p>
 * This class encapsulates the entity metadata and database dialect information
 * required during the HQL to SQL conversion process. It provides a clean way
 * to pass conversion context between different components of the converter.
 * <p>
 * Also tracks alias-to-entity mappings to properly handle inheritance and discriminator filtering.
 */
public record SqlConversionContext(DatabaseDialect dialect,
                                   Map<String, TableMapping> aliasToEntityMap) {

    /**
     * Constructs a new SqlConversionContext.
     *
     * @param dialect          The target database dialect for SQL generation
     * @param aliasToEntityMap Map of alias names to their table mappings
     */
    public SqlConversionContext {
        if (dialect == null) {
            throw new IllegalArgumentException("Database dialect cannot be null");
        }
        if (aliasToEntityMap == null) {
            throw new IllegalArgumentException("Alias to entity map cannot be null");
        }
    }

    @Override
    public String toString() {
        return "SqlConversionContext{" +
                "dialect=" + dialect +
                ", aliasCount=" + aliasToEntityMap.size() +
                '}';
    }
}
