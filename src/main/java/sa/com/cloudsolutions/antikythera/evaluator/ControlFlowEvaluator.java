package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public class ControlFlowEvaluator extends Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(ControlFlowEvaluator.class);
    protected LineOfCode currentConditional;

    public ControlFlowEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    /**
     * Controls the branch that a conditional expression takes through parameter assignment
     * @param stmt the statement that contains branching
     * @param entry an entry from the truth table which contains values to be assigned.
     * @return an optional expression that can be used to set up the condition empty if all
     *      the conditions cannot be met.
     */
    List<Expression> setupConditionThroughAssignment(Statement stmt, Map.Entry<Expression, Object> entry) {
        Expression key = entry.getKey();
        NameExpr nameExpr = key.isNameExpr() ? key.asNameExpr() : key.asMethodCallExpr().getArgument(0).asNameExpr();

        Variable v = getValue(stmt, nameExpr.getNameAsString());
        if (v != null) {
            List<Expression> expr = setupConditionThroughAssignmentForLocal(stmt, entry, v, nameExpr);
            if (expr != null) return expr;
        } else {
            v = new Variable(entry.getValue());
        }

        List<Expression> expr = setupConditionThroughAssignment(entry, v);
        for (Expression e : expr) {
            addPreCondition(stmt, e);
        }

        return expr;
    }

    @SuppressWarnings("unchecked")
    private List<Expression> setupConditionThroughAssignmentForLocal(Statement stmt, Map.Entry<Expression, Object> entry, Variable v, NameExpr nameExpr) {
        if (v.getInitializer() != null) {
            MethodDeclaration md = stmt.findAncestor(MethodDeclaration.class).orElseThrow();

            String targetParamName = nameExpr.getNameAsString();
            for (Parameter param : md.getParameters()) {
                if (param.getNameAsString().equals(targetParamName)) {
                    List<Expression> expr = setupConditionThroughAssignment(entry, v);
                    for (Expression e : expr) {
                        addPreCondition(stmt, e);
                    }
                    return expr;
                }
            }
            /*
             * We tried to match the name of the variable with the name of the parameter, but
             * a match could not be found. So it is not possible to force branching by
             * assigning values to a parameter in a conditional
             */
        }
        return List.of();
    }

    private List<Expression> setupConditionThroughAssignment(Map.Entry<Expression, Object> entry, Variable v) {
        Expression key = entry.getKey();
        NameExpr nameExpr = key.isNameExpr() ? key.asNameExpr() : key.asMethodCallExpr().getArgument(0).asNameExpr();

        List<Expression> valueExpressions;
        if (v.getType() instanceof PrimitiveType) {
            valueExpressions = List.of(Reflect.createLiteralExpression(entry.getValue()));
        } else if (entry.getValue() instanceof ClassOrInterfaceType cType) {
            Variable vx = Reflect.createVariable(Reflect.getDefault(cType.getNameAsString()), cType.getNameAsString(), v.getName());
            valueExpressions = vx.getInitializer();
        } else {
            valueExpressions = setupConditionForNonPrimitive(entry, v);
        }

        if (valueExpressions.size() == 1) {
            AssignExpr a = new AssignExpr(
                    new NameExpr(nameExpr.getNameAsString()),
                    valueExpressions.getFirst(),
                    AssignExpr.Operator.ASSIGN
            );
            if (v.getType() instanceof PrimitiveType && entry.getValue() instanceof String s && s.equals("T")) {
                parameterAssignment(a, v);
            }
            return List.of(a);
        }
        return valueExpressions;
    }

    private List<Expression> setupConditionForNonPrimitive(Map.Entry<Expression, Object> entry, Variable v) {
        if (entry.getValue() instanceof List<?> list) {
            if (list.isEmpty()) {
                if (v.getValue() instanceof List<?>) {
                    TestGenerator.addImport(new ImportDeclaration("java.util.List", false, false));
                    return List.of(StaticJavaParser.parseExpression("List.of()"));
                } else if (v.getValue() instanceof Set<?>) {
                    TestGenerator.addImport(new ImportDeclaration("java.util.Set", false, false));
                    return List.of(StaticJavaParser.parseExpression("Set.of()"));
                } else if (v.getValue() instanceof Map<?,?>) {
                    TestGenerator.addImport(new ImportDeclaration("java.util.Map", false, false));
                    return List.of(StaticJavaParser.parseExpression("Map.of()"));
                }
            }
            else if(entry.getKey() instanceof NameExpr name) {
                return setupNonEmptyCollections(v, name);
            } else if (entry.getKey() instanceof MethodCallExpr mce) {
                if (mce.getScope().isPresent()) {
                    Expression scope = mce.getScope().orElseThrow();
                    if (scope.toString().equals(TruthTable.COLLECTION_UTILS)) {
                        Expression arg = mce.getArgument(0);
                        Variable vx = getValue(mce, arg.asNameExpr().getNameAsString());
                        return setupNonEmptyCollections(vx, arg.asNameExpr());
                    }
                }
            }
        }
        if (entry.getValue() == null) {
            return List.of(new NullLiteralExpr());
        }
        if (entry.getValue() instanceof ObjectCreationExpr oce) {
            return List.of(oce);
        }
        if (entry.getValue() instanceof MethodCallExpr mce) {
            return List.of(mce);
        }
        return List.of(new StringLiteralExpr(entry.getValue().toString()));
    }

    /**
     * Conditional statements may check for emptiness in a collection or map. Create suitable non-empty objects
     * @param v represents the type of collection or map that we need
     * @param name the name of the variable
     * @return a list of expressions that can be used to set up the condition
     */
    private List<Expression> setupNonEmptyCollections(Variable v, NameExpr name) {

        Optional<Parameter> paramByName = currentConditional.getMethodDeclaration().getParameterByName(name.getNameAsString());
        if (paramByName.isPresent()) {
            Parameter param = paramByName.orElseThrow();
            Type type = param.getType();
            NodeList<Type> typeArgs = type.asClassOrInterfaceType().getTypeArguments().orElse(new NodeList<>());
            if (typeArgs.isEmpty()) {
                typeArgs.add(new ClassOrInterfaceType().setName("Object"));
            }

            return setupNonEmptyCollection(typeArgs, v, name);
        }
        return List.of();
    }

    protected List<Expression> setupNonEmptyCollection(NodeList<Type> typeArgs, Variable wrappedCollection, NameExpr name) {
        Type pimaryType = typeArgs.getFirst().orElseThrow();
        VariableDeclarator vdecl = new VariableDeclarator(pimaryType, name.getNameAsString());
        try {
            Variable member = resolveVariableDeclaration(vdecl);
            if (member.getValue() == null
                    && (Reflect.isPrimitiveOrBoxed(member.getType().asString()) || member.getType().asString().equals("String"))) {
                member = Reflect.variableFactory(member.getType().asString());
            } else if (member.getValue() instanceof Evaluator eval) {
                return createSingleItemCollectionWithInitializer(typeArgs, member, wrappedCollection, name, eval);
            }

            return List.of(createSingleItemCollection(typeArgs, member, wrappedCollection, name));

        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    private List<Expression> createSingleItemCollectionWithInitializer(NodeList<Type> typeArgs, Variable member,
                                                                       Variable wrappedCollection, NameExpr name, Evaluator eval) throws ReflectiveOperationException {
        Type pimaryType = typeArgs.getFirst().orElseThrow();
        List<Expression> fieldIntializers = eval.getFieldInitializers();
        if (fieldIntializers.isEmpty()) {
            return List.of(createSingleItemCollection(typeArgs, member, wrappedCollection, name));
        }
        else {
            List<Expression> mocks = new ArrayList<>();
            String instanceName = Variable.generateVariableName(pimaryType);
            Expression expr = StaticJavaParser.parseStatement(
                    String.format("%s %s = new %s();", pimaryType, instanceName, pimaryType)
            ).asExpressionStmt().getExpression();
            TestGenerator.addImport(new ImportDeclaration(eval.getClassName(), false, false));
            mocks.add(expr);
            for (Expression e : fieldIntializers) {
                if (e.isMethodCallExpr()) {
                    e.asMethodCallExpr().setScope(new NameExpr(instanceName));
                }
                mocks.add(e);
            }

            if (wrappedCollection.getValue() instanceof List<?> list) {
                addToList(member, list);
                mocks.add(StaticJavaParser.parseExpression(String.format("List.of(%s)", instanceName)));
            }
            else if (wrappedCollection.getValue() instanceof Set<?> set) {
                addToSet(member, set);
                mocks.add(StaticJavaParser.parseExpression(String.format("Set.of(%s)", instanceName)));
            }
            else if (wrappedCollection.getValue() instanceof Map<?, ?> map) {
                Variable value = findValueForKey(typeArgs, name);
                addToMap(member, value, map);
            }
            return mocks;
        }
    }

    private Expression createSingleItemCollection(NodeList<Type> typeArgs, Variable member,
                                                  Variable wrappedCollection, NameExpr name) throws ReflectiveOperationException {
        List<Expression> initializer = member.getInitializer();
        if (initializer.getFirst() instanceof ObjectCreationExpr && member.getValue() instanceof Evaluator eval) {
            TestGenerator.addImport(new ImportDeclaration(eval.getClassName(), false, false));
        }
        if (wrappedCollection.getValue() instanceof List<?> list) {
            addToList(member, list);
            return StaticJavaParser.parseExpression(String.format("List.of(%s)", initializer.getFirst()));
        }
        if (wrappedCollection.getValue() instanceof Set<?> set) {
            addToSet(member, set);
            return StaticJavaParser.parseExpression(String.format("Set.of(%s)", initializer.getFirst()));
        }
        if (wrappedCollection.getValue() instanceof Map<?,?>) {
            Variable value = findValueForKey(typeArgs, name);

            return StaticJavaParser.parseExpression(
                    String.format("Map.of(%s, %s)",
                            initializer.getFirst(), value.getInitializer().getFirst()));
        }
        return null;
    }

    private Variable findValueForKey(NodeList<Type> typeArgs, NameExpr name) throws ReflectiveOperationException {
        if (typeArgs.size() == 1) {
            typeArgs.add(new ClassOrInterfaceType().setName("Object"));
        }
        VariableDeclarator vdecl2 = new VariableDeclarator(typeArgs.get(1), name.getNameAsString());
        Variable resolved2 = resolveVariableDeclaration(vdecl2);
        if (resolved2.getValue() == null && Reflect.isPrimitiveOrBoxed(resolved2.getType().asString())) {
            resolved2 = Reflect.variableFactory(resolved2.getType().asString());
        }
        return resolved2;
    }

    private static void addToSet(Variable member, Set<?> set) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method m = Set.class.getMethod("add", Object.class);
        m.invoke(set, member.getValue());
        TestGenerator.addImport(new ImportDeclaration("java.util.Set", false, false));
    }

    private static void addToMap(Variable key, Variable value, Map<?,?> map) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method m = Map.class.getMethod("put", Object.class, Object.class);
        m.invoke(map,  key.getValue(), value.getValue());
        TestGenerator.addImport(new ImportDeclaration("java.util.Map", false, false));
    }

    private static void addToList(Variable member, List<?> list) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method m = List.class.getMethod("add", Object.class);
        m.invoke(list, member.getValue());
        TestGenerator.addImport(new ImportDeclaration("java.util.List", false, false));
    }

    void setupConditionThroughMethodCalls(Statement stmt, Map.Entry<Expression, Object> entry) {
        ScopeChain chain = ScopeChain.findScopeChain(entry.getKey());
        if (!chain.isEmpty()) {
            Expression expr = chain.getChain().getFirst().getExpression();
            if (expr.isNameExpr()) {
                MethodCallExpr mce = chain.getExpression().asMethodCallExpr();
                if (mce.getArguments().isNonEmpty()) {
                    Expression argument = mce.getArgument(0);
                    if (expr.toString().equals("StringUtils")) {
                        if (argument.isMethodCallExpr()) {
                            Map.Entry<Expression, Object> argumentEntry = new AbstractMap.SimpleEntry<>(argument, entry.getValue());
                            setupConditionThroughMethodCalls(stmt, argumentEntry, argument);
                        } else {
                            setupConditionThroughAssignment(stmt, entry);
                        }
                        return;
                    }
                    if (expr.toString().equals("CollectionUtils")) {
                        if (argument.isMethodCallExpr()) {
                            Map.Entry<Expression, Object> argumentEntry = new AbstractMap.SimpleEntry<>(argument, entry.getValue());
                            setupConditionThroughMethodCalls(stmt, argumentEntry, argument);
                        } else {
                            setupConditionThroughAssignment(stmt, entry);
                        }
                        return;
                    }
                }
            }
            setupConditionThroughMethodCalls(stmt, entry, expr);
        }
    }

    private void setupConditionThroughMethodCalls(Statement stmt, Map.Entry<Expression, Object> entry, Expression expr) {

        Variable v = getValue(stmt, expr.toString());
        if (v == null ) {
            if (expr.isNameExpr()) {
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
            } else if (expr instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
                v = getValue(stmt, mce.getScope().orElseThrow().toString());
            } else if (expr.isObjectCreationExpr()) {
                ObjectCreationExpr oce = expr.asObjectCreationExpr();
                addPreCondition(stmt, oce);
            }
        }

        if (v != null && v.getValue() instanceof Evaluator) {
            setupConditionalVariablesWithSetter(stmt, entry, expr);
        }
    }

    private void setupConditionalVariablesWithSetter(Statement stmt, Map.Entry<Expression, Object> entry, Expression scope) {
        MethodCallExpr setter = new MethodCallExpr();
        String name = entry.getKey().asMethodCallExpr().getNameAsString();
        if (name.startsWith("is")) {
            setter.setName("set" + name.substring(2));
        } else {
            setter.setName("set" + name.substring(3));
        }
        setter.setScope(scope);

        if (entry.getValue() == null) {
            setter.addArgument(new NullLiteralExpr());
        } else {
            if (entry.getValue().equals("T")) {
                setupConditionalNotNullValue(stmt, entry, name, setter);
            } else {
                createSetterFromGetter(entry, setter);
            }
            if (setter.getArguments().isEmpty()) {
                setter.addArgument(entry.getValue().toString());
            }
        }
        addPreCondition(stmt, setter);
    }

    @SuppressWarnings("java:S5411")
    private void createSetterFromGetter(Map.Entry<Expression, Object> entry, MethodCallExpr setter) {
        Expression key = entry.getKey();
        Optional< Node> parent = entry.getKey().getParentNode();
        if (key.isMethodCallExpr() && parent.isPresent()) {
            if (parent.get() instanceof MethodCallExpr mce && mce.getNameAsString().equals("equals")) {
                createSetterFromGetterForMCE(entry, setter, mce);
            } else if (parent.get() instanceof BinaryExpr binaryExpr && entry.getValue() instanceof Boolean) {
                createSetterFromGetterForBinaryExpr(setter, binaryExpr, key);
            }
        }
    }

    private void createSetterFromGetterForBinaryExpr(MethodCallExpr setter, BinaryExpr binaryExpr, Expression key) {
        if (binaryExpr.getLeft().equals(key)) {
            processBinaryExpressionSide(setter, binaryExpr.getRight());
        } else if (binaryExpr.getRight().equals(key)) {
            processBinaryExpressionSide(setter, binaryExpr.getLeft());
        }
    }

    private void processBinaryExpressionSide(MethodCallExpr setter, Expression side) {
        if (side.isLiteralExpr()) {
            setter.addArgument(side.asLiteralExpr());
        } else if (side.isNameExpr()) {
            Variable v = getValue(side, side.asNameExpr().getNameAsString());
            if (v != null) {
                setter.addArgument(v.getInitializer().getFirst());
            }
        } else if (side.isMethodCallExpr()) {
            setter.addArgument(side);
        }
    }

    @SuppressWarnings("java:S5411")
    private void createSetterFromGetterForMCE(Map.Entry<Expression, Object> entry, MethodCallExpr setter, MethodCallExpr mce) {
        Expression argument = mce.getArgument(0);
        if (argument.isObjectCreationExpr()) {
            try {
                Variable v = evaluateExpression(argument);
                setter.addArgument(v.getInitializer().getFirst());
            } catch (ReflectiveOperationException e) {
                throw new AntikytheraException(e);
            }
        } else if (argument.isLiteralExpr()) {
            if (entry.getValue() instanceof Boolean b && b) {
                setter.addArgument(argument.asLiteralExpr());
            } else {
                Class<?> c = Reflect.literalExpressionToClass(argument.asLiteralExpr());
                Variable v = Reflect.variableFactory(c.getName());
                if (!v.getInitializer().isEmpty()) {
                    setter.addArgument(v.getInitializer().getFirst());
                }
            }
        }
    }

    private void setupConditionalNotNullValue(Statement stmt, Map.Entry<Expression, Object> entry, String name, MethodCallExpr setter) {
        MethodCallExpr mce = entry.getKey().asMethodCallExpr();
        String value = "\"T\"";
        if (mce.getScope().isPresent()) {
            Variable scopeVar = getValue(stmt, mce.getScope().orElseThrow().toString());
            if (scopeVar != null && scopeVar.getValue() instanceof Evaluator evaluator) {
                value = findSuitableNotNullValue(name, evaluator, value);
            }
        }
        setter.addArgument(value);
    }

    private static String findSuitableNotNullValue(String name, Evaluator evaluator, String value) {
        Variable field = evaluator.getField(
                AbstractCompiler.classToInstanceName(name.substring(3)));
        if (field != null) {
            if (field.getClazz() != null) {
                Variable v = Reflect.variableFactory(field.getClazz().getName());
                if (v != null) {
                    value = v.getInitializer().getFirst().toString();
                }
            }
            else if (field.getType() != null) {
                Variable v = Reflect.variableFactory(field.getType().asString());
                if (v != null && !v.getInitializer().isEmpty()) {
                    value = v.getInitializer().getFirst().toString();
                }
            }
        }
        return value;
    }

    private void addPreCondition(Statement statement, Expression expr) {
        LineOfCode l = Branching.get(statement.hashCode());
        l.addPrecondition(new Precondition(expr));
    }

    protected List<Expression> setupConditionalsForOptional(ReturnStmt emptyReturn, MethodDeclaration method, Statement stmt, boolean state) {
        List<Expression> expressions = new ArrayList<>();
        ReturnConditionVisitor visitor = new ReturnConditionVisitor(emptyReturn);
        method.accept(visitor, null);
        Expression emptyCondition = BinaryOps.getCombinedCondition(visitor.getConditions());

        if (emptyCondition == null) {
            return expressions;
        }

        TruthTable tt = new TruthTable(emptyCondition);
        tt.generateTruthTable();
        List<Map<Expression, Object>> emptyValues = tt.findValuesForCondition(state);

        if (!emptyValues.isEmpty()) {
            Map<Expression, Object> value = emptyValues.getFirst();
            for (Parameter param : method.getParameters()) {
                Type type = param.getType();
                for (Map.Entry<Expression, Object> entry : value.entrySet()) {
                    if (type.isPrimitiveType()) {
                        expressions.addAll(setupConditionThroughAssignment(stmt, entry));
                    } else {
                        setupConditionThroughMethodCalls(stmt, entry);
                    }
                }
            }
        }

        return expressions;
    }

    @SuppressWarnings("unchecked")
    Variable handleOptionalsHelper(Scope sc) throws ReflectiveOperationException {
        MethodCallExpr methodCall = sc.getScopedMethodCall();
        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        LineOfCode l = Branching.get(stmt.hashCode());
        Variable v = (l == null) ? optionalPresentPath(sc, stmt, methodCall)
                : optionalEmptyPath(sc, l);
        MockingCall then = new MockingCall(sc.getMCEWrapper().getMatchingCallable(), v);
        then.setVariableName(variableName);

        MockingRegistry.when(className, then);
        return v;
    }

    Variable optionalPresentPath(Scope sc, Statement stmt, MethodCallExpr methodCall) throws ReflectiveOperationException {
        MethodDeclaration method = sc.getMCEWrapper().getMatchingCallable().asMethodDeclaration();
        LineOfCode l = new LineOfCode(stmt);
        Branching.add(l);

        List<Expression> expressions;
        Variable v = super.handleOptionals(sc);
        if (v.getValue() instanceof Optional<?> optional) {
            if (optional.isPresent()) {
                ReturnStmt nonEmptyReturn = findReturnStatement(method, false);
                expressions = setupConditionalsForOptional(nonEmptyReturn, method, stmt, true);
                l.setPathTaken(LineOfCode.TRUE_PATH);
            } else {
                ReturnStmt emptyReturn = findReturnStatement(method, true);
                expressions = setupConditionalsForOptional(emptyReturn, method, stmt, false);
                l.setPathTaken(LineOfCode.FALSE_PATH);
            }
            for (Expression expr : expressions) {
                mapParameterToArguments(expr, method, methodCall);
            }
            return v;
        }
        throw new IllegalStateException("This should be returning an optional");
    }

    Variable optionalEmptyPath(Scope sc, LineOfCode l) throws ReflectiveOperationException {
        if (l.getPathTaken() != LineOfCode.BOTH_PATHS) {
            for (Precondition expression : l.getPreconditions()) {
                evaluateExpression(expression.getExpression());
            }
        }
        return super.handleOptionals(sc);
    }

    private void mapParameterToArguments(Expression expr, MethodDeclaration method, MethodCallExpr methodCall) {
        // Direct parameter to argument mapping and replacement
        for (int i = 0, j = method.getParameters().size(); i < j; i++) {
            String paramName = method.getParameter(i).getNameAsString();
            String argName = methodCall.getArgument(i).toString();

            if (expr instanceof MethodCallExpr methodExpr) {
                // Replace parameter references in the method scope
                Optional<Expression> scope = methodExpr.getScope();
                if (scope.isPresent() && scope.get() instanceof NameExpr scopeName
                        && scopeName.getNameAsString().equals(paramName)) {
                    methodExpr.setScope(methodCall.getArgument(i));
                }
            } else if (expr instanceof AssignExpr assignExpr &&
                    assignExpr.getTarget() instanceof NameExpr nameExpr && nameExpr.getNameAsString().equals(paramName)) {
                assignExpr.setTarget(new NameExpr(argName));
            }
        }
    }

    private ReturnStmt findReturnStatement(MethodDeclaration method, boolean isEmpty) {
        return method.findAll(ReturnStmt.class).stream()
                .filter(r -> r.getExpression()
                        .map(e -> isEmpty == e.toString().contains("Optional.empty"))
                        .orElse(false))
                .findFirst()
                .orElse(null);
    }

    @Override
    @SuppressWarnings("java:S1872")
    void invokeReflectively(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
        super.invokeReflectively(v, reflectionArguments);

        if (v.getValue() instanceof Class<?> clazz && clazz.getName().equals(Reflect.JAVA_UTIL_OPTIONAL)) {
            Object[] finalArgs = reflectionArguments.getFinalArgs();
            if (finalArgs.length == 1 && reflectionArguments.getMethodName().equals("ofNullable")) {
                handleOptionalOfNullable(reflectionArguments);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleOptionalOfNullable(ReflectionArguments reflectionArguments) {
        Statement stmt = reflectionArguments.getMethodCallExpression().findAncestor(Statement.class).orElseThrow();
        LineOfCode l = Branching.get(stmt.hashCode());
        if (l != null) {
            return;
        }

        Expression expr = reflectionArguments.getMethodCallExpression();
        if (expr instanceof MethodCallExpr mce) {
            Expression argument = mce.getArguments().getFirst().orElseThrow();
            if (argument.isNameExpr()) {
                l = new LineOfCode(stmt);
                Branching.add(l);

                if (returnValue != null && returnValue.getValue() instanceof Optional<?> opt) {
                    Object value = null;
                    if (opt.isPresent()) {
                        l.setPathTaken(LineOfCode.TRUE_PATH);
                    } else {
                        value = Reflect.getDefault(argument.getClass());
                        l.setPathTaken(LineOfCode.FALSE_PATH);
                    }
                    Map.Entry<Expression, Object> entry = new AbstractMap.SimpleEntry<>(argument, value);
                    setupConditionThroughAssignment(stmt, entry);
                }
            }
        }
    }

    @Override
    protected Variable resolvePrimitiveOrBoxedVariable(VariableDeclarator variable, Type t) throws ReflectiveOperationException {
        Optional<Expression> init = variable.getInitializer();
        if (init.isPresent()) {
            return super.resolvePrimitiveOrBoxedVariable(variable, t);
        }

        return new Variable(t);
    }

    @Override
    Variable resolveVariableRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws ReflectiveOperationException {
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(resolvedClass).orElseThrow();
        if (type instanceof ClassOrInterfaceDeclaration cdecl) {
            String nameAsString = variable.getNameAsString();

            MockingEvaluator eval = EvaluatorFactory.create(resolvedClass, MockingEvaluator.class);
            Variable v = new Variable(eval);
            String init = ArgumentGenerator.instantiateClass(cdecl, nameAsString).replace(";","");
            String[] parts = init.split("=");
            v.setInitializer(List.of(StaticJavaParser.parseExpression(parts[1])));
            return v;
        }

        return super.resolveVariableRepresentedByCode(variable, resolvedClass);
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

    protected void parameterAssignment(AssignExpr assignExpr, Variable va) {
        Expression value = assignExpr.getValue();
        Object result = switch (va.getClazz().getSimpleName()) {
            case "Integer" -> {
                if (value instanceof NullLiteralExpr) {
                    yield null;
                }
                if (value.isStringLiteralExpr() && value.asStringLiteralExpr().getValue().equals("T")) {
                    assignExpr.setValue(new IntegerLiteralExpr().setValue("1"));
                    yield 1;
                }

                yield Integer.parseInt(value.toString());
            }
            case "Double" -> Double.parseDouble(value.toString());
            case "Long", "LongLiteralExpr" -> {
                if (value instanceof NullLiteralExpr ) {
                    yield null;
                }
                if (value.isStringLiteralExpr()) {
                    String s = value.asStringLiteralExpr().getValue().replace("L","");
                    if (s.equals("T")) {
                        assignExpr.setValue(new LongLiteralExpr().setValue("1L"));
                        yield Long.valueOf("1");
                    }
                    yield Long.parseLong(s);
                }
                if (value.isLongLiteralExpr()) {
                    yield value;
                }
                yield Long.parseLong(value.toString());
            }
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
            default -> {
                try {
                    yield evaluateExpression(value).getValue();
                } catch (ReflectiveOperationException e) {
                    throw new AntikytheraException(e);
                }
            }
        };
        va.setValue(result);
    }

}
