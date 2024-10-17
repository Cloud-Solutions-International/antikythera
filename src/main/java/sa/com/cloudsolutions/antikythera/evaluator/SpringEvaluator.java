package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ClassProcessor;
import sa.com.cloudsolutions.antikythera.generator.ControllerResponse;
import sa.com.cloudsolutions.antikythera.parser.DTOHandler;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
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

    public SpringEvaluator(String className) {
        super(className);
    }

    public static Map<String, RepositoryParser> getRepositories() {
        return respositories;
    }

    private boolean flunk = true;

    @Override
    public Variable executeMethod(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        md.getParentNode().ifPresent(p -> {
            if (p instanceof ClassOrInterfaceDeclaration cdecl && cdecl.isAnnotationPresent("RestController")) {
                 currentMethod = md;
            }
        });

        try {
            mockURIVariables(md);
        } catch (Exception e) {
            throw new EvaluatorException("Error while mocking controller arguments", e);
        }
        return super.executeMethod(md);
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

            if (paramString.startsWith("@RequestBody")) {
                /*
                 * Request body on the other hand will be more complex and will most likely be a DTO.
                 */
                Type t = param.getType();
                if (t.isClassOrInterfaceType()) {
                    String className = t.asClassOrInterfaceType().resolve().asReferenceType().getQualifiedName();
                    if (className.startsWith("java")) {
                        /*
                         * However you can't rule out the possibility that this is a Map or a List or even a
                         * boxed type.
                         */
                        if (t.asClassOrInterfaceType().isBoxedType()) {
                            Variable v = mockParameter(param.getTypeAsString());
                            /*
                             * We need to push this variable to the stack so that it can be used later.
                             */
                            AntikytheraRunTime.push(v);
                        }
                        else {
                            if (className.startsWith("java.util")) {
                                Variable v = Reflect.variableFactory(className);
                                /*
                                 * Pushed to be popped later in the callee
                                 */
                                AntikytheraRunTime.push(v);
                            }
                            else {
                                Class<?> clazz = Class.forName(className);
                                Variable v = new Variable(clazz.newInstance());
                                /*
                                 * PUsh arguments
                                 */
                                AntikytheraRunTime.push(v);
                            }
                        }
                    }
                    else {

                        Evaluator o = new Evaluator(className);
                        o.setupFields(AntikytheraRunTime.getCompilationUnit(className));
                        Variable v = new Variable(o);
                        /*
                         * Args to be popped by the callee
                         */
                        AntikytheraRunTime.push(v);
                    }
                } else {
                    logger.warn("Unhandled {}", t);
                }
            }
            else {
                /*
                 * Request parameters are typically strings or numbers and these are pushed into the stack
                 * to be popped in the callee
                 */
                Variable v = mockParameter(param.getTypeAsString());
                AntikytheraRunTime.push(v);
            }
        }
    }

    private static Variable mockParameter(String typeName) {
        return new Variable(switch (typeName) {
            case "Boolean" -> false;
            case "float", "Float", "double", "Double" -> 0.0;
            case "Integer", "int" -> 0;
            case "Long", "long" -> 0L;
            case "String" -> "Ibuprofen";
            default -> "0";
        });
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
                MethodDeclaration repoMethod = repository.getMethodDeclaration(methodCall);
                String nameAsString = repoMethod.getNameAsString();
                if ( !(nameAsString.contains("save") || nameAsString.contains("delete") || nameAsString.contains("update"))) {
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
    public void identifyFieldVariables(VariableDeclarator variable) throws IOException, AntikytheraException, ReflectiveOperationException {
        super.identifyFieldVariables(variable);

        if (variable.getType().isClassOrInterfaceType()) {
            detectRepository(variable);
        }
    }

    private static void detectRepository(VariableDeclarator variable) throws IOException {
        String shortName = variable.getType().asClassOrInterfaceType().getNameAsString();
        if (SpringEvaluator.getRepositories().containsKey(shortName)) {
            return;
        }
        Type t = variable.getType().asClassOrInterfaceType();
        String className = t.resolve().describe();

        if (!className.startsWith("java.")) {
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
    Variable executeReturnStatement(Statement statement) throws AntikytheraException, ReflectiveOperationException {
        /*
         * Leg work is done in the overloaded method.
         */
        ReturnStmt stmt = statement.asReturnStmt();
        Optional<Node> parent = stmt.getParentNode();
        super.executeReturnStatement(stmt);
        if (parent.isPresent() ) {
            // the return statement will have a parent no matter what but the optionals approach
            // requires the use of isPresent.
            ControllerResponse response = evaluateReturnStatement(parent.get(), stmt);
            if (response != null) {
                if (flunk) {
                    for (TestGenerator generator : generators) {
                        generator.createTests(currentMethod, response);
                    }
                    flunk = false;
                }
                for (TestGenerator generator : generators) {
                    generator.createTests(currentMethod, response);
                }
                Variable v = new Variable(response);
                AntikytheraRunTime.push(v);
                return v;
            }
        }

        return null;
    }

    private ControllerResponse evaluateReturnStatement(Node parent, ReturnStmt stmt) throws AntikytheraException, ReflectiveOperationException {
        try {
            if (parent instanceof IfStmt ifStmt) {
                Expression condition = ifStmt.getCondition();
                if (evaluateValidatorCondition(condition)) {
                    ControllerResponse v = new ControllerResponse(returnValue);

                    if (!flunk) {
                        buildPreconditions(currentMethod, condition);
                    }
                    return v;
                }
            } else {
                BlockStmt blockStmt = (BlockStmt) parent;
                Optional<Node> gramps = blockStmt.getParentNode();
                if (gramps.isPresent()) {
                    if (gramps.get() instanceof IfStmt ifStmt) {
                        // we have found ourselves a conditional return statement.
                        Expression condition = ifStmt.getCondition();
                        if (evaluateValidatorCondition(condition)) {
                            ControllerResponse response = new ControllerResponse(returnValue);
                            if (!flunk) {
                                buildPreconditions(currentMethod, condition);
                            }
                            return response;
                        }
                    } else if (gramps.get() instanceof MethodDeclaration) {
                        return new ControllerResponse(returnValue);
                    }
                }
            }
        } catch (EvaluatorException e) {
            logger.error("Evaluator exception");
            logger.error("\t{}", e.getMessage());
        }
        return null;
    }

    public void addGenerator(TestGenerator generator) {
        generators.add(generator);
    }


    public boolean evaluateValidatorCondition(Expression condition) throws AntikytheraException, ReflectiveOperationException {
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
     * Identifies the preconditions to be fulfilled by a check point in the controller.
     *
     * A controller may have multiple validations represented by various if conditions, we need to setup
     * parameters so that these conditions will pass and move forward to the next state.
     * @param md the MethodDeclaration for the method being tested.
     * @param expr the expression currently being evaluated
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

    @Override
    boolean resolveFieldRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws AntikytheraException, ReflectiveOperationException {
        if(super.resolveFieldRepresentedByCode(variable, resolvedClass)) {
            return true;
        }
        Optional<Node> parent = variable.getParentNode();
        if (parent.isPresent() && parent.get() instanceof FieldDeclaration fd
                && fd.getAnnotationByName("Autowired").isPresent()) {


            Evaluator eval = new Evaluator(resolvedClass);
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(resolvedClass);
            eval.setupFields(cu);
            Variable v = new Variable(eval);
            fields.put(variable.getNameAsString(), v);

            return true;
        }
        return false;
    }

    @Override
    public Variable evaluateMethodCall(Variable v, MethodCallExpr methodCall) throws AntikytheraException {
        try {
            if (v != null) {
                ReflectionArguments reflectionArguments = Reflect.buildArguments(methodCall, this);

                if (v.getValue() instanceof Evaluator eval) {
                    for (int i = reflectionArguments.getArgs().length - 1; i >= 0; i--) {
                        /*
                         * Push method arguments
                         */
                        AntikytheraRunTime.push(new Variable(reflectionArguments.getArgs()[i]));
                    }
                    return eval.executeMethod(methodCall);
                }

                return reflectiveMethodCall(v, reflectionArguments);
            } else {
                return executeMethod(methodCall);
            }
        } catch (ReflectiveOperationException ex) {
            throw new EvaluatorException("Error evaluating method call", ex);
        }
    }
}
