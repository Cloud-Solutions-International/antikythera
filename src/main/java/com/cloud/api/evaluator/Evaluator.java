package com.cloud.api.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.finch.Finch;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.ClassProcessor;
import com.cloud.api.generator.EvaluatorException;

import com.cloud.api.generator.RepositoryParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;

import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.util.Optional;

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
    private final Map<String, AntikytheraRunTime.Variable> locals ;

    private final Map<String, AntikytheraRunTime.Variable> fields;
    static Map<String, Object> finches;


    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static Map<String, RepositoryParser> respositories = new HashMap<>();

    private String scope;

    static {
        try {
            Evaluator.finches = new HashMap<>();
            List<String> scouts = (List<String>) Settings.getProperty("finch");
            if(scouts != null) {
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

    }

    public AntikytheraRunTime.Variable getValue(String name) {
        if (locals != null) {
            AntikytheraRunTime.Variable local = locals.get(name);
            if(local != null) {
                return local;
            }
        }

        AntikytheraRunTime.Variable value = fields.get(name);
        return value;
    }

    public AntikytheraRunTime.Variable evaluateCondition(Expression condition) throws EvaluatorException {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            return evaluateBinaryExpression(binaryExpr.getOperator(), left, right);

        } else if (condition.isBooleanLiteralExpr()) {

            return new AntikytheraRunTime.Variable(condition.asBooleanLiteralExpr().getValue());

        } else if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            return getValue(name);

        } else if (condition.isUnaryExpr()) {
            UnaryExpr unaryExpr = condition.asUnaryExpr();
            Expression expr = unaryExpr.getExpression();
            if (unaryExpr.getOperator().equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT)) {
                AntikytheraRunTime.Variable v = evaluateCondition(expr);
                v.setValue(!(Boolean)v.getValue());
                return v;
            }
            logger.warn("Unary expression not supported yet");
        }
        return null;
    }

    public AntikytheraRunTime.Variable evaluateExpression(Expression expr) throws EvaluatorException {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return getValue(name);
        } else if (expr.isLiteralExpr()) {
            if (expr.isBooleanLiteralExpr()) {
                return new AntikytheraRunTime.Variable(expr.asBooleanLiteralExpr().getValue());
            } else if (expr.isIntegerLiteralExpr()) {
                return new AntikytheraRunTime.Variable(Integer.parseInt(expr.asIntegerLiteralExpr().getValue()));
            } else if (expr.isStringLiteralExpr()) {
                return new AntikytheraRunTime.Variable(expr.asStringLiteralExpr().getValue());
            }
        } else if (expr.isVariableDeclarationExpr()) {
            VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
            for (var decl : varDeclExpr.getVariables()) {
                if (decl.getInitializer().isPresent() && decl.getInitializer().get().isMethodCallExpr()) {
                    MethodCallExpr methodCall = decl.getInitializer().get().asMethodCallExpr();
                    return evaluateMethodCall(methodCall);
                }
            }
        } else if (expr.isBinaryExpr()) {
            BinaryExpr binaryExpr = expr.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            return evaluateBinaryExpression(binaryExpr.getOperator(), left, right);

        } else if (expr.isMethodCallExpr()) {
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            return evaluateMethodCall(methodCall);
        }
        return null;
    }

    private AntikytheraRunTime.Variable evaluateMethodCall(MethodCallExpr methodCall) throws EvaluatorException {
        String methodName = methodCall.getNameAsString();
        List<Expression> arguments = methodCall.getArguments();
        AntikytheraRunTime.Variable[] argValues = new AntikytheraRunTime.Variable[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            argValues[i] = evaluateExpression(arguments.get(i));
        }

        try {
            if (methodCall.getScope().isPresent()) {
                Expression scopeExpr = methodCall.getScope().get();

                Class<?>[] paramTypes = new Class<?>[argValues.length];
                Object[] args = new Object[argValues.length];
                for (int i = 0; i < argValues.length; i++) {
                    paramTypes[i] = argValues[i].getValue().getClass();
                    args[i] = argValues[i].getValue();
                }

                if (scopeExpr.isFieldAccessExpr() && scopeExpr.asFieldAccessExpr().getScope().toString().equals("System")) {
                    /*
                     * System. stuff need special treatment
                     */

                    Class<?> systemClass = Class.forName("java.lang.System");
                    Field outField = systemClass.getField("out");
                    Class<?> printStreamClass = outField.getType();
                    Method printlnMethod = printStreamClass.getMethod("println", paramTypes);
                    printlnMethod.invoke(outField.get(null), args);
                }
                else {
                    ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
                    ResolvedReferenceTypeDeclaration declaringType = resolvedMethod.declaringType();

                    if (declaringType.isClass() && declaringType.getPackageName().equals("java.lang")) {
                        Class<?> clazz = Class.forName(declaringType.getQualifiedName());
                        Method method = clazz.getMethod(methodName, paramTypes);
                        return new AntikytheraRunTime.Variable(method.invoke(null, args));
                    } else {
                        if (scopeExpr.toString().equals(scope)) {
                            Optional<Node> method = resolvedMethod.toAst();
                            if (method.isPresent()) {
                                System.out.println(method);
                                executeMethod((MethodDeclaration) method.get());
                            }
                        } else {
                            Object scope = evaluateExpression(scopeExpr);
                            Method method = scope.getClass().getMethod(methodName, paramTypes);
                            return new AntikytheraRunTime.Variable(method.invoke(scope, args));
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new EvaluatorException("Error evaluating method call: " + methodCall, e);
        }
        return null;
    }

    private AntikytheraRunTime.Variable evaluateBinaryExpression(BinaryExpr.Operator operator, Expression leftValue, Expression rightValue) throws EvaluatorException {
        AntikytheraRunTime.Variable left = evaluateExpression(leftValue);
        AntikytheraRunTime.Variable right = evaluateExpression(leftValue);

        switch (operator) {
            case EQUALS: {
                if (leftValue == null && rightValue == null) {
                    return new AntikytheraRunTime.Variable(Boolean.TRUE);
                }
                if (leftValue == null || rightValue == null) {
                    return new AntikytheraRunTime.Variable(Boolean.FALSE);
                }

                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new AntikytheraRunTime.Variable( ((Comparable) leftValue).equals(rightValue));
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);
            }

            case GREATER:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new AntikytheraRunTime.Variable( ((Comparable) leftValue).compareTo(rightValue) > 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);
            case GREATER_EQUALS:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new AntikytheraRunTime.Variable( ((Comparable) leftValue).compareTo(rightValue) >= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);

            case LESS:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new AntikytheraRunTime.Variable( ((Comparable) leftValue).compareTo(rightValue) < 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);

            case LESS_EQUALS:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new AntikytheraRunTime.Variable( ((Comparable) leftValue).compareTo(rightValue) <= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);

            case NOT_EQUALS: {
                AntikytheraRunTime.Variable v = evaluateBinaryExpression(BinaryExpr.Operator.EQUALS, leftValue, rightValue);
                if ( (Boolean)v.getValue()) {
                    v.setValue(Boolean.FALSE);
                }
                else {
                    v.setValue(Boolean.TRUE);
                }
                return v;
            }

            case PLUS:
                if (left.getValue() instanceof String || right.getValue() instanceof String) {
                    return new AntikytheraRunTime.Variable(left.getValue().toString() + right.getValue().toString());
                }
                return null;

            default:
                return null;
        }
    }

    public void clearLocalVariables() {
        locals.clear();
    }

    public Object createVariable(List<Node> nodes) {
        if(nodes.get(0) instanceof ClassOrInterfaceType) {

            String varName = ((SimpleName) nodes.get(1)).toString();

            AntikytheraRunTime.Variable v = locals.get(varName);
            if(v == null) {
                v = new AntikytheraRunTime.Variable( (ClassOrInterfaceType) nodes.get(0));
                locals.put(varName, v);
            }
            try {
                MethodCallExpr mce = (MethodCallExpr) nodes.get(2);
                List<Node> children = mce.getChildNodes();
                Object result = null;

                for(Node child : children) {
                    if (child instanceof MethodCallExpr) {
                        MethodCallExpr nexpr = (MethodCallExpr) child;
                        Object value = getValue(nexpr.getScope().get().toString());
                        System.out.println(nexpr);

                        Class<?> clazz = value.getClass();
                        Method method = clazz.getMethod(nexpr.getNameAsString());
                        result = method.invoke(value);
                        System.out.println(result);
                    }
                }
                return null;
            } catch (Exception e) {
                e.printStackTrace();
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


            if(finches.get(className) != null) {
                AntikytheraRunTime.Variable v = new AntikytheraRunTime.Variable(t);
                v.setValue(finches.get(className));
                fields.put(variable.getNameAsString(), v);
            }
            else if (className.startsWith("java")) {
                AntikytheraRunTime.Variable v = new AntikytheraRunTime.Variable(t);
                Optional<Expression> init = variable.getInitializer();
                if(init.isPresent()) {
                    v.setValue(init.get());
                }
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

    public AntikytheraRunTime.Variable getLocal(String s) {
        return locals.get(s);
    }

    public void setField(String nameAsString, Type t) {
        AntikytheraRunTime.Variable f = new AntikytheraRunTime.Variable(t);
        fields.put(nameAsString, f);
    }

    public Map<String, AntikytheraRunTime.Variable> getFields() {
        return fields;
    }

    public Map<String, Object> getFinches() {
        return finches;
    }

    public static Map<String, RepositoryParser> getRespositories() {
        return respositories;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void executeMethod(MethodDeclaration md) throws EvaluatorException {
        List<Statement> statements = md.getBody().orElseThrow().getStatements();
        NodeList<Parameter> parameters = md.getParameters();
        for(int i = parameters.size() - 1 ; i >= 0 ; i--) {
            Parameter p = parameters.get(i);

            locals.put(p.getNameAsString(), AntikytheraRunTime.pop());
        }

        for (Statement stmt : statements) {
            evaluateExpression(stmt.asExpressionStmt().getExpression());
        }
    }
}
