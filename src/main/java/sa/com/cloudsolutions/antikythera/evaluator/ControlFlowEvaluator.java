package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

public class ControlFlowEvaluator extends Evaluator{
    private static final Logger logger = LoggerFactory.getLogger(ControlFlowEvaluator.class);

    protected int branchCount;
    /**
     * The preconditions that need to be met before the test can be executed.
     */
    protected Map<MethodDeclaration, LinkedHashSet<Expression>> preConditions = new HashMap<>();

    /**
     * The preconditions that we are building based on the current branches covered.
     * These will be copied to the preConditions map
     */
    protected List<Expression> preconditionsInProgress = new ArrayList<>();

    public ControlFlowEvaluator(EvaluatorFactory.Context context) {
        super(context);
    }

    @SuppressWarnings("unchecked")
    Optional<Expression> setupConditionThroughAssignment(Statement stmt, boolean state, Map.Entry<Expression, Object> entry) {
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
                        addPreCondition(stmt, state, expr);
                        return Optional.of(expr);
                    }
                }
                /*
                 * We tried to match the name of the variable with the name of the parameter but
                 * a match could not be found. So it is not possible to force branching by
                 * assigning values to a parameter in a conditional
                 */
                return Optional.empty();
            }
        }
        else {
            v = new Variable(entry.getValue());
        }

        Expression expr = setupConditionThroughAssignment(entry, v);
        addPreCondition(stmt, state, expr);
        return Optional.of(expr);
    }

    private Expression setupConditionThroughAssignment(Map.Entry<Expression, Object> entry, Variable v) {
        NameExpr nameExpr = entry.getKey().asNameExpr();
        Expression valueExpr = v.getType() instanceof PrimitiveType
                ? Reflect.createLiteralExpression(entry.getValue())
                : new StringLiteralExpr(entry.getValue().toString());

        return new AssignExpr(
                new NameExpr(nameExpr.getNameAsString()),
                valueExpr,
                AssignExpr.Operator.ASSIGN
        );
    }

    void setupConditionThroughMethodCalls(Statement stmt, boolean state, Map.Entry<Expression, Object> entry) {
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
        LineOfCode l = Branching.get(statement.hashCode());
        l.addPrecondition(new Precondition(expr), state);
        preconditionsInProgress.add(expr);
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
                        setupConditionThroughAssignment(stmt, state, entry).ifPresent(expressions::add);
                    } else {
                        setupConditionThroughMethodCalls(stmt, state, entry);
                    }
                }
            }
        }

        return expressions;
    }

    @Override
    Variable handleOptionalEmpties(ScopeChain chain) throws ReflectiveOperationException {
        Variable v = evaluateOptionalEmptyCall(chain);
        return (v == null) ?  super.handleOptionalEmpties(chain) : v;
    }

    private Variable evaluateOptionalEmptyCall(ScopeChain chain) throws ReflectiveOperationException {
        MethodCallExpr methodCall = chain.getExpression().asMethodCallExpr();
        return switch (methodCall.getNameAsString()) {
            case "orElse", "orElseGet" -> evaluateExpression(methodCall.getArgument(0));
            case "orElseThrow" -> throw new NoSuchElementException("Optional is empty");
            case "ifPresent" -> null;
            case "ifPresentOrElse" -> {
                Variable v = evaluateExpression(methodCall.getArgument(1));
                yield executeLambda(v);
            }
            default -> super.handleOptionalEmpties(chain);
        };
    }

    @SuppressWarnings("unchecked")
    Variable handleOptionalsHelper(ScopeChain.Scope sc) throws ReflectiveOperationException {
        MethodCallExpr methodCall = sc.getScopedMethodCall();
        Statement stmt = methodCall.findAncestor(Statement.class).orElseThrow();
        LineOfCode l = Branching.get(stmt.hashCode());

        if (l == null) {
            return straightPath(sc, stmt, methodCall);
        }
        else {
            return riggedPath(sc, l);
        }
    }

    Variable riggedPath(ScopeChain.Scope sc, LineOfCode l)  throws ReflectiveOperationException  {
        throw new IllegalStateException("rigged path Should be overridden");
    }

    Variable straightPath(ScopeChain.Scope sc, Statement stmt, MethodCallExpr methodCall) throws ReflectiveOperationException {
        throw new IllegalStateException("straight path Should be overridden");
    }
}
