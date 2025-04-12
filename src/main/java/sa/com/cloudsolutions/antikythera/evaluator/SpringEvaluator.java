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
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private static final HashMap<Integer, LineOfCode> branching = new HashMap<>();
    private static ArgumentGenerator argumentGenerator;
    /**
     * <p>List of test generators that we have.</p>
     * <p>
     * Generators ought to be seperated from the parsers/evaluators because different kinds of
     * tests can be created. They can be unit tests, integration tests, api tests and end-to-end
     * tests.
     */
    private final List<TestGenerator> generators = new ArrayList<>();
    /**
     * The method currently being analyzed
     */
    private MethodDeclaration currentMethod;
    private int visitNumber;
    private boolean onTest;
    private int branchCount;

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
            case "Integer" -> Integer.parseInt(value.toString());
            case "Double" -> Double.parseDouble(value.toString());
            case "Long" -> Long.parseLong(value.toString());
            case "Float" -> Float.parseFloat(value.toString());
            case "Boolean" -> value.isBooleanLiteralExpr() ? value.asBooleanLiteralExpr().getValue() : value;
            case "Character" -> value.isCharLiteralExpr() ? value.asCharLiteralExpr().getValue() : value;
            case "String" -> value.isStringLiteralExpr() ? value.asStringLiteralExpr().getValue() : value;
            default -> value;
        };
        va.setValue(result);
    }

    /**
     * <p>This is where the code evaluation really starts</p>
     * <p>
     * The method will be called by the java parser method visitor. Note that we may run the same
     * code repeatedly so that we can exercise all the paths in the code.
     * This is done by setting the values of variables to ensure conditionals evaluate to both true
     * state and the false state
     *
     * @param md The MethodDeclaration being worked on
     * @throws AntikytheraException         if evaluation fails
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public void visit(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        beforeVisit(md);
        try {
            for (visitNumber = 0; visitNumber < branchCount; visitNumber++) {
                getLocals().clear();
                setupFields();
                mockMethodArguments(md);
                executeMethod(md);

                Set<Expression> conditions = preConditions.computeIfAbsent(md, k -> new HashSet<>());
                conditions.addAll(preconditionsInProgress);
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

        branching.clear();
        preConditions.clear();
        preconditionsInProgress.clear();
        final List<Integer> s = new ArrayList<>();

        md.accept(new VoidVisitorAdapter<Void>() {
           @Override
            public void visit(IfStmt stmt, Void arg) {
                branching.putIfAbsent(stmt.hashCode(), new LineOfCode(stmt));
                s.add(1); // Count the main if branch

                Optional<Statement> elseStmt = stmt.getElseStmt();
                if (elseStmt.isEmpty()) {
                    s.add(1); // Count empty else branch
                    return;
                }
                Statement elseStatement = elseStmt.get();
                if (elseStatement instanceof BlockStmt) {
                    s.add(1); // Count block else branch
                }
                elseStatement.accept(this, arg); // Visit else branch
            }
        }, null);
        branchCount = Math.max(1, s.size());
    }

    /**
     * Set up the parameters required for the method call.
     * If there are any preconditions that need to be applied to get branch coverage the parameters
     * will be updated to reflect those preconditions.
     *
     * @param md the method declaration into whose variable space this parameter will be copied
     * @param p the parameter in question.
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    void setupParameter(MethodDeclaration md, Parameter p) throws ReflectiveOperationException {
        Variable va = AntikytheraRunTime.pop();
        int count = 0;
        for (Expression cond : preConditions.getOrDefault(md, Collections.emptySet())) {
            if (count++ == visitNumber) {
                break;
            }
            if (cond instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
                if (mce.getScope().get() instanceof NameExpr ne
                        && ne.getNameAsString().equals(p.getNameAsString())
                        && va.getValue() instanceof Evaluator eval) {
                    MCEWrapper wrapper = eval.wrapCallExpression(mce);
                    eval.executeLocalMethod(wrapper);
                }
            } else if (cond instanceof AssignExpr assignExpr) {
                parameterAssignment(p, assignExpr, va);
            }
        }

        md.getBody().ifPresent(body -> {
            setLocal(body, p.getNameAsString(), va);
            p.getAnnotationByName("RequestParam").ifPresent(SpringEvaluator::setupRequestParam);
        });

    }

    /**
     * <p>Execute a block of statements.</p>
     * <p>
     * When generating tests; we may end up executing the same block of statements repeatedly until
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
     * parameters and post bodies. We mock them here with the help of the argument generator.
     * In the case of services and other classes we can use a mocking library.
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

    Variable autoWire(VariableDeclarator variable, String resolvedClass) {
        Optional<Node> parent = variable.getParentNode();
        if (parent.isPresent() && parent.get() instanceof FieldDeclaration fd
                && fd.getAnnotationByName("Autowired").isPresent()) {
            Variable v = AntikytheraRunTime.getAutoWire(resolvedClass);
            if (v == null) {
                if (AntikytheraRunTime.getCompilationUnit(resolvedClass) != null) {
                    v = autoWireFromSourceCode(variable, resolvedClass, fd);
                } else {
                    v = autoWireFromByteCode(resolvedClass);
                }
            }
            fields.put(variable.getNameAsString(), v);
            return v;
        }
        return null;
    }

    private static Variable autoWireFromByteCode(String resolvedClass) {
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

    private static Variable autoWireFromSourceCode(VariableDeclarator variable, String resolvedClass, FieldDeclaration fd) {
        Variable v;
        Evaluator eval = AntikytheraRunTime.isMocked(AbstractCompiler.findFullyQualifiedTypeName(fd.getVariable(0)))
            ? EvaluatorFactory.createLazily(resolvedClass, MockingEvaluator.class)
            : EvaluatorFactory.createLazily(resolvedClass, SpringEvaluator.class);

        v = new Variable(eval);
        v.setType(variable.getType());
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
    public Variable evaluateMethodCall(ScopeChain.Scope scope) throws ReflectiveOperationException {
        Variable v = scope.getVariable();
        MethodCallExpr methodCall = scope.getScopedMethodCall();
        try {
            Optional<Expression> expr = methodCall.getScope();
            if (expr.isPresent()) {
                String fieldClass = getFieldClass(expr.get());
                if (repositories.containsKey(fieldClass) && !(v.getValue() instanceof MockingEvaluator)) {
                    boolean isMocked = false;
                    String fieldName = getFieldName(expr.get());
                    if (fieldName != null && fields.get(fieldName) != null && fields.get(fieldName).getType() != null) {
                        isMocked = AntikytheraRunTime.isMocked(fieldClass);
                    }
                    if (!isMocked) {
                        return executeSource(methodCall);
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
            controllerResponse.setExecption(eex);
            createTests(controllerResponse);
            returnFrom = methodCall;
        } else {
            throw eex;
        }
    }

    @Override
    Variable ifThenElseBlock(IfStmt ifst) throws Exception {
        LineOfCode l = branching.get(ifst.hashCode());
        if (l == null) {
            return super.ifThenElseBlock(ifst);
        }

        Variable v = evaluateExpression(ifst.getCondition());
        boolean result = (boolean) v.getValue();
        Statement elseStmt = ifst.getElseStmt().orElse(new BlockStmt());

        if (l.getPathTaken() == LineOfCode.UNTRAVELLED) {
            if (result) {
                l.setPathTaken(LineOfCode.TRUE_PATH);
                /* we have not been this way before. In this first execution of the code, we
                 * are taking the true path. We need to leave a flag behind so that in the
                 * next execution we will know to take the false path.
                 */
                setupIfCondition(ifst, false);
                super.executeStatement(ifst.getThenStmt());
            } else {
                l.setPathTaken(LineOfCode.FALSE_PATH);
                setupIfCondition(ifst, true);
                super.executeStatement(elseStmt);
            }
        } else {
            /*
             * We have been this way before so lets take the path not taken.
             */
            l.setPathTaken(LineOfCode.BOTH_PATHS);
            if (result) {
                super.executeStatement(ifst.getThenStmt());
            } else {
                super.executeStatement(elseStmt);
            }
        }
        return v;
    }

    /**
     * Set up an if condition so that it will evaluate to true or false in future executions.
     *
     * @param ifStmt  the if statement to mess with
     * @param state the desired state.
     */
    void setupIfCondition(IfStmt ifStmt, boolean state) {
        TruthTable tt = new TruthTable(ifStmt.getCondition());
        tt.generateTruthTable();

        List<Map<Expression, Object>> values = tt.findValuesForCondition(state);

        if (!values.isEmpty()) {
            Map<Expression, Object> value = values.getFirst();
            for (var entry : value.entrySet()) {
                if (entry.getKey().isMethodCallExpr()) {
                    setupConditionThroughMethodCalls(ifStmt, state, entry);
                } else if (entry.getKey().isNameExpr()) {
                    setupConditionThroughAssignment(ifStmt, state, entry);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setupConditionThroughAssignment(Statement stmt, boolean state, Map.Entry<Expression, Object> entry) {
        NameExpr nameExpr = entry.getKey().asNameExpr();
        Variable v = getValue(stmt, nameExpr.getNameAsString());
        if (v != null) {
            Expression init = v.getInitializer();
            if (init != null) {
                MethodDeclaration md = stmt.findAncestor(MethodDeclaration.class).orElseThrow();

                String paramName = nameExpr.getNameAsString();
                for (Parameter param : md.getParameters()) {
                    if (param.getNameAsString().equals(paramName)) {
                        setupConditionThroughAssignment(stmt, state, entry, v);
                        return;
                    }
                }

                ScopeChain chain = ScopeChain.findScopeChain(init);
                setupConditionThroughMethodCalls(stmt, state, entry, chain);
                return;
            }
        }
        else {
            v = new Variable(null);
        }

        setupConditionThroughAssignment(stmt, state, entry, v);
    }

    private void setupConditionThroughAssignment(Statement stmt, boolean state, Map.Entry<Expression, Object> entry, Variable v) {
        NameExpr nameExpr = entry.getKey().asNameExpr();
        Expression valueExpr = v.getType() instanceof PrimitiveType
                ? Reflect.createLiteralExpression(entry.getValue())
                : new StringLiteralExpr(entry.getValue().toString());

        AssignExpr expr = new AssignExpr(
                new NameExpr(nameExpr.getNameAsString()),
                valueExpr,
                AssignExpr.Operator.ASSIGN
        );
        addPreCondition(stmt, state, expr);
    }

    private void setupConditionThroughMethodCalls(Statement stmt, boolean state, Map.Entry<Expression, Object> entry) {
        ScopeChain chain = ScopeChain.findScopeChain(entry.getKey());
        setupConditionThroughMethodCalls(stmt, state, entry, chain);
    }

    private void setupConditionThroughMethodCalls(Statement stmt, boolean state, Map.Entry<Expression, Object> entry, ScopeChain chain) {
        if (!chain.isEmpty()) {
            Expression expr = chain.getChain().getFirst().getExpression();
            Variable v = getValue(stmt, expr.toString());
            if (v == null && expr.isNameExpr()) {
                /*
                 * This is likely to be a static method.
                 */
                String fullname = AbstractCompiler.findFullyQualifiedName(cu, expr.asNameExpr().getNameAsString());
                if (fullname != null) {
                    /*
                     * The only other possibility is static access on a class
                     */
                    try {
                        Class.forName(fullname);

                    } catch (ReflectiveOperationException e) {
                        /*
                         * Can probably be ignored
                         */
                        logger.info("Could not create class for {}", fullname);
                    }
                }
            }

            if (v != null && v.getValue() instanceof Evaluator) {
                setupConditionalVariable(stmt, state, entry, expr);
            }
        }
    }

    private void setupConditionalVariable(Statement stmt, boolean state, Map.Entry<Expression, Object> entry, Expression scope) {
        MethodCallExpr setter = new MethodCallExpr();
        String name = entry.getKey().asMethodCallExpr().getNameAsString();
        if (name.startsWith("is")) {
            setter.setName("set" + name.substring(2));
        }
        else {
            setter.setName("set" + name.substring(3));
        }
        setter.setScope(scope);

        if (entry.getValue() == null) {
            setter.addArgument("null");
        } else {
            if (entry.getValue().equals("T")) {
                setter.addArgument("\"T\"");
            } else {
                setter.addArgument(entry.getValue().toString());
            }
        }
        addPreCondition(stmt, state, setter);
    }

    private void addPreCondition(Statement statement, boolean state, Expression expr) {
        LineOfCode l = branching.get(statement.hashCode());
        l.addPrecondition(expr, state);
        preconditionsInProgress.add(expr);
    }

    public void resetColors() {
        branching.clear();
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

    @SuppressWarnings("unchecked")
    @Override
    Variable handleOptionalEmpties(ScopeChain chain) throws ReflectiveOperationException {
        MethodCallExpr methodCall = chain.getExpression().asMethodCallExpr();

        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        LineOfCode l = branching.get(stmt.hashCode());
        boolean isFirstTime = l == null;

        if (isFirstTime) {
            l = new LineOfCode(stmt);
            branching.put(stmt.hashCode(), l);
            branchCount++;
        }

        Expression first = chain.getChain().getFirst().getExpression();
        if (first.isMethodCallExpr()) {
            MethodCallExpr firstCall = first.asMethodCallExpr();
            MCEWrapper wrapper = new MCEWrapper(firstCall);
            Callable callable = AbstractCompiler.findMethodDeclaration(wrapper,
                    methodCall.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow()).orElseThrow();

            if (callable.isMethodDeclaration() &&
                    callable.asMethodDeclaration() instanceof MethodDeclaration method
                    && method.getType().toString().startsWith("Optional")) {

                ReturnStmt emptyReturn = method.findAll(ReturnStmt.class).stream()
                    .filter(r -> r.getExpression()
                        .map(e -> e.toString().contains("Optional.empty"))
                        .orElse(false))
                    .findFirst()
                    .orElse(null);

                if (isFirstTime || l.getPathTaken() == LineOfCode.FALSE_PATH) {
                    // Take the non-empty path first
                    l.setPathTaken(LineOfCode.TRUE_PATH);
                    if (!isFirstTime) {
                        l.setPathTaken(LineOfCode.BOTH_PATHS);
                    }
                    return evaluateOptionalCall(chain, null);
                } else {
                    // Take the empty path on subsequent visits
                    ReturnConditionVisitor visitor = new ReturnConditionVisitor(emptyReturn);
                    method.accept(visitor, null);
                    Expression emptyCondition = visitor.getCombinedCondition();

                    if (emptyCondition != null) {
                        TruthTable tt = new TruthTable(emptyCondition);
                        tt.generateTruthTable();
                        List<Map<Expression, Object>> emptyValues = tt.findValuesForCondition(true);

                        if (!emptyValues.isEmpty()) {
                            Map<Expression, Object> value = emptyValues.getFirst();
                            for (Parameter param : method.getParameters()) {
                                Type type = param.getType();
                                for (Map.Entry<Expression, Object> entry : value.entrySet()) {
                                    if (type.isPrimitiveType()) {
                                        setupConditionThroughAssignment(stmt, true, entry);
                                    } else {
                                        setupConditionThroughMethodCalls(stmt, true, entry);
                                    }
                                }
                            }
                        }
                        l.setPathTaken(LineOfCode.BOTH_PATHS);
                        return evaluateOptionalCall(chain, emptyCondition);
                    }
                }
            }
        }

        return super.handleOptionalEmpties(chain);
    }

    private Variable evaluateOptionalCall(ScopeChain chain, Expression emptyCondition) throws ReflectiveOperationException {
        MethodCallExpr methodCall = chain.getExpression().asMethodCallExpr();
        return switch (methodCall.getNameAsString()) {
            case "orElse", "orElseGet" -> evaluateExpression(methodCall.getArgument(0));
            case "orElseThrow" -> {
                if (emptyCondition != null) {
                    throw new NoSuchElementException("Optional is empty");
                }
                yield null;
            }
            case "ifPresent", "ifPresentOrElse" -> {
                if (emptyCondition == null) {
                    evaluateExpression(methodCall.getArgument(0));
                } else if (methodCall.getArguments().size() > 1) {
                    evaluateExpression(methodCall.getArgument(1));
                }
                yield null;
            }
            case "map", "flatMap", "filter" -> {
                if (emptyCondition == null) {
                    yield evaluateExpression(methodCall.getArgument(0));
                }
                yield new Variable(Optional.empty());
            }
            default -> super.handleOptionalEmpties(chain);
        };
    }
}

