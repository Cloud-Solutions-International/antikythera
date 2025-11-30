package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import com.raditha.hql.converter.JoinMapping;

import java.util.Map;

public record EntityMetadata(TypeWrapper entity, String tableName,
                             Map<String, String> propertyToColumnMap,
                             Map<String, JoinMapping> relationshipMap) {
}