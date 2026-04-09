package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.resolution.MethodUsage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReturnTypeResolution extends TestHelper {
    private static final String SAMPLE_CLASS =
            "sa.com.cloudsolutions.antikythera.testhelper.evaluator.BranchingCombinations";

    private CompilationUnit cu;
    private SpringEvaluator springEvaluator;

    @BeforeAll
    static void setupFixture() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void each() {
        cu = AntikytheraRunTime.getCompilationUnit(SAMPLE_CLASS);
        springEvaluator = EvaluatorFactory.create(SAMPLE_CLASS, SpringEvaluator.class);
    }

    @Test
    void resolveMethodCallReturnTypeForRepositoryMethods() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialProblemStrings")).orElseThrow();

        MethodCallExpr findActive = findMethodCall(method, "findActive");
        MethodCallExpr findActiveByDiagnosisType = findMethodCall(method, "findActiveByDiagnosisType");

        assertEquals("java.util.List<java.lang.String>", springEvaluator.resolveMethodCallReturnType(findActive).asString());
        assertEquals("java.util.List<java.lang.String>", springEvaluator.resolveMethodCallReturnType(findActiveByDiagnosisType).asString());
    }

    @Test
    void resolveMethodCallReturnTypeForOtherCollaboratorMethods() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("deletedByLookup")).orElseThrow();

        MethodCallExpr findAllRecords = findMethodCall(method, "findAllRecords");
        MethodCallExpr findOpenRecords = findMethodCall(method, "findOpenRecords");
        MethodCallExpr lookup = findMethodCall(method, "lookup");

        assertEquals("java.util.List<sa.com.cloudsolutions.antikythera.testhelper.evaluator.BranchingCombinations.ProblemRecord>",
                springEvaluator.resolveMethodCallReturnType(findAllRecords).asString());
        assertEquals("java.util.List<sa.com.cloudsolutions.antikythera.testhelper.evaluator.BranchingCombinations.ProblemRecord>",
                springEvaluator.resolveMethodCallReturnType(findOpenRecords).asString());
        assertEquals("sa.com.cloudsolutions.antikythera.testhelper.evaluator.BranchingCombinations.DoctorDirectoryEntry",
                springEvaluator.resolveMethodCallReturnType(lookup).asString());
    }

    @Test
    void compilerFindsInterfaceCallablesForRepositoryMethods() throws ReflectiveOperationException {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialProblemStrings")).orElseThrow();
        ClassOrInterfaceDeclaration repository = cu.findFirst(ClassOrInterfaceDeclaration.class,
                c -> c.getNameAsString().equals("CombinationRepository")).orElseThrow();

        MethodCallExpr findActive = findMethodCall(method, "findActive");
        MethodCallExpr findActiveByDiagnosisType = findMethodCall(method, "findActiveByDiagnosisType");

        MCEWrapper activeWrapper = new MCEWrapper(findActive);
        activeWrapper.setArgumentTypes(new NodeList<>(
                StaticJavaParser.parseType("Long"),
                StaticJavaParser.parseType("boolean"),
                StaticJavaParser.parseType("boolean")));
        MCEWrapper diagnosisWrapper = new MCEWrapper(findActiveByDiagnosisType);
        diagnosisWrapper.setArgumentTypes(new NodeList<>(
                StaticJavaParser.parseType("Long"),
                StaticJavaParser.parseType("boolean"),
                StaticJavaParser.parseType("boolean"),
                StaticJavaParser.parseType("String")));

        Optional<Callable> activeCallable = AbstractCompiler.findCallableDeclaration(activeWrapper, repository);
        Optional<Callable> diagnosisCallable = AbstractCompiler.findCallableDeclaration(diagnosisWrapper, repository);

        assertTrue(activeCallable.isPresent(), "Expected to resolve CombinationRepository.findActive");
        assertTrue(diagnosisCallable.isPresent(), "Expected to resolve CombinationRepository.findActiveByDiagnosisType");
        assertEquals("List<String>", activeCallable.orElseThrow().asMethodDeclaration().getType().asString());
        assertEquals("List<String>", diagnosisCallable.orElseThrow().asMethodDeclaration().getType().asString());
    }

    @Test
    void compilerResolvesMethodUsageForRepositoryMethods() {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialProblemStrings")).orElseThrow();

        MethodCallExpr findActive = findMethodCall(method, "findActive");
        MethodCallExpr findActiveByDiagnosisType = findMethodCall(method, "findActiveByDiagnosisType");

        MethodUsage activeUsage = AbstractCompiler.resolveMethodAsUsage(findActive).orElseThrow();
        MethodUsage diagnosisUsage = AbstractCompiler.resolveMethodAsUsage(findActiveByDiagnosisType).orElseThrow();

        assertEquals("findActive(java.lang.Long, boolean, boolean)", activeUsage.getSignature());
        assertEquals("findActiveByDiagnosisType(java.lang.Long, boolean, boolean, java.lang.String)",
                diagnosisUsage.getSignature());
        assertEquals("java.util.List<java.lang.String>", activeUsage.returnType().describe());
        assertEquals("java.util.List<java.lang.String>", diagnosisUsage.returnType().describe());
    }

    @Test
    void compilerResolvesCallableFromResolvedMethodWhenSourceIsAvailable() {
        MethodDeclaration method = cu.findFirst(MethodDeclaration.class,
                md -> md.getNameAsString().equals("sequentialProblemStrings")).orElseThrow();
        MethodCallExpr findActive = findMethodCall(method, "findActive");

        MCEWrapper wrapper = new MCEWrapper(findActive);
        wrapper.setArgumentTypes(new NodeList<>(
                StaticJavaParser.parseType("Long"),
                StaticJavaParser.parseType("boolean"),
                StaticJavaParser.parseType("boolean")));

        Optional<Callable> callable = AbstractCompiler.resolveCallableFromResolvedMethod(findActive, wrapper);
        assertTrue(callable.isPresent(), "Expected resolved source callable for findActive");
        assertTrue(callable.orElseThrow().isMethodDeclaration());
        assertFalse(callable.orElseThrow().asMethodDeclaration().findCompilationUnit().isEmpty());
        assertEquals("List<String>", callable.orElseThrow().asMethodDeclaration().getType().asString());
    }

    private MethodCallExpr findMethodCall(MethodDeclaration method, String name) {
        MethodCallExpr methodCallExpr = method.findFirst(MethodCallExpr.class,
                mce -> mce.getNameAsString().equals(name)).orElseThrow();
        assertNotNull(methodCallExpr);
        return methodCallExpr;
    }
}
