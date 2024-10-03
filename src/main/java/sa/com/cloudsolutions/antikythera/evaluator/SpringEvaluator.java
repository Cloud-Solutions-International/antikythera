package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ClassProcessor;
import sa.com.cloudsolutions.antikythera.generator.ControllerResponse;
import sa.com.cloudsolutions.antikythera.parser.DTOHandler;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.generator.SpringTestGenerator;
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
    private static final Map<String, RepositoryParser> respositories = new HashMap<>();

    private final List<TestGenerator> generators  = new ArrayList<>();

    private MethodDeclaration currentMethod;

    public static Map<String, RepositoryParser> getRepositories() {
        return respositories;
    }

    private boolean flunk = true;

    @Override
    public void executeMethod(MethodDeclaration md) throws EvaluatorException {
        md.getParentNode().ifPresent(p -> {
            if (p instanceof ClassOrInterfaceDeclaration cdecl) {
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
                    case "Boolean" -> false;
                    case "float", "Float", "double", "Double" -> 0.0;
                    case "Integer", "int" -> 0;
                    case "Long", "long" -> 0L;
                    case "String" -> "Ibuprofen";
                    default -> "0";
                });
                AntikytheraRunTime.push(v);
            } else if (paramString.startsWith("@RequestBody")) {
                Type t = param.getType();
                if (t.isClassOrInterfaceType()) {
                    Object o = DTOBuddy.createDynamicDTO(t.asClassOrInterfaceType());
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
                        RepositoryQuery q = executeQuery(scope.get().asNameExpr().getNameAsString(), methodCall);

                        String qualifiedName = decl.getType().resolve().asReferenceType().getQualifiedName();
                        try {
                            if(qualifiedName.startsWith("java.util")) {
                                Class<?> clazz = Class.forName(qualifiedName);
                                Variable v = new Variable(clazz.getMethod("of"));
                                return v;
                            }
                            else {
                                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(qualifiedName);
                                return new Variable(DTOBuddy.createDynamicDTO(cu.getTypes().get(0).asClassOrInterfaceDeclaration()));
                            }
                        } catch (Exception e) {
                            logger.error("Error while creating dynamic DTO {}", decl.getType().resolve().asReferenceType().getQualifiedName());
                            throw  new EvaluatorException("in evaluateVariableDeclaration", e);
                        }
                    }
                    else {
                        Variable v = evaluateMethodCall(methodCall);
                        if (v != null) {
                            v.setType(decl.getType());
                            setLocal(methodCall, decl.getNameAsString(), v);
                        }
                        return v;
                    }
                }
                else if(expression.isObjectCreationExpr()) {
                    return createObject(varDeclExpr, decl, expression.asObjectCreationExpr());
                }
            }
        }
        return null;
    }

    private static RepositoryQuery executeQuery(String name, MethodCallExpr methodCall) {
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
                if (!repoMethod.getNameAsString().equals("save")) {
                    for (int i = 0, j = methodCall.getArguments().size(); i < j; i++) {
                        q.getMethodArguments().add(new RepositoryQuery.QueryMethodArgument(methodCall.getArgument(i), i));
                        q.getMethodParameters().add(new RepositoryQuery.QueryMethodParameter(repoMethod.getParameter(i), i));
                    }

                    ResultSet rs = repository.executeQuery(methodCall.getNameAsString(), q);
                    q.setResultSet(rs);
                }
                else {
                    // todo do some fake work here
                }
            } catch (Exception e) {
                logger.warn(e.getMessage());
                logger.warn("Could not execute query {}", methodCall);
            }
            return q;
        }
        return null;
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
    void evaluateReturnStatement(Statement statement) throws EvaluatorException {

        ReturnStmt stmt = statement.asReturnStmt();
        Optional<Node> parent = stmt.getParentNode();
        if (parent.isPresent() ) {
            // the return statement will have a parent no matter what but the optionals approach
            // requires the use of isPresent.
            evaluateReturnStatement(parent.get(), stmt);
        }
        ControllerResponse response = identifyReturnType(stmt.asReturnStmt(), currentMethod);

        if(response != null) {
            if(flunk) {
                for (TestGenerator generator : generators) {
                    generator.createTests(currentMethod, response);
                }
                flunk = false;
            }
            for (TestGenerator generator : generators) {
                generator.createTests(currentMethod, response);
            }
        }
        else {
            super.evaluateReturnStatement(stmt);
        }
    }

    private void evaluateReturnStatement(Node parent, ReturnStmt stmt) throws EvaluatorException {
        try {
            if (parent instanceof IfStmt ifStmt) {
                Expression condition = ifStmt.getCondition();
                if (evaluateValidatorCondition(condition)) {
                    identifyReturnType(stmt, currentMethod);
                    if (!flunk) {
                        buildPreconditions(currentMethod, condition);
                    }
                }
            } else {
                BlockStmt blockStmt = (BlockStmt) parent;
                Optional<Node> gramps = blockStmt.getParentNode();
                if (gramps.isPresent()) {
                    if (gramps.get() instanceof IfStmt ifStmt) {
                        // we have found ourselves a conditional return statement.
                        Expression condition = ifStmt.getCondition();
                        if (evaluateValidatorCondition(condition)) {
                            identifyReturnType(stmt, currentMethod);
                            if (!flunk) {
                                buildPreconditions(currentMethod, condition);
                            }
                        }
                    } else if (gramps.get() instanceof MethodDeclaration) {
                        identifyReturnType(stmt, currentMethod);
                    }
                }
            }
        } catch (EvaluatorException e) {
            logger.error("Evaluator exception");
            logger.error("\t{}", e.getMessage());
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
            return response;
        }
        return null;
    }

    @Override
    Variable handleRegularMethodCall(MethodCallExpr methodCall, Expression scopeExpr, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        if(methodName.equals("save") && scopeExpr.isNameExpr()
                && respositories.get(scopeExpr.asNameExpr().getNameAsString()) != null) {
            return null;
        }
        else{
            return super.handleRegularMethodCall(methodCall, scopeExpr, methodName, paramTypes, args);
        }
    }


    public boolean evaluateValidatorCondition(Expression condition) throws EvaluatorException {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            if(binaryExpr.getOperator().equals(BinaryExpr.Operator.AND)) {
                return evaluateValidatorCondition(left) && evaluateValidatorCondition(right);
            } else if(binaryExpr.getOperator().equals(BinaryExpr.Operator.OR)) {
                return evaluateValidatorCondition(left) || evaluateValidatorCondition(right);
            }
            else {
                return (boolean) evaluateBinaryExpression(binaryExpr.getOperator(), left, right).getValue();
            }
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            Boolean value = (Boolean) evaluateExpression(condition.asNameExpr()).getValue();
            return value != null ? value : false;
        }
        else if(condition.isUnaryExpr()) {
            UnaryExpr unaryExpr = condition.asUnaryExpr();
            Expression expr = unaryExpr.getExpression();
            if(expr.isNameExpr() && getValue(expr, expr.asNameExpr().getNameAsString()) != null) {
                return false;
            }
            logger.warn("Unary expression not supported yet");
        }

        return false;
    }


    /**
     * Identifies the preconditions to be fullfilled by a check point in the controller.
     *
     * A controller may have multiple validations represented by various if conditions, we need to setup
     * parameters so that these conditions will pass and move forward to the next state.
     * @param md
     * @param expr
     */
    private void buildPreconditions(MethodDeclaration md, Expression expr) {
        if(expr instanceof BinaryExpr) {
            BinaryExpr binaryExpr = expr.asBinaryExpr();
            if(binaryExpr.getOperator().equals(BinaryExpr.Operator.AND) || binaryExpr.getOperator().equals(BinaryExpr.Operator.OR)) {
                buildPreconditions(md, binaryExpr.getLeft());
                buildPreconditions(md, binaryExpr.getRight());
            }
            else {
                buildPreconditions(md, binaryExpr.getLeft());
                buildPreconditions(md, binaryExpr.getRight());
            }
        }
        if(expr instanceof MethodCallExpr) {
            MethodCallExpr mce = expr.asMethodCallExpr();
            Parameter reqBody = SpringTestGenerator.findRequestBody(md);
            if(reqBody != null && reqBody.getNameAsString().equals(mce.getScope().get().toString())) {
                try {
                    if(!reqBody.getType().asClassOrInterfaceType().getTypeArguments().isPresent()) {

                        String fullClassName = reqBody.resolve().describeType();
                        String fieldName = ClassProcessor.classToInstanceName(mce.getName().asString().replace("get", ""));

                        DTOHandler handler = new DTOHandler();
                        handler.compile(AbstractCompiler.classToPath(fullClassName));
                        Map<String, FieldDeclaration> fields = AbstractCompiler.getFields(handler.getCompilationUnit(), reqBody.getTypeAsString());

                        FieldDeclaration fieldDeclaration = fields.get(fieldName);
                        if (fieldDeclaration != null) {
                            MethodCallExpr methodCall = DTOHandler.generateRandomValue(fieldDeclaration, handler.getCompilationUnit());
                            for(var gen : generators) {
                                gen.addPrecondition(methodCall);
                            }
                        }
                    }
                } catch (UnsolvedSymbolException e) {
                    logger.warn("Unsolved symbol exception");
                } catch (IOException e) {
                    logger.error("Current controller: {}", md.getParentNode());
                    if(Settings.getProperty("dependencies.on_error").toString().equals("exit")) {
                        throw new GeneratorException("Exception while identifying dependencies", e);
                    }
                    logger.error(e.getMessage());
                }
            }
        }
    }
}
