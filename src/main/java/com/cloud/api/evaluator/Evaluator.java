package com.cloud.api.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.cloud.api.finch.Finch;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.ClassProcessor;
import com.cloud.api.generator.EvaluatorException;

import com.cloud.api.generator.RepositoryParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * Expression evaluator engine.
 */
public class Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);
    /**
     * Some complexity needs to be mocked.
     * A json file called mocks.json should be maintained that contains the mock data.
     */
    private static JsonNode mocks;
    /**
     * Local variables within the block statement.
     */
    private final Map<String, Variable> locals ;

    private final Map<String, Variable> fields;
    Map<String, Object> arguments;
    static Map<String, Object> finches;


    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static Map<String, RepositoryParser> respositories = new HashMap<>();


    static {
        try {

            List<String> scouts = (List<String>) Settings.getProperty("finch");
            if(scouts != null) {
                Evaluator.finches = new HashMap<>();

                for(String scout : scouts) {
                    Map<String, Object> finches = Finch.loadClasses(new File(scout));
                    Evaluator.finches.putAll(finches);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            logger.warn("mocks could not be loaded");
        }
    }

    public Evaluator (){
        locals = new HashMap<>();
        fields = new HashMap<>();
        arguments = new HashMap<>();
    }

    public Object getValue(String name) {
        if (locals != null) {
            Variable local = locals.get(name);
            if(local != null) {
                return local.value;
            }
        }

        Object value = arguments.get(name);
        if(value != null) {
            return value;
        }

        value = fields.get(name);
        return value;
    }

    public static Map<String, Comparable> contextFactory(CompilationUnit cu) {
        Map<String, Comparable> context = new HashMap<>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(VariableDeclarationExpr n, Void arg) {
                n.getVariables().forEach(v -> {
                    if (v.getInitializer().isPresent()) {
                        Expression initializer = v.getInitializer().get();
                        if (initializer.isBooleanLiteralExpr()) {
                            context.put(v.getNameAsString(), initializer.asBooleanLiteralExpr().getValue());
                        } else if (initializer.isIntegerLiteralExpr()) {
                            context.put(v.getNameAsString(), Integer.parseInt(initializer.asIntegerLiteralExpr().getValue()));
                        }

                    }
                });
                super.visit(n, arg);
            }
        }, null);
        return context;
    }

    public boolean evaluateCondition(Expression condition) throws EvaluatorException {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            if(binaryExpr.getOperator().equals(BinaryExpr.Operator.AND)) {
                return evaluateCondition(left) && evaluateCondition(right);
            } else if(binaryExpr.getOperator().equals(BinaryExpr.Operator.OR)) {
                return evaluateCondition(left) || evaluateCondition(right);
            }
            else {
                Object leftValue = evaluateExpression(left);
                Object rightValue = evaluateExpression(right);
                if (leftValue instanceof Comparable && rightValue instanceof Comparable) {
                    return evaluateBinaryExpression(binaryExpr.getOperator(),
                            (Comparable) leftValue, (Comparable) rightValue);
                }
                else {
                    logger.warn("{} , {} not comparable", leftValue, rightValue);
                }
            }
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            Boolean value = (Boolean) getValue(name);
            return value != null ? value : false;
        }
        else if(condition.isUnaryExpr()) {
            UnaryExpr unaryExpr = condition.asUnaryExpr();
            Expression expr = unaryExpr.getExpression();
            if(expr.isNameExpr() && locals.containsKey(expr.asNameExpr().getNameAsString())) {
                return false;
            }
            logger.warn("Unary expression not supported yet");
        }

        return false;
    }

    private Object evaluateExpression(Expression expr) throws EvaluatorException {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return getValue(name);
        } else if (expr.isLiteralExpr()) {
            if (expr.isBooleanLiteralExpr()) {
                return expr.asBooleanLiteralExpr().getValue();
            } else if (expr.isIntegerLiteralExpr()) {
                return Integer.parseInt(expr.asIntegerLiteralExpr().getValue());
            }

        }
        else if(expr.isMethodCallExpr()) {
            MethodCallExpr mc = expr.asMethodCallExpr();
            // todo fix this hack
            String parts = mc.getScope().get().toString().split("\\.")[0];
            if(locals.containsKey(parts)) {
                throw new EvaluatorException("Method call involving variables not supported yet");
            }
            if(mc.getNameAsString().startsWith("get")) {
                return getValue(mc.getNameAsString().substring(3).toLowerCase());
            }
        }

        return null;
    }

    private boolean evaluateBinaryExpression(BinaryExpr.Operator operator, Comparable leftValue, Comparable rightValue) throws EvaluatorException {
        switch (operator) {
            case EQUALS:
                if(leftValue == null && rightValue == null) return true;
                return leftValue.equals(rightValue);
            case NOT_EQUALS:
                if(leftValue == null) {
                    if (rightValue != null) {
                        return false;
                    }
                    return false;
                }

                return !leftValue.equals(rightValue);
            case LESS:
                return (int) leftValue < (int) rightValue;
            case GREATER:
                return (int) leftValue > (int) rightValue;
            case LESS_EQUALS:
                if(leftValue == null) {
                    throw new EvaluatorException("Left value is null - probably because evaluator is not completed yet");
                }
                return (int) leftValue <= (int) rightValue;
            case GREATER_EQUALS:
                return (int) leftValue >= (int) rightValue;

            default:
                return false;
        }
    }

    public void clearLocalVariables() {
        locals.clear();
    }

    /**
     * Identify local variables with in the block statement
     * @param stmt the method body block. Any variable declared here will be a local.
     * @return the list of variables declared in the block statement. If we have mocked
     *       the variable, we will return null.
     */
    public NodeList<VariableDeclarator> identifyLocals(Statement stmt) {
        if (stmt.isExpressionStmt()) {
            Expression expr = stmt.asExpressionStmt().getExpression();
            if (expr.isVariableDeclarationExpr()) {
                VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
                NodeList<VariableDeclarator> variables = varDeclExpr.getVariables();
                boolean solved = false;
                for(var variable : variables) {
                    String t = variable.getType().toString();
                    Variable local = new Variable(varDeclExpr.getElementType());
                    locals.put(variable.getNameAsString(), local);
                }
                if (!solved) {
                    return variables;
                }
            }
        }
        return null;
    }

    public void identifyFieldVariables(VariableDeclarator variable) throws IOException {
        if (variable.getType().isClassOrInterfaceType()) {
            String shortName = variable.getType().asClassOrInterfaceType().getNameAsString();
            if (Evaluator.getRespositories().containsKey(shortName)) {
                return;
            }
            Type t = variable.getType().asClassOrInterfaceType();
            String className = t.resolve().describe();

            if (className.startsWith(Settings.getProperty(Constants.BASE_PACKAGE).toString())) {
                /*
                 * At the moment only compatible with repositories that are direct part of the
                 * application under test. Repositories from external jar files are not supported.
                 */
                if(finches.get(className) != null) {
                    Variable v = new Variable(t);
                    v.value = finches.get(className);
                    fields.put(variable.getNameAsString(), v);
                }
                else {
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
                                        respositories.put(variable.getNameAsString(), parser);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public Variable getLocal(String s) {
        return locals.get(s);
    }

    public void setArgument(String nameAsString, Object o) {
        arguments.put(nameAsString, o);
    }

    public void setField(String nameAsString, Type t) {
        Variable f = new Variable(t);
        fields.put(nameAsString, f);
    }

    public static class Variable {
        private boolean isNull;
        private Type type;
        private Object value;
        private boolean isMocked;

        public Variable(Type type) {
            this.type = type;
            isNull = true;
        }

        public Type getType() {
            return type;
        }

        public Object getValue() {
            return value;
        }
    }

    public Map<String, Variable> getFields() {
        return fields;
    }

    public Map<String, Object> getFinches() {
        return finches;
    }

    public static Map<String, RepositoryParser> getRespositories() {
        return respositories;
    }
}
