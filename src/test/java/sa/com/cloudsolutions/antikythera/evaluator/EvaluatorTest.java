package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.util.Map;

class EvaluatorTest {

    private Map<String, Comparable> context;
    CompilationUnit dto = StaticJavaParser.parse(
            getClass().getClassLoader().getResourceAsStream("sources/com/csi/expressions/SimpleDTO.java"));
    CompilationUnit exp = StaticJavaParser.parse(
            getClass().getClassLoader().getResourceAsStream("sources/com/csi/expressions/SimpleDTOExpressions.java"));


}
