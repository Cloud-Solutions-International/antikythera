package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extends the basic evaluator to provide support for JPA repositories and their special behavior.
 */
public class SpringEvaluator extends ControlFlowEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(SpringEvaluator.class);

    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static final Map<String, RepositoryParser> repositories = new HashMap<>();

    private static ArgumentGenerator argumentGenerator;
    /**
     * <p>List of test generators that we have.</p>
     * <p>
     * Generators ought to be separated from the parsers/evaluators because different kinds of
     * tests can be created. They can be unit tests, integration tests, api tests and end-to-end
     * tests.
     */
    private final List<TestGenerator> generators = new ArrayList<>();
    /**
     * The method currently being analyzed
     */
    private MethodDeclaration currentMethod;
    private boolean onTest;
    private LineOfCode currentConditional;

    protected SpringEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    private static void setupRequestParam(AnnotationExpr a) {
        if (a.isNormalAnnotationExpr()) {
            NormalAnnotationExpr ne = a.asNormalAnnotationExpr();
            for (MemberValuePair pair : ne.getPairs()) {
                if (pair.getNameAsString().equals("required") && pair.getValue().toString().equals("false")) {
                    return;
                }
            }
        }
    }

    /**
     * Detect a JPA repository.
     *
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
            var typeDecl = AbstractCompiler.getMatchingType(cu, shortName).orElse(null);
            if (typeDecl != null && typeDecl.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cdecl = typeDecl.asClassOrInterfaceDeclaration();

                for (var ext : cdecl.getExtendedTypes()) {
                    if (ext.getNameAsString().contains(RepositoryParser.JPA_REPOSITORY)) {
                        /*
                         * We have found a repository. Now we need to process it. Afterward
                         * it will be added to the repository map, to be identified by the
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
     * Converts an SQL row to an Entity.
     *
     * @param variable copy the data from the record into this variable.
     * @param rs       the sql result set
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

    private static void parameterAssignment(Parameter p, AssignExpr assignExpr, Variable va) {
        if (!assignExpr.getTarget().toString().equals(p.getNameAsString())) {
            return;
        }

        Expression value = assignExpr.getValue();
        Object result = switch (va.getClazz().getSimpleName()) {
            case "Integer" -> value instanceof NullLiteralExpr ? null : Integer.parseInt(value.toString());
            case "Double" -> Double.parseDouble(value.toString());
            case "Long" -> Long.parseLong(value.toString());
            case "Float" -> Float.parseFloat(value.toString());
            case "Boolean" -> value.isBooleanLiteralExpr() ? value.asBooleanLiteralExpr().getValue() : value;
            case "Character" -> value.isCharLiteralExpr() ? value.asCharLiteralExpr().getValue() : value;
            case "String" -> {
                if (value.isStringLiteralExpr()) {
                    yield value.asStringLiteralExpr().getValue();
                }
                if (value.isNullLiteralExpr()) {
                    yield null;
                }
                yield value;
            }
            default -> value;
        };
        va.setValue(result);
    }

    /**
     * <p>This is where the code evaluation really starts</p>
     * <p>
     * The method will be called by the java parser method visitor. Note that we may run the same
     * code repeatedly so that we can exercise all the paths in the code.
     * This is done by setting the values of variables to ensure conditionals evaluate to both the
     * true state and the false state
     *
     * @param md The MethodDeclaration being worked on
     * @throws AntikytheraException         if evaluation fails
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public void visit(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        beforeVisit(md);
        try {
            int oldSize = Branching.size(md);

            int safetyCheck = 0;
            while (safetyCheck < 16) {
                getLocals().clear();
                setupFields();
                mockMethodArguments(md);

                currentConditional = Branching.getHighestPriority(md);
                if ((currentConditional == null || currentConditional.isFullyTravelled()) && oldSize != 0) {
                    break;
                }

                executeMethod(md);
                safetyCheck++;
                if (currentConditional != null) {
                    currentConditional.transition();
                    Branching.add(currentConditional);
                }
                if (Branching.size(md) == 0) {
                    break;
                }
                else {
                    oldSize = Branching.size(md);
                }
            }
        } catch (AUTException aex) {
            logger.warn("This has probably been handled {}", aex.getMessage());
        }
    }


    private void beforeVisit(MethodDeclaration md) {
        md.getParentNode().ifPresent(p -> {
            if (p instanceof ClassOrInterfaceDeclaration) {
                currentMethod = md;
            }
        });

        Branching.clear();
        AntikytheraRunTime.reset();

        md.accept(new IfConditionVisitor(), null);
    }

    @Override
    protected void setupParameters(MethodDeclaration md) throws ReflectiveOperationException {
        NodeList<Parameter> parameters = md.getParameters();

        for(int i = parameters.size() - 1 ; i >= 0 ; i--) {
            setupParameter(md, parameters.get(i));
        }

        if (currentConditional != null && currentConditional.getPreconditions() != null) {
            currentConditional.getPreconditions().clear();
        }
    }

    /**
     * Set up the parameters required for the method call.
     * When there are preconditions that need to be applied to get branch coverage, the parameters
     * will be updated to reflect those preconditions.
     *
     * @param md the method declaration into whose variable space this parameter will be copied
     * @param p the parameter in question.
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    void setupParameter(MethodDeclaration md, Parameter p) throws ReflectiveOperationException {
        Variable va = AntikytheraRunTime.pop();
        md.getBody().ifPresent(body ->
                setLocal(body, p.getNameAsString(), va)
        );

        if (currentConditional != null ) {
            if (currentConditional.getStatement() instanceof IfStmt ifStmt) {
                boolean nextState = currentConditional.isFalsePath();
                setupIfCondition(ifStmt, nextState);
            }
            applyPreconditions(p, va);
        }

        md.getBody().ifPresent(body -> {
            setLocal(body, p.getNameAsString(), va);
            p.getAnnotationByName("RequestParam").ifPresent(SpringEvaluator::setupRequestParam);
        });

    }

    private void applyPreconditions(Parameter p, Variable va) throws ReflectiveOperationException {

        for (Precondition cond : currentConditional.getPreconditions()) {
            if (cond.getExpression() instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
                if (mce.getScope().get() instanceof NameExpr ne
                        && ne.getNameAsString().equals(p.getNameAsString())
                        && va.getValue() instanceof Evaluator eval) {
                    MCEWrapper wrapper = eval.wrapCallExpression(mce);
                    eval.executeLocalMethod(wrapper);
                }
            } else if (cond.getExpression() instanceof AssignExpr assignExpr) {
                parameterAssignment(p, assignExpr, va);
            }
        }
    }

    /**
     * <p>Execute a block of statements.</p>
     * <p>
     * When generating tests, we may end up executing the same block of statements repeatedly until
     * all the branches have been covered.
     *
     * @param statements the collection of statements that make up the block
     * @throws AntikytheraException         if there are situations where we cannot process the block
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
        if (!(e instanceof AntikytheraException ae)) {
            if (catching.isEmpty()) {
                EvaluatorException ex = new EvaluatorException(e.getMessage(), e);
                ex.setError(EvaluatorException.INTERNAL_SERVER_ERROR);
                testForInternalError(null, ex);
                throw new AUTException(e);
            } else {
                super.handleApplicationException(e);
            }
        } else {
            throw ae;
        }
    }

    /**
     * Mocks method arguments.
     * In the case of a rest api controller, the URL contains Path variables, Query string
     * parameters and post-bodies. We mock them here with the help of the argument generator.
     * In the case of services and other classes, we can use a mocking library.
     *
     * @param md The method declaration representing an HTTP API end point
     * @throws ReflectiveOperationException if the variables cannot be mocked.
     */
    void mockMethodArguments(MethodDeclaration md) throws ReflectiveOperationException {
        for (int i = md.getParameters().size() - 1; i >= 0; i--) {
            var param = md.getParameter(i);
            argumentGenerator.generateArgument(param);
        }
    }

    /**
     * Execute a query on a repository.
     *
     * @param name       the name of the repository
     * @param methodCall the method call expression
     * @return the result set
     */
    private RepositoryQuery executeQuery(Expression name, MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        RepositoryParser repository = repositories.get(getFieldClass(name));
        if (repository != null) {
            MCEWrapper methodCallWrapper = wrapCallExpression(methodCall);

            Optional<Callable> callable = AbstractCompiler.findCallableDeclaration(
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
                        Optional<Boolean> write = Settings.getProperty("database.write_ops", Boolean.class);
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
     *
     * @param field the field declaration
     * @throws IOException                  if the file cannot be read
     * @throws AntikytheraException         if there is an error in the code
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public Variable identifyFieldDeclarations(VariableDeclarator field) throws AntikytheraException, ReflectiveOperationException, IOException {
        Variable v = super.identifyFieldDeclarations(field);
        detectRepository(field);
        return v;
    }

    /**
     * Execute a return statement.
     * Overrides the super class method to create tests.
     *
     * @param statement the statement to execute
     * @return the variable that is returned
     * @throws AntikytheraException         if there is an error in the code
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
            Variable v = super.executeReturnStatement(stmt);
            if (parent.isPresent() && returnValue != null) {
                if (returnValue.getValue() instanceof MethodResponse mr) {
                    return createTests(mr);
                }
                MethodResponse mr = new MethodResponse();
                mr.setBody(returnValue);
                createTests(mr);
            }
            return v;
        } else {
            return super.executeReturnStatement(statement);
        }
    }

    /**
     * Finally create the tests by calling each of the test generators.
     * There maybe multiple test generators, one of unit tests, one of API tests aec.
     *
     * @param response the response from the controller
     * @return a variable that encloses the response
     */
    Variable createTests(MethodResponse response) {
        if (response != null) {
            for (TestGenerator generator : generators) {
                generator.setPreConditions(Branching.getApplicableConditions(currentMethod));
                generator.createTests(currentMethod, response);
            }
            return new Variable(response);
        }
        return null;
    }

    public void addGenerator(TestGenerator generator) {
        generators.add(generator);
    }

    Variable autoWire(VariableDeclarator variable, String resolvedClass) {
        Optional<Node> parent = variable.getParentNode();
        if (parent.isPresent() && parent.get() instanceof FieldDeclaration fd
                && fd.getAnnotationByName("Autowired").isPresent()) {
            Variable v = AntikytheraRunTime.getAutoWire(resolvedClass);
            if (v == null) {
                if (AntikytheraRunTime.getCompilationUnit(resolvedClass) != null) {
                    v = wireFromSourceCode(variable.getType(), resolvedClass, fd);
                } else {
                    v = wireFromByteCode(resolvedClass);
                }
            }
            fields.put(variable.getNameAsString(), v);
            return v;
        }
        return null;
    }

    private static Variable wireFromByteCode(String resolvedClass) {
        try {
            Class<?> cls = AbstractCompiler.loadClass(resolvedClass);
            if (!cls.isInterface()) {
                Constructor<?> c = cls.getConstructor();
                return new Variable(c.newInstance());
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    private static Variable wireFromSourceCode(Type type, String resolvedClass, FieldDeclaration fd) {
        Variable v;
        Evaluator eval = MockingRegistry.isMockTarget(AbstractCompiler.findFullyQualifiedTypeName(fd.getVariable(0)))
            ? EvaluatorFactory.createLazily(resolvedClass, MockingEvaluator.class)
            : EvaluatorFactory.createLazily(resolvedClass, SpringEvaluator.class);

        v = new Variable(eval);
        v.setType(type);
        AntikytheraRunTime.autoWire(resolvedClass, v);
        if (! (eval instanceof MockingEvaluator)) {
            eval.setupFields();
            eval.initializeFields();
            eval.invokeDefaultConstructor();
        }
        return v;
    }

    @Override
    void setupField(FieldDeclaration field, VariableDeclarator variableDeclarator) {
        String resolvedClass = AbstractCompiler.findFullyQualifiedName(cu, variableDeclarator.getTypeAsString());
        if (resolvedClass != null) {
            Variable v = autoWire(variableDeclarator, resolvedClass);
            if (v == null) {
                /*
                 * Try to substitute an implementation for the interface.
                 */
                CompilationUnit targetCu = AntikytheraRunTime.getCompilationUnit(resolvedClass);
                if (targetCu != null) {
                    String name = AbstractCompiler.findFullyQualifiedName(targetCu, variableDeclarator.getType().asString());

                    for (String impl : AntikytheraRunTime.findImplementations(name)) {
                        v = autoWire(variableDeclarator, impl);
                        if (v != null) {
                            return;
                        }
                    }
                }
            }
        }
        super.setupField(field, variableDeclarator);
    }

    @Override
    public Variable evaluateMethodCall(Scope scope) throws ReflectiveOperationException {
        Variable v = scope.getVariable();
        MethodCallExpr methodCall = scope.getScopedMethodCall();
        try {
            if (v != null && v.getValue() instanceof SpringEvaluator) {
                Optional<Expression> expr = methodCall.getScope();
                if (expr.isPresent()) {
                    String fieldClass = getFieldClass(expr.get());
                    if (repositories.containsKey(fieldClass) && !(v.getValue() instanceof MockingEvaluator)) {
                        boolean isMocked = false;
                        String fieldName = getFieldName(expr.get());
                        if (fieldName != null && fields.get(fieldName) != null && fields.get(fieldName).getType() != null) {
                            isMocked = MockingRegistry.isMockTarget(fieldClass);
                        }
                        if (!isMocked) {
                            return executeSource(methodCall);
                        }
                    }
                }
            }

            return super.evaluateMethodCall(scope);
        } catch (AntikytheraException aex) {
            if (aex instanceof EvaluatorException eex) {
                testForInternalError(methodCall, eex);
                throw eex;
            }
        }
        return null;
    }

    private void testForInternalError(MethodCallExpr methodCall, EvaluatorException eex) throws EvaluatorException {
        MethodResponse controllerResponse = new MethodResponse();
        if (eex.getError() != 0 && onTest) {
            Variable r = new Variable(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
            controllerResponse.setResponse(r);
            controllerResponse.setException(eex);
            createTests(controllerResponse);
            returnFrom = methodCall;
        } else {
            throw eex;
        }
    }

    /**
     * Set up an if condition so that it will evaluate to true or false in future executions.
     *
     * @param ifStmt  the if statement to mess with
     * @param state the desired state.
     */
    void setupIfCondition(IfStmt ifStmt, boolean state) {
        List<Expression> collectedConditions = IfConditionVisitor.collectConditionsUpToMethod(ifStmt);
        TruthTable tt = new TruthTable();

        for(Expression cond : collectedConditions) {
            if (cond.isBinaryExpr()) {
                BinaryExpr bin = cond.asBinaryExpr();
                if (bin.getLeft().isNameExpr()) {
                    tt.addConstraint(cond.asBinaryExpr().getLeft().asNameExpr(), cond.asBinaryExpr());
                }
            }
        }

        collectedConditions.add(ifStmt.getCondition());
        tt.setCondition(BinaryOps.getCombinedCondition(collectedConditions));
        tt.generateTruthTable();

        List<Map<Expression, Object>> values = tt.findValuesForCondition(state);

        if (!values.isEmpty()) {
            Map<Expression, Object> value = values.getFirst();
            for (var entry : value.entrySet()) {
                if (entry.getKey().isMethodCallExpr()) {
                    setupConditionThroughMethodCalls(ifStmt, entry);
                } else if (entry.getKey().isNameExpr()) {
                    setupConditionThroughAssignment(ifStmt, entry);
                }
            }
        }
    }

    /**
     * Execute a method that's only available to us in source code format.
     *
     * @param methodCall the method call whose execution is to be simulated.
     * @return the result from the execution as a Variable instance
     * @throws AntikytheraException         when the source code could not be executed
     * @throws ReflectiveOperationException when a reflective operation fails
     */
    @Override
    Variable executeSource(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        Expression expression = methodCall.getScope().orElseThrow();
        if (expression.isNameExpr()) {
            RepositoryParser rp = repositories.get(getFieldClass(expression));
            if (rp != null) {
                RepositoryQuery q = executeQuery(expression, methodCall);
                if (q != null) {
                    if (q.isWriteOps()) {
                        return evaluateExpression(methodCall.getArgument(0));
                    } else {
                        return processResult(
                                AbstractCompiler.findExpressionStatement(methodCall).orElseThrow(), q.getResultSet());
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
                    String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(cu, mainType);

                    if (fullyQualifiedName != null) {
                        ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null,
                                secondaryType.get(0).asClassOrInterfaceType(), new NodeList<>());

                        Variable variable = Reflect.variableFactory(fullyQualifiedName);
                        if (mainType.endsWith("List") || mainType.endsWith("Map") || mainType.endsWith("Set")) {
                            for (int i = 0; i < 10; i++) {
                                Variable row = createObject(stmt, v, objectCreationExpr);
                                if (SpringEvaluator.resultToEntity(row, rs)) {
                                    ((Collection) variable.getValue()).add(row);
                                } else {
                                    break;
                                }
                            }
                        }
                        return variable;
                    }
                } else {
                    ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, classType, new NodeList<>());
                    Variable row = createObject(stmt, v, objectCreationExpr);
                    if (SpringEvaluator.resultToEntity(row, rs)) {
                        return row;
                    } else {
                        return new Variable(null);
                    }

                }
            }
        }
        return null;
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
                } else if (v.getValue() instanceof Evaluator eval) {
                    return eval.getClassName();
                }
            }
        } else if (expr.isMethodCallExpr()) {
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            Expression scope = methodCall.getScope().orElse(null);
            if (scope != null && scope.isNameExpr()) {
                return getFieldClass(scope);
            }
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

    @Override
    Variable handleOptionals(Scope sc) throws ReflectiveOperationException {
        if (sc.getExpression().isMethodCallExpr()) {
            MCEWrapper wrapper = sc.getMCEWrapper();
            Callable callable = wrapper.getMatchingCallable();

            if (callable.isMethodDeclaration()) {
                return handleOptionalsHelper(sc);
            }
        }
        return null;
    }

}

