package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.VoidType;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class TestGenerator {
    protected ArgumentGenerator argumentGenerator;
    /**
     * The names have already been assigned to various tests.
     * Because of overloaded methods and the need to write multiple tests for a single end point
     * we may end up with duplicate method names. To avoid that, query arguments are added as
     * suffixes to the method name.
     * When the same method can have multiple tests, an alphabetic suffix is added to the method name.
     */
    Set<String> testMethodNames = new HashSet<>();

    /**
     * The compilation unit that represents the tests being generated.
     * We use the nodes of a Java Parser AST to build up the class rather than relying on strings
     *
     */
    CompilationUnit gen;
    protected Asserter asserter;
    protected MethodDeclaration methodUnderTest;

    MethodDeclaration testMethod;
    protected CompilationUnit compilationUnitUnderTest;

    protected Set<Expression> preConditions;

    static List<Expression> whenThen = new ArrayList<>();

    protected TestGenerator(CompilationUnit cu) {

        this.compilationUnitUnderTest = cu;
    }

    public static void clearWhenThen() {
        whenThen.clear();
    }

    public static void addWhenThen(Expression expr) {
        whenThen.add(expr);
    }

    public static List<Expression> getWhenThen() {
        return whenThen;
    }

    protected String createTestName(MethodDeclaration md) {
        StringBuilder paramNames = new StringBuilder();
        for(var param : md.getParameters()) {
            param.getAnnotationByName("PathVariable").ifPresent(ann ->
                    paramNames.append(param.getNameAsString().substring(0, 1).toUpperCase())
                            .append(param.getNameAsString().substring(1))
            );
        }

        String testName = String.valueOf(md.getName());
        if (paramNames.isEmpty()) {
            testName += "Test";
        } else {
            testName += "By" + paramNames + "Test";

        }

        if (testMethodNames.contains(testName)) {
            testName += "_" + (char)('A' + testMethodNames.size()  % 26 -1);
        }
        testMethodNames.add(testName);
        return testName;
    }

    /**
     * This method will typically be called at the exit point of a method being evaluated.
     * An exit point is when the evaluator runs into a return statement or the last statement.
     * Another way in which a function can exit is when an exception is raised or thrown. In all
     * those scenarios, the evaluator will call this method to generate the tests.
     * @param md the method being tested
     * @param response REST API response if this is a controller method
     */
    public abstract void createTests(MethodDeclaration md, MethodResponse response);

    MethodDeclaration buildTestMethod(MethodDeclaration md) {
        MethodDeclaration tm = new MethodDeclaration();

        md.findAncestor(TypeDeclaration.class).ifPresent(c ->
        {
            String comment = String.format("Method under test: %s.%s()%nArgument generator : %s%nAuthor : Antikythera%n",
                    c.getNameAsString(), md.getNameAsString(), argumentGenerator.getClass().getSimpleName());
            tm.setJavadocComment(comment);
        });

        tm.setName(createTestName(md));

        BlockStmt body = new BlockStmt();

        tm.setType(new VoidType());

        tm.setBody(body);
        tm.addAnnotation("Test");
        return tm;
    }

    /**
     * The common path represents the path declared in the RestController.
     * Every method in the end point will be relative to this.
     * @param commonPath the url param for the controller.
     */
    public abstract void setCommonPath(String commonPath);

    public CompilationUnit getCompilationUnit() {
        return gen;
    }

    public abstract void addBeforeClass();

    public void setArgumentGenerator(ArgumentGenerator argumentGenerator) {
        this.argumentGenerator = argumentGenerator;
    }

    /**
     * Setup fields on the methods being tested.
     * The default behaviour is to do nothing.
     */
    public void mockFields() {

    }


    protected BlockStmt getBody(MethodDeclaration md) {
        return md.getBody().orElseGet(() -> {
            BlockStmt blockStmt = new BlockStmt();
            md.setBody(blockStmt);
            return blockStmt;
        });
    }

    public void save() throws IOException {
    }

    public void setAsserter(Asserter asserter) {
        this.asserter = asserter;
    }

    public void setupImports() {
        asserter.setupImports(gen);
    }

    public void setPreConditions(Set<Expression> preConditions) {
        this.preConditions = preConditions;
    }

    protected void assertThrows(String invocation, MethodResponse response) {
        getBody(testMethod).addStatement(asserter.assertThrows(invocation, response));
    }
}
