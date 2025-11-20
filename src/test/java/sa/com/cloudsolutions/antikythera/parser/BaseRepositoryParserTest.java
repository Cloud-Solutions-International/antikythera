package sa.com.cloudsolutions.antikythera.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaseRepositoryParserTest {

    private BaseRepositoryParser parser;

    @BeforeEach
    void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        parser = new BaseRepositoryParser();
    }

    /**
     * Test the extractComponents method with various edge cases involving "In" keyword.
     * The key distinction is:
     * - "In" followed by lowercase = part of field name (e.g., "Invoice", "Invoiced")
     * - "In" followed by uppercase or end of string = SQL IN clause (e.g., "IdIn", "CategoryIn")
     */
    @ParameterizedTest
    @CsvSource({
        // Edge case: "Invoice" field should not be split at "In"
        "findByInvoice, 'findBy,Invoice'",
        // Edge case: "InvoiceItemId" with SQL IN clause at the end
        "findByInvoiceItemIdIn, 'findBy,InvoiceItemId,In'",
        // Edge case: "CategoryIn" with SQL IN clause, followed by "And"
        "findByCategoryInAndStatus, 'findBy,Category,In,And,Status'",
        // Standard cases with SQL IN clause
        "findByIdIn, 'findBy,Id,In'",
        "findByNameIn, 'findBy,Name,In'",
        // Field name starting with "In"
        "findByIndustry, 'findBy,Industry'",
        "findByInternalCode, 'findBy,InternalCode'",
        // Multiple conditions
        "findByInvoiceAndStatus, 'findBy,Invoice,And,Status'",
        "findByInvoiceIdAndStatusIn, 'findBy,InvoiceId,And,Status,In'",
        // NotIn clause (should be matched as a single keyword)
        "findByStatusNotIn, 'findBy,Status,NotIn'",
        "findByInvoiceStatusNotIn, 'findBy,InvoiceStatus,NotIn'",
        // Complex case with ordering
        "findByInvoiceItemIdInOrderByCreatedDateDesc, 'findBy,InvoiceItemId,In,OrderBy,CreatedDate,Desc'",
    })
    void testExtractComponents(String methodName, String expectedComponentsStr) {
        List<String> components = parser.extractComponents(methodName);
        List<String> expected = List.of(expectedComponentsStr.split(","));

        assertEquals(expected, components,
            String.format("Method '%s' should be parsed as %s but got %s",
                methodName, expected, components));
    }

    @Test
    void testExtractComponents_EmptyMethodName() {
        List<String> components = parser.extractComponents("");
        assertTrue(components.isEmpty(), "Empty method name should produce empty components");
    }

    @Test
    void testExtractComponents_OnlyKeyword() {
        List<String> components = parser.extractComponents("findAll");
        assertEquals(List.of("findAll"), components);
    }

    @Test
    void testExtractComponents_MultipleAndOr() {
        List<String> components = parser.extractComponents("findByInvoiceAndStatusOrCategory");
        assertEquals(List.of("findBy", "Invoice", "And", "Status", "Or", "Category"), components);
    }

    @Test
    void testExtractComponents_BetweenClause() {
        List<String> components = parser.extractComponents("findByInvoiceDateBetween");
        assertEquals(List.of("findBy", "InvoiceDate", "Between"), components);
    }

    @Test
    void testExtractComponents_GreaterThanLessThan() {
        List<String> components = parser.extractComponents("findByInvoiceAmountGreaterThanAndStatusIn");
        assertEquals(List.of("findBy", "InvoiceAmount", "GreaterThan", "And", "Status", "In"), components);
    }

    @Test
    void testExtractComponents_IsNullIsNotNull() {
        List<String> components = parser.extractComponents("findByInvoiceIsNullAndInternalCodeIsNotNull");
        assertEquals(List.of("findBy", "Invoice", "IsNull", "And", "InternalCode", "IsNotNull"), components);
    }

    @Test
    void testExtractComponents_LikeContaining() {
        List<String> components = parser.extractComponents("findByInvoiceNumberLikeAndDescriptionContaining");
        assertEquals(List.of("findBy", "InvoiceNumber", "Like", "And", "Description", "Containing"), components);
    }
}

