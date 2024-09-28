package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.ClassProcessor;
import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.RepositoryParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Extends the basic evaluator to provide support for JPA repositories and their special behavior.
 */
public class SpringEvaluator extends Evaluator {

    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static Map<String, RepositoryParser> respositories = new HashMap<>();


    public static Map<String, RepositoryParser> getRepositories() {
        return respositories;
    }

    public void identifyFieldVariables(VariableDeclarator variable) throws IOException, EvaluatorException {
        super.identifyFieldVariables(variable);

        if (variable.getType().isClassOrInterfaceType()) {
            String shortName = variable.getType().asClassOrInterfaceType().getNameAsString();
            if (SpringEvaluator.getRepositories().containsKey(shortName)) {
                return;
            }
            Type t = variable.getType().asClassOrInterfaceType();
            String className = t.resolve().describe();

            ClassProcessor proc = new ClassProcessor();
            proc.compile(AbstractCompiler.classToPath(className));
            CompilationUnit cu = proc.getCompilationUnit();
            for (var typeDecl : cu.getTypes()) {
                if (typeDecl.isClassOrInterfaceDeclaration()) {
                    ClassOrInterfaceDeclaration cdecl = typeDecl.asClassOrInterfaceDeclaration();
                    if (cdecl.getNameAsString().equals(shortName)) {
                        for (var ext : cdecl.getExtendedTypes()) {
                            if (ext.getNameAsString().contains(RepositoryParser.JPA_REPOSITORY)) {
                                /*
                                 * We have found a repository. Now we need to process it. Afterwards
                                 * it will be added to the repositories map, to be identified by the
                                 * field name.
                                 */
                                RepositoryParser parser = new RepositoryParser();
                                parser.compile(AbstractCompiler.classToPath(className));
                                parser.process();
                                respositories.put(variable.getNameAsString(), parser);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }
}
