package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import com.raditha.hql.converter.JoinMapping;

import java.util.Map;

/**
 * Immutable bundle of resolved JPA entity metadata: the entity wrapper, its
 * database table name, a property-to-column mapping, and a map of relationship
 * field names to their {@link JoinMapping} descriptors.
 */
public record EntityMetadata(TypeWrapper entity, String tableName,
                             Map<String, String> propertyToColumnMap,
                             Map<String, JoinMapping> relationshipMap) {
}
