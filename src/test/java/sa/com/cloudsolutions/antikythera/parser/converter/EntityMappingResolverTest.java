package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityMappingResolverTest {

    @BeforeEach
    void setUp() {
        EntityMappingResolver.reset();
    }

    @Test
    void testGetTableNameForEntity_UnknownEntity() {
        String tableName = EntityMappingResolver.getTableNameForEntity("UnknownEntity");
        assertEquals("unknown_entity", tableName);
    }
}
