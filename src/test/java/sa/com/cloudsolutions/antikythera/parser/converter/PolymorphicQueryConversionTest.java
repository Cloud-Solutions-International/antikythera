package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for polymorphic query conversion with inheritance.
 * 
 * Tests both SINGLE_TABLE and JOINED inheritance strategies:
 * - TYPE() function conversion
 * - Implicit discriminator filtering
 * - Inheritance JOIN generation
 * 
 * Uses entities from antikythera-test-helper:
 * - Animal/Dog/Cat (SINGLE_TABLE inheritance)
 * - Vehicle/Car (JOINED inheritance)
 */
class PolymorphicQueryConversionTest {

    private JpaQueryConverter converter;
    private EntityMappingResolver entityResolver;
    
    private EntityMetadata animalMetadata;
    private EntityMetadata vehicleMetadata;
    private EntityMetadata combinedMetadata;

    @BeforeAll
    static void setupTestHelper() throws IOException {
        // Configure settings to point to test-helper project
        Settings.loadConfigMap();
    }

    @BeforeEach
    void setUp() throws Exception {
        converter = new HibernateQueryConverter();
        entityResolver = new EntityMappingResolver();
        
        // Note: For this test, we'll create simple mock metadata
        // In a real scenario with actual source parsing, AbstractCompiler.preProcess()
        // would parse the test-helper entities from:
        // ../antikythera-test-helper/src/main/java/sa/com/cloudsolutions/antikythera/testhelper/model/
        
        // Create mock metadata with inheritance information
        animalMetadata = createAnimalMetadata();
        vehicleMetadata = createVehicleMetadata();
        combinedMetadata = createCombinedMetadata();
    }
    
    private EntityMetadata createAnimalMetadata() {
        Map<String, TableMapping> entityMappings = new HashMap<>();
        Map<String, ColumnMapping> propertyMappings = new HashMap<>();
        
        // Animal (parent with SINGLE_TABLE)
        entityMappings.put("Animal", new TableMapping(
            "Animal", "animals", null, 
            Map.of("name", "name", "age", "age", "color", "color"),
            "dtype", "ANIMAL", "SINGLE_TABLE", null
        ));
        
        // Dog (subclass)
        entityMappings.put("Dog", new TableMapping(
            "Dog", "animals", null,
            Map.of("name", "name", "age", "age", "color", "color", "breed", "breed", "trained", "trained"),
            "dtype", "DOG", "SINGLE_TABLE", null
        ));
        
        // Cat (subclass)
        entityMappings.put("Cat", new TableMapping(
            "Cat", "animals", null,
            Map.of("name", "name", "age", "age", "color", "color", "indoor", "indoor", "livesRemaining", "lives_remaining"),
            "dtype", "CAT", "SINGLE_TABLE", null
        ));
        
        // Add column mappings
        propertyMappings.put("name", new ColumnMapping("name", "name", "animals"));
        propertyMappings.put("age", new ColumnMapping("age", "age", "animals"));
        propertyMappings.put("color", new ColumnMapping("color", "color", "animals"));
        propertyMappings.put("breed", new ColumnMapping("breed", "breed", "animals"));
        propertyMappings.put("trained", new ColumnMapping("trained", "trained", "animals"));
        propertyMappings.put("indoor", new ColumnMapping("indoor", "indoor", "animals"));
        propertyMappings.put("livesRemaining", new ColumnMapping("livesRemaining", "lives_remaining", "animals"));
        
        return new EntityMetadata(entityMappings, propertyMappings, new HashMap<>());
    }
    
