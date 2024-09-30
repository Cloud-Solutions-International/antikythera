package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.generator.AbstractCompiler;
import com.github.javaparser.ast.stmt.IfStmt;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.finch.Finch;
import com.cloud.api.generator.EvaluatorException;

import com.cloud.api.generator.GeneratorException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;

import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
     * Local variables.
     *
     * These are specific to a block statement. A block statement may also be an
     * entire method. The primary key will be the hashcode of the block statement.
     */
    private final Map<Integer, Map<String, Variable>> locals ;

    /**
     * The fields that were encountered in the current class.
     */
    protected final Map<String, Variable> fields;
    static Map<String, Object> finches;

    protected Variable returnValue;

    static Map<Class<?>, Class<?>> wrapperToPrimitive = new HashMap<>();
    static {
        wrapperToPrimitive.put(Integer.class, int.class);
        wrapperToPrimitive.put(Double.class, double.class);
        wrapperToPrimitive.put(Boolean.class, boolean.class);
        wrapperToPrimitive.put(Long.class, long.class);
        wrapperToPrimitive.put(Float.class, float.class);
        wrapperToPrimitive.put(Short.class, short.class);
        wrapperToPrimitive.put(Byte.class, byte.class);
        wrapperToPrimitive.put(Character.class, char.class);
    }

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
            logger.warn("Finches could not be loaded {}", e.getMessage());
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


    /**
     * Evaluate an expression.
     *
     * This is a recursive process, evaluating a particular expression might result in this method being called
     * again and again either directly or indirectly.
     *
     * @param expr the expression to evaluat
     * @return the result as a Variable instance which can be null if the expression is supposed to return null
     *      todo: At the moment the expression can be null due to implementation shortcomings as well
     * @throws EvaluatorException
     */
    public Variable evaluateExpression(Expression expr) throws EvaluatorException {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return getValue(expr, name);
        } else if (expr.isLiteralExpr()) {
            /*
             * Literal expressions are the easiest.
             */
            if (expr.isBooleanLiteralExpr()) {
                return new Variable(expr.asBooleanLiteralExpr().getValue());
            } else if (expr.isDoubleLiteralExpr()) {
                return new Variable(Double.parseDouble(expr.asDoubleLiteralExpr().getValue()));
            } else if (expr.isIntegerLiteralExpr()) {
                return new Variable(Integer.parseInt(expr.asIntegerLiteralExpr().getValue()));
            } else if (expr.isStringLiteralExpr()) {
                return new Variable(expr.asStringLiteralExpr().getValue());
            } else if (expr.isCharLiteralExpr()) {
                return new Variable(expr.asCharLiteralExpr().getValue());
            } else if (expr.isLongLiteralExpr()) {
                return new Variable(Long.parseLong(expr.asLongLiteralExpr().getValue()));
            } else if (expr.isNullLiteralExpr()) {
                return new Variable(null);
            }
        } else if (expr.isVariableDeclarationExpr()) {
            /*
             * Variable declarations are hard and deserve their own method.
             */
            return evaluateVariableDeclaration(expr);
        } else if (expr.isBinaryExpr()) {
            /*
             * Binary expressions can also be difficul
             */
            BinaryExpr binaryExpr = expr.asBinaryExpr();
            Expression left = binaryExpr.getLeft();
            Expression right = binaryExpr.getRight();

            return evaluateBinaryExpression(binaryExpr.getOperator(), left, right);
        } else if (expr.isUnaryExpr()) {
            Expression unaryExpr = expr.asUnaryExpr().getExpression();
            if (expr.asUnaryExpr().getOperator().equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT)) {
                Variable v = evaluateExpression(unaryExpr);
                v.setValue(!(Boolean)v.getValue());
                return v;
            }
            logger.warn("Negation is the only unary operation supported at the moment");
        } else if (expr.isMethodCallExpr()) {
            /*
             * Method calls are the toughest nuts to crack. Some method calls will be from the Java api
             * or from other libraries. Or from classes that have not been compiled.
             */
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            return evaluateMethodCall(methodCall);
        } else if (expr.isAssignExpr()) {
            return evaluateAssignment(expr);
        } else if (expr.isObjectCreationExpr()) {
            return createObject(expr, null, expr);
        }
        return null;
    }

    private Variable evaluateAssignment(Expression expr) throws EvaluatorException {
        AssignExpr assignExpr = expr.asAssignExpr();
        Expression target = assignExpr.getTarget();
        Expression value = assignExpr.getValue();
        Variable v = evaluateExpression(value);

        if(target.isFieldAccessExpr()) {
            FieldAccessExpr fae = target.asFieldAccessExpr();
            String fieldName = fae.getNameAsString();
            Variable variable = getValue(expr, fae.getScope().toString());
            Object obj = variable.getValue();
            try {
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, v.getValue());
            } catch (Exception e) {
                logger.error("An error occurred", e);
            }

        }
        else if(target.isNameExpr()) {
            String name = target.asNameExpr().getNameAsString();
            setLocal(expr, name, v);
        }

        return v;
    }

    /**
     * Evaluates a variable declaration expression.
     * @param expr the expression
     * @return a Variable or null if the expression could not be evaluated or results in null
     * @throws EvaluatorException if there is an error evaluating the expression
     */
    Variable evaluateVariableDeclaration(Expression expr) throws EvaluatorException {
        VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
        for (var decl : varDeclExpr.getVariables()) {
            Optional<Expression> init = decl.getInitializer();
            if (init.isPresent()) {
                Expression expression = init.get();
                if (expression.isMethodCallExpr()) {
                    MethodCallExpr methodCall = expression.asMethodCallExpr();
                    Variable v = evaluateMethodCall(methodCall);
                    if (v != null) {
                        v.setType(decl.getType());
                        setLocal(methodCall, decl.getNameAsString(), v);
                    }
                    return v;
                }
                else if(expression.isObjectCreationExpr()) {
                    return createObject(expr, decl, expression);
                }

            }
        }
        return null;
    }

    /**
     * Create an object using reflection
     *
     * @param instructionPointer a node representing the current statement. This will in most cases be an expression.
     *                           We recursively fetch it's parent until we reach the start of the block. This is
     *                           needed because local variables are local to a block rather than a method.
     * @param decl  The variable declaration
     * @param expression The expression to be evaluated and assigned as the initial value
     * @return The object that's created will be in the value field of the Variable
     */
    Variable createObject(Node instructionPointer, VariableDeclarator decl, Expression expression) {
        ObjectCreationExpr oce = expression.asObjectCreationExpr();
        ClassOrInterfaceType type = oce.getType();
        Variable v = new Variable(type);
        try {
            Optional<ResolvedType> res = AbstractCompiler.resolveTypeSafely(type);
            if (res.isPresent()) {
                ResolvedType resolved = type.resolve();
                String className = resolved.describe();

                if (resolved.isReferenceType()) {
                    var typeDecl = resolved.asReferenceType().getTypeDeclaration();
                    if (typeDecl.isPresent() && typeDecl.get().getClassName().contains(".")) {
                        className = className.replaceFirst("\\.([^\\.]+)$", "\\$$1");
                    }
                }

                Class<?> clazz = Class.forName(className);

                Class<?> outer = clazz.getEnclosingClass();
                if (outer != null) {
                    for (Class<?> c : outer.getDeclaredClasses()) {
                        if (c.getName().equals(className)) {
                            List<Expression> arguments = oce.getArguments();
                            Class<?>[] paramTypes = new Class<?>[arguments.size() + 1];
                            Object[] args = new Object[arguments.size() + 1];

                            // todo this is wrong, this should first check for an existing instance in the current scope
                            // and then if an instance is not found build using the most suitable arguments.
                            args[0] = outer.getDeclaredConstructors()[0].newInstance();
                            paramTypes[0] = outer;

                            for (int i = 0; i < arguments.size(); i++) {
                                Variable vv = evaluateExpression(arguments.get(i));
                                Class<?> wrapperClass = vv.getValue().getClass();
                                paramTypes[i + 1] = wrapperToPrimitive.getOrDefault(wrapperClass, wrapperClass);
                                args[i + 1] = vv.getValue();
                            }

                            Constructor<?> cons = c.getDeclaredConstructor(paramTypes);
                            Object instance = cons.newInstance(args);
                            v.setValue(instance);
                        }
                    }
                } else {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    v.setValue(instance);
                }
            }
            else {
                Class<?> clazz = DTOBuddy.createDynamicDTO(type);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                v.setValue(instance);
            }
        } catch (Exception e) {
            logger.error("An error occurred", e);
        }
        if (decl != null) {
            setLocal(instructionPointer, decl.getNameAsString(), v);
        }

        return v;
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

            Map<String, Variable> localsVars = this.locals.get(hash);

            if (localsVars != null) {
                v = localsVars.get(name);
                return v;
            }
            if(n instanceof MethodDeclaration) {
                localsVars = this.locals.get(hash);
                if (localsVars != null) {
                    v = localsVars.get(name);
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
    void setLocal(Node node, String nameAsString, Variable v) {
        BlockStmt block = findBlockStatement(node);
        int hash = (block != null) ? block.hashCode() : 0;

        Map<String, Variable> localVars = this.locals.get(hash);
        if(localVars == null) {
            localVars = new HashMap<>();
            this.locals.put(hash, localVars);
        }
        localVars.put(nameAsString, v);
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
    public Variable evaluateMethodCall(MethodCallExpr methodCall) throws EvaluatorException {
        Optional<Expression> scope = methodCall.getScope();

        if (scope.isPresent()) {
            Expression scopeExpr = scope.get();
            // todo fix this hack
            if (scopeExpr.toString().equals("logger")) {
                return null;
            }
            if (scopeExpr.isMethodCallExpr()) {
                returnValue = evaluateMethodCall(scopeExpr.asMethodCallExpr());
                MethodCallExpr chained = methodCall.clone();
                chained.setScope(new NameExpr(returnValue.getValue().toString()));
                returnValue = evaluateMethodCall(chained);
            }

            String methodName = methodCall.getNameAsString();
            List<Expression> arguments = methodCall.getArguments();
            Variable[] argValues = new Variable[arguments.size()];
            Class<?>[] paramTypes = new Class<?>[arguments.size()];
            Object[] args = new Object[arguments.size()];

            for (int i = 0; i < arguments.size(); i++) {
                argValues[i] = evaluateExpression(arguments.get(i));
                Class<?> wrapperClass = argValues[i].getValue().getClass();
                paramTypes[i] = wrapperToPrimitive.getOrDefault(wrapperClass, wrapperClass);
                args[i] = argValues[i].getValue();
            }

            try {
                if (scopeExpr.isFieldAccessExpr() && scopeExpr.asFieldAccessExpr().getScope().toString().equals("System")) {
                    handleSystemOutMethodCall(paramTypes, args);
                } else {
                    return handleRegularMethodCall(methodCall, scopeExpr, methodName, paramTypes, args);
                }
            } catch (IllegalStateException e) {
                return handleIllegalStateException(methodName, paramTypes, args, e);
            } catch (Exception e) {
                throw new EvaluatorException("Error evaluating method call: " + methodCall, e);
            }
        } else {
            Optional<Node> n = methodCall.resolve().toAst();
            if (n.isPresent() && n.get() instanceof MethodDeclaration) {
                executeMethod((MethodDeclaration) n.get());
                return returnValue;
            }
        }
        return null;
    }

    private Variable handleIllegalStateException(String methodName, Class<?>[] paramTypes, Object[] args, IllegalStateException e) throws EvaluatorException {
        Class<?> clazz = returnValue.getValue().getClass();
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            Variable v = new Variable(method.invoke(returnValue.getValue(), args));
            AntikytheraRunTime.push(v);
            return v;
        } catch (Exception ex) {
            throw new EvaluatorException("Error evaluating method call: " + methodName, e);
        }
    }

    private void handleSystemOutMethodCall(Class<?>[] paramTypes, Object[] args) throws Exception {
        Class<?> systemClass = Class.forName("java.lang.System");
        Field outField = systemClass.getField("out");
        Class<?> printStreamClass = outField.getType();
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = Object.class;
        }
        Method printlnMethod = printStreamClass.getMethod("println", paramTypes);
        printlnMethod.invoke(outField.get(null), args);
    }

    Variable handleRegularMethodCall(MethodCallExpr methodCall, Expression scopeExpr, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
        ResolvedReferenceTypeDeclaration declaringType = resolvedMethod.declaringType();

        if (declaringType.isClass() && declaringType.getPackageName().equals("java.lang")) {
            Class<?> clazz = Class.forName(declaringType.getQualifiedName());
            Method method = clazz.getMethod(methodName, paramTypes);
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                Variable v = new Variable(method.invoke(null, args));
                AntikytheraRunTime.push(v);
                return v;
            } else {
                Variable v = new Variable(method.invoke(evaluateExpression(scopeExpr).getValue(), args));
                AntikytheraRunTime.push(v);
                return v;
            }
        } else {
            if (scopeExpr.toString().equals(this.scope)) {
                Optional<Node> method = resolvedMethod.toAst();
                if (method.isPresent()) {
                    executeMethod((MethodDeclaration) method.get());
                    return returnValue;
                }
            } else {
                Variable v = evaluateExpression(scopeExpr);
                if (declaringType.getQualifiedName().equals("java.util.List") || declaringType.getQualifiedName().equals("java.util.Map")) {
                    for (int i = 0; i < args.length; i++) {
                        paramTypes[i] = Object.class;
                    }
                }
                Method method = v.getValue().getClass().getMethod(methodName, paramTypes);
                return new Variable(method.invoke(v.getValue(), args));
            }
        }
        return null;
    }

    Variable evaluateBinaryExpression(BinaryExpr.Operator operator, Expression leftValue, Expression rightValue) throws EvaluatorException {
        Variable left = evaluateExpression(leftValue);

        if(operator.equals(BinaryExpr.Operator.OR) && (boolean)left.getValue()) {
             return new Variable(Boolean.TRUE);
        }

        Variable right = evaluateExpression(rightValue);

        switch (operator) {
            case EQUALS: {
                if (left == null && right == null) {
                    return new Variable(Boolean.TRUE);
                }
                if (left == null && right.getValue() == null) {
                    return new Variable(Boolean.TRUE);
                }
                if (right == null && left.getValue() == null) {
                    return new Variable(Boolean.TRUE);
                }
                if (left.getValue() == null && right.getValue() == null) {
                    return new Variable(Boolean.TRUE);
                }
                return new Variable( ((Comparable<?>) leftValue).equals(rightValue));
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
                return addOperation(left, right);

            default:
                return null;
        }
    }

    /**
     * Simple addition.
     * String can be added to anything but numbers are tricker.
     * @param left the left operand
     * @param right the right operand
     * @return the result of the add operation which may be arithmatic or string concatenation
     */
    static Variable addOperation(Variable left, Variable right) {
        if (left.getValue() instanceof String || right.getValue() instanceof String) {
            return new Variable(left.getValue().toString() + right.getValue().toString());
        }
        if (left.getValue() instanceof  Number && right.getValue() instanceof Number) {
            if (left.getValue() instanceof Double || right.getValue() instanceof Double) {
                return new Variable((Double) left.getValue() + (Double) right.getValue());
            }
            if (left.getValue() instanceof Float || right.getValue() instanceof Float) {
                return new Variable((Float) left.getValue() + (Float) right.getValue());
            }
            if (left.getValue() instanceof Long || right.getValue() instanceof Long) {
                return new Variable((Long) left.getValue() + (Long) right.getValue());
            }
            if (left.getValue() instanceof Integer || right.getValue() instanceof Integer) {
                return new Variable((Integer) left.getValue() + (Integer) right.getValue());
            }
            if (left.getValue() instanceof Short || right.getValue() instanceof Short) {
                return new Variable((Short) left.getValue() + (Short) right.getValue());
            }
            if (left.getValue() instanceof Byte || right.getValue() instanceof Byte) {
                return new Variable((Byte) left.getValue() + (Byte) right.getValue());
            }
        }
        return null;
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
                logger.error(e.getMessage());
            }
        }
        return null;
    }

    void identifyFieldVariables(VariableDeclarator variable) throws IOException, EvaluatorException {
        if (variable.getType().isClassOrInterfaceType()) {

            Type t = variable.getType().asClassOrInterfaceType();
            String className = t.resolve().describe();


            if(finches.get(className) != null) {
                Variable v = new Variable(t);
                v.setValue(finches.get(className));
                fields.put(variable.getNameAsString(), v);
            }
            else if (className.startsWith("java")) {
                Variable v = null;
                Optional<Expression> init = variable.getInitializer();
                if(init.isPresent()) {
                    v = evaluateExpression(init.get());
                    v.setType(t);
                }
                fields.put(variable.getNameAsString(), v);
            }
        }
        else {
            Variable v = null;
            Optional<Expression> init = variable.getInitializer();
            if(init.isPresent()) {
                v = evaluateExpression(init.get());
                v.setType(variable.getType());
            }
            else {
                v = new Variable(variable.getType());
            }
            v.setPrimitive(true);
            fields.put(variable.getNameAsString(), v);
        }
    }


    public Map<String, Variable> getFields() {
        return fields;
    }

    public Map<String, Object> getFinches() {
        return finches;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public void executeMethod(MethodDeclaration md) throws EvaluatorException {
        List<Statement> statements = md.getBody().orElseThrow().getStatements();
        NodeList<Parameter> parameters = md.getParameters();

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

        executeBlock(statements);

        if (!AntikytheraRunTime.isEmptyStack()) {
            AntikytheraRunTime.pop();

        }
    }

    protected void executeBlock(List<Statement> statements) throws EvaluatorException {
        for (Statement stmt : statements) {
            logger.info(stmt.toString());
            if (stmt.isExpressionStmt()) {
                evaluateExpression(stmt.asExpressionStmt().getExpression());
            }
            else if(stmt.isIfStmt()) {
                IfStmt ifst = stmt.asIfStmt();
                Variable v = evaluateExpression(ifst.getCondition());
                if ( (boolean) v.getValue() ) {
                    Statement then = ifst.getThenStmt();
                    executeBlock(then.asBlockStmt().getStatements());
                }
                else {
                    System.out.println("Else condition");
                }
            }
            else {
                if(stmt.isReturnStmt()) {
                    evaluateReturnStatement(stmt);
                }
                else {
                    logger.info("Unhandled");
                }
            }
        }
    }

    void evaluateReturnStatement(Statement stmt) throws EvaluatorException {
        Optional<Expression> expr = stmt.asReturnStmt().getExpression();
        if(expr.isPresent()) {
            returnValue = evaluateExpression(expr.get());
        }
        else {
            returnValue = null;
        }
    }

    public void setupFields(CompilationUnit cu) throws IOException, EvaluatorException {
        cu.accept(new ControllerFieldVisitor(), null);
    }

    /**
     * Java parser visitor used to setup the fields in the class.
     */
    private class ControllerFieldVisitor extends VoidVisitorAdapter<Void> {
        /**
         * The field visitor will be used to identify the fields that are being used in the class
         *
         * @param field the field to inspect
         * @param arg not used
         */
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            for (var variable : field.getVariables()) {
                try {
                    identifyFieldVariables(variable);
                } catch (UnsolvedSymbolException e) {
                    logger.debug("ignore {}", variable);
                } catch (IOException e) {
                    String action = Settings.getProperty("dependencies.on_error").toString();
                    if(action == null || action.equals("exit")) {
                        throw new GeneratorException("Exception while processing fields", e);
                    }
                    logger.error("Exception while processing fields");
                    logger.error("\t{}",e.getMessage());
                } catch (EvaluatorException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
