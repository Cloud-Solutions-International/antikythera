package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.MavenHelper;

import java.io.File;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEntities extends TestHelper {

    @BeforeAll
    static void setUp() throws IOException, XmlPullParserException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        MavenHelper mavenHelper = new MavenHelper();
        mavenHelper.readPomFile();
        mavenHelper.buildJarPaths();
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @Test
    void testJson() throws ReflectiveOperationException {
        Evaluator evaluator = EvaluatorFactory.create("sa.com.cloudsolutions.util.Util", Evaluator.class);
        MethodDeclaration md = evaluator.getCompilationUnit().findFirst(
                MethodDeclaration.class, m -> m.getNameAsString().equals("toJson")).orElseThrow();
        Variable result = evaluator.executeMethod(md);
        assertTrue(result.getValue().toString().contains(
                "\"name\":\"Hornblower\",\"address\":\"Admiralty House\",\"phone\":\"Le Harve\",\"email\":\"governor@leharve.fr\""
                ));

    }
}