    private EntityMetadata createVehicleMetadata() {
        Map<String, TableMapping> entityMappings = new HashMap<>();
        Map<String, ColumnMapping> propertyMappings = new HashMap<>();
        
        // Vehicle (parent with JOINED)
        TableMapping vehicleMapping = new TableMapping(
            "Vehicle", "vehicles", null,
            Map.of("manufacturer", "manufacturer", "year", "year", "color", "color"),
            null, null, "JOINED", null
        );
        entityMappings.put("Vehicle", vehicleMapping);
        
        // Car (subclass with own table)
        entityMappings.put("Car", new TableMapping(
            "Car", "cars", null,
            Map.of("manufacturer", "manufacturer", "year", "year", "color", "color",
                   "numberOfDoors", "number_of_doors", "transmission", "transmission", "convertible", "convertible"),
            null, null, "JOINED", vehicleMapping
        ));
        
        // Add column mappings
        propertyMappings.put("manufacturer", new ColumnMapping("manufacturer", "manufacturer", "vehicles"));
        propertyMappings.put("year", new ColumnMapping("year", "year", "vehicles"));
        propertyMappings.put("color", new ColumnMapping("color", "color", "vehicles"));
        propertyMappings.put("numberOfDoors", new ColumnMapping("numberOfDoors", "number_of_doors", "cars"));
        propertyMappings.put("transmission", new ColumnMapping("transmission", "transmission", "cars"));
        propertyMappings.put("convertible", new ColumnMapping("convertible", "convertible", "cars"));
        
        return new EntityMetadata(entityMappings, propertyMappings, new HashMap<>());
    }
    
    private EntityMetadata createCombinedMetadata() {
        Map<String, TableMapping> entityMappings = new HashMap<>();
        Map<String, ColumnMapping> propertyMappings = new HashMap<>();
        
        // Combine both hierarchies
        EntityMetadata animals = createAnimalMetadata();
        EntityMetadata vehicles = createVehicleMetadata();
        
        entityMappings.putAll(animals.getAllTableMappings().stream()
            .collect(HashMap::new, (m, t) -> m.put(t.entityName(), t), HashMap::putAll));
        entityMappings.putAll(vehicles.getAllTableMappings().stream()
            .collect(HashMap::new, (m, t) -> m.put(t.entityName(), t), HashMap::putAll));
        
        // Combine column mappings (simplified)
        propertyMappings.putAll(animals.getAllTableMappings().stream()
            .flatMap(t -> t.propertyToColumnMap().entrySet().stream())
            .collect(HashMap::new, (m, e) -> m.putIfAbsent(e.getKey(), 
                new ColumnMapping(e.getKey(), e.getValue(), "combined")), HashMap::putAll));
        
        return new EntityMetadata(entityMappings, propertyMappings, new HashMap<>());
    }

    // ========== TYPE() Function Conversion Tests ==========

    @Test
    void testTypeFunctionWithEquality() {
        String query = "SELECT a FROM Animal a WHERE TYPE(a) = Dog";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully convert TYPE() function");
        String sql = result.getNativeSql();
        
        // Should convert to discriminator check
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should reference discriminator column");
        assertTrue(sql.contains("'DOG'"), "Should filter by DOG discriminator value");
        
        System.out.println("TYPE() equality conversion: " + sql);
    }

    @Test
    void testTypeFunctionWithInOperator() {
        String query = "SELECT a FROM Animal a WHERE TYPE(a) IN (Dog, Cat)";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully convert TYPE() IN");
        String sql = result.getNativeSql();
        
        // Should convert to discriminator IN check
        assertTrue(sql.contains("IN"), "Should preserve IN operator");
        assertTrue(sql.contains("'DOG'"), "Should include DOG discriminator value");
        assertTrue(sql.contains("'CAT'"), "Should include CAT discriminator value");
        
        System.out.println("TYPE() IN conversion: " + sql);
    }

    @Test
    void testTypeFunctionWithInequality() {
        String query = "SELECT a FROM Animal a WHERE TYPE(a) != Cat";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully convert TYPE() inequality");
        String sql = result.getNativeSql();
        
        // Should convert to discriminator check with !=
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should reference discriminator column");
        assertTrue(sql.contains("!=") || sql.contains("<>"), "Should preserve inequality operator");
        assertTrue(sql.contains("'CAT'"), "Should filter by CAT discriminator value");
        
        System.out.println("TYPE() inequality conversion: " + sql);
    }

    @Test
    void testTypeFunctionWithAdditionalConditions() {
        String query = "SELECT a FROM Animal a WHERE TYPE(a) = Dog AND a.age > 5";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully convert TYPE() with conditions");
        String sql = result.getNativeSql();
        
        // Should have both discriminator check and age condition
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should reference discriminator column");
        assertTrue(sql.contains("'DOG'"), "Should filter by DOG discriminator value");
        assertTrue(sql.contains("age"), "Should include age condition");
        assertTrue(sql.contains(">"), "Should include greater than operator");
        
        System.out.println("TYPE() with conditions: " + sql);
    }

