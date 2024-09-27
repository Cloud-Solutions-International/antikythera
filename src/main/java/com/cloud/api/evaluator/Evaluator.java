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

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
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
    private final Map<String, Variable> locals ;

    private final Map<String, Variable> fields;
    Map<Type, Variable> arguments;
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
        arguments = new HashMap<>();
    }

    public Object getValue(String name) {
        if (locals != null) {
            Variable local = locals.get(name);
            if(local != null) {
                return local.value;
            }
        }

        Variable value = arguments.get(name);
        if(value != null) {
            return value;
        }

        value = fields.get(name);
        if(value != null) return value.value;
        return value;
    }

    public boolean evaluateCondition(Expression condition) throws EvaluatorException {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            if (binaryExpr.getOperator().equals(BinaryExpr.Operator.AND)) {
                return evaluateCondition(left) && evaluateCondition(right);
            } else if (binaryExpr.getOperator().equals(BinaryExpr.Operator.OR)) {
                return evaluateCondition(left) || evaluateCondition(right);
            } else {
                Object leftValue = evaluateExpression(left);
                Object rightValue = evaluateExpression(right);
                if (leftValue instanceof Comparable && rightValue instanceof Comparable) {
                    return evaluateBinaryExpression(binaryExpr.getOperator(),
                            (Comparable) leftValue, (Comparable) rightValue);
                } else {
                    logger.warn("{} , {} not comparable", leftValue, rightValue);
                }
            }
        } else if (condition.isBooleanLiteralExpr()) {
            return condition.asBooleanLiteralExpr().getValue();
        } else if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            Boolean value = (Boolean) getValue(name);
            return value != null ? value : false;
        } else if (condition.isUnaryExpr()) {
            UnaryExpr unaryExpr = condition.asUnaryExpr();
            Expression expr = unaryExpr.getExpression();
            if (unaryExpr.getOperator().equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT)) {
                return !evaluateCondition(expr);
            }
            logger.warn("Unary expression not supported yet");
        }
        return false;
    }

    public Object evaluateExpression(Expression expr) throws EvaluatorException {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return getValue(name);
        } else if (expr.isLiteralExpr()) {
            if (expr.isBooleanLiteralExpr()) {
                return expr.asBooleanLiteralExpr().getValue();
            } else if (expr.isIntegerLiteralExpr()) {
                return Integer.parseInt(expr.asIntegerLiteralExpr().getValue());
            } else if (expr.isStringLiteralExpr()) {
                return expr.asStringLiteralExpr().getValue();
            }
        } else if (expr.isVariableDeclarationExpr()) {
            VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
            for (var decl : varDeclExpr.getVariables()) {
                if (decl.getInitializer().isPresent() && decl.getInitializer().get().isMethodCallExpr()) {
                    MethodCallExpr methodCall = decl.getInitializer().get().asMethodCallExpr();
                    return evaluateMethodCall(methodCall);
                }
            }
        } else if (expr.isMethodCallExpr()) {
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            return evaluateMethodCall(methodCall);
        }
        return null;
    }

    private Object evaluateMethodCall(MethodCallExpr methodCall) throws EvaluatorException {
        String methodName = methodCall.getNameAsString();
        List<Expression> arguments = methodCall.getArguments();
        Object[] argValues = new Object[arguments.size()];

        for (int i = 0; i < arguments.size(); i++) {
            argValues[i] = evaluateExpression(arguments.get(i));
        }

        try {
            if (methodCall.getScope().isPresent()) {
                Expression scopeExpr = methodCall.getScope().get();
                ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
                ResolvedReferenceTypeDeclaration declaringType = resolvedMethod.declaringType();

                if (declaringType.isClass() && declaringType.getPackageName().equals("java.lang")) {
                    // Handle static method calls from java.lang package
                    Class<?> clazz = Class.forName(declaringType.getQualifiedName());
                    Method method = clazz.getMethod(methodName, getParameterTypes(argValues));
                    return method.invoke(null, argValues);
                } else {
                    // Handle method calls on objects
                    Object scope = evaluateExpression(scopeExpr);
                    Method method = scope.getClass().getMethod(methodName, getParameterTypes(argValues));
                    return method.invoke(scope, argValues);
                }
            }
        } catch (Exception e) {
            throw new EvaluatorException("Error evaluating method call: " + methodCall, e);
        }
        return null;
    }

    private Class<?>[] getParameterTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
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

    public Object createVariable(List<Node> nodes) {
        if(nodes.get(0) instanceof ClassOrInterfaceType) {

            String varName = ((SimpleName) nodes.get(1)).toString();

            Variable v = locals.get(varName);
            if(v == null) {
                v = new Variable( (ClassOrInterfaceType) nodes.get(0));
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
                Variable v = new Variable(t);
                v.value = finches.get(className);
                fields.put(variable.getNameAsString(), v);
            }
            else if (className.startsWith("java")) {
                Variable v = new Variable(t);
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

    public Variable getLocal(String s) {
        return locals.get(s);
    }

    public void setArgument(Type type, Object o) {
        Variable old = arguments.get(type);
        if(old != null) {
            old.setValue(o);
        }
        else {
            Variable v = new Variable(type);
            v.setValue(o);
            arguments.put(type, v);
        }
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

        public void setValue(Object value) {
            this.value = value;
        }

        public void setType(Type type) {
            this.type = type;
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

    public void setScope(String scope) {
        this.scope = scope;
    }
}
