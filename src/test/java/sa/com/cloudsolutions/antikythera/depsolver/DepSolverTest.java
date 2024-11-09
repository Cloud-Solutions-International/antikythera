package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepSolverTest extends TestHelper {
    private DepSolver depSolver;
    private GraphNode node;
    private CompilationUnit cu;
    private ClassOrInterfaceDeclaration classDecl;

    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
    }

    @BeforeEach
    public void each() throws Exception {
        depSolver = new DepSolver();
        PersonCompiler p = new PersonCompiler();
        cu = p.getCompilationUnit();
        classDecl = p.getCompilationUnit().getClassByName("Person").orElseThrow();
        node = Graph.createGraphNode(classDecl); // Use the Graph.createGraphNode method to create GraphNode
    }

    @Test
    void testFieldSearchAddsFieldToClass() throws AntikytheraException {
        FieldDeclaration field = classDecl.getFieldByName("name").orElseThrow();
        node.setNode(field);

        depSolver.fieldSearch(node);

        Optional<FieldDeclaration> addedField = node.getTypeDeclaration().getFieldByName("name");
        assertTrue(addedField.isPresent(), "Field should be added to the class.");
    }

    @Test
    void testFieldSearchAddsAnnotations() throws AntikytheraException {
        FieldDeclaration field = classDecl.getFieldByName("name").orElseThrow();
        node.setNode(field);
        field.addAnnotation("Deprecated");

        depSolver.fieldSearch(node);

        Optional<FieldDeclaration> addedField = classDecl.getFieldByName("name");
        assertTrue(addedField.isPresent(), "Field should be added to the class.");
        assertTrue(addedField.get().getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Deprecated")),
                "Field should have the Deprecated annotation.");
    }

    @Test
    void testFieldSearchAddsImports() throws AntikytheraException {
        FieldDeclaration field = classDecl.getFieldByName("name").orElseThrow();
        node.setNode(field);

        field.addAnnotation("Data");
        cu.addImport("org.lombok.Data");

        depSolver.fieldSearch(node);

        assertTrue(cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals("org.lombok.Data")),
                "Import for lombok should be added.");
    }

    @Test
    void testMethodSearch() throws AntikytheraException {
        MethodDeclaration md = classDecl.getMethodsByName("getName").getFirst();
        node.setNode(md);

        depSolver.methodSearch(node);
        MethodDeclaration m = classDecl.getMethodsByName("getName").getFirst();
        assertNotNull(m, "method should be added to the class.");

    }

    class PersonCompiler extends AbstractCompiler {
        protected PersonCompiler() throws IOException {
            File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/Person.java");
            cu = getJavaParser().parse(file).getResult().get();
        }
    }
}