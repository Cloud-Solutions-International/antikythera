package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.logging.LogRecorder;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.BaseRepositoryParser;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
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
        List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(variable);
        if (wrappers.isEmpty()) {
            return;
        }

        String className = wrappers.getLast().getFullyQualifiedName();
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
        if (cu != null) {
            var typeDecl = AbstractCompiler.getMatchingType(cu, shortName).orElse(null);
            if (typeDecl != null && typeDecl.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cdecl = typeDecl.asClassOrInterfaceDeclaration();

                for (var ext : cdecl.getExtendedTypes()) {
                    if (ext.getNameAsString().contains(BaseRepositoryParser.JPA_REPOSITORY)) {
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


                for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                    for (VariableDeclarator fieldVar : field.getVariables()) {
                        String fieldName = fieldVar.getNameAsString();
                        try {
                            if (rs.findColumn(BaseRepositoryParser.camelToSnake(fieldName)) > 0) {
                                Object value = rs.getObject(BaseRepositoryParser.camelToSnake(fieldName));
                                Variable v = new Variable(value);
                                v.setType(fieldVar.getType());
                                evaluator.setField(fieldName, v);
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
        List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(fd.getVariable(0));
        Evaluator eval = MockingRegistry.isMockTarget(wrappers.getLast().getFullyQualifiedName())
                ? EvaluatorFactory.createLazily(resolvedClass, MockingEvaluator.class)
                : EvaluatorFactory.createLazily(resolvedClass, SpringEvaluator.class);

        v = new Variable(eval);
        v.setType(type);
        AntikytheraRunTime.autoWire(resolvedClass, v);
        if (!(eval instanceof MockingEvaluator)) {
            eval.setupFields();
            eval.initializeFields();
            eval.invokeDefaultConstructor();
        }
        return v;
    }

    private static void setupEnumMismatch(TypeWrapper t, Expression key, Map<Expression, Object> result, Expression expr) {
        t.getEnumConstant().getParentNode().ifPresent(parent -> {
            if (parent instanceof EnumDeclaration enumDeclaration) {
                for (EnumConstantDeclaration ecd : enumDeclaration.getEntries()) {
                    if (key.isNameExpr() && !ecd.getNameAsString().equals(key.asNameExpr().getNameAsString())) {
                        result.put(expr, ecd);
                    } else if (key.isFieldAccessExpr() && !ecd.getNameAsString().equals(key.asFieldAccessExpr().getNameAsString())) {
                        FieldAccessExpr fae = new FieldAccessExpr()
                                .setScope(enumDeclaration.getNameAsExpression())
                                .setName(ecd.getName());
                        result.put(expr, fae);
                    }
                }
            }
        });
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
                LogRecorder.clearLogs();
                setupFields();
                mockMethodArguments(md);

                currentConditional = Branching.getHighestPriority(md);
                if ((currentConditional == null || currentConditional.isFullyTravelled()) && oldSize != 0) {
                    break;
                }

                executeMethod(md);

                if (md.getType().isVoidType()) {
                    MethodResponse mr = new MethodResponse();
                    createTests(mr);
                }
                safetyCheck++;
                if (currentConditional != null) {
                    currentConditional.transition();
                    Branching.add(currentConditional);

                    if (currentConditional.getPreconditions() != null) {
                        currentConditional.getPreconditions().clear();
                    }
                }
                if (Branching.size(md) == 0) {
                    break;
                } else {
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

        md.accept(new ConditionVisitor(), null);
    }

    @Override
    protected void setupParameters(MethodDeclaration md) throws ReflectiveOperationException {
        super.setupParameters(md);
        NodeList<Parameter> parameters = md.getParameters();
        for (int i = parameters.size() - 1; i >= 0; i--) {
            setupParameter(md, parameters.get(i));
        }
    }

    /**
     * Set up the parameters required for the method call.
     * When there are preconditions that need to be applied to get branch coverage, the parameters
     * will be updated to reflect those preconditions.
     *
     * @param md the method declaration into whose variable space this parameter will be copied
     * @param p  the parameter in question.
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    void setupParameter(MethodDeclaration md, Parameter p) throws ReflectiveOperationException {
        Symbol va = getValue(md.getBody().orElseThrow(), p.getNameAsString());

        if (currentConditional != null) {
            if (currentConditional.getStatement() instanceof IfStmt || currentConditional.getConditionalExpression() != null) {
                setupIfCondition();
            }
            applyPreconditions(p, va);
        }

        md.getBody().ifPresent(body -> {
            setLocal(body, p.getNameAsString(), va);
            p.getAnnotationByName("RequestParam").ifPresent(SpringEvaluator::setupRequestParam);
        });

    }

    private void applyPreconditions(Parameter p, Symbol va) throws ReflectiveOperationException {

        for (Precondition cond : currentConditional.getPreconditions()) {
            if (cond.getExpression() instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
                if (mce.getScope().orElseThrow() instanceof NameExpr ne
                        && ne.getNameAsString().equals(p.getNameAsString())
                        && va.getValue() instanceof Evaluator eval) {

                    MCEWrapper wrapper = eval.wrapCallExpression(mce);
                    eval.executeLocalMethod(wrapper);
                }
            } else if (cond.getExpression() instanceof AssignExpr assignExpr &&
                    assignExpr.getTarget().toString().equals(p.getNameAsString())) {

                parameterAssignment(assignExpr, va);
                va.setInitializer(List.of(assignExpr));
            } else if (cond.getExpression() instanceof ObjectCreationExpr oce) {
                va.setValue(createObject(oce).getValue());
                va.setInitializer(List.of(oce));
            }
        }
    }

    @Override
    protected void handleApplicationException(Exception e, BlockStmt parent) throws AntikytheraException, ReflectiveOperationException {
        if (!(e instanceof AntikytheraException ae)) {
            if (catching.isEmpty()) {
                EvaluatorException ex = new EvaluatorException(e.getMessage(), e);
                ex.setError(EvaluatorException.INTERNAL_SERVER_ERROR);
                testForInternalError(null, ex);
                throw new AUTException(e);
            } else {
                super.handleApplicationException(e, parent);
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
                RepositoryQuery q = repository.getQueryFromRepositoryMethod(callable.get());

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
                        for (int i = 0 ; i < methodCall.getArguments().size(); i++) {
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
     * @throws AntikytheraException         if there is an error in the code
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public Variable resolveVariableDeclaration(VariableDeclarator field) throws AntikytheraException, ReflectiveOperationException {
        Variable v = super.resolveVariableDeclaration(field);
        try {
            detectRepository(field);
        } catch (IOException e) {
            throw new AntikytheraException(e);
        }
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
    @SuppressWarnings("unchecked")
    @Override
    Variable executeReturnStatement(Statement statement) throws AntikytheraException, ReflectiveOperationException {
        /*
         * Leg work is done in the overloaded method.
         */
        Variable v = super.executeReturnStatement(statement);

        if (AntikytheraRunTime.isControllerClass(getClassName()) || onTest) {
            Optional<Node> parent = statement.getParentNode();

            if (parent.isPresent() && returnValue != null && returnFrom != null) {
                Optional<MethodDeclaration> ancestor = returnFrom.findAncestor(MethodDeclaration.class);
                if (ancestor.isPresent() && ancestor.get().equals(currentMethod)) {
                    if (returnValue.getValue() instanceof MethodResponse mr) {
                        return createTests(mr);
                    }
                    MethodResponse mr = new MethodResponse();
                    mr.setBody(returnValue);
                    createTests(mr);
                }
            }
        }
        return v;
    }

    /**
     * Finally, create the tests by calling each of the test generators.
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

    Variable autoWire(VariableDeclarator variable, List<TypeWrapper> resolvedTypes) {
        if (resolvedTypes.isEmpty()) {
            throw new AntikytheraException("No types found for variable " + variable);
        }

        Optional<Node> parent = variable.getParentNode();
        String registryKey = MockingRegistry.generateRegistryKey(resolvedTypes);

        if (parent.isPresent() && parent.get() instanceof FieldDeclaration fd
                && (fd.getAnnotationByName("Autowired").isPresent() || MockingRegistry.isMockTarget(registryKey))) {

            Variable v = AntikytheraRunTime.getAutoWire(registryKey);
            if (v == null) {
                if (MockingRegistry.isMockTarget(registryKey)) {
                    try {
                        v = MockingRegistry.mockIt(variable);
                    } catch (ReflectiveOperationException e) {
                        throw new AntikytheraException(e);
                    }
                } else if (AntikytheraRunTime.getCompilationUnit(resolvedTypes.getLast().getFullyQualifiedName()) != null) {
                    v = wireFromSourceCode(variable.getType(), resolvedTypes.getLast().getFullyQualifiedName(), fd);
                } else {
                    v = wireFromByteCode(resolvedTypes.getLast().getFullyQualifiedName());
                }
            }
            fields.put(variable.getNameAsString(), v);
            return v;
        }
        return null;
    }

    @Override
    void setupField(FieldDeclaration field, VariableDeclarator variableDeclarator) {
        List<TypeWrapper> wrappers = AbstractCompiler.findTypesInVariable(field);
        if (!wrappers.isEmpty()) {
            String resolvedClass = wrappers.getFirst().getFullyQualifiedName();
            Variable v = autoWire(variableDeclarator, wrappers);
            if (v == null) {
                for (String impl : AntikytheraRunTime.findImplementations(resolvedClass)) {
                    v = AntikytheraRunTime.getAutoWire(impl);
                    if (v != null) {
                        return;
                    }
                }
            } else {
                fields.put(variableDeclarator.getNameAsString(), v);
                return;
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
                        if (fieldName != null && getField(fieldName) != null && getField(fieldName).getType() != null) {
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
     */
    @SuppressWarnings("java:S5411")
    void setupIfCondition() {
        boolean state = currentConditional.isFalsePath();
        TruthTable tt = new TruthTable();

        List<Expression> collectedConditions = ConditionVisitor.collectConditionsUpToMethod(currentConditional.getStatement());
        tt.addConstraints(collectedConditions);

        collectedConditions.add(currentConditional.getConditionalExpression());
        tt.setCondition(BinaryOps.getCombinedCondition(collectedConditions));
        tt.generateTruthTable();

        List<Map<Expression, Object>> values = tt.findValuesForCondition(state);

        if (!values.isEmpty()) {
            setupIfCondition(values);
        }
    }

    private void setupIfCondition(List<Map<Expression, Object>> combinations) {
        Map<Expression, Object> combination = adjustForEnums(combinations.getFirst());
        for (var entry : combination.entrySet()) {
            Expression key = entry.getKey();
            if (key instanceof MethodCallExpr mce) {
                if (mce.getScope().isPresent() && mce.getScope().orElseThrow() instanceof NameExpr name
                        && name.getNameAsString().equals(TruthTable.COLLECTION_UTILS)) {
                    var collection = combination.get(new NameExpr(TruthTable.COLLECTION_UTILS));
                    if (collection != null) {
                        setupConditionThroughMethodCalls(currentConditional.getStatement(),
                                new AbstractMap.SimpleEntry<>(key, collection));
                        break;
                    }
                }
                setupConditionThroughMethodCalls(currentConditional.getStatement(), entry);
            } else if (key.isNameExpr() && !key.asNameExpr().getNameAsString().equals(TruthTable.COLLECTION_UTILS)) {
                setupConditionThroughAssignment(currentConditional.getStatement(), entry);
            } else if (key.isObjectCreationExpr() && entry.getValue() instanceof Boolean b && b) {
                setupConditionThroughMethodCalls(currentConditional.getStatement(), entry);
                return;
            }
        }
    }

    private Map<Expression, Object> adjustForEnums(Map<Expression, Object> combination) {
        final Map<Expression, Object> result = new HashMap<>();
        for (var entry : combination.entrySet()) {
            Expression key = entry.getKey();
            if (key.isNameExpr() || key.isFieldAccessExpr()) {
                adjustForEnums(combination, entry, result);
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private void adjustForEnums(Map<Expression, Object> combination, Map.Entry<Expression, Object> entry, Map<Expression, Object> result) {
        Expression key = entry.getKey();
        Optional<Node> parentNode = key.getParentNode();

        if (parentNode.isEmpty()) {
            result.put(key, entry.getValue());
            return;
        }

        Node node = parentNode.get();
        TypeWrapper keyType = (key instanceof FieldAccessExpr fieldAccessExpr)
                ? AbstractCompiler.findType(cu, fieldAccessExpr.getScope().asNameExpr().getNameAsString())
                : AbstractCompiler.findType(cu, key.asNameExpr().getNameAsString());

        if (keyType != null && (keyType.getEnumConstant() != null || keyType.getType().isEnumDeclaration())) {
            adjustForEnumConstantComparison(node, combination, entry, result);
        } else if (node instanceof MethodCallExpr methodCall) {
            adjustForEnumMethodCall(methodCall, entry, result);
        } else {
            result.put(key, entry.getValue());
        }
    }

    private void adjustForEnumConstantComparison(Node node, Map<Expression, Object> combination, Map.Entry<Expression, Object> entry, Map<Expression, Object> result) {
        if (node instanceof BinaryExpr binaryExpr) {
            handleBinaryExprWithEnum(binaryExpr, combination, entry, result);
        } else if (node instanceof MethodCallExpr methodCall) {
            handleMethodCallWithEnum(methodCall, combination, entry, result);
        }
    }

    private void handleBinaryExprWithEnum(BinaryExpr binaryExpr, Map<Expression, Object> combination,
            Map.Entry<Expression, Object> entry, Map<Expression, Object> result) {

        Expression left = binaryExpr.getLeft();
        Expression right = binaryExpr.getRight();

        TypeWrapper leftType = resolveType(left);
        TypeWrapper rightType = resolveType(right);

        boolean isEquals = binaryExpr.getOperator() == BinaryExpr.Operator.EQUALS;
        boolean conditionMatches = isEquals == (combination.get(left) == combination.get(right));

        if (leftType != null && leftType.getEnumConstant() != null) {
            if (conditionMatches) {
                result.put(right, leftType.getEnumConstant());
            } else {
                setupEnumMismatch(leftType, entry.getKey(), result, right);
            }
            return;
        }

        if (rightType != null && rightType.getEnumConstant() != null) {
            if (conditionMatches) {
                result.put(left, rightType.getEnumConstant());
            } else {
                setupEnumMismatch(rightType, entry.getKey(), result, left);
            }
            return;
        }

        if (rightType != null && rightType.getType() instanceof EnumDeclaration enumDecl) {
            EnumConstantDeclaration enumConst = findEnumConstant(enumDecl, left, right);
            rightType.setEnumConstant(enumConst);
            if (conditionMatches) {
                result.put(left, right instanceof FieldAccessExpr ? right : rightType.getEnumConstant());
            } else {
                setupEnumMismatch(rightType, entry.getKey(), result, left);
            }
        }
    }

    private TypeWrapper resolveType(Expression expr) {
        if (expr instanceof FieldAccessExpr fae) {
            return AbstractCompiler.findType(cu, fae.getScope().asNameExpr().getNameAsString());
        } else if (expr.isNameExpr()) {
            return AbstractCompiler.findType(cu, expr.asNameExpr().getNameAsString());
        }
        return null;
    }

    private EnumConstantDeclaration findEnumConstant(EnumDeclaration enumDecl, Expression left, Expression right) {
        return enumDecl.findFirst(EnumConstantDeclaration.class, ecd -> {
            if (right instanceof FieldAccessExpr fae) {
                return ecd.getNameAsString().equals(fae.getNameAsString());
            } else if (left instanceof FieldAccessExpr fae) {
                return ecd.getNameAsString().equals(fae.getNameAsString());
            }
            return false;
        }).orElseThrow();
    }

    private void handleMethodCallWithEnum(MethodCallExpr methodCall, Map<Expression, Object> combination, Map.Entry<Expression, Object> entry, Map<Expression, Object> result) {
        methodCall.getScope().ifPresent(scope -> {
            if (scope instanceof NameExpr nameExpr) {
                TypeWrapper scopeType = AbstractCompiler.findType(cu, nameExpr.getNameAsString());
                TypeWrapper keyType = AbstractCompiler.findType(cu, entry.getKey().asNameExpr().getNameAsString());

                if (scopeType != null && scopeType.getEnumConstant() != null) {
                    handleEnumComparison(scopeType, entry, combination, nameExpr, entry.getKey().asNameExpr(), result);
                } else if (keyType != null && keyType.getEnumConstant() != null) {
                    handleEnumComparison(keyType, entry, combination, nameExpr, nameExpr, result);
                }
            }
        });
    }

    private void handleEnumComparison(TypeWrapper enumType, Map.Entry<Expression, Object> entry, Map<Expression, Object> combination, NameExpr compareExpr, NameExpr targetExpr, Map<Expression, Object> result) {
        if (entry.getValue() == combination.get(compareExpr)) {
            result.put(targetExpr, enumType.getEnumConstant());
        } else {
            setupEnumMismatch(enumType, entry.getKey(), result, targetExpr);
        }
    }

    private void adjustForEnumMethodCall(MethodCallExpr methodCall, Map.Entry<Expression, Object> entry, Map<Expression, Object> result) {
        boolean hasEnumArgument = methodCall.getArguments().isNonEmpty() &&
                methodCall.getArgument(0) instanceof NameExpr nameExpr &&
                hasEnumConstant(nameExpr.getNameAsString());

        boolean hasEnumScope = methodCall.getScope().isPresent() &&
                methodCall.getScope().orElseThrow() instanceof NameExpr nameExpr &&
                hasEnumConstant(nameExpr.getNameAsString());

        if (!hasEnumArgument && !hasEnumScope) {
            result.put(entry.getKey(), entry.getValue());
        }
    }

    private boolean hasEnumConstant(String name) {
        TypeWrapper wrapper = AbstractCompiler.findType(cu, name);
        return wrapper != null && wrapper.getEnumConstant() != null;
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
                                Variable row = createObject(objectCreationExpr);
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
                    Variable row = createObject(objectCreationExpr);
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
            Variable v = getField(name);
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
    Variable createObject(ObjectCreationExpr oce) throws AntikytheraException, ReflectiveOperationException {
        Variable v = super.createObject(oce);
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
        if (v != null) {
            v.setType(type);
        }
        return v;
    }

    public void setArgumentGenerator(ArgumentGenerator argumentGenerator) {
        SpringEvaluator.argumentGenerator = argumentGenerator;
        for (TestGenerator gen : generators) {
            gen.setArgumentGenerator(argumentGenerator);
        }
    }

    /**
     * Gets the map of parsed repositories.
     * 
     * @return map of fully qualified class names to RepositoryParser instances
     */
    public static Map<String, RepositoryParser> getRepositories() {
        return repositories;
    }
}

