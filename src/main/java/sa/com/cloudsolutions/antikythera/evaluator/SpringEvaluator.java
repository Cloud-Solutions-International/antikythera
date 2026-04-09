package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
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
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.evaluator.GeneratorState;
import sa.com.cloudsolutions.antikythera.evaluator.ITestGenerator;
import sa.com.cloudsolutions.antikythera.generator.MethodResponse;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
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
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Extends the basic evaluator to provide support for JPA repositories and their special behavior.
 */
@SuppressWarnings("java:S106")
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
    private final List<ITestGenerator> generators = new ArrayList<>();
    /**
     * The method currently being analyzed
     */
    private CallableDeclaration<?> currentCallable;
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
                resultToEntity(rs, evaluator);
                return true;
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        }
        return false;
    }

    private static void resultToEntity(ResultSet rs, Evaluator evaluator) {
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
        if (isSourceInterface(resolvedClass)) {
            v = createInterfaceAutowire(resolvedClass);
            if (v != null) {
                v.setType(type);
                return v;
            }
        }

        boolean useMockingEvaluator = MockingRegistry.isMockTarget(wrappers.getLast().getFullyQualifiedName());
        Evaluator eval = useMockingEvaluator
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

    private static Variable createInterfaceAutowire(String resolvedClass) {
        Evaluator eval = EvaluatorFactory.createLazily(resolvedClass, MockingEvaluator.class);
        return new Variable(eval);
    }

    private static boolean isSourceInterface(String resolvedClass) {
        return AntikytheraRunTime.getTypeDeclaration(resolvedClass)
                .filter(ClassOrInterfaceDeclaration.class::isInstance)
                .map(ClassOrInterfaceDeclaration.class::cast)
                .map(ClassOrInterfaceDeclaration::isInterface)
                .orElse(false);
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
     * @param cd The ConstructorDeclaration being worked on
     * @throws AntikytheraException         if evaluation fails
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public void visit(ConstructorDeclaration cd) throws AntikytheraException, ReflectiveOperationException {
        visitCallable(cd);
    }

    @Override
    public void visit(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        visitCallable(md);
    }

    private void visitCallable(CallableDeclaration<?> cd) throws AntikytheraException, ReflectiveOperationException {
        beforeVisit(cd);
        try {
            int oldSize = Branching.size(cd);

            int safetyCheck = 0;
            while (safetyCheck < 16) {
                prepareInvocationContext(cd);

                currentConditional = Branching.getHighestPriority(cd);
                if (currentConditional != null) {
                    BranchingTrace.record(() -> "target:"
                            + cd.getNameAsString()
                            + "|statement=" + currentConditional.getStatement()
                            + "|pathTaken=" + currentConditional.getPathTaken());
                }
                if ((currentConditional == null || currentConditional.isFullyTravelled()) && oldSize != 0) {
                    break;
                }

                String output = invokeCallableWithCapture(cd);
                maybeRecordVoidResponse(cd, output);

                safetyCheck++;
                oldSize = advanceBranchingState(cd);
                if (oldSize < 0) {
                    break;
                }
            }
        } catch (AUTException aex) {
            logger.warn("This has probably been handled {}", aex.getMessage());
        }
    }

    private void prepareInvocationContext(CallableDeclaration<?> cd) throws AntikytheraException, ReflectiveOperationException {
        getLocals().clear();
        LogRecorder.clearLogs();
        setupFields();
        mockMethodArguments(cd);
    }

    private String invokeCallableWithCapture(CallableDeclaration<?> cd) throws AntikytheraException, ReflectiveOperationException {
        String output = null;
        try {
            if (onTest) {
                startOutputCapture();
            }
            Evaluator.clearLastExceptionContext();
            GeneratorState.clearWhenThen();
            GeneratorState.clearMockStubReturnHints();
            GeneratorState.clearPendingObjectStubReturnFqns();
            if (cd instanceof MethodDeclaration md) {
                md.findCompilationUnit().ifPresent(cu -> md.findAncestor(ClassOrInterfaceDeclaration.class)
                        .ifPresent(coid -> MethodBodyMockStubAnalyzer.registerHintsForType(coid, cu)));
                executeMethod(md);
            } else if (cd instanceof ConstructorDeclaration constructorDeclaration) {
                executeConstructor(constructorDeclaration);
            }
        } finally {
            if (onTest) {
                output = stopOutputCapture();
                if (output != null && !output.isEmpty()) {
                    System.out.print(output);
                }
            }
        }
        return output;
    }

    private void maybeRecordVoidResponse(CallableDeclaration<?> cd, String output) {
        boolean isVoid = cd instanceof MethodDeclaration md && md.getType().isVoidType();
        boolean isConstructor = cd instanceof ConstructorDeclaration;

        if (!(isVoid || isConstructor)) {
            return;
        }

        boolean skipNoSideEffects = Settings.getProperty(Settings.SKIP_VOID_NO_SIDE_EFFECTS, Boolean.class).orElse(true);
        boolean hasSideEffects = (output != null && !output.isEmpty())
                || !GeneratorState.getWhenThen().isEmpty()
                || !Branching.getApplicableConditions(cd).isEmpty()
                || Evaluator.getLastExceptionContext() != null
                || sa.com.cloudsolutions.antikythera.evaluator.logging.LogRecorder.hasLogs();

        if (!skipNoSideEffects || hasSideEffects) {
            MethodResponse mr = new MethodResponse();
            if (onTest) {
                mr.setCapturedOutput(output);
            }
            ExceptionContext last = Evaluator.getLastExceptionContext();
            if (last != null && last.getException() != null) {
                Throwable t = last.getException();
                if (t instanceof EvaluatorException ee) {
                    mr.setException(ee);
                } else {
                    mr.setException(new EvaluatorException("Symbolic evaluation", t));
                }
            }
            createTests(mr);
        }
    }

    private int advanceBranchingState(CallableDeclaration<?> cd) {
        if (currentConditional != null) {
            currentConditional.transition();
            Branching.add(currentConditional);

            if (currentConditional.getPreconditions() != null) {
                currentConditional.getPreconditions().clear();
            }
        }
        if (Branching.size(cd) == 0) {
            return -1;
        }
        return Branching.size(cd);
    }

    private void beforeVisit(CallableDeclaration<?> cd) {
        cd.getParentNode().ifPresent(p -> {
            if (p instanceof ClassOrInterfaceDeclaration) {
                currentCallable = cd;
            }
        });

        Branching.clear();
        AntikytheraRunTime.reset();

        cd.accept(new ConditionVisitor(), null);
    }

    @Override
    protected void setupParameters(MethodDeclaration md) throws ReflectiveOperationException {
        super.setupParameters(md);
        NodeList<Parameter> parameters = md.getParameters();
        for (int i = parameters.size() - 1; i >= 0; i--) {
            setupParameter(md, parameters.get(i));
        }
    }

    @Override
    public void executeConstructor(ConstructorDeclaration cd) throws ReflectiveOperationException {
        super.executeConstructor(cd);
        NodeList<Parameter> parameters = cd.getParameters();
        for (int i = parameters.size() - 1; i >= 0; i--) {
            setupParameter(cd, parameters.get(i));
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
    void setupParameter(CallableDeclaration<?> md, Parameter p) throws ReflectiveOperationException {
        BlockStmt body = md instanceof MethodDeclaration mdecl ? mdecl.getBody().orElseThrow() : ((ConstructorDeclaration)md).getBody();
        Symbol va = getValue(body, p.getNameAsString());

        if (currentConditional != null) {
            if (currentConditional.getStatement() instanceof IfStmt || currentConditional.getConditionalExpression() != null) {
                setupIfCondition();
            }
            applyPreconditions(p, va);
        }

        setLocal(body, p.getNameAsString(), va);
        p.getAnnotationByName("RequestParam").ifPresent(SpringEvaluator::setupRequestParam);
    }

    private void applyPreconditions(Parameter p, Symbol va) throws ReflectiveOperationException {

        for (Precondition cond : currentConditional.getPreconditions()) {
            if (cond.getExpression() instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
                if (mce.getScope().orElseThrow() instanceof NameExpr ne
                        && ne.getNameAsString().equals(p.getNameAsString())) {
                    if (va.getValue() instanceof Evaluator eval) {
                        applyEvaluatorPrecondition(eval, mce);
                    } else {
                        applyCollectionPrecondition(va, mce);
                    }
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

    private void applyEvaluatorPrecondition(Evaluator eval, MethodCallExpr mce) throws ReflectiveOperationException {
        try {
            MCEWrapper wrapper = eval.wrapCallExpression(mce);
            eval.executeLocalMethod(wrapper);
        } catch (RuntimeException | ReflectiveOperationException ex) {
            if (!applyDirectSetterFieldPrecondition(eval, mce)) {
                throw ex;
            }
        }
    }

    private boolean applyDirectSetterFieldPrecondition(Evaluator eval, MethodCallExpr mce) throws ReflectiveOperationException {
        if (!mce.getNameAsString().startsWith("set") || mce.getArguments().size() != 1) {
            return false;
        }
        String fieldName = AbstractCompiler.classToInstanceName(mce.getNameAsString().substring(3));
        Variable field = eval.getField(fieldName);
        if (field == null) {
            return false;
        }
        Expression argument = mce.getArgument(0);
        Variable value = evaluateExpression(argument);

        if (value != null) {
            field.setValue(value.getValue());
            if (value.getType() != null) {
                field.setType(value.getType());
            }
            if (value.getInitializer() != null && !value.getInitializer().isEmpty()) {
                field.setInitializer(value.getInitializer());
                return true;
            }
        }
        if (applySyntheticCollectionValue(field, argument)) {
            return true;
        }
        field.setInitializer(List.of(argument.clone()));
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean applySyntheticCollectionValue(Variable field, Expression argument) {
        if (argument.isNullLiteralExpr()) {
            field.setValue(null);
            field.setInitializer(List.of(argument.clone()));
            return true;
        }
        if (!argument.isObjectCreationExpr()) {
            return false;
        }

        Object syntheticValue = synthesizeObjectCreationValue(argument.asObjectCreationExpr());
        if (syntheticValue == null) {
            return false;
        }

        field.setValue(syntheticValue);
        if (field.getType() == null) {
            field.setClazz(syntheticValue.getClass());
        }
        field.setInitializer(List.of(argument.clone()));
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object synthesizeObjectCreationValue(ObjectCreationExpr oce) {
        String typeName = oce.getType().getNameAsString();
        return switch (typeName) {
            case "ArrayList", "LinkedList" -> synthesizeListValue(oce);
            case "HashSet", "LinkedHashSet", "TreeSet" -> synthesizeSetValue(oce);
            case "HashMap", "LinkedHashMap", "TreeMap" -> synthesizeMapValue(oce);
            default -> null;
        };
    }

    private List<Object> synthesizeListValue(ObjectCreationExpr oce) {
        List<Object> values = new ArrayList<>();
        if (oce.getArguments().size() == 1) {
            Object seed = synthesizeFactoryArgument(oce.getArgument(0));
            if (seed instanceof Collection<?> collection) {
                values.addAll(collection);
            } else if (seed != null) {
                values.add(seed);
            }
        }
        return values;
    }

    private Set<Object> synthesizeSetValue(ObjectCreationExpr oce) {
        Set<Object> values = new HashSet<>();
        if (oce.getArguments().size() == 1) {
            Object seed = synthesizeFactoryArgument(oce.getArgument(0));
            if (seed instanceof Collection<?> collection) {
                values.addAll(collection);
            } else if (seed != null) {
                values.add(seed);
            }
        }
        return values;
    }

    private Map<Object, Object> synthesizeMapValue(ObjectCreationExpr oce) {
        Map<Object, Object> values = new HashMap<>();
        if (oce.getArguments().size() == 1) {
            Object seed = synthesizeFactoryArgument(oce.getArgument(0));
            if (seed instanceof Map<?, ?> map) {
                values.putAll(map);
            }
        }
        return values;
    }

    private Object synthesizeFactoryArgument(Expression expr) {
        Variable evaluated = null;
        try {
            evaluated = evaluateExpression(expr);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to AST-based synthesis for collection factory methods.
        }
        if (evaluated != null) {
            return evaluated.getValue();
        }
        if (!expr.isMethodCallExpr()) {
            return null;
        }

        MethodCallExpr factoryCall = expr.asMethodCallExpr();
        if (factoryCall.getNameAsString().equals("of")) {
            if (factoryCall.getScope().isPresent() && factoryCall.getScope().orElseThrow().toString().endsWith("List")) {
                return synthesizeFactoryList(factoryCall);
            }
            if (factoryCall.getScope().isPresent() && factoryCall.getScope().orElseThrow().toString().endsWith("Set")) {
                return new HashSet<>(synthesizeFactoryList(factoryCall));
            }
            if (factoryCall.getScope().isPresent() && factoryCall.getScope().orElseThrow().toString().endsWith("Map")) {
                return synthesizeFactoryMap(factoryCall);
            }
        }
        return null;
    }

    private List<Object> synthesizeFactoryList(MethodCallExpr factoryCall) {
        List<Object> values = new ArrayList<>();
        for (Expression argument : factoryCall.getArguments()) {
            Variable value = null;
            try {
                value = evaluateExpression(argument);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Leave unresolved arguments out of the synthetic collection.
            }
            if (value != null) {
                values.add(value.getValue());
            }
        }
        return values;
    }

    private Map<Object, Object> synthesizeFactoryMap(MethodCallExpr factoryCall) {
        Map<Object, Object> values = new HashMap<>();
        for (int i = 0; i + 1 < factoryCall.getArguments().size(); i += 2) {
            Object key = null;
            Object valueObject = null;
            try {
                Variable keyValue = evaluateExpression(factoryCall.getArgument(i));
                if (keyValue != null) {
                    key = keyValue.getValue();
                }
                Variable valueValue = evaluateExpression(factoryCall.getArgument(i + 1));
                if (valueValue != null) {
                    valueObject = valueValue.getValue();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Ignore unresolved map elements in the synthetic value.
            }
            if (key != null) {
                values.put(key, valueObject);
            }
        }
        return values;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyCollectionPrecondition(Symbol parameterValue, MethodCallExpr mce) throws ReflectiveOperationException {
        Object target = parameterValue.getValue();
        if (target instanceof Map map && mce.getNameAsString().equals("put") && mce.getArguments().size() == 2) {
            Object key = evaluateExpression(mce.getArgument(0)).getValue();
            Object value = evaluateExpression(mce.getArgument(1)).getValue();
            map.put(key, value);
        } else if (target instanceof Collection collection && mce.getNameAsString().equals("add")
                && mce.getArguments().size() == 1) {
            Object value = evaluateExpression(mce.getArgument(0)).getValue();
            collection.add(value);
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
    void mockMethodArguments(CallableDeclaration<?> md) throws ReflectiveOperationException {
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
                if (ancestor.isPresent() && ancestor.get().equals(currentCallable)) {
                    if (returnValue.getValue() instanceof MethodResponse mr) {
                        return createTests(mr);
                    }
                    MethodResponse mr = new MethodResponse();
                    mr.setBody(returnValue);
                    if (capturedOutputStream != null) {
                        mr.setCapturedOutput(capturedOutputStream.toString());
                    }
                    createTests(mr);

                    if (onTest && returnValue.getValue() instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator<?> fpEval
                            && currentCallable instanceof MethodDeclaration md) {
                        tryGenerateFPApplicationTest(fpEval, md);
                    }
                }
            }
        }
        return v;
    }

    /**
     * Invokes the returned functional evaluator (e.g., a JPA {@code Specification} lambda) with
     * all-null arguments to discover whether applying the function at runtime throws an exception.
     *
     * <p>When an exception is found, a second {@link MethodResponse} is created with
     * {@link MethodResponse#isFpApplicationTest()} {@code = true} so that the generator emits the
     * primary invocation as a plain statement and wraps the functional-application call in
     * {@code assertThrows}.</p>
     */
    private void tryGenerateFPApplicationTest(
            sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator<?> fpEval,
            MethodDeclaration md) {

        int arity = fpEval.getMethodDeclaration().getParameters().size();
        ExceptionContext ctx = null;
        try {
            invokeFPEvaluatorWithNullArgs(fpEval, arity);
        } catch (Exception e) {
            ctx = buildExceptionContextFromFPFailure(e);
        }

        if (ctx == null) {
            return;
        }

        String fpMethodName = inferFunctionalMethodName(md, fpEval);
        StringBuilder sb = new StringBuilder("resp.").append(fpMethodName).append("(");
        for (int i = 0; i < arity; i++) {
            if (i > 0) sb.append(", ");
            sb.append("null");
        }
        sb.append(")");

        MethodResponse fpMr = new MethodResponse();
        fpMr.setExceptionContext(ctx);
        fpMr.setFpApplicationTest(true);
        fpMr.setFpApplicationCall(sb.toString());
        createTests(fpMr);
    }

    private static void invokeFPEvaluatorWithNullArgs(
            sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator<?> fpEval,
            int arity) {
        Object[] nullArgs = new Object[arity];
        if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.NAryFunctionEvaluator n) {
            n.invoke(nullArgs);
        } else if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.NAryConsumerEvaluator n) {
            n.invoke(nullArgs);
        } else if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.BiFunctionEvaluator bfe) {
            bfe.apply(null, null);
        } else if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.BiConsumerEvaluator bce) {
            bce.accept(null, null);
        } else if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.FunctionEvaluator fe) {
            fe.apply(null);
        } else if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.ConsumerEvaluator ce) {
            ce.accept(null);
        } else if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.SupplierEvaluator se) {
            se.get();
        } else if (fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.RunnableEvaluator re) {
            re.run();
        }
    }

    private static ExceptionContext buildExceptionContextFromFPFailure(Exception e) {
        // Unwrap wrapper exceptions to find the root cause
        Throwable unwrapped = e;
        while (unwrapped.getCause() != null) {
            unwrapped = unwrapped.getCause();
        }

        EvaluatorException eex;
        if (unwrapped instanceof EvaluatorException ee && ee.getError() == EvaluatorException.NPE) {
            // Re-wrap with a real NullPointerException cause so JunitAsserter emits NPE.class
            eex = new EvaluatorException("Application NPE", new NullPointerException());
        } else {
            eex = new EvaluatorException(e.getMessage() != null ? e.getMessage() : "FP application exception", e);
        }

        ExceptionContext ctx = new ExceptionContext();
        ctx.setException(eex);
        return ctx;
    }

    /**
     * Infers the single abstract method name for the functional interface returned by
     * {@code md}, falling back to {@code "apply"} / {@code "accept"} for unknown types.
     */
    private static String inferFunctionalMethodName(
            MethodDeclaration md,
            sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator<?> fpEval) {
        Type returnType = md.getType();
        if (returnType.isClassOrInterfaceType()) {
            String typeName = returnType.asClassOrInterfaceType().getNameAsString();
            return switch (typeName) {
                case "Specification" -> "toPredicate";
                case "Predicate" -> "test";
                case "Supplier", "Callable" -> "get";
                case "Runnable" -> "run";
                default -> isConsumerLike(fpEval) ? "accept" : "apply";
            };
        }
        return isConsumerLike(fpEval) ? "accept" : "apply";
    }

    private static boolean isConsumerLike(
            sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator<?> fpEval) {
        return fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.ConsumerEvaluator
                || fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.BiConsumerEvaluator
                || fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.NAryConsumerEvaluator
                || fpEval instanceof sa.com.cloudsolutions.antikythera.evaluator.functional.RunnableEvaluator;
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
            for (ITestGenerator generator : generators) {
                Branching.BranchAttempt attempt = Branching.getBranchAttempt(currentCallable, currentConditional);
                List<Precondition> applicableConditions = attempt.applicableConditions();
                BranchingTrace.record(() -> "preconditions:"
                        + currentCallable.getNameAsString()
                        + "|target=" + (attempt.target() == null ? "<none>" : attempt.target().getStatement())
                        + "|values=" + applicableConditions);
                generator.setPreConditions(applicableConditions);
                generator.createTests(currentCallable, response);
            }
            return new Variable(response);
        }
        return null;
    }

    public void addGenerator(ITestGenerator generator) {
        generators.add(generator);
        if (argumentGenerator != null) {
            generator.setArgumentGenerator(argumentGenerator);
        }
    }

    Variable autoWire(VariableDeclarator variable, List<TypeWrapper> resolvedTypes) {
        if (resolvedTypes.isEmpty()) {
            throw new AntikytheraException("No types found for variable " + variable);
        }

        Optional<Node> parent = variable.getParentNode();
        String registryKey = MockingRegistry.generateRegistryKey(resolvedTypes);

        if (parent.isPresent() && parent.get() instanceof FieldDeclaration fd
                && shouldAutoWireField(fd, variable, registryKey)) {

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

    private boolean shouldAutoWireField(FieldDeclaration fd, VariableDeclarator variable, String registryKey) {
        return fd.getAnnotationByName("Autowired").isPresent()
                || MockingRegistry.isMockTarget(registryKey)
                || (fd.isFinal() && variable.getInitializer().isEmpty());
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
                try {
                    detectRepository(variableDeclarator);
                } catch (IOException e) {
                    throw new AntikytheraException(e);
                }
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
            
            // Get context from evaluator or create new one
            ExceptionContext ctx = Evaluator.getLastExceptionContext();
            if (ctx == null) {
                ctx = new ExceptionContext();
                ctx.setException(eex);
            }
            controllerResponse.setExceptionContext(ctx);
            
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
        BranchingTrace.record(() -> "truthTable:"
                + currentConditional.getCallableDeclaration().getNameAsString()
                + "|desiredState=" + state
                + "|rows=" + values.size()
                + "|condition=" + tt.getCondition());

        if (!values.isEmpty()) {
            setupIfCondition(values, state);
        }
    }

    private void setupIfCondition(List<Map<Expression, Object>> combinations, boolean desiredState) {
        Map<Expression, Object> combination = selectNextCombination(combinations, desiredState);
        BranchingTrace.record(() -> "selected:"
                + currentConditional.getCallableDeclaration().getNameAsString()
                + "|combination=" + combination);
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

    private Map<Expression, Object> selectNextCombination(List<Map<Expression, Object>> combinations, boolean desiredState) {
        int targetPath = desiredState ? LineOfCode.TRUE_PATH : LineOfCode.FALSE_PATH;
        Map<Expression, Object> fallback = null;
        String fallbackFingerprint = null;

        for (Map<Expression, Object> candidate : combinations) {
            Map<Expression, Object> adjusted = adjustForEnums(candidate);
            String fingerprint = fingerprintCombination(adjusted);
            if (fallback == null) {
                fallback = adjusted;
                fallbackFingerprint = fingerprint;
            }
            if (!currentConditional.hasAttemptedCombination(targetPath, fingerprint)) {
                currentConditional.recordCombinationAttempt(targetPath, fingerprint);
                BranchingTrace.record(() -> "selectedRow:"
                        + currentConditional.getCallableDeclaration().getNameAsString()
                        + "|path=" + targetPath
                        + "|fingerprint=" + fingerprint
                        + "|mode=new");
                return adjusted;
            }
        }

        if (fallback != null) {
            currentConditional.recordCombinationAttempt(targetPath, Objects.requireNonNull(fallbackFingerprint));
            String recordedFingerprint = fallbackFingerprint;
            BranchingTrace.record(() -> "selectedRow:"
                    + currentConditional.getCallableDeclaration().getNameAsString()
                    + "|path=" + targetPath
                    + "|fingerprint=" + recordedFingerprint
                    + "|mode=reuse");
            return fallback;
        }

        return new HashMap<>();
    }

    private String fingerprintCombination(Map<Expression, Object> combination) {
        return combination.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.toString().compareTo(b.toString())))
                .map(entry -> entry.getKey() + "=" + fingerprintValue(entry.getValue()))
                .reduce((left, right) -> left + "|" + right)
                .orElse("<empty>");
    }

    private String fingerprintValue(Object value) {
        if (value instanceof Expression expression) {
            return expression.toString();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::fingerprintValue).toList().toString();
        }
        if (value instanceof Set<?> set) {
            return set.stream().map(this::fingerprintValue).sorted().toList().toString();
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> fingerprintValue(entry.getKey()) + "->" + fingerprintValue(entry.getValue()))
                    .sorted()
                    .toList()
                    .toString();
        }
        return String.valueOf(value);
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
        if (node instanceof MethodCallExpr methodCall && isEnumEqualsMethodCall(methodCall)) {
            handleMethodCallWithEnum(methodCall, combination, entry, result);
            return;
        }

        TypeWrapper keyType = (key instanceof FieldAccessExpr fieldAccessExpr)
                ? AbstractCompiler.findType(cu, fieldAccessExpr.getScope().toString())
                : AbstractCompiler.findType(cu, key.toString());

        if (keyType != null && keyType.isEnum()) {
            adjustForEnumConstantComparison(node, combination, entry, result);
        } else if (node instanceof MethodCallExpr methodCall) {
            adjustForEnumMethodCall(methodCall, entry, result);
        } else {
            result.put(key, entry.getValue());
        }
    }

    private boolean isEnumEqualsMethodCall(MethodCallExpr methodCall) {
        if (!"equals".equals(methodCall.getNameAsString()) || methodCall.getArguments().size() != 1
                || methodCall.getScope().isEmpty()) {
            return false;
        }
        return resolveEnumConstant(methodCall.getScope().orElseThrow()) != null
                || resolveEnumConstant(methodCall.getArgument(0)) != null;
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
        boolean conditionMatches = isEquals == Objects.equals(combination.get(left), combination.get(right));

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
        if (!"equals".equals(methodCall.getNameAsString()) || methodCall.getArguments().size() != 1
                || methodCall.getScope().isEmpty()) {
            result.put(entry.getKey(), entry.getValue());
            return;
        }

        Expression scope = methodCall.getScope().orElseThrow();
        Expression argument = methodCall.getArgument(0);

        EnumConstantDeclaration scopeEnum = resolveEnumConstant(scope);
        EnumConstantDeclaration argumentEnum = resolveEnumConstant(argument);

        EnumConstantDeclaration constantEnum;
        Expression targetExpr;
        if (scopeEnum != null) {
            constantEnum = scopeEnum;
            targetExpr = argument;
        } else if (argumentEnum != null) {
            constantEnum = argumentEnum;
            targetExpr = scope;
        } else {
            result.put(entry.getKey(), entry.getValue());
            return;
        }

        if (!entry.getKey().toString().equals(targetExpr.toString()) || !targetExpr.isNameExpr()) {
            return;
        }

        boolean conditionMatches = Objects.equals(combination.get(scope), combination.get(argument));
        if (conditionMatches) {
            result.put(targetExpr.asNameExpr(), constantEnum);
        } else {
            putEnumMismatch(constantEnum, targetExpr, result);
        }
    }

    private EnumConstantDeclaration resolveEnumConstant(Expression expr) {
        if (!(expr instanceof NameExpr || expr instanceof FieldAccessExpr)) {
            return null;
        }
        String constantName = expr instanceof NameExpr ne ? ne.getNameAsString() : expr.asFieldAccessExpr().getNameAsString();

        if (typeDeclaration instanceof EnumDeclaration enumDecl) {
            return enumDecl.getEntries().stream()
                    .filter(entry -> entry.getNameAsString().equals(constantName))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void putEnumMismatch(EnumConstantDeclaration constantEnum, Expression targetExpr, Map<Expression, Object> result) {
        constantEnum.findAncestor(EnumDeclaration.class).ifPresent(enumDecl -> enumDecl.getEntries().stream()
                .filter(entry -> !entry.getNameAsString().equals(constantEnum.getNameAsString()))
                .findFirst()
                .ifPresent(other -> result.put(targetExpr, other)));
    }

    private boolean matchesEnumConstant(TypeWrapper enumType, Object candidate) {
        if (enumType == null || enumType.getEnumConstant() == null || candidate == null) {
            return false;
        }
        String expectedName = enumType.getEnumConstant().getNameAsString();
        if (candidate instanceof EnumConstantDeclaration ecd) {
            return expectedName.equals(ecd.getNameAsString());
        }
        if (candidate instanceof FieldAccessExpr fae) {
            return expectedName.equals(fae.getNameAsString());
        }
        return Objects.equals(candidate, enumType.getEnumConstant());
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
                Variable optionalFallback = fallbackRepositoryOptional(methodCall, rp, expression);
                if (optionalFallback != null) {
                    return optionalFallback;
                }
            }
        }
        return super.executeSource(methodCall);
    }

    private Variable fallbackRepositoryOptional(MethodCallExpr methodCall, RepositoryParser repository, Expression expression)
            throws ReflectiveOperationException {
        MCEWrapper methodCallWrapper = wrapCallExpression(methodCall);
        Optional<Callable> callable = AbstractCompiler.findCallableDeclaration(
                methodCallWrapper, repository.getCompilationUnit().getType(0));
        if (callable.isPresent() && callable.get().isMethodDeclaration()) {
            MethodDeclaration md = callable.get().asMethodDeclaration();
            String returnType = md.getType().asString();
            if (returnType.startsWith(Reflect.OPTIONAL) || returnType.startsWith(Reflect.JAVA_UTIL_OPTIONAL)) {
                methodCallWrapper.setMatchingCallable(callable.get());
                return handleRepositoryOptional(methodCall, expression, methodCallWrapper, md);
            }
        }
        return null;
    }

    private Variable handleRepositoryOptional(MethodCallExpr methodCall, Expression expression,
                                              MCEWrapper methodCallWrapper, MethodDeclaration md) {
        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        Variable v = buildRepositoryOptionalVariable(stmt, md);
        MockingCall then = new MockingCall(methodCallWrapper.getMatchingCallable(), v);
        then.setVariableName(getFieldName(expression));
        MockingRegistry.when(className, then);
        return v;
    }

    private Variable buildRepositoryOptionalVariable(Statement stmt, MethodDeclaration md) {
        ClassOrInterfaceType classType = md.getType().asClassOrInterfaceType();
        LineOfCode branch = Branching.get(stmt.hashCode());
        if (branch == null) {
            branch = new LineOfCode(stmt);
            Branching.add(branch.markPreconditionOnly());
            branch.setPathTaken(LineOfCode.TRUE_PATH);
            return createRepositoryOptionalValue(classType, true);
        }
        if (branch.getPathTaken() == LineOfCode.TRUE_PATH) {
            branch.setPathTaken(LineOfCode.FALSE_PATH);
            return createRepositoryOptionalValue(classType, false);
        }
        if (branch.getPathTaken() == LineOfCode.FALSE_PATH) {
            branch.setPathTaken(LineOfCode.TRUE_PATH);
            return createRepositoryOptionalValue(classType, true);
        }
        return createRepositoryOptionalValue(classType, false);
    }

    private Variable createRepositoryOptionalValue(ClassOrInterfaceType classType, boolean present) {
        if (!present) {
            return buildOptionalVariable(classType, false, null);
        }

        Type nestedType = classType.getTypeArguments()
                .flatMap(NodeList::getFirst)
                .orElse(null);
        if (nestedType == null) {
            return buildOptionalVariable(classType, false, null);
        }

        Variable candidate = Reflect.generateNonDefaultVariable(nestedType.asString());
        Object value = candidate != null ? candidate.getValue() : null;
        return buildOptionalVariable(classType, value != null, value);
    }

    private Variable processResult(ExpressionStmt stmt, ResultSet rs) throws AntikytheraException, ReflectiveOperationException {
        if (stmt.getExpression().isVariableDeclarationExpr()) {
            VariableDeclarationExpr vdecl = stmt.getExpression().asVariableDeclarationExpr();

            Type elementType = vdecl.getElementType();
            if (elementType.isClassOrInterfaceType()) {
                ClassOrInterfaceType classType = elementType.asClassOrInterfaceType();
                if (Reflect.OPTIONAL.equals(classType.getNameAsString())
                        || Reflect.JAVA_UTIL_OPTIONAL.equals(classType.getNameAsString())) {
                    return processOptionalResult(stmt, classType, rs);
                }
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

    private Variable processOptionalResult(ExpressionStmt stmt, ClassOrInterfaceType classType, ResultSet rs) throws ReflectiveOperationException {
        boolean hasRow = false;
        Object value = null;
        try {
            hasRow = rs != null && rs.next();
            if (hasRow) {
                value = rs.getObject(1);
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        }

        LineOfCode branch = Branching.get(stmt.hashCode());
        if (branch == null) {
            branch = new LineOfCode(stmt);
            Branching.add(branch);
            branch.setPathTaken(hasRow ? LineOfCode.TRUE_PATH : LineOfCode.FALSE_PATH);
            return buildOptionalVariable(classType, hasRow, value);
        }

        if (branch.getPathTaken() == LineOfCode.TRUE_PATH) {
            return buildOptionalVariable(classType, false, null);
        }
        if (branch.getPathTaken() == LineOfCode.FALSE_PATH) {
            return buildOptionalVariable(classType, true, value);
        }
        return buildOptionalVariable(classType, hasRow, value);
    }

    private Variable buildOptionalVariable(ClassOrInterfaceType classType, boolean present, Object value) {
        Variable v = new Variable(present ? Optional.ofNullable(value) : Optional.empty());
        v.setType(classType);
        return v;
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
        if (v == null) {
            return null;
        }
        v.setType(type);
        return v;
    }

    public void setArgumentGenerator(ArgumentGenerator argumentGenerator) {
        SpringEvaluator.argumentGenerator = argumentGenerator;
        for (ITestGenerator gen : generators) {
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
