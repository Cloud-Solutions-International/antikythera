package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class TestGenerator {
    protected ArgumentGenerator argumentGenerator;
    /**
     * The names have already been assigned to various tests.
     * Because of overloaded methods and the need to write multiple tests for a single end point
     * we may end up with duplicate method names. To avoid that we add a suffix of the query
     * string arguments to distinguish overloaded and a alphabetic suffix to identify multiple
     * tests for the same method.
     */
    Set<String> testMethodNames = new HashSet<>();

    /**
     * The compilation unit that represents the tests being generated.
     * We use the nodes of a Java Parser AST to build up the class rather than relying on strings
     *
     */
    CompilationUnit gen = new CompilationUnit();

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

    public abstract void createTests(MethodDeclaration md, ControllerResponse response);

    /**
     * The common path represents the path declared in the RestController.
     * Every method in the end point will be relative to this.
     * @param commonPath the url param for the controller.
     */
    public abstract void setCommonPath(String commonPath);

    public CompilationUnit getCompilationUnit() {
        return gen;
    }

    public void setCompilationUnit(CompilationUnit gen) {
        this.gen = gen;
    }

    public abstract void setPreconditions(List<Expression> expr);

    public abstract boolean isBranched();

    public abstract void setBranched(boolean branched);

    public ArgumentGenerator getArgumentGenerator() {
        return argumentGenerator;
    }

    public void setArgumentGenerator(ArgumentGenerator argumentGenerator) {
        this.argumentGenerator = argumentGenerator;
    }
}
