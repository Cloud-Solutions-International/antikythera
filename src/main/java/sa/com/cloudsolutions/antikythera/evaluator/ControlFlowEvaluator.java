package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.IOException;
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
    @SuppressWarnings("unchecked")
    Optional<Expression> setupConditionThroughAssignment(Statement stmt, Map.Entry<Expression, Object> entry) {
        NameExpr nameExpr = entry.getKey().asNameExpr();
        Variable v = getValue(stmt, nameExpr.getNameAsString());
        if (v != null) {
            Expression init = v.getInitializer();
            if (init != null) {
                MethodDeclaration md = stmt.findAncestor(MethodDeclaration.class).orElseThrow();

                String targetParamName = nameExpr.getNameAsString();
                for (Parameter param : md.getParameters()) {
                    if (param.getNameAsString().equals(targetParamName)) {
                        Expression expr = setupConditionThroughAssignment(entry, v);
                        addPreCondition(stmt, expr);
                        return Optional.of(expr);
                    }
                }
                /*
                 * We tried to match the name of the variable with the name of the parameter, but
                 * a match could not be found. So it is not possible to force branching by
                 * assigning values to a parameter in a conditional
                 */
                return Optional.empty();
            }
        } else {
            v = new Variable(entry.getValue());
        }

        Expression expr = setupConditionThroughAssignment(entry, v);
        addPreCondition(stmt, expr);
        return Optional.of(expr);
    }

    private Expression setupConditionThroughAssignment(Map.Entry<Expression, Object> entry, Variable v) {
        NameExpr nameExpr = entry.getKey().asNameExpr();
        Expression valueExpr;
        if (v.getType() instanceof PrimitiveType) {
            valueExpr = Reflect.createLiteralExpression(entry.getValue());
        } else {
            valueExpr = setupConditionForNonPrimitive(entry, v);
        }

        return new AssignExpr(
                new NameExpr(nameExpr.getNameAsString()),
                valueExpr,
                AssignExpr.Operator.ASSIGN
        );
    }

    private Expression setupConditionForNonPrimitive(Map.Entry<Expression, Object> entry, Variable v) {
        if (entry.getValue() instanceof List<?> list) {
            if (list.isEmpty()) {
                if (v.getValue() instanceof List<?>) {
                    return StaticJavaParser.parseExpression("List.of()");
                } else if (v.getValue() instanceof Set<?>) {
                    return StaticJavaParser.parseExpression("Set.of()");
                } else if (v.getValue() instanceof Map<?,?>) {
                    return StaticJavaParser.parseExpression("Map.of()");
                }
            }
            else if(entry.getKey() instanceof NameExpr name) {
                return setupNonEmptyCollections(v, name);
            }
        }
        return entry.getValue() == null ? new NullLiteralExpr()
                        : new StringLiteralExpr(entry.getValue().toString());
    }

    /**
     * Conditional statements may check for emptiness in a collection or map. Create suitable non-empty objects
     * @param v represents the type of collection or map that we need
     * @param name the name of the variable
     * @return an expression that can be used to set up the condition
     */
    private Expression setupNonEmptyCollections(Variable v, NameExpr name) {
        Parameter param = currentConditional.getMethodDeclaration().getParameterByName(name.getNameAsString()).orElseThrow();
        Type type = param.getType();
        NodeList<Type> typeArgs = type.asClassOrInterfaceType().getTypeArguments().orElse(new NodeList<>());
        if (typeArgs.isEmpty()) {
            typeArgs.add(new ClassOrInterfaceType().setName("Object"));
        }
        VariableDeclarator vdecl = new VariableDeclarator(typeArgs.get(0), name.getNameAsString());

        try {
            Variable resolved = resolveVariableDeclaration(vdecl);
            if (resolved.getValue() == null && Reflect.isPrimitiveOrBoxed(resolved.getType().asString())) {
                resolved = Reflect.variableFactory(resolved.getType().asString());
            }

            if (v.getValue() instanceof List<?>) {
                return StaticJavaParser.parseExpression(String.format("List.of(%s)", resolved.getInitializer()));
            }
            if (v.getValue() instanceof Set<?>) {
                return StaticJavaParser.parseExpression(String.format("Set.of(%s)", resolved.getInitializer()));
            }
            if (v.getValue() instanceof Map<?,?>) {
                if (typeArgs.size() == 1) {
                    typeArgs.add(new ClassOrInterfaceType().setName("Object"));
                }
                VariableDeclarator vdecl2 = new VariableDeclarator(typeArgs.get(1), name.getNameAsString());
                Variable resolved2 = resolveVariableDeclaration(vdecl2);
                if (resolved2.getValue() == null && Reflect.isPrimitiveOrBoxed(resolved2.getType().asString())) {
                    resolved2 = Reflect.variableFactory(resolved2.getType().asString());
                }

                return StaticJavaParser.parseExpression(
                        String.format("Map.of(%s, %s)",
                                resolved.getInitializer(), resolved2.getInitializer()));
            }

        } catch (ReflectiveOperationException|IOException e) {
            throw new AntikytheraException(e);
        }
        return null;
    }

    void setupConditionThroughMethodCalls(Statement stmt, Map.Entry<Expression, Object> entry) {
        ScopeChain chain = ScopeChain.findScopeChain(entry.getKey());
        setupConditionThroughMethodCalls(stmt, entry, chain);
    }

    private void setupConditionThroughMethodCalls(Statement stmt, Map.Entry<Expression, Object> entry, ScopeChain chain) {
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
                setupConditionalVariable(stmt, entry, expr);
            }
        }
    }

    private void setupConditionalVariable(Statement stmt, Map.Entry<Expression, Object> entry, Expression scope) {
        MethodCallExpr setter = new MethodCallExpr();
        String name = entry.getKey().asMethodCallExpr().getNameAsString();
        if (name.startsWith("is")) {
            setter.setName("set" + name.substring(2));
        } else {
            setter.setName("set" + name.substring(3));
        }
        setter.setScope(scope);

        if (entry.getValue() == null) {
            setter.addArgument("null");
        } else {
            if (entry.getValue().equals("T")) {
                setupConditionalNotNullValue(stmt, entry, name, setter);
            } else {
                setter.addArgument(entry.getValue().toString());
            }
        }
        addPreCondition(stmt, setter);
    }

    private void setupConditionalNotNullValue(Statement stmt, Map.Entry<Expression, Object> entry, String name, MethodCallExpr setter) {
        MethodCallExpr mce = entry.getKey().asMethodCallExpr();
        String value = "\"T\"";
        if (mce.getScope().isPresent()) {
            Variable scopeVar = getValue(stmt, mce.getScope().orElseThrow().toString());
            if (scopeVar != null && scopeVar.getValue() instanceof Evaluator evaluator) {
                Variable field = evaluator.fields.get(
                        ClassProcessor.classToInstanceName(name.substring(3)));
                if (field != null && field.getClazz() != null) {
                    Variable v = Reflect.variableFactory(field.getClazz().getName());
                    if (v != null) {
                        value = v.getInitializer().toString();
                    }
                }
            }
        }
        setter.addArgument(value);
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
                        setupConditionThroughAssignment(stmt, entry).ifPresent(expressions::add);
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
                expressions = setupConditionalsForOptional(nonEmptyReturn, method, stmt, false);
                l.setPathTaken(LineOfCode.TRUE_PATH);
            } else {
                ReturnStmt emptyReturn = findReturnStatement(method, true);
                expressions = setupConditionalsForOptional(emptyReturn, method, stmt, true);
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
        List<Precondition> expressions;
        if (l.getPathTaken() != LineOfCode.BOTH_PATHS) {
            expressions = l.getPreconditions();

            for (Precondition expression : expressions) {
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

        if (v.getValue() instanceof Class<?> clazz && clazz.getName().equals("java.util.Optional")) {
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
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(resolvedClass);
        TypeDeclaration<?> type = AbstractCompiler.getMatchingType(cu, resolvedClass).orElseThrow();
        if (type instanceof ClassOrInterfaceDeclaration cdecl) {
            MockingEvaluator eval = EvaluatorFactory.create(resolvedClass, MockingEvaluator.class);
            Variable v = new Variable(eval);
            String init = ArgumentGenerator.instantiateClass(cdecl, variable.getNameAsString()).replace(";","");
            String[] parts = init.split("=");
            v.setInitializer(StaticJavaParser.parseExpression(parts[1]));
            return v;
        }

        return super.resolveVariableRepresentedByCode(variable, resolvedClass);
    }
}
