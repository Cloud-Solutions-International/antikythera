package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ClassProcessor;
import sa.com.cloudsolutions.antikythera.generator.ControllerResponse;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extends the basic evaluator to provide support for JPA repositories and their special behavior.
 */
public class SpringEvaluator extends Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(SpringEvaluator.class);

    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static final Map<String, RepositoryParser> respositories = new HashMap<>();

    /**
     * List of generators that we have.
     *
     * Generators ought to be seperated from the parsers/evaluators because different kinds of
     * tests can be created. They can be unit tests, integration tests, api tests and end to
     * end tests.
     */
    private final List<TestGenerator> generators  = new ArrayList<>();

    /**
     * The method currently being analyzed
     */
    private MethodDeclaration currentMethod;

    /**
     * The lines of code already looked at in the method.
     */
    private Set<LineOfCode> lines = new HashSet<>();

    public SpringEvaluator(String className) {
        super(className);
    }

    public static Map<String, RepositoryParser> getRepositories() {
        return respositories;
    }


    /**
     * Called by the java parser method visitor.
     * This is where the code evaluation begins.
     * @param md The MethodDeclaration being worked on
     * @throws AntikytheraException
     * @throws ReflectiveOperationException
     */
    @Override
    public void visit(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {

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

        for(Statement st : md.getBody().get().getStatements()) {
            LineOfCode b = new LineOfCode(st);

            if(!lines.contains(b)) {
                super.executeMethod(md);
            }
        }
    }

    /**
     * Execute a block of statements.
     *
     * We may end up executing the same block of statements repeatedly until all the branches
     * have been covered.
     *
     * @param statements the collection of statements that make up the block
     * @throws AntikytheraException if there are situations where we cannot process the block
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    protected void executeBlock(List<Statement> statements) throws AntikytheraException, ReflectiveOperationException {
        try {
            for (Statement stmt : statements) {
                LineOfCode b = new LineOfCode(stmt);
                if(!lines.contains(b)) {
                    lines.add(b);
                    if (loops.isEmpty() || loops.peekLast().equals(Boolean.TRUE)) {
                        executeStatement(stmt);
                        if (returnFrom != null) {
                            break;
                        }
                    }
                }
            }
        } catch (EvaluatorException|ReflectiveOperationException ex) {
            throw ex;
        } catch (Exception e) {
            handleApplicationException(e);
        }
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
            return createTests(response);
        }

        return null;
    }

    private Variable createTests(ControllerResponse response) {
        if (response != null) {
            for (TestGenerator generator : generators) {
                generator.createTests(currentMethod, response);
            }
            Variable v = new Variable(response);
            AntikytheraRunTime.push(v);
            return v;
        }
        return null;
    }

    private ControllerResponse evaluateReturnStatement(Node parent, ReturnStmt stmt) throws AntikytheraException, ReflectiveOperationException {
        try {
            if (parent instanceof IfStmt ifStmt) {
                Expression condition = ifStmt.getCondition();
                if (evaluateValidatorCondition(condition)) {
                    ControllerResponse v = new ControllerResponse(returnValue);
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
     * Resolves fields while taking into consideration the AutoWired annotation of srping
     * @param variable a variable declaration statement
     * @param resolvedClass the name of the class that the field is of
     * @return true if the resolution was successfull
     * @throws AntikytheraException
     * @throws ReflectiveOperationException
     */
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
    public Variable evaluateMethodCall(Variable v, MethodCallExpr methodCall) throws EvaluatorException {
        try {
            return super.evaluateMethodCall(v, methodCall);
        } catch (AntikytheraException aex) {
            if (aex instanceof EvaluatorException eex) {
                throw eex;
            }
            ControllerResponse response = new ControllerResponse();
            response.setStatusCode(500);

        }
        return null;
    }

    @Override
    protected Variable checkEquality(Variable left, Variable right) {
        Variable v = super.checkEquality(left, right);
        if (left.getInitializer() != null && right.getInitializer() != null) {
            Node n = left.getInitializer();
            while(n != null) {
                if(n instanceof IfStmt ifStmt) {
                    if (v.getValue() instanceof Boolean b && b) {
                        System.out.println("Condition was true");
                    } else {
                        System.out.println("Condition was false");
                    }
                    break;
                }
                n = n.getParentNode().orElse(null);
            }

        }
        return v;

    }

    @Override
    public Evaluator createEvaluator(String name) {
        return new SpringEvaluator(name);
    }
}
