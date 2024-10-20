package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
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
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
import java.util.LinkedList;
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
    private static final Map<String, RepositoryParser> repositories = new HashMap<>();

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
    private static HashMap<Integer, LineOfCode> lines = new HashMap<>();

    private final Set<IfStmt> branching = new HashSet<>();

    /**
     * It is better to use create evaluator
     * @param className the name of the class associated with this evaluator
     */
    public SpringEvaluator(String className) {
        super(className);
    }

    public static Map<String, RepositoryParser> getRepositories() {
        return repositories;
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
            NodeList<Statement> statements = md.getBody().get().getStatements();
            for (int i =0 ; i < statements.size() ; i++) {
                Statement st = statements.get(i);
                if(!lines.containsKey(st.hashCode())) {
                    mockURIVariables(md);
                    super.executeMethod(md);
                    if(returnFrom != null) {
                        // rewind!
                        //branching.clear();
                        i = 0;
                    }
                }
            }
        } catch (Exception e) {
            throw new EvaluatorException("Error while mocking controller arguments", e);
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
                executeStatement(stmt);
                if (returnFrom != null) {
                    break;
                }
            }
        } catch (EvaluatorException|ReflectiveOperationException ex) {
            throw ex;
        } catch (Exception e) {
            handleApplicationException(e);
        }
    }

    @Override
    void executeStatement(Statement stmt) throws Exception {
        if(!stmt.isIfStmt()) {
            LineOfCode l = lines.get(stmt.hashCode());
            if (l == null) {
                l = new LineOfCode(stmt);
                l.setColor(LineOfCode.BLACK);
                lines.put(stmt.hashCode(), l);
            }
            else {
                l.setColor(LineOfCode.BLACK);
            }
        }
        super.executeStatement(stmt);
    }

    /**
     * The URL contains Path variables, Query string parameters and post bodies. We mock them here
     * @param md The method declaration representing an HTTP API end point
     * @throws Exception if the variables cannot be mocked.
     */
    private void mockURIVariables(MethodDeclaration md) throws ReflectiveOperationException {
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

                        Evaluator o = new SpringEvaluator(className);
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
        RepositoryParser repository = repositories.get(name);
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
                                repositories.put(variable.getNameAsString(), parser);
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
        buildPreconditions();
        super.executeReturnStatement(stmt);
        if (parent.isPresent() ) {
            // the return statement will have a parent no matter what but the optionals approach
            // requires the use of isPresent.
            ControllerResponse response = evaluateReturnStatement(parent.get(), stmt);
            return createTests(response);
        }

        return null;
    }

    private void buildPreconditions() {
        List<Expression> expressions = new ArrayList<>();
        for (LineOfCode l : lines.values()) {
            if(branching.contains(l.getStatement())) {
                expressions.addAll(l.getPrecondition(false));
            }
        }
        for(TestGenerator gen : generators) {
            gen.setPreconditions(expressions);
        }
    }

    private Variable createTests(ControllerResponse response) {
        if (response != null) {
            for (TestGenerator generator : generators) {
                generator.createTests(currentMethod, response);
            }
            return new Variable(response);
        }
        return null;
    }

    private ControllerResponse evaluateReturnStatement(Node parent, ReturnStmt stmt) {
        return new ControllerResponse(returnValue);

    }

    public void addGenerator(TestGenerator generator) {
        generators.add(generator);
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


            Evaluator eval = new SpringEvaluator(resolvedClass);
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

    /**
     * Handle if then else statements.
     *
     * We use truth tables to analyze the condition and to set values on the DTOs accordingly.
     *
     * @param ifst If / Then statement
     * @throws Exception
     */
    @Override
    Variable ifThenElseBlock(IfStmt ifst) throws Exception {
        LineOfCode l = lines.get(ifst.hashCode());
        if (l == null) {
            /*
             * This if condition has never been executed before. First we will determine if the condition
             * evaluates to true or false. Then we will use the truth table to find out what values will
             * result in it going from true to false or false to true.
             */
            l = new LineOfCode(ifst);
            lines.put(ifst.hashCode(), l);
            l.setColor(LineOfCode.GREY);

            branching.add(ifst);
            Variable v = super.ifThenElseBlock(ifst);
            if ((Boolean)v.getValue()) {
                setupIfCondition(ifst, false);
            } else {
                setupIfCondition(ifst, true);
            }
            return v;
        }
        else if (l.getColor() == LineOfCode.GREY) {
            /*
             * We have been here before but only traversed the then part of the if statement.
             */
            for (Expression st : l.getPrecondition(false)) {
                evaluateExpression(st);
            }
            if (allVisited(ifst)) {
                l.setColor(LineOfCode.BLACK);
            }
            else {
                return super.ifThenElseBlock(ifst);
            }
        } else if (ifst.getElseStmt().isPresent()) {
            l = lines.get(ifst.getElseStmt().get());
            if (l == null || l.getColor() != LineOfCode.BLACK) {
                l.setColor(LineOfCode.GREY);
                return super.ifThenElseBlock(ifst);
            }
        } else {
            l.setColor(LineOfCode.BLACK);
        }
        return null;
    }

    private void setupIfCondition(IfStmt ifst, boolean state) {
        TruthTable tt = new TruthTable(ifst.getCondition());

        LineOfCode l = lines.get(ifst.hashCode());
        List<Map<Expression, Object>> values = tt.findValuesForCondition(state);

        if (!values.isEmpty()) {
            Map<Expression, Object> value = values.getFirst();
            for (var entry : value.entrySet()) {
                if(entry.getKey().isMethodCallExpr()) {
                    LinkedList<Expression> chain = findScopeChain(entry.getKey());
                    if (!chain.isEmpty()) {
                        Expression expr = chain.getFirst();
                        Variable v = getValue(ifst, expr.toString());
                        if (v != null) {
                            MethodCallExpr setter = new MethodCallExpr();
                            setter.setName("set" + entry.getKey().asMethodCallExpr().getNameAsString().substring(3));
                            setter.setScope(expr);
                            setter.addArgument("1L");
                            l.addPrecondition(setter, state);
                        }
                    }
                }
            }
        }
    }

    private boolean checkStatements(List<Statement> statements) {
        for (Statement line : statements) {
            if (line.isIfStmt()) {
                if (!allVisited(line.asIfStmt())) {
                    return false;
                }
            } else if (!isLineVisited(line)) {
                return false;
            }
        }
        return true;
    }

    public boolean allVisited(IfStmt stmt) {
        LineOfCode l = lines.get(stmt.hashCode());
        if (l == null) {
            return false;
        }
        if (l.getColor() == LineOfCode.BLACK) {
            return true;
        }
        Statement then = stmt.getThenStmt();
        if (then.isBlockStmt()) {
            if (!checkStatements(then.asBlockStmt().getStatements())) {
                return false;
            }
        } else {
            if (!isLineVisited(then)) {
                return false;
            }
        }
        if (stmt.getElseStmt().isPresent()) {
            Statement elseStmt = stmt.getElseStmt().get();
            if (elseStmt.isBlockStmt()) {
                if (!checkStatements(elseStmt.asBlockStmt().getStatements())) {
                    return false;
                }
            } else {
                if (!isLineVisited(elseStmt)) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean isLineVisited(Statement stmt) {
        LineOfCode l = lines.get(stmt.hashCode());
        if (l == null) {
            return false;
        }
        return l.getColor() == LineOfCode.BLACK;
    }

    public void resetColors() {
        lines.clear();
    }

    @Override
    Variable executeSource(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        Expression expression = methodCall.getScope().orElseGet(null);
        if (expression != null && expression.isNameExpr()) {
            RepositoryParser rp = repositories.get(expression.asNameExpr().getNameAsString());
            if (rp != null) {
                RepositoryQuery q = executeQuery(expression.asNameExpr().getNameAsString(), methodCall);
                if (q != null) {
                    return new Variable(q);
                }
            }
        }
        return super.executeSource(methodCall);
    }
}

