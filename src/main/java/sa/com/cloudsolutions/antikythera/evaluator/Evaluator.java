package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
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
    private final String className;

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

    public Evaluator (String className){
        this.className = className;
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
        } else if(expr.isFieldAccessExpr()) {
            FieldAccessExpr fae = expr.asFieldAccessExpr();
            try {
                String name = fae.resolve().getType().describe();
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(name);
//                if (cu != null) {
//                    VoidVisitorAdapter<Void> adapter = new FieldVisitor();
//                    adapter.visit(cu, null);
//                }
            } catch (Exception e) {
                throw new EvaluatorException("Error evaluating field access expression", e);
            }
            System.out.println("Fields baby");
        }
        return null;
    }

    private static Variable evaluateLiteral(Expression expr) throws EvaluatorException {
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
            String value = expr.asLongLiteralExpr().getValue();
            if (value.endsWith("L")) {
                return new Variable(Long.parseLong(value.replaceFirst("L","")));
            }
            else {
                return new Variable(Long.parseLong(value));
            }
        } else if (expr.isNullLiteralExpr()) {
            return new Variable(null);
        }
        throw new EvaluatorException("Unknown literal expression %s".formatted(expr));
    }

    private Variable evaluateAssignment(Expression expr) throws AntikytheraException, ReflectiveOperationException {
        AssignExpr assignExpr = expr.asAssignExpr();
        Expression target = assignExpr.getTarget();
        Expression value = assignExpr.getValue();
        Variable v = evaluateExpression(value);

        if(target.isFieldAccessExpr()) {
            FieldAccessExpr fae = target.asFieldAccessExpr();
            String fieldName = fae.getNameAsString();
            Variable variable = fae.getScope().toString().equals("this")
                    ? getValue(expr, fieldName)
                    : getValue(expr, fae.getScope().toString());

            Object obj = variable.getValue();
            try {
                Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, v.getValue());
            } catch (ReflectiveOperationException e) {
                /*
                 * This is not something that was created with class.forName or byte buddy.
                 */
                this.fields.put(fieldName,variable);
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
     * @param decl  The variable declaration. Pass null here if you don't want to create local variable.
     *              This would typically be the case if you have a method call and one of the arguments
     *              to the method call is a new instance.
     * @param expression The expression to be evaluated and assigned as the initial value
     * @return The object that's created will be in the value field of the Variable
     */
    Variable createObject(Node instructionPointer, VariableDeclarator decl, Expression expression) throws AntikytheraException, ReflectiveOperationException {
        ObjectCreationExpr oce = expression.asObjectCreationExpr();
        ClassOrInterfaceType type = oce.getType();
        Variable vx = null;

        vx = createUsingReflection(type, oce);

        if (vx == null) {
            vx = createUsingEvaluator(type, oce);
            if(vx == null) {
                vx = createUsingByteBuddy(oce, type);
            }
        }
        if (decl != null) {
            setLocal(instructionPointer, decl.getNameAsString(), vx);
        }

        return vx;
    }

    private Variable createUsingByteBuddy(ObjectCreationExpr oce, ClassOrInterfaceType type) {
        if (Settings.getProperty("bytebuddy") != null) {

            try {
                List<Expression> arguments = oce.getArguments();
                Object[] constructorArgs = new Object[arguments.size()];

                for (int i = 0; i < arguments.size(); i++) {
                    Variable arg = evaluateExpression(arguments.get(i));
                    constructorArgs[i] = arg.getValue();
                }

                // Create the dynamic DTO with the extracted arguments
                Object instance = DTOBuddy.createDynamicDTO(type, constructorArgs);
                return new Variable(type, instance);
            } catch (Exception e) {
                logger.error("An error occurred in creating a variable with bytebuddy", e);
            }
        }
        return null;
    }

    /**
     * Create a new object as an evaluator instance.
     * @param type the class or interface type that we need to create an instance of
     * @param oce the object creation expression.
     */
    private Variable createUsingEvaluator(ClassOrInterfaceType type, ObjectCreationExpr oce) throws AntikytheraException, ReflectiveOperationException {
        Optional<ResolvedType> res = AbstractCompiler.resolveTypeSafely(type);
        if (res.isPresent()) {
            ResolvedType resolved = type.resolve();
            String resolvedClass = resolved.describe();
            Evaluator eval = new Evaluator(resolvedClass);
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(resolvedClass);

            eval.setupFields(cu);

            List<ConstructorDeclaration> constructors = cu.findAll(ConstructorDeclaration.class);
            if (constructors.isEmpty()) {
                return new Variable(eval);
            }

            Optional<ConstructorDeclaration> matchingConstructor = AbstractCompiler.findMatchingConstructor(cu, oce);
            if (matchingConstructor.isPresent()) {
                ConstructorDeclaration constructor = matchingConstructor.get();
                for (int i = oce.getArguments().size() - 1; i >= 0; i--) {
                    /*
                     * Push method arguments
                     */
                    AntikytheraRunTime.push(evaluateExpression(oce.getArguments().get(i)));
                }
                eval.executeConstructor(constructor);
                return new Variable(eval);
            }
        }
        return null;
    }


    /**
     * Create a new object using reflection.
     * Typically intended for use for classes contained in the standard library.
     *
     * @param type the type of the class
     * @param oce the object creation expression
     * @return a Variable if the instance could be created or null.
     */
    private Variable createUsingReflection(ClassOrInterfaceType type, ObjectCreationExpr oce) {
        try {
            Optional<ResolvedType> res = AbstractCompiler.resolveTypeSafely(type);
            if (res.isPresent()) {
                ResolvedType resolved = type.resolve();
                String resolvedClass = resolved.describe();

                if (resolved.isReferenceType()) {
                    var typeDecl = resolved.asReferenceType().getTypeDeclaration();
                    if (typeDecl.isPresent() && typeDecl.get().getClassName().contains(".")) {
                        resolvedClass = resolvedClass.replaceFirst("\\.([^\\.]+)$", "\\$$1");
                    }
                }

                Class<?> clazz = Class.forName(resolvedClass);

                Class<?> outer = clazz.getEnclosingClass();
                if (outer != null) {
                    for (Class<?> c : outer.getDeclaredClasses()) {
                        if (c.getName().equals(resolvedClass)) {
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
                                return new Variable(type, instance);
                            }
                            else {
                                throw new EvaluatorException("Could not find a constructor for class " + c.getName());
                            }
                        }
                    }
                } else {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    return new Variable(type, instance);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not create an instance of type {} using reflection", type);
            logger.warn("The error was {}", e.getMessage());

        }
        return null;
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
            if (currentNode instanceof BlockStmt blockStmt) {
                return blockStmt;
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
        Optional<Expression> methodScope = methodCall.getScope();

        if (methodScope.isPresent()) {
            Expression scopeExpr = methodScope.get();
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
            }
            ReflectionArguments reflectionArguments = Reflect.buildArguments(methodCall, this);

            try {
                if (scopeExpr.isFieldAccessExpr() && scopeExpr.asFieldAccessExpr().getScope().toString().equals("System")) {
                    handleSystemOutMethodCall(reflectionArguments);
                    return null;
                } else if(scopeExpr.isNameExpr()) {
                    Variable v = getValue(methodCall, scopeExpr.toString());
                    if (v != null) {
                        if (v.getValue() instanceof Evaluator eval) {
                            for (int i = reflectionArguments.getArgs().length - 1 ; i >= 0 ; i--) {
                                /*
                                 * Push method arguments
                                 */
                                AntikytheraRunTime.push(new Variable(reflectionArguments.getArgs()[i]));
                            }
                            return eval.executeMethod(methodCall);
                        }

                        Class<?> clazz = v.getClazz();
                        String methodName = reflectionArguments.getMethodName();
                        Class<?>[] paramTypes = reflectionArguments.getParamTypes();
                        Object[] args = reflectionArguments.getArgs();
                        Method method = findMethod(clazz, methodName, paramTypes);
                        if (method != null) {
                            Variable result = new Variable(method.invoke(v.getValue(), args));
                            result.setClazz(clazz);
                            /*
                             * check if the method returns a value, and push it into the stack so that
                             * it can be popped off by the caller
                             */
                            if (method.getReturnType() != void.class) {
                                AntikytheraRunTime.push(result);
                            }

                            return result;
                        }
                        throw new EvaluatorException("Error evaluating method call: " + methodName);
                    }
                }
                return handleRegularMethodCall(methodCall, scopeExpr, reflectionArguments);
            } catch (IllegalStateException e) {
                return handleUnsolvableMethodCall(reflectionArguments, e);
            }
        }
        return executeMethod(methodCall);
    }

    private Variable executeMethod(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        Optional<Node> n = methodCall.resolve().toAst();
        if (n.isPresent() && n.get() instanceof MethodDeclaration md) {
            return executeMethod(md);
        }
        return null;
    }

    private Variable handleUnsolvableMethodCall(ReflectionArguments reflectionArguments, IllegalStateException e) throws EvaluatorException {
        String methodName = reflectionArguments.getMethodName();
        Class<?>[] paramTypes = reflectionArguments.getParamTypes();
        Object[] args = reflectionArguments.getArgs();

        Class<?> clazz = returnValue.getClazz();
        try {

            Method method = findMethod(clazz, methodName, paramTypes);
            if (method != null) {
                Variable v = new Variable(method.invoke(returnValue.getValue(), args));
                v.setClazz(method.getReturnType());
                return v;
            }
            throw new EvaluatorException("Error evaluating method call: " + methodName, e);
        } catch (ReflectiveOperationException ex) {
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
                if(types.length == 1 && types[0].equals(Object[].class)) {
                    return m;
                }
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

        Variable scopedVariable = getValue(methodCall, scopeExpr.toString());

        String methodName = reflectionArguments.getMethodName();
        Class<?>[] paramTypes = reflectionArguments.getParamTypes();
        Object[] args = reflectionArguments.getArgs();
        Class<?> clazz = scopedVariable  == null ?  getClass(declaringType.getQualifiedName()) : scopedVariable.getClazz();

        if (clazz != null && declaringType.isClass() && declaringType.getPackageName().equals("java.lang")) {
            Method method = findMethod(clazz, methodName, paramTypes);
            if(method != null) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    return new Variable(method.invoke(null, args));

                } else {
                    /*
                     * Some methods take an Object[] as the only argument and that will match against our
                     * criteria. However that means further changes to the args are required.
                     */
                    Object[] finalArgs = args;
                    if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0].equals(Object[].class)) {
                        finalArgs = new Object[]{args};
                    }
                    return  new Variable(method.invoke(evaluateExpression(scopeExpr).getValue(), finalArgs));
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
                if (left == null) {
                    if (right == null || right.getValue() == null) {
                        return new Variable(Boolean.TRUE);
                    }
                    return new Variable(Boolean.FALSE);
                }
                if (right == null) {
                    if (left.getValue() == null) {
                        return new Variable(Boolean.TRUE);
                    }
                    return new Variable(Boolean.FALSE);
                }
                if (left.getValue() == right.getValue()) {
                    return new Variable(Boolean.TRUE);
                }
                return new Variable( left.getValue().equals(right.getValue()));
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

            case OR:
                if (  (left.getClazz().equals(Boolean.class) || left.getClazz().equals(boolean.class))
                        && (right.getClazz().equals(Boolean.class) || right.getClazz().equals(boolean.class))) {
                    return new Variable( (Boolean)left.getValue() || (Boolean)right.getValue());
                }
                return null;

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
            String resolvedClass = t.resolve().describe();

            if(finches.get(resolvedClass) != null) {
                Variable v = new Variable(t);
                v.setValue(finches.get(resolvedClass));
                fields.put(variable.getNameAsString(), v);
            }
            else if (resolvedClass.startsWith("java")) {
                setupPrimitiveOrBoxedField(variable, t);
            }
            else {
                CompilationUnit compilationUnit = AntikytheraRunTime.getCompilationUnit(resolvedClass);
                if (compilationUnit != null) {
                    resolveFieldRepresentedByCode(variable, resolvedClass);
                }
                else {
                    System.out.println("Unsolved " + resolvedClass);
                }
            }
        }
        else {
            resolveNonClassFields(variable);
        }
    }

    private void setupPrimitiveOrBoxedField(VariableDeclarator variable, Type t) throws AntikytheraException, ReflectiveOperationException {
        Variable v = null;
        Optional<Expression> init = variable.getInitializer();
        if(init.isPresent()) {
            v = evaluateExpression(init.get());
            if (v == null && init.get().isNameExpr()) {
                /*
                 * This is probably a constant that is imported as static.
                 */

                NameExpr nameExpr = init.get().asNameExpr();
                String name = nameExpr.getNameAsString();
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
                for (ImportDeclaration importDeclaration : cu.getImports()) {
                    Name importedName = importDeclaration.getName();
                    String[] parts = importedName.toString().split("\\.");

                    if (importedName.toString().equals(name)) {
                        Evaluator eval = new Evaluator(importedName.toString());
                        v = eval.getFields().get(name);
                        break;
                    }
                    else if(parts.length > 1 && parts[parts.length - 1].equals(name)) {
                        int last = importedName.toString().lastIndexOf(".");
                        String cname = importedName.toString().substring(0, last);
                        CompilationUnit dep = AntikytheraRunTime.getCompilationUnit(cname);
                        Evaluator eval = new Evaluator(cname);
                        eval.setupFields(dep);
                        v = eval.getFields().get(name);
                        break;
                    }
                }
            }
            v.setType(t);
        }
        else
        {
            v = new Variable(t, null);
        }
        fields.put(variable.getNameAsString(), v);
    }

    private void resolveFieldRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws AntikytheraException, ReflectiveOperationException {
        Optional<Expression> init = variable.getInitializer();
        if (init.isPresent()) {
            if(init.get().isObjectCreationExpr()) {
                Variable v = createObject(variable, variable, init.get());
                fields.put(variable.getNameAsString(), v);
            }
            else {
                Evaluator eval = new Evaluator(resolvedClass);
                Variable v = new Variable(eval);
                fields.put(variable.getNameAsString(), v);
            }
        }
    }

    private void resolveNonClassFields(VariableDeclarator variable) throws AntikytheraException, ReflectiveOperationException {
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


    public Map<String, Variable> getFields() {
        return fields;
    }

    public Map<String, Object> getFinches() {
        return finches;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Variable executeMethod(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
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

        return returnValue;
    }

    public void executeConstructor(ConstructorDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        List<Statement> statements = md.getBody().getStatements();
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
                setLocal(md.getBody(), p.getNameAsString(), AntikytheraRunTime.pop());
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
                executeStatement(stmt);
            }
        } catch (EvaluatorException|ReflectiveOperationException ex) {
            throw ex;
        } catch (Exception e) {
            handleApplicationException(e);
        }
    }

    private void executeStatement(Statement stmt) throws Exception {
        logger.info(stmt.toString());
        if (stmt.isExpressionStmt()) {
            evaluateExpression(stmt.asExpressionStmt().getExpression());
        } else if (stmt.isIfStmt()) {
            ifThenElseBlock(stmt);
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
            returnValue = evaluateReturnStatement(stmt);
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

    private void ifThenElseBlock(Statement stmt) throws Exception {
        IfStmt ifst = stmt.asIfStmt();
        Variable v = evaluateExpression(ifst.getCondition());
        if ((boolean) v.getValue()) {
            Statement then = ifst.getThenStmt();
            if (then.isBlockStmt()) {
                executeBlock(then.asBlockStmt().getStatements());
            }
            else {
                executeStatement(then);
            }
        } else {

            Optional<Statement> elseBlock = ifst.getElseStmt();
            if(elseBlock.isPresent()) {
                Statement el = elseBlock.get();
                if(el.isBlockStmt()) {
                    executeBlock(el.asBlockStmt().getStatements());
                }
                else {
                    executeStatement(el);
                }
            }
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
                String resolvedClass = clause.getParameter().getType().asClassOrInterfaceType().resolve().describe();
                if(resolvedClass.equals(e.getClass().getName())) {
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

    Variable evaluateReturnStatement(Statement stmt) throws AntikytheraException, ReflectiveOperationException {
        Optional<Expression> expr = stmt.asReturnStmt().getExpression();
        if(expr.isPresent()) {
            returnValue = evaluateExpression(expr.get());
        }
        else {
            returnValue = null;
        }
        return returnValue;
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

    public void reset() {
        locals.clear();
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
