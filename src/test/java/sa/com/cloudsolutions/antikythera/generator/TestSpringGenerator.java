package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSpringGenerator {

    @ParameterizedTest
    @ValueSource(strings = {"", "PathVariable", "NotRelevant"})
    void buildTestMethodCreatesTestMethodWithAnnotationsAndName(String annotation) {
        MethodDeclaration md = new MethodDeclaration();
        md.setName("sampleMethod");

        Parameter param1 = new Parameter();
        param1.setType("String");
        param1.setName("param1");
        if (!annotation.isEmpty()) {
            param1.addAnnotation(annotation);
        }

        md.addParameter(param1);

        SpringTestGenerator generator = new SpringTestGenerator();
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
    @ValueSource(strings = {"GetMapping","PostMapping","DeleteMapping"})
    void createTestsCallsCorrectMethods(String annotation)  {
        MethodDeclaration md = new MethodDeclaration();

        NormalAnnotationExpr annotationExpr = new NormalAnnotationExpr();
        annotationExpr.setName(annotation);
        md.addAnnotation(annotationExpr);

        ControllerResponse response = new ControllerResponse();

        SpringTestGenerator generator =new SpringTestGenerator();
        generator.setCompilationUnit(StaticJavaParser.parse("public class TestDummyFile {}"));

        generator.setCommonPath("");

        generator.createTests(md, response);

        CompilationUnit gen = generator.getCompilationUnit();
        assertEquals(1, gen.getTypes().size());
        assertEquals(1, gen.getType(0).getMethods().size());
    }


    @ParameterizedTest
    @ValueSource(strings = {"RequestMethod.GET","RequestMethod.POST","RequestMethod.DELETE",
            "RequestMethod.PUT"})
    void createTestsCallsCorrectMethods2(String annotation)  {
        MethodDeclaration md = new MethodDeclaration();

        NormalAnnotationExpr annotationExpr = new NormalAnnotationExpr();
        annotationExpr.setName("RequestMapping");
        annotationExpr.addPair("method", annotation);
        annotationExpr.addPair("path", "/");
        md.addAnnotation(annotationExpr);

        ControllerResponse response = new ControllerResponse();

        SpringTestGenerator generator =new SpringTestGenerator();
        generator.setCompilationUnit(StaticJavaParser.parse("public class TestDummyFile {}"));

        generator.setCommonPath("");

        generator.createTests(md, response);

        CompilationUnit gen = generator.getCompilationUnit();
        assertEquals(1, gen.getTypes().size());
        assertEquals(1, gen.getType(0).getMethods().size());
    }

    @ParameterizedTest
    @CsvSource({
            "String, Ibuprofen",
            "int, 0",
            "Boolean, false",
            "float, 0.0",
            "Long, 0"
    })
    void handleURIVariablesTestPath(String paramType, String paramValue) throws ReflectiveOperationException {
        // Arrange
        MethodDeclaration md = new MethodDeclaration();
        Parameter param = new Parameter();
        param.setType(paramType);
        param.setName("param1");
        param.addAnnotation("PathVariable");
        md.addParameter(param);

        ControllerRequest request = new ControllerRequest();
        request.setPath("/api/test/{param1}");

        SpringTestGenerator generator = new SpringTestGenerator();
        DummyArgumentGenerator argumentGenerator = new DummyArgumentGenerator();
        argumentGenerator.generateArgument(param);
        generator.setArgumentGenerator(argumentGenerator);
        generator.handleURIVariables(md, request);

        assertEquals("/api/test/" + paramValue, request.getPath());
    }

    @ParameterizedTest
    @CsvSource({
            "String, med, Ibuprofen",
            "Boolean, flag, false",
            "int, number, 0",
            "dto, dto, 0",
    })
    void handleURIVariablesTestQueryString(String paramType, String paramName, String paramValue) throws ReflectiveOperationException {
        // Arrange
        MethodDeclaration md = new MethodDeclaration();
        Parameter param = new Parameter();
        param.setType(paramType);
        param.setName(paramName);
        param.addAnnotation("RequestParam");
        md.addParameter(param);

        ControllerRequest request = new ControllerRequest();
        request.setPath("/api/test/");

        SpringTestGenerator generator = new SpringTestGenerator();
        DummyArgumentGenerator argumentGenerator = new DummyArgumentGenerator();
        generator.setArgumentGenerator(argumentGenerator);
        argumentGenerator.generateArgument(param);

        generator.handleURIVariables(md, request);

        assertEquals("/api/test/", request.getPath());
        assertTrue(request.getQueryParameters().containsKey(paramName));
        assertEquals(argumentGenerator.getArguments().get(paramName).getValue().toString(), paramValue);
    }
}
