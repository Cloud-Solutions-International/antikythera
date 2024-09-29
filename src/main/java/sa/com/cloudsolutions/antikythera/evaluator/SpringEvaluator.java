package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.ClassProcessor;
import com.cloud.api.generator.ControllerResponse;
import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.GeneratorException;
import com.cloud.api.generator.RepositoryParser;
import com.cloud.api.generator.RepositoryQuery;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private List<TestGenerator> generators  = new ArrayList<>();

    private MethodDeclaration currentMethod;

    public static Map<String, RepositoryParser> getRepositories() {
        return respositories;
    }

    @Override
    public void executeMethod(MethodDeclaration md) throws EvaluatorException {
        md.getParentNode().ifPresent(p -> {
            if (p instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration cdecl = (ClassOrInterfaceDeclaration) p;
                if (cdecl.isAnnotationPresent("RestController")) {
                    currentMethod = md;
                }
            }
        });

        try {
            mockURIVariables(md);
        } catch (Exception e) {
            throw new EvaluatorException("Error while mocking controller arguments", e);
        }
        super.executeMethod(md);
    }

    /**
     * The URL contains Path variables, Query string parameters and post bodies. We mock them here
     * @param md The method declaration representing an HTTP API end point
     * @throws Exception if the variables cannot be mocked.
     */
    private void mockURIVariables(MethodDeclaration md) throws Exception {
        for (int i = md.getParameters().size() - 1; i >= 0; i--) {
            var param = md.getParameter(i);
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
            } else if (paramString.startsWith("@RequestBody")) {
                Type t = param.getType();
                if (t.isClassOrInterfaceType()) {
                    Class<?> clazz = DTOBuddy.createDynamicDTO(t.asClassOrInterfaceType());
                    Object o = clazz.getDeclaredConstructor().newInstance();
                    Variable v = new Variable(o);
                    AntikytheraRunTime.push(v);
                } else {
                    logger.warn("Unhandled {}", t);
                }
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

    @Override
    void evaluateReturnStatement(Statement stmt) throws EvaluatorException {
        ControllerResponse response = identifyReturnType(stmt.asReturnStmt(), currentMethod);
        Optional<Expression> expr = stmt.asReturnStmt().getExpression();
        if(expr.isPresent()) {
            returnValue = evaluateExpression(expr.get());
        }
        else {
            returnValue = null;
        }
    }

    public void addGenerator(TestGenerator generator) {
        generators.add(generator);
    }

    /**
     * Extracts the type from a method call expression
     *
     * This is used when the controller directly returns the result of a method call.
     * we iterate through the imports to find the class. Then we iterate through the
     * methods in that class to identify what is being called. Finally when we find
     * the match, we extract it's type.
     *
     * todo - need to improve the handling of overloaded methods.
     *
     * @param type
     * @param methodCallExpr
     * @throws IOException
     */
    private void extractTypeFromCall(Type type, MethodCallExpr methodCallExpr) throws IOException {

    }


    private ControllerResponse identifyReturnType(ReturnStmt returnStmt, MethodDeclaration md) {
        Expression expression = returnStmt.getExpression().orElse(null);
        if (expression != null) {
            ControllerResponse response = new ControllerResponse();
            response.setStatusCode(200);
            if (expression.isObjectCreationExpr()) {
                ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
                if (objectCreationExpr.getType().asString().contains("ResponseEntity")) {
                    for (Expression typeArg : objectCreationExpr.getArguments()) {
                        if (typeArg.isFieldAccessExpr()) {
                            FieldAccessExpr fae = typeArg.asFieldAccessExpr();
                            if (fae.getScope().isNameExpr() && fae.getScope().toString().equals("HttpStatus")) {
                                response.setStatusCode(fae.getNameAsString());
                            }
                        }
                        if (typeArg.isNameExpr()) {
                            String nameAsString = typeArg.asNameExpr().getNameAsString();
                            if (nameAsString != null && getLocal(returnStmt, nameAsString) != null) {
                                response.setType(getLocal(returnStmt, nameAsString).getType());
                            } else {
                                logger.warn("NameExpr is null in identify return type");
                            }
                        } else if (typeArg.isStringLiteralExpr()) {
                            response.setType(StaticJavaParser.parseType("java.lang.String"));
                            response.setResponse(typeArg.asStringLiteralExpr().asString());
                        } else if (typeArg.isMethodCallExpr()) {
                            MethodCallExpr methodCallExpr = typeArg.asMethodCallExpr();
                            try {
                                Optional<Expression> scope = methodCallExpr.getScope();
                                if (scope.isPresent()) {
                                    Variable f = (scope.get().isFieldAccessExpr())
                                            ? fields.get(scope.get().asFieldAccessExpr().getNameAsString())
                                            : fields.get(scope.get().asNameExpr().getNameAsString());
                                    if (f != null) {
                                        extractTypeFromCall(f.getType(), methodCallExpr);
                                        logger.debug(f.toString());
                                    } else {
                                        logger.debug("Type not found {}", scope.get());
                                    }
                                }
                            } catch (IOException e) {
                                throw new GeneratorException("Exception while identifying dependencies", e);
                            }
                        }
                    }
                }
            } else if (expression.isMethodCallExpr()) {
                MethodCallExpr methodCallExpr = expression.asMethodCallExpr();
                try {
                    Optional<Expression> scope = methodCallExpr.getScope();
                    if (scope.isPresent()) {
                        Type type = (scope.get().isFieldAccessExpr())
                                ? fields.get(scope.get().asFieldAccessExpr().getNameAsString()).getType()
                                : fields.get(md.getType().asString()).getType();
                        if(type != null) {
                            extractTypeFromCall(type, methodCallExpr);
                            logger.debug(type.toString());
                        }
                        else {
                            logger.debug("Type not found {}", scope.get());
                        }
                    }
                } catch (IOException e) {
                    throw new GeneratorException("Exception while identifying dependencies", e);
                }
            } else if (expression.isNameExpr()) {
                String nameAsString = expression.asNameExpr().getNameAsString();
                if (nameAsString != null && getLocal(returnStmt, nameAsString) != null) {
                    response.setType(getLocal(returnStmt, nameAsString).getType());
                } else {
                    logger.warn("NameExpr is null in identify return type");
                }
            }
           // createTests(md, response);
            return response;
        }
        return null;
    }
}
