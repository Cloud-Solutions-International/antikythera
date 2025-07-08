package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSpringGenerator {
    MethodDeclaration md;
    MethodResponse response;
    CompilationUnit cu;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        md = new MethodDeclaration();
        BlockStmt body = new BlockStmt();
        md.setBody(body);
        response = new MethodResponse();
        cu = StaticJavaParser.parse("public class TestDummyFile {}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PathVariable", "NotRelevant"})
    void buildTestMethodCreatesTestMethodWithAnnotationsAndName(String annotation) {
        md.setName("sampleMethod");

        Parameter param1 = new Parameter();
        param1.setType("String");
        param1.setName("param1");
        if (!annotation.isEmpty()) {
            param1.addAnnotation(annotation);
        }

        md.addParameter(param1);

        SpringTestGenerator generator = new SpringTestGenerator(cu);
        generator.setCommonPath("/api");

        MethodDeclaration testMethod = generator.buildTestMethod(md);

        assertNotNull(testMethod);
        assertTrue(testMethod.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("Test")));
        assertTrue(testMethod.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("TestCaseType")));
        if(annotation.equals("PathVariable")) {
            assertEquals("sampleMethodByParam1Test", testMethod.getNameAsString());
        }
        else {
            assertEquals("sampleMethodTest", testMethod.getNameAsString());

        }
        assertTrue(testMethod.getBody().isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GetMapping","PostMapping","DeleteMapping","PutMapping"})
    void testVerbAnnotations(String annotation)  {

        NormalAnnotationExpr annotationExpr = new NormalAnnotationExpr();
        annotationExpr.setName(annotation);
        md.addAnnotation(annotationExpr);

        testVerbs();
    }


    @ParameterizedTest
    @ValueSource(strings = {"RequestMethod.GET","RequestMethod.POST","RequestMethod.DELETE",
            "RequestMethod.PUT"})
    void testRequestMappingAnnotation(String annotation)  {

        NormalAnnotationExpr annotationExpr = new NormalAnnotationExpr();
        annotationExpr.setName("RequestMapping");
        annotationExpr.addPair("method", annotation);
        annotationExpr.addPair("path", "/");
        md.addAnnotation(annotationExpr);

        testVerbs();
    }

    private void testVerbs() {
        SpringTestGenerator generator =new SpringTestGenerator(cu);
        generator.setCommonPath("");
        generator.createTests(md, response);

        CompilationUnit gen = generator.getCompilationUnit();
        assertEquals(1, gen.getTypes().size());
        assertEquals(1, gen.getType(0).getMethods().size());
    }

    @ParameterizedTest
    @CsvSource({
            "String, Antikythera",
            "int, 0",
            "Double, 0.0",
            "Boolean, true",
            "float, 0.0",
            "Long, 0"
    })
    void handleURIVariablesTestPath(String paramType, String paramValue) throws ReflectiveOperationException {
        Parameter param = new Parameter();
        param.setType(paramType);
        param.setName("param1");
        param.addAnnotation("PathVariable");
        md.addParameter(param);

        ControllerRequest request = new ControllerRequest();
        request.setPath("/api/test/{param1}");

        SpringTestGenerator generator = new SpringTestGenerator(cu);
        generator.createTests(md, response);

        DummyArgumentGenerator argumentGenerator = new DummyArgumentGenerator(null);
        argumentGenerator.generateArgument(param);
        generator.setArgumentGenerator(argumentGenerator);
        generator.handleURIVariables(request);

        assertEquals("/api/test/" + paramValue, request.getPath());
    }

    @ParameterizedTest
    @CsvSource({
            "String, med, Antikythera",
            "Boolean, flag, true",
            "int, number, 0",
            "java.util.List, dto, []",
    })
    void handleURIVariablesTestQueryString(String paramType, String paramName, String paramValue) throws ReflectiveOperationException {
        cu.addImport("java.util.List");

        Parameter param = new Parameter();
        param.setType(paramType);
        param.setName(paramName);
        param.addAnnotation("RequestParam");
        md.addParameter(param);
        cu.getType(0).addMember(md);

        ControllerRequest request = new ControllerRequest();
        request.setPath("/api/test/");

        SpringTestGenerator generator = new SpringTestGenerator(cu);
        generator.createTests(md, response);

        DummyArgumentGenerator argumentGenerator = new DummyArgumentGenerator(null);
        generator.setArgumentGenerator(argumentGenerator);
        argumentGenerator.generateArgument(param);

        generator.handleURIVariables(request);

        assertEquals("/api/test/", request.getPath());
        assertTrue(request.getQueryParameters().containsKey(paramName));
        assertEquals(argumentGenerator.getArguments().get(paramName).getValue().toString(), paramValue);
    }
}