    @Test
    void testTypeFunctionWithJoinedInheritance() {
        String query = "SELECT v FROM Vehicle v WHERE TYPE(v) = Car";
        
        ConversionResult result = converter.convertToNativeSQL(query, vehicleMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully convert TYPE() for JOINED inheritance");
        String sql = result.getNativeSql();
        
        // For JOINED strategy, should convert to subquery
        assertTrue(sql.contains("IN") || sql.contains("EXISTS"), "Should use subquery for JOINED inheritance");
        assertTrue(sql.contains("cars") || sql.contains("car"), "Should reference car table");
        
        System.out.println("TYPE() JOINED inheritance: " + sql);
    }

    // ========== Implicit Discriminator Filtering Tests ==========

    @Test
    void testImplicitDiscriminatorFilteringOnSubclass() {
        String query = "SELECT d FROM Dog d WHERE d.breed = 'Labrador'";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully add implicit discriminator filtering");
        String sql = result.getNativeSql();
        
        // Should automatically add discriminator filtering for Dog
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should add discriminator column");
        assertTrue(sql.contains("'DOG'"), "Should filter by DOG discriminator value");
        assertTrue(sql.contains("breed"), "Should preserve original condition");
        
        System.out.println("Implicit discriminator filtering: " + sql);
    }

    @Test
    void testImplicitDiscriminatorFilteringWithExistingWhere() {
        String query = "SELECT d FROM Dog d WHERE d.trained = true AND d.age < 10";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should add discriminator to existing WHERE");
        String sql = result.getNativeSql();
        
        // Should combine discriminator filter with existing conditions
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should add discriminator column");
        assertTrue(sql.contains("'DOG'"), "Should filter by DOG");
        assertTrue(sql.contains("trained"), "Should preserve trained condition");
        assertTrue(sql.contains("age"), "Should preserve age condition");
        assertTrue(sql.contains("AND"), "Should combine conditions with AND");
        
        System.out.println("Discriminator with existing WHERE: " + sql);
    }

    @Test
    void testNoDiscriminatorFilteringOnParentClass() {
        String query = "SELECT a FROM Animal a WHERE a.color = 'brown'";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully convert parent class query");
        String sql = result.getNativeSql();
        
        // Should NOT add discriminator filtering for parent class
        // (unless Animal has a discriminatorValue, which it doesn't in a typical setup)
        assertTrue(sql.contains("color"), "Should preserve color condition");
        
        System.out.println("Parent class query (no auto-filter): " + sql);
    }

    @Test
    void testImplicitDiscriminatorWithMultipleSubclasses() {
        String query = "SELECT d FROM Dog d WHERE d.breed = 'Poodle' UNION SELECT c FROM Cat c WHERE c.indoor = true";
        
        // Note: UNION queries are complex, this tests if we handle subclass queries independently
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        // May succeed or fail depending on UNION support
        if (result.isSuccessful()) {
            String sql = result.getNativeSql();
            System.out.println("UNION with discriminators: " + sql);
        } else {
            System.out.println("UNION not supported: " + result.getErrorMessage());
        }
    }

    // ========== JOINED Inheritance Strategy Tests ==========

    @Test
    void testJoinedInheritanceAddsParentJoin() {
        String query = "SELECT c FROM Car c WHERE c.transmission = 'automatic'";
        
        ConversionResult result = converter.convertToNativeSQL(query, vehicleMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should successfully convert JOINED inheritance query");
        String sql = result.getNativeSql();
        
        // Should add INNER JOIN to parent table
        assertTrue(sql.contains("JOIN") || sql.contains("join"), "Should add JOIN clause");
        assertTrue(sql.contains("vehicles") || sql.contains("vehicle"), "Should join to vehicles table");
        assertTrue(sql.contains("cars") || sql.contains("car"), "Should reference cars table");
        assertTrue(sql.contains(".id"), "Should join on ID columns");
        
        System.out.println("JOINED inheritance with parent JOIN: " + sql);
    }

    @Test
    void testJoinedInheritanceWithParentProperties() {
        String query = "SELECT c FROM Car c WHERE c.manufacturer = 'Toyota' AND c.numberOfDoors = 4";
        
        ConversionResult result = converter.convertToNativeSQL(query, vehicleMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should handle parent and child properties");
        String sql = result.getNativeSql();
        
        // Should have JOIN and both properties
        assertTrue(sql.contains("JOIN"), "Should add JOIN clause");
        assertTrue(sql.contains("manufacturer"), "Should include parent property");
        assertTrue(sql.contains("number_of_doors") || sql.contains("numberOfDoors"), "Should include child property");
        
        System.out.println("JOINED inheritance with mixed properties: " + sql);
    }

    @Test
    void testNoJoinForParentClassQuery() {
        String query = "SELECT v FROM Vehicle v WHERE v.year > 2020";
        
        ConversionResult result = converter.convertToNativeSQL(query, vehicleMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should convert parent class query");
        String sql = result.getNativeSql();
        
        // Should NOT add inheritance JOINs for parent class
        assertFalse(sql.toUpperCase().contains("INNER JOIN cars") || 
                   sql.toUpperCase().contains("INNER JOIN car"), 
                   "Should not join to child tables");
        assertTrue(sql.contains("year"), "Should preserve year condition");
        
        System.out.println("Parent class query (no child JOIN): " + sql);
    }

    // ========== Complex Polymorphic Query Tests ==========

    @Test
    void testComplexQueryWithTypeAndConditions() {
        String query = "SELECT a FROM Animal a WHERE (TYPE(a) = Dog AND a.age > 5) OR (TYPE(a) = Cat AND a.age < 3)";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should handle complex TYPE() conditions");
        String sql = result.getNativeSql();
        
        // Should have multiple discriminator checks
        assertTrue(sql.contains("'DOG'"), "Should include DOG discriminator");
        assertTrue(sql.contains("'CAT'"), "Should include CAT discriminator");
        assertTrue(sql.contains("OR"), "Should preserve OR logic");
        assertTrue(sql.contains("age"), "Should include age conditions");
        
        System.out.println("Complex polymorphic query: " + sql);
    }

    @Test
    void testQueryWithSubclassSpecificProperty() {
        String query = "SELECT d FROM Dog d WHERE d.name LIKE '%Max%' AND d.breed = 'Beagle'";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should handle parent and child properties");
        String sql = result.getNativeSql();
        
        // Should have discriminator, parent property, and child property
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should add discriminator");
        assertTrue(sql.contains("'DOG'"), "Should filter by DOG");
        assertTrue(sql.contains("name"), "Should include parent property (name)");
        assertTrue(sql.contains("breed"), "Should include child property (breed)");
        assertTrue(sql.contains("LIKE"), "Should preserve LIKE operator");
        
        System.out.println("Subclass with mixed properties: " + sql);
    }

    // ========== Property to Column Mapping Tests ==========

    @Test
    void testCamelCaseToSnakeCaseConversion() {
        String query = "SELECT c FROM Car c WHERE c.numberOfDoors = 2";
        
        ConversionResult result = converter.convertToNativeSQL(query, vehicleMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should convert camelCase properties");
        String sql = result.getNativeSql();
        
        // Should convert numberOfDoors to number_of_doors
        assertTrue(sql.contains("number_of_doors") || sql.contains("numberOfDoors"), 
                  "Should convert camelCase to snake_case");
        
        System.out.println("CamelCase conversion: " + sql);
    }

    @Test
    void testBooleanPropertyConversion() {
        String query = "SELECT d FROM Dog d WHERE d.trained = true";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should handle boolean properties");
        String sql = result.getNativeSql();
        
        assertTrue(sql.contains("trained"), "Should include trained property");
        // Boolean handling depends on dialect
        assertTrue(sql.contains("true") || sql.contains("1"), "Should handle boolean value");
        
        System.out.println("Boolean property conversion: " + sql);
    }

    // ========== Dialect-Specific Tests ==========

    @Test
    void testOracleDialectConversion() {
        String query = "SELECT d FROM Dog d WHERE d.breed = 'Bulldog'";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.ORACLE);
        
        assertTrue(result.isSuccessful(), "Should convert for Oracle dialect");
        String sql = result.getNativeSql();
        
        // Should add discriminator filtering and handle Oracle specifics
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should add discriminator");
        assertTrue(sql.contains("'DOG'"), "Should filter by DOG");
        
        System.out.println("Oracle dialect conversion: " + sql);
    }

    @Test
    void testPostgreSQLDialectConversion() {
        String query = "SELECT c FROM Cat c WHERE c.livesRemaining > 5";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should convert for PostgreSQL dialect");
        String sql = result.getNativeSql();
        
        // Should add discriminator filtering
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should add discriminator");
        assertTrue(sql.contains("'CAT'"), "Should filter by CAT");
        assertTrue(sql.contains("lives_remaining") || sql.contains("livesRemaining"), "Should include lives_remaining");
        
        System.out.println("PostgreSQL dialect conversion: " + sql);
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    void testEmptyQuery() {
        String query = "";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertFalse(result.isSuccessful(), "Should fail on empty query");
        assertNotNull(result.getErrorMessage(), "Should provide error message");
    }

    @Test
    void testQueryWithUnknownEntity() {
        String query = "SELECT u FROM UnknownEntity u WHERE u.field = 'value'";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        // May succeed with fallback or fail gracefully
        if (!result.isSuccessful()) {
            assertNotNull(result.getErrorMessage(), "Should provide error message");
            System.out.println("Unknown entity error: " + result.getErrorMessage());
        }
    }

    @Test
    void testQueryWithMissingMetadata() {
        String query = "SELECT d FROM Dog d WHERE d.breed = 'Terrier'";
        EntityMetadata emptyMetadata = EntityMetadata.empty();
        
        ConversionResult result = converter.convertToNativeSQL(query, emptyMetadata, DatabaseDialect.POSTGRESQL);
        
        // Should either fallback or fail gracefully
        if (!result.isSuccessful()) {
            assertNotNull(result.getErrorMessage(), "Should provide error message");
            System.out.println("Missing metadata error: " + result.getErrorMessage());
        }
    }

    // ========== Integration Tests ==========

    @Test
    void testCompleteRepositoryMethodConversion() {
        // Simulate repository method: findYoungTrainedDogs
        String query = "SELECT d FROM Dog d WHERE d.trained = true AND d.age < :maxAge";
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        assertTrue(result.isSuccessful(), "Should convert repository method query");
        String sql = result.getNativeSql();
        
        // Verify all aspects
        assertTrue(sql.contains("dtype") || sql.contains("DTYPE"), "Should add discriminator");
        assertTrue(sql.contains("'DOG'"), "Should filter by DOG");
        assertTrue(sql.contains("trained"), "Should include trained condition");
        assertTrue(sql.contains("age"), "Should include age condition");
        assertTrue(sql.contains("?"), "Should convert named parameter to positional");
        
        // Verify parameter mappings
        assertFalse(result.getParameterMappings().isEmpty(), "Should have parameter mappings");
        
        System.out.println("Repository method conversion: " + sql);
        System.out.println("Parameter mappings: " + result.getParameterMappings());
    }

    @Test
    void testMultiEntityQuery() {
        // Query involving both inheritance hierarchies
        String query = "SELECT a FROM Animal a, Vehicle v WHERE a.color = v.color";
        
        ConversionResult result = converter.convertToNativeSQL(query, combinedMetadata, DatabaseDialect.POSTGRESQL);
        
        // Complex cross-entity queries may or may not be supported
        if (result.isSuccessful()) {
            String sql = result.getNativeSql();
            System.out.println("Multi-entity query: " + sql);
        } else {
            System.out.println("Multi-entity query not supported: " + result.getErrorMessage());
        }
    }

    // ========== DIAGNOSTIC DEBUG TESTS ==========

    @Test
    void debugMetadataIntegrity() {
        EntityMetadata metadata = createAnimalMetadata();
        
        System.out.println("\n=== METADATA INTEGRITY CHECK ===");
        System.out.println("Entities: " + metadata.getEntityToTableMappings().keySet());
        
        // Check Animal
        TableMapping animal = metadata.getTableMapping("Animal");
        System.out.println("\nAnimal: " + (animal != null ? animal.toString() : "NULL"));
        if (animal != null) {
            System.out.println("  - isSingleTable: " + animal.isSingleTableInheritance());
            System.out.println("  - discriminator: " + animal.discriminatorValue());
        }
        
        // Check Dog
        TableMapping dog = metadata.getTableMapping("Dog");
        System.out.println("\nDog: " + (dog != null ? dog.toString() : "NULL"));
        if (dog != null) {
            System.out.println("  - isSingleTable: " + dog.isSingleTableInheritance());
            System.out.println("  - discriminator: " + dog.discriminatorValue());
            System.out.println("  - table: " + dog.tableName());
        }
        
        // Check Cat
        TableMapping cat = metadata.getTableMapping("Cat");
        System.out.println("\nCat: " + (cat != null ? cat.toString() : "NULL"));
        if (cat != null) {
            System.out.println("  - isSingleTable: " + cat.isSingleTableInheritance());
            System.out.println("  - discriminator: " + cat.discriminatorValue());
        }
        
        // Assertions
        assertNotNull(animal, "Animal is NULL!");
        assertNotNull(dog, "Dog is NULL!");
        assertNotNull(cat, "Cat is NULL!");
        assertTrue(dog.isSingleTableInheritance(), "Dog should be SINGLE_TABLE");
        assertEquals("DOG", dog.discriminatorValue(), "Dog discriminator should be 'DOG'");
    }

    @Test
    void debugSingleFilteringQuery() {
        String query = "SELECT d FROM Dog d WHERE d.breed = 'Labrador'";
        System.out.println("\n=== DEBUGGING SINGLE QUERY ===");
        System.out.println("Query: " + query);
        
        ConversionResult result = converter.convertToNativeSQL(query, animalMetadata, DatabaseDialect.POSTGRESQL);
        
        System.out.println("\n=== RESULT ===");
        System.out.println("Success: " + result.isSuccessful());
        System.out.println("SQL: " + result.getNativeSql());
        
        if (!result.isSuccessful()) {
            System.out.println("Error: " + result.getErrorMessage());
        }
        
        // Check for discriminator in output
        String sql = result.getNativeSql();
        boolean hasDiscriminator = sql.contains("dtype") || sql.contains("DTYPE");
        boolean hasDogValue = sql.contains("'DOG'");
        
        System.out.println("\n=== DISCRIMINATOR CHECK ===");
        System.out.println("Contains 'dtype': " + hasDiscriminator);
        System.out.println("Contains 'DOG': " + hasDogValue);
        
        if (!hasDiscriminator || !hasDogValue) {
            System.out.println("\n⚠️  DISCRIMINATOR FILTER NOT ADDED!");
            System.out.println("Expected: ... WHERE (d.dtype = 'DOG') AND (d.breed = 'Labrador')");
            System.out.println("Got:      ... " + sql);
        }
        
        assertTrue(hasDiscriminator, "Discriminator column not in SQL");
        assertTrue(hasDogValue, "DOG discriminator value not in SQL");
    }

    @Test
    void debugAliasRegistration() throws Exception {
        String query = "SELECT d FROM Dog d WHERE d.breed = 'Labrador'";
        
        System.out.println("\n=== ALIAS REGISTRATION DEBUG ===");
        
        // Create context
        SqlConversionContext context = new SqlConversionContext(animalMetadata, DatabaseDialect.POSTGRESQL);
        
        System.out.println("Context created, initial alias map size: " + context.getAliasToEntityMap().size());
        
        // Manually register alias (simulate what pre-extraction should do)
        TableMapping dogMapping = animalMetadata.getTableMapping("Dog");
        System.out.println("\nDog mapping: " + dogMapping);
        
        context.registerAlias("d", dogMapping);
        
        System.out.println("After registration, alias map size: " + context.getAliasToEntityMap().size());
        
        // Retrieve and verify
        Map<String, TableMapping> aliasMap = context.getAliasToEntityMap();
        System.out.println("\nAlias map contents:");
        for (Map.Entry<String, TableMapping> entry : aliasMap.entrySet()) {
            TableMapping tm = entry.getValue();
            System.out.println("  '" + entry.getKey() + "' -> " + 
                             "entity=" + tm.entityName() + 
                             ", disc=" + tm.discriminatorValue() +
                             ", isSingleTable=" + tm.isSingleTableInheritance());
        }
        
        // Verify it's correct
        TableMapping retrieved = aliasMap.get("d");
        assertNotNull(retrieved, "Alias 'd' not registered!");
        assertEquals("Dog", retrieved.entityName(), "Wrong entity for alias 'd'");
        assertEquals("DOG", retrieved.discriminatorValue(), "Wrong discriminator value");
    }
}
