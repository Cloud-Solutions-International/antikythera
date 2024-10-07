package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;

import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import com.github.javaparser.ast.stmt.IfStmt;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.finch.Finch;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;

import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
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
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
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
    static Map<Class<?>, Class<?>> primitiveToWrapper = new HashMap<>();
    static {
        wrapperToPrimitive.put(Integer.class, int.class);
        wrapperToPrimitive.put(Double.class, double.class);
        wrapperToPrimitive.put(Boolean.class, boolean.class);
        wrapperToPrimitive.put(Long.class, long.class);
        wrapperToPrimitive.put(Float.class, float.class);
        wrapperToPrimitive.put(Short.class, short.class);
        wrapperToPrimitive.put(Byte.class, byte.class);
        wrapperToPrimitive.put(Character.class, char.class);

        for(Map.Entry<Class<?>, Class<?>> entry : wrapperToPrimitive.entrySet()) {
            primitiveToWrapper.put(entry.getValue(), entry.getKey());
        }
    }

    private Deque<TryStmt> catching = new LinkedList<>();

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
     * @return the value for the variable in the current scope
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
     * @throws EvaluatorException if we have done something wrong.
     */
    public Variable evaluateExpression(Expression expr) throws AntikytheraException, ReflectiveOperationException {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return getValue(expr, name);
        } else if (expr.isLiteralExpr()) {
            /*
             * Literal expressions are the easiest.
             */
            return evaluateLiteral(expr);

        } else if (expr.isVariableDeclarationExpr()) {
            /*
             * Variable declarations are hard and deserve their own method.
             */
            return evaluateVariableDeclaration(expr);
        } else if (expr.isBinaryExpr()) {
            /*
             * Binary expressions can also be difficult
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

    private static Variable evaluateLiteral(Expression expr) {
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
        logger.warn("Unknown literal expression {}", expr);
        return null;
    }

    private Variable evaluateAssignment(Expression expr) throws AntikytheraException, ReflectiveOperationException {
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
    Variable evaluateVariableDeclaration(Expression expr) throws AntikytheraException, ReflectiveOperationException {
        VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
        Variable v = null;
        for (var decl : varDeclExpr.getVariables()) {
            Optional<Expression> init = decl.getInitializer();
            if (init.isPresent()) {
                Expression expression = init.get();
                if (expression.isMethodCallExpr()) {
                    MethodCallExpr methodCall = expression.asMethodCallExpr();
                    v = evaluateMethodCall(methodCall);
                    if (v != null) {
                        v.setType(decl.getType());
                        setLocal(methodCall, decl.getNameAsString(), v);
                    }
                }
                else if(expression.isObjectCreationExpr()) {
                    v = createObject(expr, decl, expression);
                    if (v != null) {
                        v.setType(decl.getType());
                        setLocal(expression, decl.getNameAsString(), v);
                    }
                }
                else {
                    v = evaluateExpression(expression);
                    if (v != null) {
                        v.setType(decl.getType());
                        setLocal(expression, decl.getNameAsString(), v);
                    }
                }

            }
        }
        return v;
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
        Variable vx = null;
        Optional<ResolvedType> res = AbstractCompiler.resolveTypeSafely(type);
        ResolvedType resolved = null;
        try {

            if (res.isPresent()) {
                resolved = type.resolve();
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
                                paramTypes[i + 1] =wrapperClass;
                                args[i + 1] = vv.getValue();
                            }

                            Constructor<?> cons = findConstructor(c, paramTypes);
                            if(cons !=  null) {
                                Object instance = cons.newInstance(args);
                                vx = new Variable(type, instance);
                            }
                            else {
                                throw new EvaluatorException("Could not find a constructor for class " + c.getName());
                            }
                        }
                    }
                } else {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    vx = new Variable(type, instance);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not create an instance of type {} going to try again with bytebuddy", type);
            logger.warn("The error was {}", e.getMessage());

        }

        if (vx == null) {
            Object instance  = null;
            try {
                instance = DTOBuddy.createDynamicDTO(type);
                vx = new Variable(type, instance);
            } catch (Exception e) {
                logger.error("An error occurred in creating a variable with bytebuddy", e);
            }
        }
        if (decl != null) {
            setLocal(instructionPointer, decl.getNameAsString(), vx);
        }

        return vx;
    }

    /**
     * Find local variable
     * Does not look at fields. You probably want to call getValue() instead.
     *
     * @param node the node representing the current expresion.
     *             It's primary purpose is to help identify the current block
     * @param name the name of the variable to look up
     * @return the Variable if it's found or null.
     */
    public Variable getLocal(Node node, String name) {
        Variable v = null;
        Node n = node;

        while (true) {
            BlockStmt block = findBlockStatement(n);
            int hash = (block != null) ? block.hashCode() : 0;

            Map<String, Variable> localsVars = this.locals.get(hash);

            if (localsVars != null) {
                v = localsVars.get(name);
                if (v != null )
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
     * @param nameAsString the variable name.
     *                     If the variable is already available as a local it's value will be replaced.
     * @param v The value to be set for the variable.
     */
    void setLocal(Node node, String nameAsString, Variable v) {
        Variable old = getValue(node, nameAsString);
        if (old != null) {
            old.setValue(v.getValue());
        }
        else {
            BlockStmt block = findBlockStatement(node);
            int hash = (block != null) ? block.hashCode() : 0;

            Map<String, Variable> localVars = this.locals.get(hash);
            if (localVars == null) {
                localVars = new HashMap<>();
                this.locals.put(hash, localVars);
            }
            localVars.put(nameAsString, v);
        }
    }

    /**
     * Recursively traverse parents to find a block statement.
     * @param expr the expression to start from
     * @return the block statement that contains expr
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
    public Variable evaluateMethodCall(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
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
            else if (scopeExpr.isLiteralExpr()) {
                returnValue = evaluateLiteral(scopeExpr.asLiteralExpr());
                MethodCallExpr chained = methodCall.clone();
                chained.setScope(new NameExpr(returnValue.getValue().toString()));
                returnValue = evaluateMethodCall(chained);
            }
            ReflectionArguments reflectionArguments = Reflect.buildArguments(methodCall, this);

            try {
                if (scopeExpr.isFieldAccessExpr() && scopeExpr.asFieldAccessExpr().getScope().toString().equals("System")) {
                    handleSystemOutMethodCall(reflectionArguments);
                } else {
                    return handleRegularMethodCall(methodCall, scopeExpr, reflectionArguments);
                }
            } catch (IllegalStateException e) {
                return handleIllegalStateException(reflectionArguments, e);
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

    private Variable handleIllegalStateException(ReflectionArguments reflectionArguments, IllegalStateException e) throws EvaluatorException {
        String methodName = reflectionArguments.getMethodName();
        Class<?>[] paramTypes = reflectionArguments.getParamTypes();
        Object[] args = reflectionArguments.getArgs();

        Class<?> clazz = returnValue.getValue().getClass();
        try {

            Method method = findMethod(clazz, methodName, paramTypes);
            if (method != null) {
                Variable v = new Variable(method.invoke(returnValue.getValue(), args));
                AntikytheraRunTime.push(v);
                return v;
            }
            throw new EvaluatorException("Error evaluating method call: " + methodName, e);
        } catch (Exception ex) {
            throw new EvaluatorException("Error evaluating method call: " + methodName, e);
        }
    }

    /**
     * Finds a matching method using parameters.
     *
     * This function has side effects. The paramTypes may end up being converted from a boxed to
     * primitive or wise versa. This is because the Variable class that we use has an Object
     * representing the value. Where as some of the methods have parameters that require a primitive
     * type. Hence the conversion needs to happen.
     *
     * @param clazz the class on which we need to match the method name
     * @param methodName the name of the method to find
     * @param paramTypes and array or parameter types.
     * @return a Method instance or null.
     */
    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {

        Method[] methods = clazz.getMethods();
        for (Method m : methods) {
            if (m.getName().equals(methodName)) {
                Class<?>[] types = m.getParameterTypes();
                if (types.length != paramTypes.length) {
                    continue;
                }
                boolean found = true;
                for(int i = 0 ; i < paramTypes.length ; i++) {
                    if (types[i].equals(paramTypes[i])) {
                        continue;
                    }
                    if (wrapperToPrimitive.get(types[i]) != null && wrapperToPrimitive.get(types[i]).equals(paramTypes[i])) {
                        paramTypes[i] = wrapperToPrimitive.get(types[i]);
                        continue;
                    }
                    if(primitiveToWrapper.get(types[i]) != null && primitiveToWrapper.get(types[i]).equals(paramTypes[i])) {
                        paramTypes[i] = primitiveToWrapper.get(types[i]);
                        continue;
                    }
                    if(types[i].getName().equals("java.lang.Object")) {
                        continue;
                    }
                    found = false;
                }
                if (found) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Find a constructor matching the given parameters.
     *
     * This method has side effects. The paramTypes may end up being converted from a boxed to primitive
     * or vice verce
     *
     * @param clazz the Class for which we need to find a constructor
     * @param paramTypes the types of the parameters we are looking for.
     * @return a Constructor instance or null.
     */
    private Constructor<?> findConstructor(Class<?> clazz, Class<?>[] paramTypes) {
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            Class<?>[] types = c.getParameterTypes();
            if (types.length != paramTypes.length) {
                continue;
            }
            boolean found = true;
            for(int i = 0 ; i < paramTypes.length ; i++) {
                if (types[i].equals(paramTypes[i])) {
                    continue;
                }
                if (wrapperToPrimitive.get(types[i]) != null && wrapperToPrimitive.get(types[i]).equals(paramTypes[i])) {
                    paramTypes[i] = wrapperToPrimitive.get(types[i]);
                    continue;
                }
                if(primitiveToWrapper.get(types[i]) != null && primitiveToWrapper.get(types[i]).equals(paramTypes[i])) {
                    paramTypes[i] = primitiveToWrapper.get(types[i]);
                    continue;
                }
                found = false;
            }
            if (found) {
                return c;
            }
        }
        return null;
    }

    /**
     * Simulates a System.out method call
     * @param reflectionArguments the set of arguments to use
     * @throws ReflectiveOperationException if the operation cannot be carried out with reflection
     */
    private void handleSystemOutMethodCall(ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
        Class<?>[] paramTypes = reflectionArguments.getParamTypes();
        Object[] args = reflectionArguments.getArgs();
        Class<?> systemClass = Class.forName("java.lang.System");
        Field outField = systemClass.getField("out");
        Class<?> printStreamClass = outField.getType();
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = Object.class;
        }
        Method printlnMethod = printStreamClass.getMethod("println", paramTypes);
        printlnMethod.invoke(outField.get(null), args);
    }

    static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            logger.info("Could not find class {}", className);
        }
        return null;
    }

    Variable handleRegularMethodCall(MethodCallExpr methodCall, Expression scopeExpr, ReflectionArguments reflectionArguments)
            throws AntikytheraException, ReflectiveOperationException {
        ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
        ResolvedReferenceTypeDeclaration declaringType = resolvedMethod.declaringType();

        Variable scopedVariable = getLocal(methodCall, scopeExpr.toString());

        String methodName = reflectionArguments.getMethodName();
        Class<?>[] paramTypes = reflectionArguments.getParamTypes();
        Object[] args = reflectionArguments.getArgs();
        Class<?> clazz = scopedVariable  == null ?  getClass(declaringType.getQualifiedName()) : scopedVariable.getClazz();

        if (clazz != null && declaringType.isClass() && declaringType.getPackageName().equals("java.lang")) {
            Method method = findMethod(clazz, methodName, paramTypes);
            if(method != null) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    Variable v = new Variable(method.invoke(null, args));
                    AntikytheraRunTime.push(v);
                    return v;
                } else {
                    Variable v = new Variable(method.invoke(evaluateExpression(scopeExpr).getValue(), args));
                    AntikytheraRunTime.push(v);
                    return v;
                }
            }
            throw new EvaluatorException(String.format("Method %s not found ", methodName));
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
                Method method = findMethod(v.getValue().getClass(), methodName, paramTypes);
                if(method != null) {
                    Variable response = new Variable(method.invoke(v.getValue(), args));
                    response.setClazz(method.getReturnType());
                    return response;
                }
                throw new EvaluatorException(String.format("Method %s not found ", methodName));
            }
        }
        return null;
    }

    Variable evaluateBinaryExpression(BinaryExpr.Operator operator,
                                      Expression leftExpression, Expression rightExpression) throws AntikytheraException, ReflectiveOperationException {
        Variable left = evaluateExpression(leftExpression);

        if(operator.equals(BinaryExpr.Operator.OR) && (boolean)left.getValue()) {
             return new Variable(Boolean.TRUE);
        }

        Variable right = evaluateExpression(rightExpression);

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
                return new Variable( ((Comparable<?>) left.getValue()).equals(right.getValue()));
            }

            case GREATER:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) > 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case GREATER_EQUALS:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) >= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case LESS:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) < 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case LESS_EQUALS:
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    return new Variable(NumericComparator.compare(left.getValue(), right.getValue()) <= 0);
                }
                throw new EvaluatorException("Cannot compare " + leftExpression + " and " + rightExpression);

            case NOT_EQUALS:
                return new Variable(!left.getValue().equals(right.getValue()));

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

    void identifyFieldVariables(VariableDeclarator variable) throws IOException, AntikytheraException, ReflectiveOperationException {
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
            Variable v;
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

    public void executeMethod(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
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

    protected void executeBlock(List<Statement> statements) throws AntikytheraException, ReflectiveOperationException {
        try {
            for (Statement stmt : statements) {
                logger.info(stmt.toString());
                if (stmt.isExpressionStmt()) {
                    evaluateExpression(stmt.asExpressionStmt().getExpression());
                } else if (stmt.isIfStmt()) {
                    IfStmt ifst = stmt.asIfStmt();
                    Variable v = evaluateExpression(ifst.getCondition());
                    if ((boolean) v.getValue()) {
                        Statement then = ifst.getThenStmt();
                        executeBlock(then.asBlockStmt().getStatements());
                    } else {
                        Optional<Statement> elseBlock = ifst.getElseStmt();
                        if(elseBlock.isPresent()) {
                            executeBlock(elseBlock.get().asBlockStmt().getStatements());
                        }
                    }
                } else if (stmt.isTryStmt()) {
                    catching.addLast(stmt.asTryStmt());
                    executeBlock(stmt.asTryStmt().getTryBlock().getStatements());
                } else if (stmt.isThrowStmt()) {
                    ThrowStmt t = stmt.asThrowStmt();
                    if(t.getExpression().isObjectCreationExpr()) {
                        ObjectCreationExpr oce = t.getExpression().asObjectCreationExpr();
                        Variable v = createObject(stmt, null, oce);
                        if (v.getValue() instanceof Exception ex) {
                            throw ex;
                        }
                        else {
                            logger.error("Should have an exception");
                        }
                    }
                } else if (stmt.isReturnStmt()) {
                    evaluateReturnStatement(stmt);
                } else if (stmt.isForStmt() || stmt.isForEachStmt() || stmt.isDoStmt() || stmt.isSwitchStmt() || stmt.isWhileStmt()) {
                    logger.warn("Some block statements are not being handled at the moment");
                } else if (stmt.isBlockStmt()) {
                    // in C like languages it's possible to have a block that is not directly
                    // associated with a condtional, loop or method etc.
                    executeBlock(stmt.asBlockStmt().getStatements());
                } else {
                    logger.info("Unhandled");
                }

            }
        } catch (EvaluatorException|ReflectiveOperationException ex) {
            throw ex;
        } catch (Exception e) {
            handleApplicationException(e);
        }
    }

    protected void handleApplicationException(Exception e) throws AntikytheraException, ReflectiveOperationException {
        if(catching.isEmpty()) {
            throw new AUTException("Unhandled exception", e);
        }
        TryStmt t = catching.pollLast();
        boolean matched = false;
        for(CatchClause clause : t.getCatchClauses()) {
            if(clause.getParameter().getType().isClassOrInterfaceType()) {
                String className = clause.getParameter().getType().asClassOrInterfaceType().resolve().describe();
                if(className.equals(e.getClass().getName())) {
                    setLocal(t, clause.getParameter().getNameAsString(), new Variable(e));
                    executeBlock(clause.getBody().getStatements());
                    if(t.getFinallyBlock().isPresent()) {
                        executeBlock(t.getFinallyBlock().get().getStatements());
                    }
                    matched = true;
                    break;
                }
            }
        }
        if(!matched) {
            throw new AUTException("Unhandled exception", e);
        }
    }

    void evaluateReturnStatement(Statement stmt) throws AntikytheraException, ReflectiveOperationException {
        Optional<Expression> expr = stmt.asReturnStmt().getExpression();
        if(expr.isPresent()) {
            returnValue = evaluateExpression(expr.get());
        }
        else {
            returnValue = null;
        }
    }

    public void setupFields(CompilationUnit cu)  {
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

                } catch (AntikytheraException|ReflectiveOperationException e) {
                    throw new GeneratorException(e);
                }
            }
        }
    }
}

class NumericComparator {
    private NumericComparator() {
    }

    public static int compare(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            if (leftNumber instanceof Double || rightNumber instanceof Double) {
                return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
            } else if (leftNumber instanceof Float || rightNumber instanceof Float) {
                return Float.compare(leftNumber.floatValue(), rightNumber.floatValue());
            } else if (leftNumber instanceof Long || rightNumber instanceof Long) {
                return Long.compare(leftNumber.longValue(), rightNumber.longValue());
            } else {
                return Integer.compare(leftNumber.intValue(), rightNumber.intValue());
            }
        } else if (left instanceof String leftString && right instanceof String rightString) {
            return leftString.compareTo(rightString);
        } else if (left instanceof Comparable leftComparable && right instanceof Comparable rightComparable) {
            return leftComparable.compareTo(rightComparable);
        } else {
            throw new IllegalArgumentException("Cannot compare " + left + " and " + right);
        }
    }
}
