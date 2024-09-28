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

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
     * Local variables.
     *
     * These are specific to a block statement. A block statement may also be an
     * entire method. The primary key will be the hashcode of the block statement.
     */
    private final Map<Integer, Map<String, Variable>> locals ;

    /**
     * The fields that were encountered in the current class.
     */
    private final Map<String, Variable> fields;
    static Map<String, Object> finches;

    private Variable returnValue;

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

    /**
     * Get the value for the given variable in the current scope.
     *
     * The variable may have been defined as a local (which could be a variable defined in the current block
     * or an argument to the function) or a field.
     *
     * @param n a node depicting the current statement. It will be used to identify the current block
     * @param name the name of the variable.
     * @return
     */
    public Variable getValue(Node n, String name) {
        Variable value = getLocal(n, name);
        if (value == null) {
            return fields.get(name);
        }
        return value;
    }

    public Variable evaluateCondition(Expression condition) throws EvaluatorException {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            return evaluateBinaryExpression(binaryExpr.getOperator(), left, right);

        } else if (condition.isBooleanLiteralExpr()) {

            return new Variable(condition.asBooleanLiteralExpr().getValue());

        } else if (condition.isNameExpr()) {
            String name = condition.asNameExpr().getNameAsString();
            return getValue(condition, name);

        } else if (condition.isUnaryExpr()) {
            UnaryExpr unaryExpr = condition.asUnaryExpr();
            Expression expr = unaryExpr.getExpression();
            if (unaryExpr.getOperator().equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT)) {
                Variable v = evaluateCondition(expr);
                v.setValue(!(Boolean)v.getValue());
                return v;
            }
            logger.warn("Unary expression not supported yet");
        }
        return null;
    }

    public Variable evaluateExpression(Expression expr) throws EvaluatorException {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return getValue(expr, name);
        } else if (expr.isLiteralExpr()) {
            if (expr.isBooleanLiteralExpr()) {
                return new Variable(expr.asBooleanLiteralExpr().getValue());
            } else if (expr.isIntegerLiteralExpr()) {
                return new Variable(Integer.parseInt(expr.asIntegerLiteralExpr().getValue()));
            } else if (expr.isStringLiteralExpr()) {
                return new Variable(expr.asStringLiteralExpr().getValue());
            }
        } else if (expr.isVariableDeclarationExpr()) {
            VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
            for (var decl : varDeclExpr.getVariables()) {
                if (decl.getInitializer().isPresent() && decl.getInitializer().get().isMethodCallExpr()) {
                    MethodCallExpr methodCall = decl.getInitializer().get().asMethodCallExpr();
                    Variable v = evaluateMethodCall(methodCall);
                    if (v != null) {
                        v.setType(decl.getType());
                        setLocal(methodCall, decl.getNameAsString(), v);
                    }
                    return v;
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

    /**
     * Find local variable
     * @param node the node representing the current expresion.
     *             It's primary purpose is to help identify the current block
     * @param name the name of the variable to look up
     * @return the Variable if it's found or null.
     */
    public Variable getLocal(Node node, String name) {
        Variable v = null;
        Node n = node;

        while(true) {
            BlockStmt block = findBlockStatement(n);
            int hash = (block != null) ? block.hashCode() : 0;

            Map<String, Variable> locals = this.locals.get(hash);
            if (locals != null) {
                v = locals.get(name);
                return v;
            }
            if(n instanceof MethodDeclaration) {
                locals = this.locals.get(hash);
                if (locals != null) {
                    v = locals.get(name);
                    return v;
                }
                break;
            }
            if(block == null) {
                break;
            }
            n = block.getParentNode().orElse(null);
            if(n == null) {
                break;
            }
        }
        return null;
    }

    /**
     * Sets a local variable
     * @param node An expression representing the code being currently executed. It will be used to identify the
     *             encapsulating block.
     * @param nameAsString the variable name
     * @param v The value to be set for the variable.
     */
    private void setLocal(Node node, String nameAsString, Variable v) {
        BlockStmt block = findBlockStatement(node);
        int hash = (block != null) ? block.hashCode() : 0;

        Map<String, Variable> locals = this.locals.get(hash);
        if(locals == null) {
            locals = new HashMap<>();
            this.locals.put(hash, locals);
        }
        locals.put(nameAsString, v);
    }

    /**
     * Recursively traverse parents to find a block statement.
     * @param expr
     * @return
     */
    private static BlockStmt findBlockStatement(Node expr) {
        Node currentNode = expr;
        while (currentNode != null) {
            if (currentNode instanceof BlockStmt) {
                return (BlockStmt) currentNode;
            }
            currentNode = currentNode.getParentNode().orElse(null);
        }
        return null; // No block statement found
    }

    /**
     * Evaluate a method call.
     *
     * Does so by executing all the code contained in that method where possible.
     *
     * @param methodCall the method call expression
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
    private Variable evaluateMethodCall(MethodCallExpr methodCall) throws EvaluatorException {
        Optional<Expression> scope = methodCall.getScope();

        if (scope.isPresent()) {
            Expression scopeExpr = scope.get();
            if (scopeExpr.isMethodCallExpr()) {
                /*
                 * Chanined method calls
                 */
                returnValue = evaluateMethodCall(scopeExpr.asMethodCallExpr());
                MethodCallExpr chained = methodCall.clone();
                chained.setScope(new NameExpr(returnValue.getValue().toString()));
                returnValue = evaluateMethodCall(chained);
            }

            String methodName = methodCall.getNameAsString();
            List<Expression> arguments = methodCall.getArguments();
            Variable[] argValues = new Variable[arguments.size()];

            for (int i = 0; i < arguments.size(); i++) {
                argValues[i] = evaluateExpression(arguments.get(i));
            }


            Class<?>[] paramTypes = new Class<?>[argValues.length];
            Object[] args = new Object[argValues.length];
            for (int i = 0; i < argValues.length; i++) {
                Class<?> wrapperClass = argValues[i].getValue().getClass();
                if (wrapperClass == Integer.class) {
                    paramTypes[i] = int.class;
                } else if (wrapperClass == Double.class) {
                    paramTypes[i] = double.class;
                } else if (wrapperClass == Boolean.class) {
                    paramTypes[i] = boolean.class;
                } else if (wrapperClass == Long.class) {
                    paramTypes[i] = long.class;
                } else if (wrapperClass == Float.class) {
                    paramTypes[i] = float.class;
                } else if (wrapperClass == Short.class) {
                    paramTypes[i] = short.class;
                } else if (wrapperClass == Byte.class) {
                    paramTypes[i] = byte.class;
                } else if (wrapperClass == Character.class) {
                    paramTypes[i] = char.class;
                } else {
                    paramTypes[i] = wrapperClass;
                }
                args[i] = argValues[i].getValue();
            }

            try {

                if (scopeExpr.isFieldAccessExpr() && scopeExpr.asFieldAccessExpr().getScope().toString().equals("System")) {
                    /*
                     * System. stuff need special treatment
                     */
                    Class<?> systemClass = Class.forName("java.lang.System");
                    Field outField = systemClass.getField("out");
                    Class<?> printStreamClass = outField.getType();
                    Method printlnMethod = printStreamClass.getMethod("println", paramTypes);
                    printlnMethod.invoke(outField.get(null), args);
                } else {
                    ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
                    ResolvedReferenceTypeDeclaration declaringType = resolvedMethod.declaringType();

                    if (declaringType.isClass() && declaringType.getPackageName().equals("java.lang")) {
                        Class<?> clazz = Class.forName(declaringType.getQualifiedName());
                        Method method = clazz.getMethod(methodName, paramTypes);
                        Variable v = new Variable(method.invoke(evaluateExpression(scopeExpr).getValue(), args));
                        AntikytheraRunTime.push(v);
                        return v;
                    } else {
                        if (scopeExpr.toString().equals(this.scope)) {
                            Optional<Node> method = resolvedMethod.toAst();
                            if (method.isPresent()) {
                                System.out.println(method);
                                executeMethod((MethodDeclaration) method.get());
                            }
                        } else {
                            Object obj = evaluateExpression(scopeExpr);
                            Method method = obj.getClass().getMethod(methodName, paramTypes);
                            return new Variable(method.invoke(obj, args));
                        }
                    }
                }

            } catch (IllegalStateException e) {
                /*
                 * I am a python program so this logic here is perfectly ok :)
                 */
                Class<?> clazz = returnValue.getValue().getClass();
                try {
                    Method method = clazz.getMethod(methodName, paramTypes);
                    Variable v = new Variable(method.invoke(returnValue.getValue(), args));
                    AntikytheraRunTime.push(v);
                    return v;
                } catch (Exception ex) {
                    throw new EvaluatorException("Error evaluating method call: " + methodCall, e);
                }
            } catch (Exception e) {
                throw new EvaluatorException("Error evaluating method call: " + methodCall, e);
            }
        }
        return null;
    }

    private Variable evaluateBinaryExpression(BinaryExpr.Operator operator, Expression leftValue, Expression rightValue) throws EvaluatorException {
        Variable left = evaluateExpression(leftValue);
        Variable right = evaluateExpression(rightValue);

        switch (operator) {
            case EQUALS: {
                if (leftValue == null && rightValue == null) {
                    return new Variable(Boolean.TRUE);
                }
                if (leftValue == null || rightValue == null) {
                    return new Variable(Boolean.FALSE);
                }

                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new Variable( ((Comparable) leftValue).equals(rightValue));
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);
            }

            case GREATER:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new Variable( ((Comparable) leftValue).compareTo(rightValue) > 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);
            case GREATER_EQUALS:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new Variable( ((Comparable) leftValue).compareTo(rightValue) >= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);

            case LESS:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new Variable( ((Comparable) leftValue).compareTo(rightValue) < 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);

            case LESS_EQUALS:
                if (left.getValue() instanceof Comparable && right.getValue() instanceof Comparable) {
                    return new Variable( ((Comparable) leftValue).compareTo(rightValue) <= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftValue + " and " + rightValue);

            case NOT_EQUALS: {
                Variable v = evaluateBinaryExpression(BinaryExpr.Operator.EQUALS, leftValue, rightValue);
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
                    return new Variable(left.getValue().toString() + right.getValue().toString());
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

            Variable v = getLocal(nodes.get(0), varName);
            if(v == null) {
                v = new Variable( (ClassOrInterfaceType) nodes.get(0));
                setLocal(nodes.get(0), varName, v);
            }
            try {
                MethodCallExpr mce = (MethodCallExpr) nodes.get(2);
                List<Node> children = mce.getChildNodes();
                Object result = null;

                for(Node child : children) {
                    if (child instanceof MethodCallExpr) {
                        MethodCallExpr nexpr = (MethodCallExpr) child;
                        Expression expression = nexpr.getScope().get();
                        Object value = getValue(expression, expression.toString());
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
                v.setValue(finches.get(className));
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

    public void setField(String nameAsString, Type t) {
        Variable f = new Variable(t);
        fields.put(nameAsString, f);
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

    public void executeMethod(MethodDeclaration md) throws EvaluatorException {
        List<Statement> statements = md.getBody().orElseThrow().getStatements();
        NodeList<Parameter> parameters = md.getParameters();
        locals.clear();
        returnValue = null;
        for(int i = parameters.size() - 1 ; i >= 0 ; i--) {
            Parameter p = parameters.get(i);

            /*
             * Our implementation differs from a standard Expression Evaluation engine in that we do not
             * throw an exception if the stack is empty.
             *
             * The primary purpose of this is to generate tests. Those tests are sometimes generated for
             * very complex classes. We are not trying to achieve 100% efficiency. If we can get close and
             * allow the developer to make a few manual edits that's more than enougn.
             */
            if (AntikytheraRunTime.isEmptyStack()) {
                logger.warn("Stack is empty");
            }
            else {
                setLocal(md.getBody().get(), p.getNameAsString(), AntikytheraRunTime.pop());
            }
        }

        for (Statement stmt : statements) {
            evaluateExpression(stmt.asExpressionStmt().getExpression());
        }

        if (!AntikytheraRunTime.isEmptyStack()) {
            AntikytheraRunTime.pop();

        }
    }
}
