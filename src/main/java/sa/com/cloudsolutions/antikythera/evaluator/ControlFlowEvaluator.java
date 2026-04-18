package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingCall;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.evaluator.GeneratorState;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

        Symbol v = getValue(stmt, nameExpr.getNameAsString());
        if (v != null) {
            List<Expression> expr = setupConditionThroughAssignmentForLocal(stmt, entry, v, nameExpr);
            if (expr != null) return expr;
        } else {
            List<Expression> derivedExpr = setupConditionThroughPriorLocalAssignment(stmt, entry, nameExpr.getNameAsString());
            if (!derivedExpr.isEmpty()) {
                for (Expression expression : derivedExpr) {
                    addPreCondition(stmt, expression);
                }
                return derivedExpr;
            }
            List<Expression> mockExpr = setupConditionThroughExistingMock(stmt, entry);
            if (!mockExpr.isEmpty()) {
                for (Expression expression : mockExpr) {
                    addPreCondition(stmt, expression);
                }
                return mockExpr;
            }
            v = new Variable(entry.getValue());
        }

        List<Expression> expr = setupConditionThroughAssignment(entry, v);
        for (Expression e : expr) {
            addPreCondition(stmt, e);
        }

        return expr;
    }

    private List<Expression> setupConditionThroughPriorLocalAssignment(Statement stmt, Map.Entry<Expression, Object> entry,
                                                                       String variableName) {
        MethodDeclaration methodDeclaration = stmt.findAncestor(MethodDeclaration.class).orElse(null);
        if (methodDeclaration == null || methodDeclaration.getBody().isEmpty()) {
            return List.of();
        }
        Expression assignedExpression = findPreviousAssignmentExpression(methodDeclaration.getBody().orElseThrow(), stmt, variableName);
        if (assignedExpression == null) {
            if (BranchingTrace.isEnabled()) {
                BranchingTrace.record("priorLocal:miss|name=" + variableName + "|statement=" + stmt);
            }
            return List.of();
        }

        if (assignedExpression.isConditionalExpr()) {
            assignedExpression = selectConditionalBranch(assignedExpression.asConditionalExpr());
        }
        List<Expression> derivedExpressions = setupConditionThroughDerivedLocalAssignment(stmt, assignedExpression, entry);
        if (!derivedExpressions.isEmpty()) {
            if (BranchingTrace.isEnabled()) {
                BranchingTrace.record("priorLocal:emit|name=" + variableName + "|expression=" + derivedExpressions);
            }
            return derivedExpressions;
        }
        if (!assignedExpression.isMethodCallExpr()) {
            if (BranchingTrace.isEnabled()) {
                BranchingTrace.record("priorLocal:skip|name=" + variableName + "|expression=" + assignedExpression);
            }
            return List.of();
        }

        MethodCallExpr methodCallExpr = assignedExpression.asMethodCallExpr();
        Type returnType = resolveMethodCallReturnType(methodCallExpr);
        if (returnType == null) {
            if (BranchingTrace.isEnabled()) {
                BranchingTrace.record("priorLocal:skip|name=" + variableName + "|reason=noReturnType|expression=" + methodCallExpr);
            }
            return List.of();
        }
        Expression returnValue = adaptDomainValueToParameterType(returnType, entry.getValue());
        if (returnValue == null) {
            if (BranchingTrace.isEnabled()) {
                BranchingTrace.record("priorLocal:skip|name=" + variableName + "|reason=noReturnValue|type=" + returnType);
            }
            return List.of();
        }

        // When the getter call's scope is a simple name (e.g. pr.getSource()),
        // generate a setter-call precondition (pr.setSource("ALL")) instead of
        // Mockito.when(pr.getSource()).thenReturn("ALL"). The setter form has
        // scope NameExpr("pr") which matches applyPreconditions()'s check.
        if (methodCallExpr.getScope().isPresent()
                && methodCallExpr.getScope().orElseThrow() instanceof NameExpr scopeExpr
                && getValue(stmt, scopeExpr.getNameAsString()) != null) {
            String setterName = AbstractCompiler.setterNameFromGetterName(methodCallExpr.getNameAsString());
            if (!setterName.equals(methodCallExpr.getNameAsString())) {
                MethodCallExpr setter = new MethodCallExpr();
                setter.setName(setterName);
                setter.setScope(scopeExpr.clone());
                setter.addArgument(returnValue);
                BranchingTrace.record(() -> "priorLocal:emit|name=" + variableName + "|expression=" + setter);
                return List.of(setter);
            }
        }

        MethodCallExpr when = new MethodCallExpr(new NameExpr("Mockito"), "when")
                .addArgument(methodCallExpr.clone());
        MethodCallExpr thenReturn = new MethodCallExpr(when, "thenReturn")
                .addArgument(returnValue);
        BranchingTrace.record(() -> "priorLocal:emit|name=" + variableName + "|expression=" + thenReturn);
        return List.of(thenReturn);
    }

    private List<Expression> setupConditionThroughDerivedLocalAssignment(Statement stmt,
                                                                         Expression assignedExpression,
                                                                         Map.Entry<Expression, Object> entry) {
        if (!assignedExpression.isMethodCallExpr()) {
            return List.of();
        }
        MethodCallExpr methodCallExpr = assignedExpression.asMethodCallExpr();
        List<Expression> expressions = setupConditionThroughOptionalFallback(stmt, methodCallExpr, entry);
        if (!expressions.isEmpty()) {
            return expressions;
        }
        return List.of();
    }

    private List<Expression> setupConditionThroughOptionalFallback(Statement stmt,
                                                                   MethodCallExpr methodCallExpr,
                                                                   Map.Entry<Expression, Object> entry) {
        if (!"orElse".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 1) {
            return List.of();
        }
        Expression scope = methodCallExpr.getScope().orElse(null);
        if (!(scope instanceof MethodCallExpr optionalCall)
                || !"ofNullable".equals(optionalCall.getNameAsString())
                || optionalCall.getArguments().size() != 1) {
            return List.of();
        }

        Expression source = optionalCall.getArgument(0);
        if (!source.isNameExpr()) {
            return List.of();
        }

        Object fallbackValue = literalValueOrSentinel(methodCallExpr.getArgument(0));
        if (fallbackValue == UnresolvedLiteral.INSTANCE) {
            return List.of();
        }

        Object sourceValue = java.util.Objects.equals(entry.getValue(), fallbackValue) ? null : entry.getValue();
        Symbol sourceSymbol = getValue(stmt, source.toString());
        if (sourceSymbol == null) {
            return List.of();
        }
        return setupConditionThroughAssignment(new AbstractMap.SimpleEntry<>(source.clone(), sourceValue), sourceSymbol);
    }

    private Object literalValueOrSentinel(Expression expression) {
        if (expression.isNullLiteralExpr()) {
            return null;
        }
        if (expression instanceof IntegerLiteralExpr integerLiteralExpr) {
            return integerLiteralExpr.asNumber().intValue();
        }
        if (expression instanceof LongLiteralExpr longLiteralExpr) {
            return longLiteralExpr.asNumber().longValue();
        }
        if (expression instanceof StringLiteralExpr stringLiteralExpr) {
            return stringLiteralExpr.getValue();
        }
        if (expression instanceof BooleanLiteralExpr booleanLiteralExpr) {
            return booleanLiteralExpr.getValue();
        }
        return UnresolvedLiteral.INSTANCE;
    }

    private enum UnresolvedLiteral {
        INSTANCE
    }

    private Expression findPreviousAssignmentExpression(BlockStmt block, Statement currentStatement, String variableName) {
        Expression latestCandidate = null;
        for (Statement statement : block.getStatements()) {
            if (statement == currentStatement) {
                break;
            }
            Expression candidate = extractAssignmentExpression(statement, variableName);
            if (candidate != null) {
                latestCandidate = candidate;
            }
        }
        return latestCandidate;
    }

    private Expression extractAssignmentExpression(Statement statement, String variableName) {
        if (statement.isIfStmt()) {
            return extractAssignmentExpression(statement.asIfStmt(), variableName);
        }
        if (!(statement instanceof ExpressionStmt expressionStmt)) {
            return null;
        }
        Expression expression = expressionStmt.getExpression();
        if (expression.isVariableDeclarationExpr()) {
            for (VariableDeclarator variableDeclarator : expression.asVariableDeclarationExpr().getVariables()) {
                if (variableDeclarator.getNameAsString().equals(variableName)) {
                    return variableDeclarator.getInitializer().orElse(null);
                }
            }
        }
        if (expression.isAssignExpr()) {
            AssignExpr assignExpr = expression.asAssignExpr();
            if (assignExpr.getTarget().isNameExpr()
                    && assignExpr.getTarget().asNameExpr().getNameAsString().equals(variableName)) {
                return assignExpr.getValue();
            }
        }
        return null;
    }

    private Expression extractAssignmentExpression(IfStmt ifStmt, String variableName) {
        boolean conditionValue = resolveConditionValue(ifStmt.getCondition()).orElseGet(() ->
                evaluateConditionSafely(ifStmt.getCondition()));
        Statement selectedBranch = conditionValue
                ? ifStmt.getThenStmt()
                : ifStmt.getElseStmt().orElse(null);
        if (selectedBranch == null) {
            return null;
        }
        if (selectedBranch.isBlockStmt()) {
            for (Statement nested : selectedBranch.asBlockStmt().getStatements()) {
                Expression expression = extractAssignmentExpression(nested, variableName);
                if (expression != null) {
                    return expression;
                }
            }
            return null;
        }
        return extractAssignmentExpression(selectedBranch, variableName);
    }

    private boolean evaluateConditionSafely(Expression condition) {
        try {
            Variable variable = evaluateExpression(condition);
            return variable != null && Boolean.TRUE.equals(variable.getValue());
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    private Expression selectConditionalBranch(ConditionalExpr conditionalExpr) {
        return resolveConditionValue(conditionalExpr.getCondition()).orElseGet(() ->
                evaluateConditionSafely(conditionalExpr.getCondition()))
                ? conditionalExpr.getThenExpr()
                : conditionalExpr.getElseExpr();
    }

    private Optional<Boolean> resolveConditionValue(Expression condition) {
        if (currentConditional == null) {
            return Optional.empty();
        }
        BranchAttempt attempt = Branching.getBranchAttempt(
                currentConditional.getCallableDeclaration(), currentConditional);
        List<Expression> applicableExpressions = attempt.applicableConditions().stream()
                .map(Precondition::getExpression)
                .toList();

        Optional<Boolean> fromAssignments = resolveConditionFromAssignments(condition, applicableExpressions);
        if (fromAssignments.isPresent()) {
            return fromAssignments;
        }
        return resolveConditionFromSetterPreconditions(condition, applicableExpressions);
    }

    private Optional<Boolean> resolveConditionFromAssignments(Expression condition, List<Expression> expressions) {
        if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            return expressions.stream()
                    .filter(AssignExpr.class::isInstance)
                    .map(AssignExpr.class::cast)
                    .filter(assignExpr -> assignExpr.getTarget().isNameExpr()
                            && assignExpr.getTarget().asNameExpr().getNameAsString().equals(name))
                    .max(Comparator.comparingInt(expr -> expr.toString().length()))
                    .flatMap(assignExpr -> booleanLiteralValue(assignExpr.getValue()));
        }
        return Optional.empty();
    }

    private Optional<Boolean> resolveConditionFromSetterPreconditions(Expression condition, List<Expression> expressions) {
        if (!condition.isMethodCallExpr()) {
            return Optional.empty();
        }
        MethodCallExpr conditionCall = condition.asMethodCallExpr();
        if (conditionCall.getScope().isEmpty() || !(conditionCall.getScope().orElseThrow() instanceof NameExpr scope)) {
            return Optional.empty();
        }

        String utility = scope.getNameAsString();
        if (!utility.equals(TruthTable.STRING_UTILS) && !utility.equals(TruthTable.COLLECTION_UTILS)) {
            return Optional.empty();
        }
        if (conditionCall.getArguments().size() != 1 || !conditionCall.getArgument(0).isMethodCallExpr()) {
            return Optional.empty();
        }

        MethodCallExpr getter = conditionCall.getArgument(0).asMethodCallExpr();
        if (getter.getScope().isEmpty()) {
            return Optional.empty();
        }
        String target = getter.getScope().orElseThrow().toString();
        String setterName = getter.getNameAsString().startsWith("get")
                ? "set" + getter.getNameAsString().substring(3)
                : getter.getNameAsString().startsWith("is")
                ? "set" + getter.getNameAsString().substring(2)
                : null;
        if (setterName == null) {
            return Optional.empty();
        }

        for (int i = expressions.size() - 1; i >= 0; i--) {
            Expression expression = expressions.get(i);
            if (!(expression instanceof MethodCallExpr setter) || setter.getScope().isEmpty()) {
                continue;
            }
            if (!setter.getNameAsString().equals(setterName) || setter.getArguments().size() != 1) {
                continue;
            }
            if (!setter.getScope().orElseThrow().toString().equals(target)) {
                continue;
            }
            return evaluateUtilityCondition(utility, conditionCall.getNameAsString(), setter.getArgument(0));
        }
        return Optional.empty();
    }

    private Optional<Boolean> evaluateUtilityCondition(String utility, String methodName, Expression argument) {
        if (utility.equals(TruthTable.STRING_UTILS) && methodName.equals("isEmpty")) {
            if (argument.isNullLiteralExpr()) {
                return Optional.of(true);
            }
            if (argument.isStringLiteralExpr()) {
                return Optional.of(argument.asStringLiteralExpr().getValue().isEmpty());
            }
            return Optional.of(false);
        }
        if (utility.equals(TruthTable.COLLECTION_UTILS) && methodName.equals("isEmpty")) {
            if (argument.isMethodCallExpr()) {
                String text = argument.toString();
                if (text.equals("List.of()") || text.equals("Set.of()") || text.equals("Map.of()")) {
                    return Optional.of(true);
                }
            }
            if (argument.isObjectCreationExpr()) {
                return Optional.of(argument.asObjectCreationExpr().getArguments().isEmpty());
            }
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private Optional<Boolean> booleanLiteralValue(Expression expression) {
        if (expression.isBooleanLiteralExpr()) {
            return Optional.of(expression.asBooleanLiteralExpr().getValue());
        }
        return Optional.empty();
    }

    protected Type resolveMethodCallReturnType(MethodCallExpr methodCallExpr) {
        MethodResolution resolution = resolveMethodCall(methodCallExpr);
        if (resolution.returnType() != null) {
            return resolution.returnType();
        }
        return null;
    }

    private MethodResolution resolveMethodCall(MethodCallExpr methodCallExpr) {
        try {
            Type resolvedReturnType = resolveReturnTypeFromResolvedMethodUsage(methodCallExpr);
            MCEWrapper lightweightWrapper = new MCEWrapper(methodCallExpr);
            Callable callable = AbstractCompiler.resolveCallableFromResolvedMethod(methodCallExpr, lightweightWrapper)
                    .orElse(null);
            if (callable == null) {
                MCEWrapper wrapper = wrapCallExpression(methodCallExpr);
                callable = wrapper.getMatchingCallable();
                if (callable == null) {
                    callable = resolveCallableForReturnType(methodCallExpr, wrapper).orElse(null);
                    if (callable != null) {
                        wrapper.setMatchingCallable(callable);
                    }
                }
            } else {
                lightweightWrapper.setMatchingCallable(callable);
            }
            Type callableReturnType = extractReturnType(callable);
            if (resolvedReturnType != null || callableReturnType != null) {
                return new MethodResolution(callable, resolvedReturnType != null ? resolvedReturnType : callableReturnType);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Best effort only.
        }
        try {
            ResolvedType resolvedType = methodCallExpr.calculateResolvedType();
            return new MethodResolution(null, StaticJavaParser.parseType(resolvedType.describe()));
        } catch (RuntimeException ignored) {
            // Best effort only.
        }
        return new MethodResolution(null, null);
    }

    private Type resolveReturnTypeFromResolvedMethodUsage(MethodCallExpr methodCallExpr) {
        Optional<MethodUsage> usage = AbstractCompiler.resolveMethodAsUsage(methodCallExpr);
        if (usage.isPresent()) {
            return parseResolvedType(usage.get().returnType());
        }
        Optional<ResolvedMethodDeclaration> declaration = AbstractCompiler.resolveMethodDeclaration(methodCallExpr);
        if (declaration.isPresent()) {
            return parseResolvedType(declaration.get().getReturnType());
        }
        return null;
    }

    private Type parseResolvedType(ResolvedType resolvedType) {
        try {
            return StaticJavaParser.parseType(resolvedType.describe());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Optional<Callable> resolveCallableForReturnType(MethodCallExpr methodCallExpr, MCEWrapper wrapper) {
        if (methodCallExpr.getScope().isPresent()) {
            Optional<Callable> scopedCallable = resolveCallableFromScope(methodCallExpr, wrapper,
                    methodCallExpr.getScope().orElseThrow());
            if (scopedCallable.isPresent()) {
                return scopedCallable;
            }
        }
        if (typeDeclaration != null) {
            return AbstractCompiler.findCallableDeclaration(wrapper, typeDeclaration);
        }
        return Optional.empty();
    }

    private Optional<Callable> resolveCallableFromScope(MethodCallExpr methodCallExpr, MCEWrapper wrapper,
                                                        Expression scopeExpression) {
        if (scopeExpression.isThisExpr() && typeDeclaration != null) {
            return AbstractCompiler.findCallableDeclaration(wrapper, typeDeclaration);
        }

        Type scopeType = resolveScopeType(methodCallExpr, scopeExpression);
        if (scopeType == null || cu == null) {
            return Optional.empty();
        }

        TypeWrapper wrapperType = AbstractCompiler.findType(cu, scopeType);
        if (wrapperType == null) {
            return Optional.empty();
        }
        if (wrapperType.getType() != null) {
            return AbstractCompiler.findCallableDeclaration(wrapper, wrapperType.getType());
        }
        return Optional.empty();
    }

    private Type resolveScopeType(MethodCallExpr methodCallExpr, Expression scopeExpression) {
        if (scopeExpression.isNameExpr()) {
            String name = scopeExpression.asNameExpr().getNameAsString();
            Symbol symbol = getValue(methodCallExpr, name);
            if (symbol != null && symbol.getType() != null) {
                return symbol.getType();
            }
            Type declaredType = findDeclaredType(methodCallExpr, name);
            if (declaredType != null) {
                return declaredType;
            }
        }

        try {
            ResolvedType resolvedType = scopeExpression.calculateResolvedType();
            return StaticJavaParser.parseType(resolvedType.describe());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Type findDeclaredType(MethodCallExpr methodCallExpr, String name) {
        MethodDeclaration methodDeclaration = methodCallExpr.findAncestor(MethodDeclaration.class).orElse(null);
        if (methodDeclaration != null) {
            for (Parameter parameter : methodDeclaration.getParameters()) {
                if (parameter.getNameAsString().equals(name)) {
                    return parameter.getType();
                }
            }
            for (VariableDeclarator variableDeclarator : methodDeclaration.findAll(VariableDeclarator.class)) {
                if (variableDeclarator.getNameAsString().equals(name)) {
                    return variableDeclarator.getType();
                }
            }
        }
        if (typeDeclaration != null) {
            for (VariableDeclarator variableDeclarator : typeDeclaration.findAll(VariableDeclarator.class)) {
                if (variableDeclarator.getNameAsString().equals(name)) {
                    return variableDeclarator.getType();
                }
            }
        }
        return null;
    }

    private Type extractReturnType(Callable callable) {
        if (callable == null) {
            return null;
        }
        if (callable.isMethodDeclaration()) {
            return callable.asMethodDeclaration().getType();
        }
        if (callable.getMethod() != null) {
            try {
                return StaticJavaParser.parseType(callable.getMethod().getGenericReturnType().getTypeName());
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private record MethodResolution(Callable callable, Type returnType) {
    }

    private List<Expression> setupConditionThroughExistingMock(Statement stmt, Map.Entry<Expression, Object> entry) {
        List<MockingCall> mocks = MockingRegistry.getAllMocks();
        for (int i = mocks.size() - 1; i >= 0; i--) {
            MockingCall mockingCall = mocks.get(i);
            Expression whenThen = rewriteMockReturnExpression(mockingCall, entry.getValue());
            if (whenThen != null) {
                BranchingTrace.record(() -> "mockFallback:emit|statement=" + stmt + "|expression=" + whenThen);
                return List.of(whenThen);
            }
        }
        return List.of();
    }

    private Expression rewriteMockReturnExpression(MockingCall mockingCall, Object domainValue) {
        if (mockingCall.getExpression() == null || mockingCall.getExpression().isEmpty()) {
            return null;
        }
        Expression last = mockingCall.getExpression().getLast();
        if (!(last instanceof MethodCallExpr thenReturn) || !thenReturn.getNameAsString().equals("thenReturn")) {
            return null;
        }
        Type returnType = resolveMockingCallReturnType(mockingCall);
        if (returnType == null) {
            return null;
        }
        Expression returnValue = adaptDomainValueToParameterType(returnType, domainValue);
        if (returnValue == null) {
            return null;
        }

        MethodCallExpr rewritten = thenReturn.clone();
        rewritten.setArgument(0, returnValue);
        return rewritten;
    }

    private Type resolveMockingCallReturnType(MockingCall mockingCall) {
        if (mockingCall.getVariable() != null && mockingCall.getVariable().getType() != null) {
            return mockingCall.getVariable().getType();
        }
        if (mockingCall.getCallable() != null && mockingCall.getCallable().isMethodDeclaration()) {
            return mockingCall.getCallable().asMethodDeclaration().getType();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Expression> setupConditionThroughAssignmentForLocal(Statement stmt, Map.Entry<Expression, Object> entry, Symbol v, NameExpr nameExpr) {
        if (v instanceof Variable variable) {
            BranchingTrace.record(() -> "localAssign:"
                    + nameExpr.getNameAsString()
                    + "|type=" + variable.getType()
                    + "|value=" + variable.getValue()
                    + "|initializers=" + variable.getInitializer().size());
        } else {
            BranchingTrace.record(() -> "localAssign:" + nameExpr.getNameAsString() + "|symbol=" + v.getClass().getSimpleName());
        }
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
            List<Expression> stubExpressions = setupConditionThroughMockedLocalInitializer(entry, v);
            if (!stubExpressions.isEmpty()) {
                for (Expression expression : stubExpressions) {
                    addPreCondition(stmt, expression);
                }
                return stubExpressions;
            }

            List<Expression> fieldExpressions = setupCollectionFieldPrecondition(entry, v, nameExpr);
            if (!fieldExpressions.isEmpty()) {
                for (Expression expression : fieldExpressions) {
                    addPreCondition(stmt, expression);
                }
                return fieldExpressions;
            }
            /*
             * We tried to match the name of the variable with the name of the parameter, but
             * a match could not be found. So it is not possible to force branching by
             * assigning values to a parameter in a conditional
             */
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Expression> setupCollectionFieldPrecondition(Map.Entry<Expression, Object> entry,
                                                              Symbol v, NameExpr nameExpr) {
        if (!(v.getValue() instanceof Collection<?>) || !(entry.getValue() instanceof List<?> domainList)) {
            return List.of();
        }
        if (domainList.isEmpty()) {
            return List.of();
        }
        MethodCallExpr addCall = new MethodCallExpr(nameExpr.clone(), "add");
        Object seedValue = domainList.getFirst();
        Expression seedExpression;
        if (seedValue == null) {
            seedExpression = new NullLiteralExpr();
        } else {
            Expression literal = Reflect.createLiteralExpression(seedValue);
            if (literal instanceof StringLiteralExpr && !(seedValue instanceof String)) {
                seedExpression = new NullLiteralExpr();
            } else {
                seedExpression = literal;
            }
        }
        addCall.addArgument(seedExpression);
        BranchingTrace.record(() -> "fieldCollection:add|name=" + nameExpr.getNameAsString());
        return List.of(addCall);
    }

    private List<Expression> setupConditionThroughMockedLocalInitializer(Map.Entry<Expression, Object> entry, Symbol value) {
        if (value.getInitializer().isEmpty()) {
            BranchingTrace.record(() -> "localStub:skip|reason=noInitializer|entry=" + entry.getKey());
            return List.of();
        }
        Expression initializer = value.getInitializer().getFirst();
        if (!initializer.isMethodCallExpr()) {
            BranchingTrace.record(() -> "localStub:skip|reason=initializer=" + initializer.getClass().getSimpleName()
                    + "|entry=" + entry.getKey());
            return List.of();
        }

        Type returnType = resolveMockedLocalReturnType(value, initializer.asMethodCallExpr());
        if (returnType == null) {
            BranchingTrace.record(() -> "localStub:skip|reason=noReturnType|initializer=" + initializer
                    + "|entry=" + entry.getKey());
            return List.of();
        }

        Expression returnValue = adaptDomainValueToParameterType(returnType, entry.getValue());
        if (returnValue == null) {
            BranchingTrace.record(() -> "localStub:skip|reason=noReturnValue|returnType=" + returnType
                    + "|domain=" + entry.getValue());
            return List.of();
        }

        MethodCallExpr when = new MethodCallExpr(new NameExpr("Mockito"), "when")
                .addArgument(initializer.clone());
        MethodCallExpr thenReturn = new MethodCallExpr(when, "thenReturn")
                .addArgument(returnValue);
        BranchingTrace.record(() -> "localStub:emit|initializer=" + initializer + "|returnType=" + returnType
                + "|returnValue=" + returnValue);
        return List.of(thenReturn);
    }

    private Type resolveMockedLocalReturnType(Symbol value, MethodCallExpr initializer) {
        if (value.getType() != null) {
            return value.getType();
        }
        return resolveMethodCallReturnType(initializer);
    }

    private List<Expression> setupConditionThroughAssignment(Map.Entry<Expression, Object> entry, Symbol v) {
        Expression key = entry.getKey();
        NameExpr nameExpr = key.isNameExpr() ? key.asNameExpr() : key.asMethodCallExpr().getArgument(0).asNameExpr();

        List<Expression> objectEqualsAssignments = setupObjectEqualsReferenceAssignment(entry, v, nameExpr);
        if (!objectEqualsAssignments.isEmpty()) {
            return objectEqualsAssignments;
        }

        List<Expression> valueExpressions;
        if (entry.getValue() == null) {
            if (v.getClazz() != null && v.getClazz().isPrimitive()) {
                valueExpressions = List.of(Reflect.createLiteralExpression(Reflect.getDefault(v.getClazz())));
            } else {
                valueExpressions = List.of(new NullLiteralExpr());
            }
        } else if (v.getType() instanceof PrimitiveType) {
            valueExpressions = List.of(Reflect.createLiteralExpression(entry.getValue()));
        } else if (entry.getValue() instanceof ClassOrInterfaceType cType) {
            Variable vx = Reflect.createVariable(Reflect.getDefault(cType.getNameAsString()), cType.getNameAsString(), v.getName());
            valueExpressions = vx.getInitializer();
        } else if (entry.getValue() instanceof EnumConstantDeclaration ec) {
            valueExpressions = List.of(
                    new NameExpr(ec.getNameAsString())
            );
        } else if (entry.getValue() instanceof FieldAccessExpr fae) {
            valueExpressions = List.of(fae);
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

    private List<Expression> setupObjectEqualsReferenceAssignment(Map.Entry<Expression, Object> entry,
                                                                  Symbol v,
                                                                  NameExpr target) {
        if (!(entry.getValue() instanceof Boolean desiredState) || v.getType() instanceof PrimitiveType) {
            return List.of();
        }
        Optional<Node> parent = entry.getKey().getParentNode();
        if (parent.isEmpty() || !(parent.get() instanceof MethodCallExpr methodCallExpr)
                || !"equals".equals(methodCallExpr.getNameAsString())
                || methodCallExpr.getArguments().size() != 1) {
            return List.of();
        }
        if (!methodCallExpr.getScope().map(target::equals).orElse(false)) {
            return List.of();
        }

        Expression source = methodCallExpr.getArgument(0);
        AssignExpr assignExpr;
        if (desiredState) {
            assignExpr = new AssignExpr(new NameExpr(target.getNameAsString()), source.clone(), AssignExpr.Operator.ASSIGN);
        } else {
            Expression distinct = v.getType() != null ? createDefaultExpressionForType(v.getType()) : null;
            if (distinct == null) {
                return List.of();
            }
            assignExpr = new AssignExpr(new NameExpr(target.getNameAsString()), distinct, AssignExpr.Operator.ASSIGN);
        }
        return List.of(assignExpr);
    }

    private List<Expression> setupConditionForNonPrimitive(Map.Entry<Expression, Object> entry, Symbol v) {
        if (entry.getValue() instanceof List<?> list) {
            return setupConditionForNonPrimitive(entry, list, v);
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

    private List<Expression> setupConditionForNonPrimitive(Map.Entry<Expression, Object> entry, List<?> list, Symbol v) {
        if (list.isEmpty()) {
            // If this collection parameter is passed as an argument to a method call before the
            // condition, it may be an output parameter that gets populated before the condition is
            // checked (e.g., an error-accumulator list filled by a validateAndAddError helper).
            // Forcing it to empty would produce a test variant that is unreachable at runtime, so
            // we skip generating an override for this case.
            String varName = extractVariableName(entry.getKey());
            if (isPassedToMethodBeforeCondition(varName)) {
                return List.of();
            }
            if (v.getValue() instanceof List<?>) {
                return List.of(StaticJavaParser.parseExpression("List.of()"));
            } else if (v.getValue() instanceof Set<?>) {
                return List.of(StaticJavaParser.parseExpression("Set.of()"));
            } else if (v.getValue() instanceof Map<?,?>) {
                return List.of(StaticJavaParser.parseExpression("Map.of()"));
            }
        }
        else if(entry.getKey() instanceof NameExpr name) {
            return setupNonEmptyCollections(v, name);
        } else if (entry.getKey() instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
            Expression scope = mce.getScope().orElseThrow();
            if (scope.toString().equals(TruthTable.COLLECTION_UTILS)) {
                Expression arg = mce.getArgument(0);
                Symbol vx = getValue(mce, arg.asNameExpr().getNameAsString());
                return setupNonEmptyCollections(vx, arg.asNameExpr());
            }
        }
        return List.of();
    }
    /**
     * Conditional statements may check for emptiness in a collection or map. Create suitable non-empty objects
     * @param collection represents the type of collection or map that we need
     * @param name the name of the variable
     * @return a list of expressions that can be used to set up the condition
     */
    private List<Expression> setupNonEmptyCollections(Symbol collection, NameExpr name) {

        Optional<Parameter> paramByName = currentConditional.getMethodDeclaration().getParameterByName(name.getNameAsString());
        if (paramByName.isPresent()) {
            Parameter param = paramByName.orElseThrow();
            Type type = param.getType();
            return setupNonEmptyCollection(type, collection, name);
        }
        return List.of();
    }

    protected List<Expression> setupNonEmptyCollection(Type type, Symbol wrappedCollection, NameExpr name) {
        NodeList<Type> typeArgs = getTypeArgs(type);
        Type primaryType = typeArgs.getFirst().orElseThrow();
        VariableDeclarator vdecl = new VariableDeclarator(primaryType, name.getNameAsString());
        try {
            Variable member = resolveVariableDeclaration(vdecl);
            if (member.getValue() == null
                    && (Reflect.isPrimitiveOrBoxed(member.getType().asString()) || member.getType().asString().equals("String"))) {
                member = Reflect.variableFactory(member.getType().asString());
            } else if (member.getValue() instanceof Evaluator eval) {
                return createSingleItemCollectionWithInitializer(type, member, wrappedCollection, name, eval);
            } else if (member.getValue() == null) {
                member = recreateVariable(primaryType);
            }
            return List.of(createSingleItemCollection(type, member, wrappedCollection, name));

        } catch (ReflectiveOperationException e) {
            throw new AntikytheraException(e);
        }
    }

    private Variable recreateVariable(Type type) {
        TypeWrapper wrapper = AbstractCompiler.findType(cu, type);
        if (wrapper != null) {
            if (wrapper.getType() != null) {
               return DummyArgumentGenerator.createObjectWithSimplestConstructor(
                       wrapper.getType().asClassOrInterfaceDeclaration(),  "nomatter");
            }
            else {
                Constructor<?> constructor = DummyArgumentGenerator.findSimplestConstructor(wrapper.getClazz());
                if (constructor != null) {
                    try {
                        return DummyArgumentGenerator.createObjectWithSimplestConstructor(constructor, type);
                    } catch (ReflectiveOperationException e) {
                        throw new AntikytheraException(e);
                    }
                }
            }
        }
        throw new AntikytheraException("Could not find constructor for " + type);
    }

    private static NodeList<Type> getTypeArgs(Type type) {
        NodeList<Type> typeArgs;
        if (type.isClassOrInterfaceType()) {
            typeArgs = type.asClassOrInterfaceType().getTypeArguments().orElse(new NodeList<>());
        } else {
            typeArgs = new NodeList<>();
        }
        if (typeArgs.isEmpty()) {
            typeArgs.add(new ClassOrInterfaceType().setName("Object"));
        }
        return typeArgs;
    }

    private List<Expression> createSingleItemCollectionWithInitializer(Type type, Symbol member,
                                                                       Symbol wrappedCollection, NameExpr name, Evaluator eval) throws ReflectiveOperationException {
        NodeList<Type> typeArgs = getTypeArgs(type);
        Type pimaryType = typeArgs.getFirst().orElseThrow();
        List<Expression> fieldIntializers = eval.getFieldInitializers();
        if (fieldIntializers.isEmpty()) {
            return List.of(createSingleItemCollection(type, member, wrappedCollection, name));
        }
        else {
            List<Expression> mocks = new ArrayList<>();
            String instanceName = Variable.generateVariableName(pimaryType);
            Expression expr = StaticJavaParser.parseStatement(
                    String.format("%s %s = new %s();", pimaryType, instanceName, pimaryType)
            ).asExpressionStmt().getExpression();
            GeneratorState.addImport(new ImportDeclaration(eval.getClassName(), false, false));
            mocks.add(expr);
            for (Expression e : fieldIntializers) {
                if (e.isMethodCallExpr()) {
                    e.asMethodCallExpr().setScope(new NameExpr(instanceName));
                }
                mocks.add(e);
            }

            if (wrappedCollection.getValue() instanceof List<?>) {
                addToList(member, wrappedCollection);
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

    private Expression createSingleItemCollection(Type type, Symbol member,
                                                  Symbol wrappedCollection, NameExpr name) throws ReflectiveOperationException {
        NodeList<Type> typeArgs = getTypeArgs(type);
        List<Expression> initializer = member.getInitializer();
        if (initializer.getFirst() instanceof ObjectCreationExpr && member.getValue() instanceof Evaluator eval) {
            GeneratorState.addImport(new ImportDeclaration(eval.getClassName(), false, false));
        }
        if (wrappedCollection.getValue() instanceof List<?>) {
            addToList(member, wrappedCollection);
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

    private static void addToSet(Symbol member, Set<?> set) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method m = Set.class.getMethod("add", Object.class);
        m.invoke(set, member.getValue());
        GeneratorState.addImport(new ImportDeclaration("java.util.Set", false, false));
    }

    private static void addToMap(Symbol key, Variable value, Map<?,?> map) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method m = Map.class.getMethod("put", Object.class, Object.class);
        m.invoke(map,  key.getValue(), value.getValue());
        GeneratorState.addImport(new ImportDeclaration("java.util.Map", false, false));
    }

    private static void addToList(Symbol member, Symbol collection) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        try {
            List<?> list = (List<?>) collection.getValue();
            Method m = List.class.getMethod("add", Object.class);
            m.invoke(list, member.getValue());
            GeneratorState.addImport(new ImportDeclaration("java.util.List", false, false));
        } catch (InvocationTargetException e) {
            ArrayList<?> list = new ArrayList<>();
            Method m = List.class.getMethod("add", Object.class);
            m.invoke(list, member.getValue());
            GeneratorState.addImport(new ImportDeclaration("java.util.List", false, false));
            GeneratorState.addImport(new ImportDeclaration("java.util.ArrayList", false, false));
            collection.setValue(list);
        }
    }

    void setupConditionThroughMethodCalls(Statement stmt, Map.Entry<Expression, Object> entry) {
        ScopeChain chain = ScopeChain.findScopeChain(entry.getKey());
        if (!chain.isEmpty()) {
            Expression expr = chain.getChain().getFirst().getExpression();
            if (expr.isNameExpr()) {
                MethodCallExpr mce = chain.getExpression().asMethodCallExpr();
                if (mce.getArguments().isNonEmpty()) {
                    Expression argument = mce.getArgument(0);
                    String exprName = expr.toString();

                    if (exprName.equals("StringUtils") || exprName.equals("CollectionUtils")) {
                        handleUtilsMethodCall(stmt, entry, argument);
                        return;
                    }
                }
            }
            setupConditionThroughMethodCalls(stmt, entry, expr);
        }
    }

    private void handleUtilsMethodCall(Statement stmt, Map.Entry<Expression, Object> entry, Expression argument) {
        if (argument.isMethodCallExpr()) {
            Map.Entry<Expression, Object> argumentEntry = new AbstractMap.SimpleEntry<>(argument, entry.getValue());
            setupConditionThroughMethodCalls(stmt, argumentEntry, argument);
        } else {
            List<Expression> expressions = setupConditionThroughAssignment(stmt, entry);
            if (expressions.isEmpty()) {
                expressions = setupConditionThroughExistingMock(stmt, entry);
            }
            for (Expression expression : expressions) {
                addPreCondition(stmt, expression);
            }
        }
    }

    private void setupConditionThroughMethodCalls(Statement stmt, Map.Entry<Expression, Object> entry, Expression expr) {

        Symbol v = getValue(stmt, expr.toString());
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

        if (v != null && addCollectionMembershipPrecondition(stmt, entry, expr, v)) {
            return;
        }

        if (v != null && v.getValue() instanceof Evaluator) {
            setupConditionalVariablesWithSetter(stmt, entry, expr);
        }
    }

    private boolean addCollectionMembershipPrecondition(Statement stmt, Map.Entry<Expression, Object> entry,
                                                        Expression expr, Symbol value) {
        if (!(entry.getKey() instanceof MethodCallExpr conditionCall)
                || !(entry.getValue() instanceof Boolean desiredState)
                || !conditionCall.getScope().map(expr::equals).orElse(false)) {
            return false;
        }

        MethodCallExpr precondition = desiredState ? buildPositiveMembershipPrecondition(expr, value, conditionCall) : null;
        if (precondition != null) {
            addPreCondition(stmt, precondition);
            return true;
        }

        if (!desiredState && requiresCollectionReset(value, conditionCall.getNameAsString())) {
            Expression reset = buildEmptyCollectionInitializer(value);
            if (reset != null) {
                addPreCondition(stmt, new AssignExpr(expr.clone(), reset, AssignExpr.Operator.ASSIGN));
                return true;
            }
        }
        return false;
    }

    private MethodCallExpr buildPositiveMembershipPrecondition(Expression scope, Symbol wrappedValue,
                                                               MethodCallExpr conditionCall) {
        Object currentValue = wrappedValue.getValue();
        return switch (conditionCall.getNameAsString()) {
            case "contains" -> currentValue instanceof Collection<?>
                    ? new MethodCallExpr(scope.clone(), "add").addArgument(conditionCall.getArgument(0).clone())
                    : null;
            case "containsKey" -> buildMapContainsKeyPrecondition(scope, currentValue, conditionCall);
            case "containsValue" -> buildMapContainsValuePrecondition(scope, currentValue, conditionCall);
            default -> null;
        };
    }

    private MethodCallExpr buildMapContainsKeyPrecondition(Expression scope, Object currentValue,
                                                           MethodCallExpr conditionCall) {
        if (!(currentValue instanceof Map<?, ?>)) {
            return null;
        }
        Expression valueExpr = createDefaultExpressionForType(getCollectionTypeArgument(scope, 1));
        return new MethodCallExpr(scope.clone(), "put")
                .addArgument(conditionCall.getArgument(0).clone())
                .addArgument(valueExpr);
    }

    private MethodCallExpr buildMapContainsValuePrecondition(Expression scope, Object currentValue,
                                                             MethodCallExpr conditionCall) {
        if (!(currentValue instanceof Map<?, ?>)) {
            return null;
        }
        Expression keyExpr = createDefaultExpressionForType(getCollectionTypeArgument(scope, 0));
        return new MethodCallExpr(scope.clone(), "put")
                .addArgument(keyExpr)
                .addArgument(conditionCall.getArgument(0).clone());
    }

    private boolean requiresCollectionReset(Symbol value, String methodName) {
        Object currentValue = value.getValue();
        return switch (methodName) {
            case "contains", TruthTable.IS_EMPTY -> currentValue instanceof Collection<?> collection && !collection.isEmpty();
            case "containsKey", "containsValue" -> currentValue instanceof Map<?, ?> map && !map.isEmpty();
            default -> false;
        };
    }

    private Type getCollectionTypeArgument(Expression scope, int index) {
        if (!scope.isNameExpr() || currentConditional == null) {
            return new ClassOrInterfaceType().setName("Object");
        }
        return currentConditional.getMethodDeclaration()
                .getParameterByName(scope.asNameExpr().getNameAsString())
                .flatMap(param -> param.getType().isClassOrInterfaceType()
                        ? param.getType().asClassOrInterfaceType().getTypeArguments()
                        : Optional.empty())
                .filter(typeArgs -> index < typeArgs.size())
                .map(typeArgs -> typeArgs.get(index))
                .orElse(new ClassOrInterfaceType().setName("Object"));
    }

    private Expression createDefaultExpressionForType(Type type) {
        String qualifiedName = AbstractCompiler.findFullyQualifiedName(cu, type);
        Variable variable = Reflect.variableFactory(qualifiedName != null ? qualifiedName : type.asString());
        if (variable != null && !variable.getInitializer().isEmpty()) {
            return variable.getInitializer().getFirst().clone();
        }
        Object defaultValue = Reflect.getDefault(type.asString());
        if (defaultValue != null) {
            return Reflect.createLiteralExpression(defaultValue);
        }
        return new NullLiteralExpr();
    }

    private Expression buildEmptyCollectionInitializer(Symbol value) {
        Object currentValue = value.getValue();
        if (currentValue instanceof Map<?, ?>) {
            return StaticJavaParser.parseExpression("new java.util.HashMap<>()");
        }
        if (currentValue instanceof Set<?>) {
            return StaticJavaParser.parseExpression("new java.util.HashSet<>()");
        }
        if (currentValue instanceof Collection<?>) {
            return StaticJavaParser.parseExpression("new java.util.ArrayList<>()");
        }
        return null;
    }

    private void setupConditionalVariablesWithSetter(Statement stmt, Map.Entry<Expression, Object> entry, Expression scope) {
        MethodCallExpr setter = new MethodCallExpr();
        String name = entry.getKey().asMethodCallExpr().getNameAsString();
        setter.setName(AbstractCompiler.setterNameFromGetterName(name));
        if ( scope instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
            setter.setScope(mce.getScope().orElseThrow().clone());
        }
        else {
            setter.setScope(scope.clone());
        }

        if (entry.getValue() == null) {
            setter.addArgument(new NullLiteralExpr());
        } else {
            if (entry.getValue().equals("T")) {
                setupConditionalNotNullValue(stmt, entry, name, setter);
            } else {
                createSetterFromGetter(entry, setter);
            }
            if (setter.getArguments().isEmpty()) {
                Object setterDomainValue = normalizeSetterDomainValue(entry);
                Expression setterArg = resolveSetterArgument(stmt, scope, setter.getNameAsString(), setterDomainValue);
                setter.addArgument(setterArg != null ? setterArg : new NullLiteralExpr());
            }
        }
        addPreCondition(stmt, setter);
    }

    private Object normalizeSetterDomainValue(Map.Entry<Expression, Object> entry) {
        if (!(entry.getValue() instanceof Boolean desiredState)) {
            return entry.getValue();
        }
        Optional<Node> parent = entry.getKey().getParentNode();
        if (parent.isPresent() && parent.get() instanceof BinaryExpr binaryExpr) {
            Expression otherSide = binaryExpr.getLeft().equals(entry.getKey()) ? binaryExpr.getRight() : binaryExpr.getLeft();
            if (otherSide.isNullLiteralExpr()) {
                boolean needsNonNull = switch (binaryExpr.getOperator()) {
                    case NOT_EQUALS -> desiredState;
                    case EQUALS -> !desiredState;
                    default -> false;
                };
                return needsNonNull ? "T" : null;
            }
        }
        return entry.getValue();
    }

    /**
     * Resolves a valid Java expression to use as the argument for a setter call.
     * Looks up the declared parameter type from the TypeDeclaration, then tries to adapt
     * the domain value to that type (so e.g. Integer(1) for a Long setter becomes 1L).
     * Falls back to Reflect.variableFactory when the domain value is incompatible
     * (e.g. a List from TruthTable.isEmptyMethodCall passed to a String setter).
     */
    /**
     * Resolves the appropriate expression to use as an argument for a setter method call.
     * <p>
     * This method analyzes the setter's parameter type and creates an expression that matches
     * that type, adapting the provided domain value as needed.
     *
     * @param stmt the statement context for symbol lookup
     * @param scope the expression representing the object on which the setter is called
     * @param setterName the name of the setter method
     * @param domainValue the domain value to be passed to the setter
     * @return an Expression representing the adapted argument value
     */
    private Expression resolveSetterArgument(Statement stmt, Expression scope, String setterName, Object domainValue) {
        String scopeName = extractScopeName(scope);
        Symbol sym = getValue(stmt, scopeName);
        
        if (sym == null || !(sym.getValue() instanceof Evaluator ev)) {
            return domainValueToExpression(domainValue, null);
        }

        Optional<TypeDeclaration<?>> typeOpt = AntikytheraRunTime.getTypeDeclaration(ev.getClassName());
        if (typeOpt.isEmpty()) {
            return domainValueToExpression(domainValue, null);
        }

        return resolveArgumentFromTypeDeclaration(typeOpt.get(), setterName, domainValue);
    }

    /**
     * Extracts the scope name from a scope expression.
     * <p>
     * If the scope is a method call expression with a scope, extracts that scope's string
     * representation. Otherwise, uses the scope expression's toString().
     *
     * @param scope the scope expression
     * @return the scope name as a string
     */
    private String extractScopeName(Expression scope) {
        if (scope instanceof MethodCallExpr mce && mce.getScope().isPresent()) {
            return mce.getScope().orElseThrow().toString();
        }
        return scope.toString();
    }

    /**
     * Resolves the setter argument by examining the type declaration's setter methods.
     * <p>
     * Looks for the setter method by name, extracts its parameter type, and adapts the
     * domain value to match that type. Falls back to runtime type adaptation if the
     * setter is not found (e.g., Lombok-generated setters).
     *
     * @param typeDecl the type declaration containing the setter method
     * @param setterName the name of the setter method
     * @param domainValue the domain value to adapt
     * @return an Expression representing the adapted argument
     */
    private Expression resolveArgumentFromTypeDeclaration(TypeDeclaration<?> typeDecl, String setterName, Object domainValue) {
        for (MethodDeclaration md : typeDecl.getMethodsByName(setterName)) {
            if (md.getParameters().size() == 1) {
                Type paramType = md.getParameter(0).getType();
                Expression adapted = adaptDomainValueToParameterType(paramType, domainValue);
                if (adapted != null) {
                    return adapted;
                }
                break;
            }
        }
        String propertyName = AbstractCompiler.classToInstanceName(setterName.substring(3));
        for (var field : typeDecl.getFields()) {
            if (field.getVariable(0).getNameAsString().equals(propertyName)) {
                Expression adapted = adaptDomainValueToParameterType(field.getVariable(0).getType(), domainValue);
                if (adapted != null) {
                    return adapted;
                }
            }
        }
        // Setter not found in TypeDeclaration (e.g. Lombok-generated) — adapt domain value using runtime type
        return domainValueToExpression(domainValue, null);
    }

    /**
     * Adapts a domain value to match a specific parameter type.
     * <p>
     * First attempts direct type-based conversion. If that fails, tries to create a
     * Variable using reflection and extract its initializer expression.
     *
     * @param paramType the target parameter type
     * @param domainValue the domain value to adapt
     * @return an Expression representing the adapted value, or null if adaptation fails
     */
    private Expression adaptDomainValueToParameterType(Type paramType, Object domainValue) {
        Expression collectionExpr = collectionDomainValueToExpression(paramType, domainValue);
        if (collectionExpr != null) {
            return collectionExpr;
        }

        // Handle truth-table type mismatch: when the TruthTable cannot resolve the
        // variable type for an isEmpty() call it falls back to a Collection domain.
        // If the actual parameter type is String, convert based on emptiness.
        if (domainValue instanceof Collection<?> coll && paramType.isClassOrInterfaceType()
                && "String".equals(paramType.asClassOrInterfaceType().getNameAsString())) {
            return new StringLiteralExpr(coll.isEmpty() ? "" : "T");
        }

        // Try direct type conversion
        Expression adapted = domainValueToExpression(domainValue, paramType.asString());
        if (adapted != null) {
            return adapted;
        }

        // Try using reflection to create a variable and extract its initializer
        String fqn = AbstractCompiler.findFullyQualifiedName(cu, paramType.asString());
        if (fqn == null) {
            fqn = paramType.asString();
        }
        
        Variable v = Reflect.variableFactory(fqn);
        if (v != null && v.getInitializer() != null && !v.getInitializer().isEmpty()) {
            return v.getInitializer().getFirst();
        }
        
        return null;
    }

    private Expression collectionDomainValueToExpression(Type paramType, Object domainValue) {
        if (!paramType.isClassOrInterfaceType()) {
            return null;
        }
        String rawType = paramType.asClassOrInterfaceType().getNameAsString();
        NodeList<Type> typeArgs = paramType.asClassOrInterfaceType()
                .getTypeArguments()
                .orElse(new NodeList<>());

        if (domainValue instanceof List<?> list) {
            return switch (rawType) {
                case "List", "Collection", "ArrayList", "LinkedList" -> buildListExpression(typeArgs, list);
                case "Set", "HashSet", "LinkedHashSet", "TreeSet" -> buildSetExpression(typeArgs, list);
                default -> null;
            };
        }
        if (domainValue instanceof Set<?> set) {
            List<?> values = new ArrayList<>(set);
            return switch (rawType) {
                case "Set", "HashSet", "LinkedHashSet", "TreeSet" -> buildSetExpression(typeArgs, values);
                case "List", "Collection", "ArrayList", "LinkedList" -> buildListExpression(typeArgs, values);
                default -> null;
            };
        }
        if (domainValue instanceof Map<?, ?> map && (rawType.equals("Map") || rawType.equals("HashMap")
                || rawType.equals("LinkedHashMap") || rawType.equals("TreeMap"))) {
            return buildMapExpression(typeArgs, map);
        }
        return null;
    }

    private Expression buildListExpression(NodeList<Type> typeArgs, List<?> values) {
        if (values.isEmpty()) {
            return StaticJavaParser.parseExpression("new java.util.ArrayList<>()");
        }
        Expression element = buildCollectionElementExpression(typeArgs, 0, values.getFirst());
        return StaticJavaParser.parseExpression(
                "new java.util.ArrayList<>(java.util.List.of(" + element + "))");
    }

    private Expression buildSetExpression(NodeList<Type> typeArgs, List<?> values) {
        if (values.isEmpty()) {
            return StaticJavaParser.parseExpression("new java.util.HashSet<>()");
        }
        Expression element = buildCollectionElementExpression(typeArgs, 0, values.getFirst());
        return StaticJavaParser.parseExpression(
                "new java.util.HashSet<>(java.util.List.of(" + element + "))");
    }

    private Expression buildMapExpression(NodeList<Type> typeArgs, Map<?, ?> values) {
        if (values.isEmpty()) {
            return StaticJavaParser.parseExpression("new java.util.HashMap<>()");
        }
        Map.Entry<?, ?> first = values.entrySet().iterator().next();
        Expression key = buildCollectionElementExpression(typeArgs, 0, first.getKey());
        Expression value = buildCollectionElementExpression(typeArgs, 1, first.getValue());
        return StaticJavaParser.parseExpression(
                "new java.util.HashMap<>(java.util.Map.of(" + key + ", " + value + "))");
    }

    private Expression buildCollectionElementExpression(NodeList<Type> typeArgs, int index, Object value) {
        if (value != null) {
            Expression direct = domainValueToExpression(value, null);
            if (direct != null) {
                return direct;
            }
        }
        if (index < typeArgs.size()) {
            return createDefaultExpressionForType(typeArgs.get(index));
        }
        return value == null ? new NullLiteralExpr() : new StringLiteralExpr(value.toString());
    }

    /**
     * Converts a domain value to a Java AST literal expression adapted to the declared type name.
     * When declaredType is null, uses the runtime type of the value directly.
     * Returns null when the value cannot be expressed as a valid literal for the declared type.
     */
    private Expression domainValueToExpression(Object value, String declaredType) {
        if (value instanceof String s) {
            if (declaredType == null || declaredType.equals("String") || declaredType.equals("java.lang.String")) {
                return new StringLiteralExpr(s);
            }
            return null;
        }
        if (value instanceof Boolean b) {
            if (declaredType == null || declaredType.equals("boolean") || declaredType.equals("Boolean") || declaredType.equals("java.lang.Boolean")) {
                return StaticJavaParser.parseExpression(b.toString());
            }
            return null;
        }
        if (value instanceof Number n) {
            String t = declaredType != null ? declaredType : (value instanceof Long ? "long" : value instanceof Double ? "double" : value instanceof Float ? "float" : "int");
            return switch (t) {
                case "int", "Integer", "java.lang.Integer" -> new IntegerLiteralExpr(String.valueOf(n.intValue()));
                case "long", "Long", "java.lang.Long" -> new LongLiteralExpr(n.longValue() + "L");
                case "double", "Double", "java.lang.Double" -> StaticJavaParser.parseExpression(String.valueOf(n.doubleValue()));
                case "float", "Float", "java.lang.Float" -> StaticJavaParser.parseExpression(n.floatValue() + "f");
                default -> null;
            };
        }
        return null;
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

    void createSetterFromGetterForBinaryExpr(MethodCallExpr setter, BinaryExpr binaryExpr, Expression key) {
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
            Symbol v = getValue(side, side.asNameExpr().getNameAsString());
            if (v != null) {
                setter.addArgument(v.getInitializer().getFirst());
            }
        } else if (side.isMethodCallExpr()) {
            setter.addArgument(side);
        }
    }

    @SuppressWarnings("java:S5411")
    void createSetterFromGetterForMCE(Map.Entry<Expression, Object> entry, MethodCallExpr setter, MethodCallExpr mce) {
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
            Symbol scopeVar = getValue(stmt, mce.getScope().orElseThrow().toString());
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

    /**
     * Extracts the variable name from a truth-table key expression.
     * The key may be a plain NameExpr or a method call such as
     * {@code CollectionUtils.isEmpty(varName)}.
     */
    private static String extractVariableName(Expression key) {
        if (key instanceof NameExpr nameExpr) {
            return nameExpr.getNameAsString();
        }
        if (key instanceof MethodCallExpr mce && !mce.getArguments().isEmpty()
                && mce.getArgument(0).isNameExpr()) {
            return mce.getArgument(0).asNameExpr().getNameAsString();
        }
        return null;
    }

    /**
     * Returns {@code true} when the named variable appears as an argument (not as a scope)
     * in any method-call statement that precedes the current conditional in the enclosing
     * method body.  A positive result indicates the variable may be an output parameter
     * (e.g. an error-accumulator list) that is populated before the condition is checked,
     * making a "force-empty" override unreachable at runtime.
     *
     * <p><strong>Algorithm:</strong>
     * <ol>
     *     <li>Early return {@code false} if inputs are invalid (null varName, no current conditional)</li>
     *     <li>Extract the enclosing method declaration and the conditional statement being evaluated</li>
     *     <li>Early return {@code false} if method structure is invalid (no body)</li>
     *     <li>Iterate through all statements in the method body <strong>until</strong> reaching the
     *         conditional statement (break prevents scanning statements after the condition)</li>
     *     <li>For each statement before the condition:
     *         <ul>
     *             <li>Find all method call expressions (including nested calls)</li>
     *             <li>Check each argument to each call</li>
     *             <li>Return {@code true} immediately if the variable name matches any argument</li>
     *         </ul>
     *     </li>
     *     <li>Return {@code false} if no matches found in pre-condition statements</li>
     * </ol>
     *
     * <p><strong>Example Scenario:</strong><br>
     * Consider this method:
     * <pre>{@code
     * void process(List<String> errors) {
     *     validate(errors);        // errors passed as argument here
     *     if (errors.isEmpty()) {  // conditional we're analyzing
     *         // ...
     *     }
     * }
     * }</pre>
     * When analyzing the {@code errors.isEmpty()} condition, this method returns {@code true}
     * because "errors" appears as an argument to {@code validate()} before the condition.
     * This indicates "errors" is likely an output parameter that gets populated by validate(),
     * so forcing it to be empty for branch coverage would be unrealistic.
     *
     * @param varName the variable name to search for (typically extracted from a condition)
     * @return {@code true} if the variable is passed as an argument to any method call before
     *         the current condition, {@code false} otherwise
     */
    private boolean isPassedToMethodBeforeCondition(String varName) {
        if (varName == null || currentConditional == null) return false;
        MethodDeclaration md = currentConditional.getMethodDeclaration();
        Statement conditionStmt = currentConditional.getStatement();
        if (md == null || conditionStmt == null || md.getBody().isEmpty()) return false;

        for (Statement stmt : md.getBody().orElseThrow().getStatements()) {
            if (stmt == conditionStmt) break;
            for (MethodCallExpr call : stmt.findAll(MethodCallExpr.class)) {
                for (Expression arg : call.getArguments()) {
                    if (arg.isNameExpr() && arg.asNameExpr().getNameAsString().equals(varName)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        if (BranchingTrace.isEnabled()) {
            BranchingTrace.record("ofNullable:seen|statement=" + stmt + "|existing=" + (l != null));
        }
        if (l != null) {
            if (BranchingTrace.isEnabled()) {
                BranchingTrace.record("ofNullable:skip|statement=" + stmt + "|reason=existingBranch|path=" + l.getPathTaken()
                        + "|preconditions=" + l.getPreconditions().size());
            }
            return;
        }

        Expression expr = reflectionArguments.getMethodCallExpression();
        if (expr instanceof MethodCallExpr mce) {
            Expression argument = mce.getArguments().getFirst().orElseThrow();
            if (argument.isNameExpr()) {
                l = new LineOfCode(stmt);
                CallableDeclaration<?> callable = stmt.findAncestor(CallableDeclaration.class).orElse(null);
                boolean hasActiveConditional = currentConditional != null
                        && currentConditional.getCallableDeclaration().equals(callable);
                if (callable != null && !hasActiveConditional) {
                    Branching.add(l);
                    BranchingTrace.record(() -> "ofNullable:queue|statement=" + stmt + "|mode=conditional");
                } else {
                    Branching.add(l.markPreconditionOnly());
                    BranchingTrace.record(() -> "ofNullable:queue|statement=" + stmt + "|mode=branchOnly");
                }
                BranchingTrace.record(() -> "ofNullable:register|statement=" + stmt + "|argument=" + argument);

                if (returnValue != null && returnValue.getValue() instanceof Optional<?> opt) {
                    Object value = null;
                    if (opt.isPresent()) {
                        l.setPathTaken(LineOfCode.TRUE_PATH);
                        BranchingTrace.record(() -> "ofNullable:path|statement=" + stmt + "|path=present");
                    } else {
                        l.setPathTaken(LineOfCode.FALSE_PATH);
                        if (BranchingTrace.isEnabled()) {
                            BranchingTrace.record("ofNullable:path|statement=" + stmt + "|path=empty|assigned=" + value);
                        }
                    }
                    Map.Entry<Expression, Object> entry = new AbstractMap.SimpleEntry<>(argument, value);
                    setupConditionThroughAssignment(stmt, entry);
                    if (BranchingTrace.isEnabled()) {
                        BranchingTrace.record("ofNullable:afterAssign|statement=" + stmt + "|preconditions=" + l.getPreconditions().size());
                    }
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

    void parameterAssignment(AssignExpr assignExpr, Symbol va) {
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
