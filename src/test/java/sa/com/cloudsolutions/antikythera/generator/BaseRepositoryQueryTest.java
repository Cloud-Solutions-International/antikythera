package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseRepositoryQueryTest {

    @BeforeAll
    static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        // Reset any static state if needed
    }

    @Test
    void testProcessJoinWithValidJoinColumn() throws EvaluatorException {
        // Create a mock Join with a right item that has the expected format
        Join mockJoin = mock(Join.class);
        Table mockTable = mock(Table.class);
        when(mockTable.toString()).thenReturn("p.department d");
        when(mockJoin.getRightItem()).thenReturn(mockTable);
        when(mockJoin.getFromItem()).thenReturn(mockTable);
        when(mockJoin.getOnExpressions()).thenReturn(new ArrayList<>());

        // Create a TypeWrapper for the main entity (Person)
        CompilationUnit personCu = new CompilationUnit();
        personCu.setPackageDeclaration("sa.com.cloudsolutions.model");
        var personClass = personCu.addClass("Person");
        
        // Add a department field with @JoinColumn annotation
        FieldDeclaration departmentField = personClass.addField("Department", "department");
        NormalAnnotationExpr joinColumnAnnotation = new NormalAnnotationExpr();
        joinColumnAnnotation.setName("JoinColumn");
        joinColumnAnnotation.addPair("name", new StringLiteralExpr("department_id"));
        joinColumnAnnotation.addPair("referencedColumnName", new StringLiteralExpr("id"));
        departmentField.addAnnotation(joinColumnAnnotation);
        
        TypeWrapper personWrapper = new TypeWrapper(personClass);
        
        // Create a list with the person entity
        List<TypeWrapper> units = new ArrayList<>();
        units.add(personWrapper);

        // Test the protected method with parts array
        String[] parts = {"p", "department d"};
        
        // Call the protected method under test
        TypeWrapper result = BaseRepositoryQuery.processJoin(mockJoin, units, parts);

        // Verify that the join was processed (onExpressions should be populated)
        verify(mockJoin, atLeastOnce()).getOnExpressions();
    }

    @Test
    void testProcessJoinWithSingleMemberAnnotation() throws EvaluatorException {
        // Create a mock Join
        Join mockJoin = mock(Join.class);
        Table mockTable = mock(Table.class);
        when(mockTable.toString()).thenReturn("p.department d");
        when(mockJoin.getRightItem()).thenReturn(mockTable);
        when(mockJoin.getFromItem()).thenReturn(mockTable);
        when(mockJoin.getOnExpressions()).thenReturn(new ArrayList<>());

        // Create a TypeWrapper for the main entity
        CompilationUnit personCu = new CompilationUnit();
        personCu.setPackageDeclaration("sa.com.cloudsolutions.model");
        var personClass = personCu.addClass("Person");
        
        // Add a department field with single member @JoinColumn annotation
        FieldDeclaration departmentField = personClass.addField("Department", "department");
        SingleMemberAnnotationExpr joinColumnAnnotation = new SingleMemberAnnotationExpr();
        joinColumnAnnotation.setName("JoinColumn");
        joinColumnAnnotation.setMemberValue(new StringLiteralExpr("department_id"));
        departmentField.addAnnotation(joinColumnAnnotation);
        
        TypeWrapper personWrapper = new TypeWrapper(personClass);
        
        List<TypeWrapper> units = new ArrayList<>();
        units.add(personWrapper);

        // Test the protected method with parts array
        String[] parts = {"p", "department d"};

        // Call the protected method under test
        TypeWrapper result = BaseRepositoryQuery.processJoin(mockJoin, units, parts);

        // Verify that the join was processed
        verify(mockJoin, atLeastOnce()).getOnExpressions();
    }

    @Test
    void testProcessJoinWithNoMatchingField() throws EvaluatorException {
        // Create a mock Join with a field that doesn't exist
        Join mockJoin = mock(Join.class);
        Table mockTable = mock(Table.class);
        when(mockTable.toString()).thenReturn("p.nonExistentField n");
        when(mockJoin.getRightItem()).thenReturn(mockTable);

        // Create a TypeWrapper for an entity without the referenced field
        CompilationUnit personCu = new CompilationUnit();
        personCu.setPackageDeclaration("sa.com.cloudsolutions.model");
        var personClass = personCu.addClass("Person");
        personClass.addField("String", "name"); // Different field
        
        TypeWrapper personWrapper = new TypeWrapper(personClass);
        
        List<TypeWrapper> units = new ArrayList<>();
        units.add(personWrapper);

        // Test the protected method with parts array
        String[] parts = {"p", "nonExistentField n"};

        // Call the method under test - should not throw exception and return null
        TypeWrapper result = assertDoesNotThrow(() -> BaseRepositoryQuery.processJoin(mockJoin, units, parts));
        assertNull(result);
    }

    @Test
    void testProcessJoinWithEmptyUnits() throws EvaluatorException {
        // Create a mock Join
        Join mockJoin = mock(Join.class);
        
        // Empty units list - no entities to match against
        List<TypeWrapper> units = new ArrayList<>();

        // Test the protected method with valid parts but empty units
        String[] parts = {"p", "department d"};

        // Call the method under test - should return null since no matching entity
        TypeWrapper result = BaseRepositoryQuery.processJoin(mockJoin, units, parts);
        
        // Should return null since no entities to match against
        assertNull(result);
    }

    @Test
    void testProcessJoinWithFieldButNoAnnotation() throws EvaluatorException {
        // Create a mock Join
        Join mockJoin = mock(Join.class);
        Table mockTable = mock(Table.class);
        when(mockTable.toString()).thenReturn("p.department d");
        when(mockJoin.getRightItem()).thenReturn(mockTable);
        when(mockJoin.getFromItem()).thenReturn(mockTable);
        when(mockJoin.getOnExpressions()).thenReturn(new ArrayList<>());

        // Create a TypeWrapper for the main entity
        CompilationUnit personCu = new CompilationUnit();
        personCu.setPackageDeclaration("sa.com.cloudsolutions.model");
        var personClass = personCu.addClass("Person");
        
        // Add a department field WITHOUT @JoinColumn annotation
        FieldDeclaration departmentField = personClass.addField("Department", "department");
        
        TypeWrapper personWrapper = new TypeWrapper(personClass);
        
        List<TypeWrapper> units = new ArrayList<>();
        units.add(personWrapper);

        // Test the protected method with parts array
        String[] parts = {"p", "department d"};

        // Call the method under test - should handle missing annotation gracefully
        TypeWrapper result = assertDoesNotThrow(() -> BaseRepositoryQuery.processJoin(mockJoin, units, parts));
        
        // Should still process but may return null due to missing annotation
        // The exact behavior depends on RepositoryParser.findEntity implementation
    }
}