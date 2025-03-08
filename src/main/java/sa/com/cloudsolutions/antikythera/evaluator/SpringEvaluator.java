package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
     * tests can be created. They can be unit tests, integration tests, api tests and end-to-end
     * tests.
     */
    private final List<TestGenerator> generators  = new ArrayList<>();

    /**
     * The method currently being analyzed
     */
    private MethodDeclaration currentMethod;

    private static final HashMap<Integer, LineOfCode> branching = new HashMap<>();

    private boolean onTest;

    private static ArgumentGenerator argumentGenerator;
    /**
     * It is better to use create evaluator
     * @param className the name of the class associated with this evaluator
     */
    public SpringEvaluator(String className) {
        super(className);
    }

    /**
     * Called by the java parser method visitor.
     *
     * This is where the code evaluation begins. Note that we may run the same code repeatedly
     * so that we can exercise all the paths in the code. This is done by setting the values
     * of variables so that different branches in conditional statements are taken.
     *
     * @param md The MethodDeclaration being worked on
     * @throws AntikytheraException if evaluation fails
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public void visit(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        md.getParentNode().ifPresent(p -> {
            if (p instanceof ClassOrInterfaceDeclaration) {
                currentMethod = md;
            }
        });

        branching.clear();
        preConditions.clear();

        md.accept(new VoidVisitorAdapter<Void>(){
            @Override
            public void visit(IfStmt stmt, Void arg) {
                LineOfCode l = new LineOfCode(stmt);
                branching.putIfAbsent(stmt.hashCode(), l);
            }
        }, null);

        try {
            for (int i = 0; i < branching.size() * 2; i++) {
                mockMethodArguments(md);
                executeMethod(md);
            }
        } catch (AUTException aex) {
            logger.warn("This has probably been handled {}", aex.getMessage());
        }
    }

    @Override
    public Variable executeMethod(CallableDeclaration<?> cd) throws AntikytheraException, ReflectiveOperationException {
        if (cd instanceof MethodDeclaration md) {
            returnFrom = null;
            returnValue = null;

            List<Statement> statements = md.getBody().orElseThrow().getStatements();
            if (setupParameters(md)) {
                applyPreconditions(md);
                executeBlock(statements);
            } else {
                return testForBadRequest();
            }
            return returnValue;
        }
        return null;
    }

    private void applyPreconditions(MethodDeclaration md) throws ReflectiveOperationException {
        for (Expression cond : preConditions.getOrDefault(md, Collections.emptySet())) {
            evaluateExpression(cond);
        }
    }

    private Variable testForBadRequest() {
        MethodResponse cr = new MethodResponse();
        ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        cr.setResponse(new Variable(response));

        return createTests(cr);
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
        } catch (Exception e) {
            handleApplicationException(e);
        }
    }

    @Override
    protected void handleApplicationException(Exception e) throws AntikytheraException, ReflectiveOperationException {
        if (! (e instanceof AntikytheraException ae)) {
            if (catching.isEmpty()) {
                EvaluatorException ex = new EvaluatorException(e.getMessage(), e);
                ex.setError(EvaluatorException.INTERNAL_SERVER_ERROR);
                testForInternalError(null,ex);
                throw new AUTException(e.getMessage());
            } else {
                super.handleApplicationException(e);
            }
        }
        else {
            throw ae;
        }
    }

    /**
     * Mocks method arguments.
     * In the case of a rest api controller, the URL contains Path variables, Query string
     * parameters and post bodies. We mock them here with the help of the argument generator.
     * In the case of services and other classes we can use a mocking library.
     * @param md The method declaration representing an HTTP API end point
     * @throws ReflectiveOperationException if the variables cannot be mocked.
     */
    private void mockMethodArguments(MethodDeclaration md) throws ReflectiveOperationException {
        for (int i = md.getParameters().size() - 1; i >= 0; i--) {
            var param = md.getParameter(i);
            argumentGenerator.generateArgument(param);
        }
    }

    /**
     * Execute a query on a repository.
     * @param name the name of the repository
     * @param methodCall the method call expression
     * @return the result set
     */
    private RepositoryQuery executeQuery(Expression name, MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        RepositoryParser repository = repositories.get(getFieldClass(name));
        if(repository != null) {
            MCEWrapper methodCallWrapper = wrapCallExpression(methodCall);

            Optional<Callable> callable = AbstractCompiler.findMethodDeclaration(
                    methodCallWrapper, repository.getCompilationUnit().getType(0));
            if (callable.isPresent()) {
                RepositoryQuery q = repository.get(callable.get());

                try {
                    /*
                     * We have one more challenge; to find the parameters that are being used in the repository
                     * method. These will then have to be mapped to the jdbc placeholders and reverse mapped
                     * to the arguments that are passed in when the method is actually being called.
                     */
                    String nameAsString = callable.get().getNameAsString();
                    if (!(nameAsString.contains("save") || nameAsString.contains("delete") || nameAsString.contains("update"))) {
                        q.getMethodArguments().clear();
                        for (int i = 0, j = methodCall.getArguments().size(); i < j; i++) {
                            q.getMethodArguments().add(null);
                        }
                        for (int i = methodCall.getArguments().size() - 1; i >= 0; i--) {
                            QueryMethodArgument qa = new QueryMethodArgument(methodCall.getArgument(i), i, AntikytheraRunTime.pop());
                            q.getMethodArguments().set(i, qa);
                        }

                        repository.executeQuery(callable.get());
                        DatabaseArgumentGenerator.setQuery(q);
                    } else {
                        Optional<Boolean> write = Settings.getProperty("database.write_ops",Boolean.class);
                        if (write.isPresent() && write.get()) {
                            // todo this needs to be completed
                        }
                        q.setWriteOps(true);
                        return q;
                    }
                } catch (Exception e) {
                    logger.warn(e.getMessage());
                    logger.warn("Could not execute query {}", methodCall);
                }
                return q;
            }
        }
        return null;
    }

    /**
     * Identify fields in the class.
     * This process needs to be carried out before executing any code.
     * @param field the field declaration
     * @throws IOException if the file cannot be read
     * @throws AntikytheraException if there is an error in the code
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public void identifyFieldDeclarations(VariableDeclarator field) throws AntikytheraException, ReflectiveOperationException, IOException {
        super.identifyFieldDeclarations(field);
        detectRepository(field);
    }

    /**
     * Detect a JPA repository.
     * @param variable the variable declaration
     * @throws IOException if the file cannot be read
     */
    private static void detectRepository(VariableDeclarator variable) throws IOException {
        if (variable.getType() == null || !variable.getType().isClassOrInterfaceType()) {
            return;
        }

        ClassOrInterfaceType t = variable.getType().asClassOrInterfaceType();
        String shortName = t.getNameAsString();

        String className = AbstractCompiler.findFullyQualifiedTypeName(variable);
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
        if (cu != null) {
            var typeDecl = AbstractCompiler.getMatchingType(cu, shortName);
            if (typeDecl != null && typeDecl.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cdecl = typeDecl.asClassOrInterfaceDeclaration();

                for (var ext : cdecl.getExtendedTypes()) {
                    if (ext.getNameAsString().contains(RepositoryParser.JPA_REPOSITORY)) {
                        /*
                         * We have found a repository. Now we need to process it. Afterward
                         * it will be added to the repositories map, to be identified by the
                         * field name.
                         */
                        RepositoryParser parser = new RepositoryParser();
                        parser.compile(AbstractCompiler.classToPath(className));
                        parser.processTypes();

                        String fqn = AbstractCompiler.findFullyQualifiedName(cu, t.getNameAsString());
                        repositories.put(fqn, parser);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Execute a return statement.
     * Overrides the super class method to create tests.
     *
     * @param statement the statement to execute
     * @return the variable that is returned
     * @throws AntikytheraException if there is an error in the code
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    Variable executeReturnStatement(Statement statement) throws AntikytheraException, ReflectiveOperationException {
        /*
         * Leg work is done in the overloaded method.
         */
        if (AntikytheraRunTime.isControllerClass(getClassName()) || onTest) {
            ReturnStmt stmt = statement.asReturnStmt();
            Optional<Node> parent = stmt.getParentNode();
            super.executeReturnStatement(stmt);
            if (parent.isPresent() && returnValue != null) {
                if (returnValue.getValue() instanceof MethodResponse mr) {
                    return createTests(mr);
                }
                MethodResponse mr = new MethodResponse();
                mr.setBody(returnValue);
                createTests(mr);
            }
        }
        else {
            return super.executeReturnStatement(statement);
        }

        return null;
    }

    /**
     * Finally create the tests by calling each of the test generators.
     * There maybe multiple test generators, one of unit tests, one of API tests aec.
     * @param response the response from the controller
     * @return a variable that encloses the response
     */
    Variable createTests(MethodResponse response) {
        if (response != null) {
            for (TestGenerator generator : generators) {
                generator.setPreConditions(preConditions.getOrDefault(currentMethod, Collections.emptySet()));
                generator.createTests(currentMethod, response);
            }
            return new Variable(response);
        }
        return null;
    }

    public void addGenerator(TestGenerator generator) {
        generators.add(generator);
    }

    /**
     * Resolves fields while taking into consideration the AutoWired annotation of spring.
     * When the field declaration is an interface, will try to find a suitable implementation.
     *
     * @param variable a variable declaration statement
     * @param resolvedClass the name of the class that the field is of
     * @return true if the resolution was successful
     * @throws AntikytheraException when the field cannot be resolved
     * @throws ReflectiveOperationException if a reflective operation goes wrong
     */
    @Override
    boolean resolveFieldRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws AntikytheraException, ReflectiveOperationException {
        /*
         * Try to substitute an implementation for the interface.
         */
        String name = AbstractCompiler.findFullyQualifiedName(
                AntikytheraRunTime.getCompilationUnit(resolvedClass),
                variable.getType().asString());

        Set<String> implementations = AntikytheraRunTime.findImplementations(name);
        if (implementations != null) {
            for (String impl : implementations) {
                if (super.resolveFieldRepresentedByCode(variable, impl)) {
                    return true;
                }
                else {
                    if (autoWire(variable, impl)) return true;
                }
            }
        }

        if(super.resolveFieldRepresentedByCode(variable, resolvedClass)) {
            return true;
        }
        return autoWire(variable, resolvedClass);
    }

    private boolean autoWire(VariableDeclarator variable, String resolvedClass) {
        Optional<Node> parent = variable.getParentNode();
        if (parent.isPresent() && parent.get() instanceof FieldDeclaration fd
                && fd.getAnnotationByName("Autowired").isPresent()) {
            Variable v = AntikytheraRunTime.getAutoWire(resolvedClass);
            if (v == null) {
                if (AntikytheraRunTime.isMocked(fd.getElementType())) {
                    Evaluator eval = new MockingEvaluator(resolvedClass);
                    v = new Variable(eval);
                    v.setType(variable.getType());
                    AntikytheraRunTime.autoWire(resolvedClass, v);
                }
                else {
                    Evaluator eval = new SpringEvaluator(resolvedClass);
                    CompilationUnit dependant = AntikytheraRunTime.getCompilationUnit(resolvedClass);
                    v = new Variable(eval);
                    v.setType(variable.getType());
                    AntikytheraRunTime.autoWire(resolvedClass, v);
                    eval.setupFields(dependant);
                }
                fields.put(variable.getNameAsString(), v);
            }

            return true;
        }
        return false;
    }


    @Override
    public Variable evaluateMethodCall(Variable v, MethodCallExpr methodCall) throws EvaluatorException, ReflectiveOperationException {
        try {
            if(methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                String fieldClass = getFieldClass(scope);
                if(repositories.containsKey(fieldClass) && !(v.getValue() instanceof MockingEvaluator)) {
                    boolean isMocked = false;
                    String fieldName = getFieldName(scope);
                    if (fieldName != null) {
                        Variable field = fields.get(fieldName);
                        if (field != null && field.getType() != null) {
                            isMocked = AntikytheraRunTime.isMocked(field.getType());
                        }
                    }
                    if (!isMocked) {
                        return executeSource(methodCall);
                    }
                }
            }
            return super.evaluateMethodCall(v, methodCall);
        } catch (AntikytheraException aex) {
            if (aex instanceof EvaluatorException eex) {
                testForInternalError(methodCall, eex);
                throw eex   ;
            }
        }
        return null;
    }

    private void testForInternalError(MethodCallExpr methodCall, EvaluatorException eex) throws EvaluatorException {
        MethodResponse controllerResponse = new MethodResponse();
        if (eex.getError() != 0 && onTest) {
            Variable r = new Variable(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            controllerResponse.setResponse(r);
            controllerResponse.setExecption(eex);
            createTests(controllerResponse);
            returnFrom = methodCall;
        }
        else {
            throw eex;
        }
    }

    @Override
    public Evaluator createEvaluator(String name) {
        return new SpringEvaluator(name);
    }

    @Override
    Variable ifThenElseBlock(IfStmt ifst) throws Exception {
        LineOfCode l = branching.get(ifst.hashCode());
        if (l == null) {
            return super.ifThenElseBlock(ifst);
        }
        return switch (l.getPathTaken()) {
            case LineOfCode.UNTAVELLED -> {
                Variable v = super.ifThenElseBlock(ifst);
                if ((boolean) v.getValue()) {
                    l.setPathTaken(LineOfCode.TRUE_PATH);
                    setupIfCondition(ifst, false);
                }
                else {
                    l.setPathTaken(LineOfCode.FALSE_PATH);
                    setupIfCondition(ifst, true);
                }
                yield v;
            }
            case LineOfCode.FALSE_PATH -> {
                setupIfCondition(ifst, true);
                l.setPathTaken(LineOfCode.BOTH_PATHS);
                yield super.ifThenElseBlock(ifst);
            }
            case LineOfCode.TRUE_PATH -> {
                setupIfCondition(ifst, false);
                l.setPathTaken(LineOfCode.BOTH_PATHS);
                yield super.ifThenElseBlock(ifst);
            }
            default -> null;
        };
    }

    /**
     * Set up an if condition so that it will evaluate to true or false in future executions.
     * @param ifst the if statement to mess with
     * @param state the desired state.
     */
    private void setupIfCondition(IfStmt ifst, boolean state)  {
        TruthTable tt = new TruthTable(ifst.getCondition());
        List<Map<Expression, Object>> values = tt.findValuesForCondition(state);

        if (!values.isEmpty()) {
            Map<Expression, Object> value = values.getFirst();
            for (var entry : value.entrySet()) {
                if(entry.getKey().isMethodCallExpr()) {

                    LinkedList<Expression> chain = Evaluator.findScopeChain(entry.getKey());
                    if (!chain.isEmpty()) {
                        Expression expr = chain.getFirst();
                        Variable v = getValue(ifst, expr.toString());
                        if (v == null && expr.isNameExpr()) {
                            /*
                             * This is likely to be a static method.
                             */
                            String fullname = AbstractCompiler.findFullyQualifiedName(cu, expr.asNameExpr().getNameAsString());
                            if(fullname != null) {
                                /*
                                 * The only other possibility is static access on a class
                                 */
                                try {
                                    Class.forName(fullname);

                                } catch (ReflectiveOperationException e) {
                                    /*
                                     * Can probably be ignroed
                                     */
                                    logger.info("Could not create class for {}", fullname);
                                }
                            }
                        }

                        if (v != null && v.getValue() instanceof Evaluator) {
                            setupConditionalVariable(ifst, state, entry, expr);
                        }
                    }
                }
            }
        }
    }

    private void setupConditionalVariable(IfStmt ifst, boolean state, Map.Entry<Expression, Object> entry, Expression scope) {
        MethodCallExpr setter = new MethodCallExpr();
        String name = entry.getKey().asMethodCallExpr().getNameAsString().substring(3);
        setter.setName("set" + name);
        setter.setScope(scope);

        if (entry.getValue() == null) {
            setter.addArgument("null");
        }
        else {
            setter.addArgument(entry.getValue().toString());
        }
        LineOfCode l = branching.get(ifst.hashCode());
        l.addPrecondition(setter, state);
        ifst.findAncestor(MethodDeclaration.class).ifPresent(md -> {
            Set<Expression> expressions = preConditions.get(md);
            if (expressions == null) {
                expressions = new HashSet<>();
                preConditions.put(md, expressions);
            }
            expressions.add(setter);
        });
    }

    /**
     * Check if a collection of statements have been previously executed or not.
     * @param statements The collection of statements to check.
     * @return true if the statements have all been executed.
     */
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

    /**
     * Check if all the code in the then branch as well as the else branch have been executed.
     * @param stmt
     * @return
     */
    public boolean allVisited(IfStmt stmt) {
        LineOfCode l = branching.get(stmt.hashCode());
        if (l == null) {
            return false;
        }
        if (l.getPathTaken() == LineOfCode.TRUE_PATH) {
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

    /**
     * Has this line of code being executed before
     * @param stmt statement representing a line of code (except that a multiline statements are not supported)
     * @return true if this line has been visited.
     */
    private boolean isLineVisited(Statement stmt) {
        LineOfCode l = branching.get(stmt.hashCode());
        if (l == null) {
            return false;
        }
        return l.getPathTaken() == LineOfCode.TRUE_PATH;
    }

    public void resetColors() {
        branching.clear();
    }

    /**
     * Execute a method that's only available to us in source code format.
     * @param methodCall the method call whose execution is to be simulated.
     * @return the result from the execution as a Variable instance
     * @throws AntikytheraException when the source code could not be executed
     * @throws ReflectiveOperationException when a reflective operation fails
     */
    @Override
    Variable executeSource(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        if (AntikytheraRunTime.isControllerClass(getClassName())) {
            for(TestGenerator gen : generators) {
                gen.setBranched(false);
            }
        }
        Expression expression = methodCall.getScope().orElseThrow();
        if (expression.isNameExpr()) {
            RepositoryParser rp = repositories.get(getFieldClass(expression));
            if (rp != null) {
                RepositoryQuery q = executeQuery(expression, methodCall);
                if (q != null) {
                    if (q.isWriteOps()) {
                        return evaluateExpression(methodCall.getArgument(0));
                    }
                    else {
                        return processResult(findExpressionStatement(methodCall), q.getResultSet());
                    }
                }
            }
        }
        return super.executeSource(methodCall);
    }

    private Variable processResult(ExpressionStmt stmt, ResultSet rs) throws AntikytheraException, ReflectiveOperationException {
        if (stmt.getExpression().isVariableDeclarationExpr()) {
            VariableDeclarationExpr vdecl = stmt.getExpression().asVariableDeclarationExpr();
            VariableDeclarator v = vdecl.getVariable(0);

            Type elementType = vdecl.getElementType();
            if (elementType.isClassOrInterfaceType()) {
                ClassOrInterfaceType classType = elementType.asClassOrInterfaceType();
                NodeList<Type> secondaryType = classType.getTypeArguments().orElse(null);

                if (secondaryType != null) {
                    String mainType = classType.getNameAsString();
                    String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(cu,mainType);

                    if (fullyQualifiedName != null) {
                        ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null,
                                secondaryType.get(0).asClassOrInterfaceType(), new NodeList<>());

                        Variable variable = Reflect.variableFactory(fullyQualifiedName);
                        if (mainType.endsWith("List") || mainType.endsWith("Map") || mainType.endsWith("Set")) {
                            for(int i = 0 ; i < 10 ; i++) {
                                Variable row = createObject(stmt, v, objectCreationExpr);
                                if(SpringEvaluator.resultToEntity(row, rs)) {
                                    ((Collection) variable.getValue()).add(row);
                                }
                                else {
                                    break;
                                }
                            }
                        }
                        return variable;
                    }
                }
                else {
                    ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, classType, new NodeList<>());
                    Variable row = createObject(stmt, v, objectCreationExpr);
                    if(SpringEvaluator.resultToEntity(row, rs)) {
                        return row;
                    } else {
                        return new Variable(null);
                    }

                }
            }
        }
        return null;
    }

    /**
     * Converts an SQL row to an Entity.
     * @param variable copy the data from the record into this variable.
     * @param rs the sql result set
     */
    private static boolean resultToEntity(Variable variable, ResultSet rs) {
        try {
            if (variable.getValue() instanceof Evaluator evaluator && rs.next()) {
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(evaluator.getClassName());
                Map<String, Variable> fields = evaluator.getFields();

                for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                    for (VariableDeclarator fieldVar : field.getVariables()) {
                        String fieldName = fieldVar.getNameAsString();
                        try {
                            if (rs.findColumn(RepositoryParser.camelToSnake(fieldName)) > 0) {
                                Object value = rs.getObject(RepositoryParser.camelToSnake(fieldName));
                                Variable v = new Variable(value);
                                v.setType(fieldVar.getType());
                                fields.put(fieldName, v);
                            }
                        } catch (SQLException e) {
                            logger.warn(e.getMessage());
                        }
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        }
        return false;
    }

    String getFieldName(Expression expr) {
        if (expr.isNameExpr()) {
            return expr.asNameExpr().getNameAsString();
        } else if (expr.isMethodCallExpr()) {
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            Expression scope = methodCall.getScope().orElse(null);
            if (scope != null) {
                return getFieldName(scope);
            }
        }
        return null;
    }

    String getFieldClass(Expression expr) {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            Variable v = fields.get(name);
            if (v != null) {
                Type t = v.getType();
                if (t != null) {
                    return AbstractCompiler.findFullyQualifiedName(cu, t.asString());
                }
                else if (v.getValue() instanceof Evaluator eval) {
                    return eval.getClassName();
                }
            }
        }
        else if (expr.isMethodCallExpr()) {
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            Expression scope = methodCall.getScope().orElse(null);
            if (scope != null && scope.isNameExpr()) {
                return getFieldClass(scope);
            }
        }
        return null;
    }

    private ExpressionStmt findExpressionStatement(MethodCallExpr methodCall) {
        Node n = methodCall;
        while (n != null && !(n instanceof MethodDeclaration)) {
            if (n instanceof ExpressionStmt stmt) {
                /*
                 * We have found the expression statement corresponding to this query
                 */
                return stmt;
            }
            n = n.getParentNode().orElse(null);
        }
        return null;
    }

    public void setOnTest(boolean b) {
        onTest = b;
    }

    @Override
    Variable createObject(Node instructionPointer, VariableDeclarator decl, ObjectCreationExpr oce) throws AntikytheraException, ReflectiveOperationException {
        Variable v = super.createObject(instructionPointer, decl, oce);
        ClassOrInterfaceType type = oce.getType();
        if (type.toString().contains("ResponseEntity")) {
            MethodResponse response = new MethodResponse(v);
            response.setType(type);

            Optional<Expression> arg = oce.getArguments().getFirst();
            if (arg.isPresent()) {
                Variable body = evaluateExpression(arg.get());
                response.setBody(body);
                if (type.toString().equals("ResponseEntity")) {
                    response.setType(new ClassOrInterfaceType(null, type.getName(), new NodeList<>(body.getType())));
                }
            }
            return new Variable(response);
        }
        v.setType(type);
        return v;
    }

    public void setArgumentGenerator(ArgumentGenerator argumentGenerator) {
        SpringEvaluator.argumentGenerator = argumentGenerator;
        for (TestGenerator gen : generators) {
            gen.setArgumentGenerator(argumentGenerator);
        }
    }
}

