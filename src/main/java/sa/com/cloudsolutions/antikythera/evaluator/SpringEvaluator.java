package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.ClassProcessor;
import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.RepositoryParser;
import com.cloud.api.generator.RepositoryQuery;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Extends the basic evaluator to provide support for JPA repositories and their special behavior.
 */
public class SpringEvaluator extends Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(SpringEvaluator.class);

    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static Map<String, RepositoryParser> respositories = new HashMap<>();


    public static Map<String, RepositoryParser> getRepositories() {
        return respositories;
    }

    @Override
    public void executeMethod(MethodDeclaration md) throws EvaluatorException {
        mockURIVariables(md);
        super.executeMethod(md);
    }

    private void mockURIVariables(MethodDeclaration md) {
        for(var param : md.getParameters()) {
            String paramString = String.valueOf(param);

            if (paramString.startsWith("@RequestParam") || paramString.startsWith("@PathVariable")) {
                Variable v = new Variable(switch (param.getTypeAsString()) {
                    case "Boolean" -> true;
                    case "float", "Float", "double", "Double" -> 1.0;
                    case "Integer", "int" -> 1;
                    case "Long", "long" -> 1L;
                    case "String" -> "Ibuprofen";
                    default -> "0";
                });
                AntikytheraRunTime.push(v);
            }
        }
    }

    /**
     * Evaluates a variable declaration expression.
     * @param expr the expression
     * @return a Variable or null if the expression could not be evaluated or results in null
     * @throws EvaluatorException if there is an error evaluating the expression
     */
    @Override
    Variable evaluateVariableDeclaration(Expression expr) throws EvaluatorException {
        VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
        for (var decl : varDeclExpr.getVariables()) {
            Optional<Expression> init = decl.getInitializer();
            if (init.isPresent()) {
                Expression expression = init.get();
                if (expression.isMethodCallExpr()) {
                    MethodCallExpr methodCall = expression.asMethodCallExpr();
                    Optional<Expression> scope = methodCall.getScope();
                    if(scope.isPresent() && scope.get().isNameExpr()) {
                        executeQuery(scope.get().asNameExpr().getNameAsString(), methodCall);
                    }

                    Variable v = evaluateMethodCall(methodCall);
                    if (v != null) {
                        v.setType(decl.getType());
                        setLocal(methodCall, decl.getNameAsString(), v);
                    }
                    return v;
                }
            }
        }
        return null;
    }

    private static void executeQuery(String name, MethodCallExpr methodCall) {
        RepositoryParser repository = respositories.get(name);
        if(repository != null) {
            RepositoryQuery q = repository.getQueries().get(methodCall.getNameAsString());
            try {
                /*
                 * We have one more challenge; to find the parameters that are being used in the repository
                 * method. These will then have to be mapped to the jdbc placeholders and reverse mapped
                 * to the arguments that are passed in when the method is actually being called.
                 */
                MethodDeclaration repoMethod = repository.getCompilationUnit().getTypes().get(0).getMethodsByName(methodCall.getNameAsString()).get(0);
                for (int i = 0, j = methodCall.getArguments().size(); i < j; i++) {
                    q.getMethodArguments().add(new RepositoryQuery.QueryMethodArgument(methodCall.getArgument(i), i));
                    q.getMethodParameters().add(new RepositoryQuery.QueryMethodParameter(repoMethod.getParameter(i), i));
                }

                ResultSet rs = repository.executeQuery(methodCall.getNameAsString(), q);
                q.setResultSet(rs);
            } catch (Exception e) {
                logger.warn(e.getMessage());
                logger.warn("Could not execute query {}", methodCall);
            }
           // return q;
        }
    }

    @Override
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
