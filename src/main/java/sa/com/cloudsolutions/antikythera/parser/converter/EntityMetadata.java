package sa.com.cloudsolutions.antikythera.parser.converter;

import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.Map;

public record EntityMetadata(TypeWrapper entity, String tableName,
                             Map<String, String> propertyToColumnMap,
                             Map<String, JoinMapping> relationshipMap) {
}