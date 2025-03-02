package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
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
import com.github.javaparser.ast.type.UnknownType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;

import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.finch.Finch;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

/**
 * Expression evaluator engine.
 */
public class Evaluator extends AbstractEvaluator implements ExpressionEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);

    /**
     * The fields that were encountered in the current class.
     */
    protected final Map<String, Variable> fields;
    static Map<String, Object> finches;

    /**
     * The preconditions that need to be met before the test can be executed.
     */
    protected final Map<MethodDeclaration, Set<Expression>> preConditions = new HashMap<>();

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

    public Evaluator (String className) {
        super(className);
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
    protected Variable getValue(Node n, String name) {
        Variable value = getLocal(n, name);
        if (value == null && fields.get(name) != null) {
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
     * @param expr the expression to evaluate
     * @return the result as a Variable instance which can be null if the expression is supposed to return null
     */
    public Variable evaluateExpression(Expression expr) throws ReflectiveOperationException {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            return getValue(expr, name);
        } else if (expr.isMethodCallExpr()) {

            /*
             * Method calls are the toughest nuts to crack. Some method calls will be from the Java api
             * or from other libraries. Or from classes that have not been compiled.
             */
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            return evaluateMethodCall(methodCall);

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
            return evaluateBinaryExpression(expr);
        } else if (expr.isUnaryExpr()) {
            return  evaluateUnaryExpression(expr);
        } else if (expr.isAssignExpr()) {
            return evaluateAssignment(expr);
        } else if (expr.isObjectCreationExpr()) {
            return createObject(expr, null, expr.asObjectCreationExpr());
        } else if(expr.isFieldAccessExpr()) {
            return evaluateFieldAccessExpression(expr);
        } else if(expr.isArrayInitializerExpr()) {
            /*
             * Array Initialization is tricky
             */
            ArrayInitializerExpr arrayInitializerExpr = expr.asArrayInitializerExpr();
            return createArray(arrayInitializerExpr);

        } else if(expr.isEnclosedExpr()) {
            /*
             * Enclosed expressions are just brackets around stuff.
             */
            return evaluateExpression(expr.asEnclosedExpr().getInner());
        } else if(expr.isCastExpr()) {
            return evaluateExpression(expr.asCastExpr().getExpression());
        } else if (expr.isConditionalExpr()) {
            return evaluateConditionalExpression(expr.asConditionalExpr());
        } else if (expr.isLambdaExpr()) {
            return createLambdaExpression(expr.asLambdaExpr());
        }
        return null;
    }

    private Variable createLambdaExpression(LambdaExpr lambdaExpr)  {
        // Create a synthetic method from the lambda
        MethodDeclaration md = new MethodDeclaration();

        // Set the method body based on lambda type (expression or block)
        if (lambdaExpr.getBody().isBlockStmt()) {
            BlockStmt body = lambdaExpr.getBody().asBlockStmt();
            md.setBody(body);
        } else {
            BlockStmt blockStmt = new BlockStmt();
            blockStmt.addStatement(lambdaExpr.getBody());
            md.setBody(blockStmt);
        }
        md.setType(new UnknownType());

        // Add lambda parameters to method
        lambdaExpr.getParameters().forEach(md::addParameter);

        // Create an evaluator instance for the lambda
        FunctionalEvaluator eval = new FunctionalEvaluator("lambda");
        eval.setMethod(md);
        return new Variable(eval);
    }

    private Variable evaluateBinaryExpression(Expression expr) throws ReflectiveOperationException {
        BinaryExpr binaryExpr = expr.asBinaryExpr();
        Expression left = binaryExpr.getLeft();
        Expression right = binaryExpr.getRight();

        return evaluateBinaryExpression(binaryExpr.getOperator(), left, right);
    }

    private Variable evaluateConditionalExpression(ConditionalExpr conditionalExpr) throws ReflectiveOperationException {
        Variable v = evaluateBinaryExpression(conditionalExpr.getCondition());
        if (v != null && v.getValue().equals(Boolean.TRUE)) {
            return evaluateExpression(conditionalExpr.getThenExpr());
        }
        else {
            return evaluateExpression(conditionalExpr.getElseExpr());
        }
    }

    /**
     * Create an array using reflection
     * @param arrayInitializerExpr the ArrayInitializerExpr which describes how the array will be build
     * @return a Variable which holds the generated array as a value
     * @throws ReflectiveOperationException when a reflection method fails
     * @throws AntikytheraException when a parser operation fails
     */
    Variable createArray(ArrayInitializerExpr arrayInitializerExpr) throws ReflectiveOperationException, AntikytheraException {
        Optional<Node> parent = arrayInitializerExpr.getParentNode();
        if (parent.isPresent() && parent.get() instanceof VariableDeclarator vdecl) {
            Type componentType = vdecl.getType();
            Class<?> componentClass;

            String elementType = componentType.getElementType().toString();
            componentClass = Reflect.getComponentClass(elementType);

            List<Expression> values = arrayInitializerExpr.getValues();
            Object array = Array.newInstance(componentClass, values.size());

            for (int i = 0; i < values.size(); i++) {
                Object value = evaluateExpression(values.get(i)).getValue();
                Array.set(array, i, value);
            }

            return new Variable(array);
        }

        return null;
    }

    private Variable evaluateUnaryExpression(Expression expr) throws ReflectiveOperationException {
        Expression unaryExpr = expr.asUnaryExpr().getExpression();
        UnaryExpr.Operator operator = expr.asUnaryExpr().getOperator();
        if (operator.equals(UnaryExpr.Operator.LOGICAL_COMPLEMENT)) {
            Variable v = evaluateExpression(unaryExpr);
            v.setValue(!(Boolean)v.getValue());
            return v;
        }
        else if(operator.equals(UnaryExpr.Operator.POSTFIX_INCREMENT)
                || operator.equals(UnaryExpr.Operator.PREFIX_INCREMENT)) {
            Variable v = evaluateExpression(unaryExpr);
            if (v.getValue() instanceof Integer n) {
                v.setValue(n + 1);
            } else if (v.getValue() instanceof Double d) {
                v.setValue(d + 1);
            } else if (v.getValue() instanceof Long l) {
                v.setValue(l + 1);
            }
            return evaluateExpression(unaryExpr);
        }
        else if(operator.equals(UnaryExpr.Operator.POSTFIX_DECREMENT)
                || operator.equals(UnaryExpr.Operator.PREFIX_DECREMENT)) {
            Variable v = evaluateExpression(unaryExpr);
            if (v.getValue() instanceof Integer n) {
                v.setValue(n - 1);
            } else if (v.getValue() instanceof Double d) {
                v.setValue(d - 1);
            } else if (v.getValue() instanceof Long l) {
                v.setValue(l - 1);
            }
            return evaluateExpression(unaryExpr);
        }
        else if(operator.equals(UnaryExpr.Operator.MINUS)) {
            Variable v = evaluateExpression(unaryExpr);
            if (v.getValue() instanceof Integer n) {
                v.setValue(-1 * n);
            } else if (v.getValue() instanceof Double d) {
                v.setValue(-1 * d);
            } else if (v.getValue() instanceof Long l) {
                v.setValue(-1 * l);
            }
            return v;
        }

        logger.warn("Negation is the only unary operation supported at the moment");
        return null;
    }

    private Variable evaluateFieldAccessExpression(Expression expr) throws ReflectiveOperationException {
        FieldAccessExpr fae = expr.asFieldAccessExpr();

        if (cu != null) {
            String fullName = AbstractCompiler.findFullyQualifiedName(cu, fae.getScope().toString());
            if (fullName != null) {
                CompilationUnit dep = AntikytheraRunTime.getCompilationUnit(fullName);
                if (dep == null) {
                    /*
                     * Use class loader
                     */
                    Class<?> clazz = Class.forName(fullName);
                    Field field = clazz.getDeclaredField(fae.getNameAsString());
                    field.setAccessible(true);
                    return new Variable(field.get(null));
                } else {
                    TypeDeclaration<?> typeDeclaration = AbstractCompiler.getMatchingType(dep, fae.getScope().toString());
                    if (typeDeclaration != null) {
                        Optional<FieldDeclaration> fieldDeclaration = typeDeclaration.getFieldByName(fae.getNameAsString());

                        if (fieldDeclaration.isPresent()) {
                            FieldDeclaration field = fieldDeclaration.get();
                            Variable v = new Variable(field.getVariable(0).getType().asString());
                            field.getVariable(0).getInitializer().ifPresent(f -> v.setValue(f.toString()));
                            return v;
                        }
                    }
                }
            }
            else {
                logger.warn("Could not resolve {} for field access", fae.getScope());
            }
        }
        else {
            Variable v = getFields().get(fae.getScope().toString());
            if (v != null) {
                Object obj = v.getValue();
                Field field = obj.getClass().getDeclaredField(fae.getNameAsString());
                field.setAccessible(true);
                return new Variable(field.get(obj));
            }
        }
        return null;
    }

    private static Variable evaluateLiteral(Expression expr) throws EvaluatorException {
        return switch (expr) {
            case BooleanLiteralExpr booleanLiteralExpr ->
                new Variable(AbstractCompiler.convertLiteralToType(booleanLiteralExpr), booleanLiteralExpr.getValue());
            case DoubleLiteralExpr doubleLiteralExpr ->
                new Variable(AbstractCompiler.convertLiteralToType(doubleLiteralExpr), Double.parseDouble(doubleLiteralExpr.getValue()));
            case IntegerLiteralExpr integerLiteralExpr ->
                new Variable(AbstractCompiler.convertLiteralToType(integerLiteralExpr), Integer.parseInt(integerLiteralExpr.getValue()));
            case StringLiteralExpr stringLiteralExpr ->
                new Variable(AbstractCompiler.convertLiteralToType(stringLiteralExpr), stringLiteralExpr.getValue());
            case CharLiteralExpr charLiteralExpr ->
                new Variable(AbstractCompiler.convertLiteralToType(charLiteralExpr), charLiteralExpr.getValue());
            case LongLiteralExpr longLiteralExpr -> {
                String value = longLiteralExpr.getValue();
                yield new Variable(Long.parseLong(value.endsWith("L") ? value.replaceFirst("L", "") : value));
            }
            case NullLiteralExpr nullLiteralExpr ->
                new Variable(null);
            default -> throw new EvaluatorException("Unknown literal expression %s".formatted(expr));
        };
    }

    private Variable evaluateAssignment(Expression expr) throws ReflectiveOperationException {
        AssignExpr assignExpr = expr.asAssignExpr();
        Expression target = assignExpr.getTarget();
        Expression value = assignExpr.getValue();

        Variable v = switch (assignExpr.getOperator()) {
            case PLUS -> evaluateBinaryExpression(BinaryExpr.Operator.PLUS, target, value);
            case MULTIPLY -> evaluateBinaryExpression(BinaryExpr.Operator.MULTIPLY, target, value);
            case MINUS -> evaluateBinaryExpression(BinaryExpr.Operator.MINUS, target, value);
            case DIVIDE -> evaluateBinaryExpression(BinaryExpr.Operator.DIVIDE, target, value);
            default -> evaluateExpression(value);
        };

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
            } catch (ReflectiveOperationException|NullPointerException e) {
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
     * Will return the result of the variable declaration as well as saving it in the symbol table.
     *
     * @param expr the expression
     * @return a Variable or null if the expression could not be evaluated.
     *    If the expression itself evaluates to null, the result will be a Variable instance
     *    holding a null value.
     *
     *    in some cases multiple variables can be declared in a single line. In such a situation
     *    the returned value will be the last variable declared. you will need to fetch the rest
     *    using the local symbol table.
     * @throws ReflectiveOperationException if a reflective operation failed
     */
    Variable evaluateVariableDeclaration(Expression expr) throws ReflectiveOperationException {
        VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
        Variable v = null;
        for (var decl : varDeclExpr.getVariables()) {
            Optional<Expression> init = decl.getInitializer();
            if (init.isPresent()) {
                v = initializeVariable(decl, init.get());
                v.setInitializer(init.get());
            }
            else {
                /*
                 * No initializer. We need to create an entry in the symbol table. If the variable is
                 * primitive we need to set the appropriate default value. Non primitives will be null
                 */
                if (decl.getType().isPrimitiveType()) {
                    Object obj = Reflect.getDefault(decl.getType().asString());
                    v = new Variable(decl.getType(), obj);
                    v.setPrimitive(true);
                }
                else {
                    v = new Variable(decl.getType(),null);

                }
                setLocal(expr, decl.getNameAsString(), v);
            }
        }
        return v;
    }

    private Variable initializeVariable(VariableDeclarator decl, Expression init) throws ReflectiveOperationException {
        Variable v;
        if (init.isMethodCallExpr()) {
            MethodCallExpr methodCall = init.asMethodCallExpr();
            v = evaluateMethodCall(methodCall);
            if (v != null) {
                v.setType(decl.getType());
                setLocal(decl, decl.getNameAsString(), v);
            }
        }
        else if(init.isObjectCreationExpr()) {
            v = createObject(init, decl, init.asObjectCreationExpr());
            if (v != null) {
                v.setType(decl.getType());
                setLocal(decl, decl.getNameAsString(), v);
            }
        }
        else {
            v = evaluateExpression(init);
            if (v != null) {
                v.setType(decl.getType());
                setLocal(decl, decl.getNameAsString(), v);
            }
        }
        return v;
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
    private Variable createUsingEvaluator(ClassOrInterfaceType type, ObjectCreationExpr oce, Node context) throws ReflectiveOperationException {
        TypeDeclaration<?> match = AbstractCompiler.resolveTypeSafely(type, context);
        if (match != null) {
            ExpressionEvaluator eval = createEvaluator(match.getFullyQualifiedName().get());
            annonymousOverrides(type, oce, eval);
            List<ConstructorDeclaration> constructors = match.findAll(ConstructorDeclaration.class);
            if (constructors.isEmpty()) {
                return new Variable(eval);
            }
            MCEWrapper mce = wrapCallExpression(oce);

            Optional<Callable> matchingConstructor =  AbstractCompiler.findConstructorDeclaration(mce, match);

            if (matchingConstructor.isPresent()) {
                eval.executeConstructor(matchingConstructor.get().getCallableDeclaration());
                return new Variable(eval);
            }
            /*
             * No matching constructor found but in evals the default does not show up. So let's roll
             */
            return new Variable(eval);
        }

        return null;
    }

    protected MCEWrapper wrapCallExpression(NodeWithArguments<?> oce) throws ReflectiveOperationException {
        MCEWrapper mce = new MCEWrapper(oce);
        NodeList<Type> argTypes = new NodeList<>();
        Stack<Type> args = new Stack<>();
        mce.setArgumentTypes(argTypes);

        for (int i = oce.getArguments().size() - 1; i >= 0; i--) {
            /*
             * Push method arguments
             */
            Variable variable = evaluateExpression(oce.getArguments().get(i));
            args.push(variable.getType());
            AntikytheraRunTime.push(variable);
        }

        while(!args.isEmpty()) {
            argTypes.add(args.pop());
        }

        return mce;
    }

    private static void annonymousOverrides(ClassOrInterfaceType type, ObjectCreationExpr oce, ExpressionEvaluator eval) {
        TypeDeclaration<?> match;
        Optional<NodeList<BodyDeclaration<?>>> anonymousClassBody = oce.getAnonymousClassBody();
        if (anonymousClassBody.isPresent()) {
            /*
             * Merge the anon class stuff into the parent
             */
            CompilationUnit cu = eval.getCompilationUnit().clone();
            eval.setCompilationUnit(cu);
            match = AbstractCompiler.getMatchingType(cu, type.getNameAsString());
            for(BodyDeclaration<?> body : anonymousClassBody.get()) {
                if (body.isMethodDeclaration() && match != null) {
                    MethodDeclaration md = body.asMethodDeclaration();
                    MethodDeclaration replace = null;
                    for(MethodDeclaration method : match.findAll(MethodDeclaration.class)) {
                        if (method.getNameAsString().equals(md.getNameAsString())) {
                            replace = method;
                            break;
                        }
                    }
                    if (replace != null) {
                        replace.replace(md);
                    }
                    else {
                        match.addMember(md);
                    }
                }
            }
        }
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
            String resolvedClass = null;
            ImportWrapper importDeclaration = AbstractCompiler.findImport(cu, type.getNameAsString());
            if (importDeclaration != null) {
                resolvedClass = importDeclaration.getNameAsString();
            }


            Class<?> clazz;
            try {
                clazz = Class.forName(resolvedClass);
            } catch (ClassNotFoundException cnf) {
                clazz = AbstractCompiler.loadClass(resolvedClass);
            }

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

                        Constructor<?> cons = Reflect.findConstructor(c, paramTypes);
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
                ReflectionArguments reflectionArguments = Reflect.buildArguments(oce, this);

                Constructor<?> cons = Reflect.findConstructor(clazz, reflectionArguments.getParamTypes());
                if(cons !=  null) {
                    Object instance = cons.newInstance(reflectionArguments.getArgs());
                    return new Variable(type, instance);
                }
                else {
                    throw new EvaluatorException("Could not find a constructor for class " + clazz.getName());
                }
            }

        } catch (Exception e) {
            logger.warn("Could not create an instance of type {} using reflection", type);
            logger.warn("The error was {}", e.getMessage());

        }
        return null;
    }

    /**
     * Evaluate a method call.
     * There are two types of method calls, those that return values and those that do not.
     * The ones that return values will typically reach here through a flow such as initialize
     * variables.
     * Void method calls will typically reach here through the evaluate expression flow.
     *
     * Does so by executing all the code contained in that method where possible.
     *
     * @param methodCall the method call expression
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
    public Variable evaluateMethodCall(MethodCallExpr methodCall) throws ReflectiveOperationException {
        Optional<Expression> scoped = methodCall.getScope();
        if (scoped.isPresent() && scoped.get().toString().equals("logger")) {
            return null;
        }

        LinkedList<Expression> chain = ExpressionEvaluator.findScopeChain(methodCall);

        if (chain.isEmpty()) {
            return executeLocalMethod(methodCall);
        }

        Variable variable = evaluateScopeChain(chain);

        return evaluateMethodCall(variable, methodCall);
    }

    private Variable evaluateScopeChain(LinkedList<Expression> chain) throws ReflectiveOperationException {
        Variable variable = null;
        while(!chain.isEmpty()) {
            Expression expr2 = chain.pollLast();
            if (expr2.isNameExpr()) {
                variable = resolveExpression(expr2.asNameExpr());
            }
            else if(expr2.isFieldAccessExpr() && variable != null) {
                /*
                 * When we get here the getValue should have returned to us a valid field. That means
                 * we will have an evaluator instance as the 'value' in the variable v
                 */
                if (variable.getClazz().equals(System.class)) {
                    Field field = System.class.getField(expr2.asFieldAccessExpr().getNameAsString());
                    variable = new Variable(field.get(null));
                }
                else if (variable.getValue() instanceof Evaluator eval) {
                    variable = eval.getValue(expr2, expr2.asFieldAccessExpr().getNameAsString());
                }
                else {
                    if (variable.getValue() instanceof Evaluator eval) {
                        variable = eval.evaluateFieldAccessExpression(expr2.asFieldAccessExpr());
                    }
                    else {
                        variable = evaluateFieldAccessExpression(expr2.asFieldAccessExpr());
                    }
                }
            }
            else if(expr2.isMethodCallExpr()) {
                variable = evaluateMethodCall(variable, expr2.asMethodCallExpr());
            }
            else if (expr2.isLiteralExpr()) {
                variable = evaluateLiteral(expr2);
            }
            else if (expr2.isThisExpr()) {
                variable = new Variable(this);
            }
            else if (expr2.isTypeExpr()) {
                /*
                 * todo  : fix this hack.
                 *  currently only supports System related stuff.
                 */
                String s = expr2.toString();
                variable = new Variable(switch (s) {
                    case "System.out" -> System.out;
                    case "System.err" -> System.err;
                    case "System.in" -> System.in;
                    default -> throw new IllegalArgumentException("Unexpected value: " + s);
                });
            }
        }
        return variable;
    }

    private Variable resolveExpression(NameExpr expr) {
        if(expr.getNameAsString().equals("System")) {
            Variable variable = new Variable(System.class);
            variable.setClazz(System.class);
            return variable;
        }
        else {
            Variable v = getValue(expr, expr.asNameExpr().getNameAsString());
            if (v == null) {
                /*
                 * We know that we don't have a matching local variable or field. That indicates the
                 * presence of an import, a class from same package or this is part of java.lang package
                 * or a Static import
                 */
                String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(cu, expr.getNameAsString());
                Class<?> clazz = getClass(fullyQualifiedName);
                if (clazz != null) {
                    v = new Variable(clazz);
                    v.setClazz(clazz);
                }
                else {
                    ExpressionEvaluator eval = createEvaluator(fullyQualifiedName);
                    eval.setupFields(AntikytheraRunTime.getCompilationUnit(fullyQualifiedName));
                    v = new Variable(eval);
                }
            }

            return v;
        }
    }

    public Variable evaluateMethodCall(Variable v, MethodCallExpr methodCall) throws ReflectiveOperationException {
        if (v != null) {
            NodeList<Expression> arguments = methodCall.getArguments();
            if(arguments.isNonEmpty())
            {
                Expression argument = arguments.get(0);
                if(argument.isMethodReferenceExpr()) {
                    return evaluateMethodReference(v, arguments);
                }
                else if (argument.isLambdaExpr()) {
                    return evaluateLambda(v, arguments);
                }
            }
            if (v.getValue() instanceof ExpressionEvaluator eval) {
                MCEWrapper wrapper = wrapCallExpression(methodCall);
                return eval.executeMethod(wrapper);
            }

            ReflectionArguments reflectionArguments = Reflect.buildArguments(methodCall, this);
            return reflectiveMethodCall(v, reflectionArguments);
        } else {
            MCEWrapper wrapper = wrapCallExpression(methodCall);
            return executeMethod(wrapper);
        }
    }

    private Variable evaluateMethodReference(Variable v, NodeList<Expression> arguments) throws ReflectiveOperationException {
        MethodReferenceExpr rfCall = arguments.get(0).asMethodReferenceExpr();
        LinkedList<Expression> chain = ExpressionEvaluator.findScopeChain(rfCall);

        if (chain.isEmpty()) {
            return null;
        }

        Variable variable = evaluateScopeChain(chain);
        if (v.getValue() instanceof Collection<?> c) {
            Method m = Reflect.findMethod(variable.getClazz(), rfCall.getIdentifier(), new Class[]{c.getClass()});
            if (m != null) {
                for(Object o : c) {
                    m.invoke(variable.getValue(), o);
                }
            }
        }
        returnValue = new Variable(null);
        return null;

    }

    private Variable evaluateLambda(Variable v, NodeList<Expression> arguments) throws ReflectiveOperationException {
        LambdaExpr lambda = arguments.get(0).asLambdaExpr();
        MethodDeclaration md = new MethodDeclaration();
        if(lambda.getBody().isBlockStmt()) {
            md.setBody(lambda.getBody().asBlockStmt());
        }
        else {
            BlockStmt blockStmt = new BlockStmt();
            blockStmt.addStatement(lambda.getBody());
            md.setBody(blockStmt);
        }

        md.addParameter(lambda.getParameter(0));

        if (v.getValue() instanceof Collection<?> c) {
            ExpressionEvaluator eval = createEvaluator("lambda");
            for (Object o : c) {
                AntikytheraRunTime.push(new Variable(o));
                eval.executeMethod(md);
            }
        }
        returnValue = new Variable(null);
        return null;
    }

   Variable reflectiveMethodCall(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
       Method method = findAccessibleMethod(v.getClazz(), reflectionArguments.getMethodName(), reflectionArguments.getParamTypes());
       if (method == null) {
           if (v.getValue() == null) {
               throw new EvaluatorException("Application NPE: " + reflectionArguments.getMethodName(), EvaluatorException.NPE);
           }
           throw new EvaluatorException("Error evaluating method call: " + reflectionArguments.getMethodName());
       }

       Object[] finalArgs = Reflect.buildObjects(reflectionArguments, method);

       try {
           returnValue = new Variable(method.invoke(v.getValue(), finalArgs));
           if (returnValue.getValue() == null && returnValue.getClazz() == null) {
               returnValue.setClazz(method.getReturnType());
           }

       } catch (IllegalAccessException e) {
           invokeinAccessibleMethod(v, reflectionArguments, method);
       }
       return returnValue;
   }


    private void invokeinAccessibleMethod(Variable v, ReflectionArguments reflectionArguments, Method method) throws ReflectiveOperationException {
        Object[] finalArgs = Reflect.buildObjects(reflectionArguments, method);
        try {
            method.setAccessible(true);

            returnValue = new Variable(method.invoke(v.getValue(), finalArgs));
            if (returnValue.getValue() == null && returnValue.getClazz() == null) {
                returnValue.setClazz(method.getReturnType());
            }
        } catch (InaccessibleObjectException ioe) {
            // If module access fails, try to find a public interface or superclass method
            Method publicMethod = findPublicMethod(v.getClazz(), reflectionArguments.getMethodName(), reflectionArguments.getParamTypes());
            if (publicMethod != null) {
                returnValue = new Variable(publicMethod.invoke(v.getValue(), finalArgs));
                if (returnValue.getValue() == null && returnValue.getClazz() == null) {
                    returnValue.setClazz(publicMethod.getReturnType());
                }
            }
        }
    }

    private Method findAccessibleMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
       Method method = Reflect.findMethod(clazz, methodName, paramTypes);
       if (method != null) return method;

       // Search interfaces
       for (Class<?> iface : clazz.getInterfaces()) {
           method = Reflect.findMethod(iface, methodName, paramTypes);
           if (method != null) return method;
       }

       // Search superclass if no interface method found
       Class<?> superclass = clazz.getSuperclass();
       return superclass != null ? findAccessibleMethod(superclass, methodName, paramTypes) : null;
   }

   private Method findPublicMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
       // Try public interfaces first
       for (Class<?> iface : clazz.getInterfaces()) {
           try {
               Method method = iface.getMethod(methodName, paramTypes);
               if (Modifier.isPublic(method.getModifiers())) {
                   return method;
               }
           } catch (NoSuchMethodException ignored) {}
       }

       // Try superclass hierarchy
       Class<?> superclass = clazz.getSuperclass();
       while (superclass != null) {
           try {
               Method method = superclass.getMethod(methodName, paramTypes);
               if (Modifier.isPublic(method.getModifiers())) {
                   return method;
               }
           } catch (NoSuchMethodException ignored) {}
           superclass = superclass.getSuperclass();
       }

       return null;
   }

    /**
     * Execute a method that has not been prefixed by a scope.
     * That means the method being called is a member of the current class or a parent of the current class.
     * @param methodCall the method call expression to be executed
     * @return a Variable containing the result of the method call
     * @throws AntikytheraException if there are parsing related errors
     * @throws ReflectiveOperationException if there are reflection related errors
     */
    Variable executeLocalMethod(MethodCallExpr methodCall) throws ReflectiveOperationException {
        returnFrom = null;
        Optional<ClassOrInterfaceDeclaration> cdecl = methodCall.findAncestor(ClassOrInterfaceDeclaration.class);
        if (cdecl.isPresent()) {
            /*
             * At this point we are searching for the method call in the current class. For example it
             * maybe a getter or setter that has been defined through lombok annotations.
             */
            MCEWrapper wrapper = wrapCallExpression(methodCall);
            Optional<Callable> mdecl = AbstractCompiler.findMethodDeclaration(wrapper, cdecl.get());

            if (mdecl.isPresent()) {
                return executeMethod(mdecl.get().getCallableDeclaration());
            }
            else {
                ClassOrInterfaceDeclaration c = cdecl.get();
                if (methodCall.getNameAsString().startsWith("get") && (
                        c.getAnnotationByName("Data").isPresent()
                                || c.getAnnotationByName("Getter").isPresent())) {
                    String field = ClassProcessor.classToInstanceName(
                            methodCall.getNameAsString().replace("get","")
                    );
                    return new Variable(getValue(methodCall, field).getValue());
                }
                if (methodCall.getNameAsString().startsWith("set") && (
                        c.getAnnotationByName("Data").isPresent()
                                || c.getAnnotationByName("Setter").isPresent())) {
                    String field = ClassProcessor.classToInstanceName(
                            methodCall.getNameAsString().replace("set","")
                    );
                    return new Variable(getValue(methodCall, field).getValue());
                }
                else if (methodCall.getScope().isPresent()){
                    /*
                     * At this point we switch to searching for the method call in other classes in the AUT
                     */
                    return executeSource(methodCall);
                }
            }
        }
        return null;
    }

    /**
     * Execute a method that is available only in source code format.
     * @param methodCall method call that is present in source code form
     * @return the result of the method call wrapped in a Variable
     * @throws AntikytheraException if there are parsing related errors
     * @throws ReflectiveOperationException if there are reflection related errors
     */
    Variable executeSource(MethodCallExpr methodCall) throws ReflectiveOperationException {

        TypeDeclaration<?> decl = AbstractCompiler.getMatchingType(cu,
                ClassProcessor.instanceToClassName(ClassProcessor.fullyQualifiedToShortName(className)));
        if (decl != null) {
            MCEWrapper wrapper = wrapCallExpression(methodCall);
            Optional<Callable> md = AbstractCompiler.findMethodDeclaration(wrapper, decl);
            if (md.isPresent() && md.get().isMethodDeclaration()) {
                return executeMethod(md.get().asMethodDeclaration());
            }
        }
        return null;
    }

    static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            logger.info("Could not find class {}", className);
        }
        return null;
    }

    Variable evaluateBinaryExpression(BinaryExpr.Operator operator,
                                      Expression leftExpression, Expression rightExpression) throws ReflectiveOperationException {
        Variable left = evaluateExpression(leftExpression);
        left.setInitializer(leftExpression);

        if(operator.equals(BinaryExpr.Operator.OR) && (boolean)left.getValue()) {
             return new Variable(Boolean.TRUE);
        }

        Variable right = evaluateExpression(rightExpression);
        right.setInitializer(rightExpression);

        switch (operator) {
            case EQUALS:
                return checkEquality(left, right);

            case AND:
                return new Variable((boolean) left.getValue() && (boolean) right.getValue());

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
                Variable v = checkEquality(left, right);
                if (v.getValue() == null || Boolean.parseBoolean(v.getValue().toString())) {
                    return new Variable(Boolean.FALSE);
                }
                return new Variable(Boolean.TRUE);

            case OR:
                if (  (left.getClazz().equals(Boolean.class) || left.getClazz().equals(boolean.class))
                        && (right.getClazz().equals(Boolean.class) || right.getClazz().equals(boolean.class))) {
                    return new Variable( (Boolean)left.getValue() || (Boolean)right.getValue());
                }
                return null;

            case PLUS:
            case MINUS:
            case MULTIPLY:
            case DIVIDE:
                return arithmeticOperation(left, right, operator);

            default:
                return null;
        }
    }

    /**
     * Check that the left and right variables are equals
     * @param left a Variable
     * @param right the other Variable
     * @return a Variable holding either Boolean.TRUE or Boolean.FALSE
     */
    protected Variable checkEquality(Variable left, Variable right) {
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
        return new Variable(left.getValue().equals(right.getValue()));
    }


    private static Number performOperation(Number left, Number right, BinaryExpr.Operator operator) {
        return switch (operator) {
            case PLUS -> left.doubleValue() + right.doubleValue();
            case MINUS -> left.doubleValue() - right.doubleValue();
            case DIVIDE -> left.doubleValue() / right.doubleValue();
            case MULTIPLY -> left.doubleValue() * right.doubleValue();
            default ->
                throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    /**
     * Simple arithmetic operations.
     * String can be added to anything but numbers are tricker.
     * @param left the left operand
     * @param right the right operand
     * @return the result of the add operation which may be arithmatic or string concatenation
     */
    static Variable arithmeticOperation(Variable left, Variable right, BinaryExpr.Operator operator) {
        if (left.getValue() instanceof String || right.getValue() instanceof String) {
            return new Variable(left.getValue().toString() + right.getValue().toString());
        }
        if (left.getValue() instanceof Number l && right.getValue() instanceof Number r) {
            Number result = performOperation(l, r, operator);

            if (l instanceof Double || r instanceof Double) {
                return new Variable(result.doubleValue());
            } else if (l instanceof Float || r instanceof Float) {
                return new Variable(result.floatValue());
            } else if (l instanceof Long || r instanceof Long) {
                return new Variable(result.longValue());
            } else if (l instanceof Integer || r instanceof Integer) {
                return new Variable(result.intValue());
            } else if (l instanceof Short || r instanceof Short) {
                return new Variable(result.shortValue());
            } else if (l instanceof Byte || r instanceof Byte) {
                return new Variable(result.byteValue());
            }
        }
        return null;
    }

    void identifyFieldDeclarations(VariableDeclarator variable) throws ReflectiveOperationException, IOException {
        if (AntikytheraRunTime.isMocked(variable.getType())) {
            String fqdn = AbstractCompiler.findFullyQualifiedTypeName(variable);
            Variable v = new Variable(new MockingEvaluator(fqdn));
            v.setType(variable.getType());
            fields.put(variable.getNameAsString(), v);
        }
        if (variable.getType().isClassOrInterfaceType()) {
            resolveNonPrimitiveFields(variable);
        }
        else {
            resolvePrimitiveFields(variable);
        }
    }

    private void resolveNonPrimitiveFields(VariableDeclarator variable) throws ReflectiveOperationException {
        ClassOrInterfaceType t = variable.getType().asClassOrInterfaceType();
        List<ImportWrapper> imports = AbstractCompiler.findImport(cu, t);
        if (imports.isEmpty()) {
            setupPrimitiveOrBoxedField(variable, t);
        }
        else {
            for (ImportWrapper imp : imports) {
                String resolvedClass = imp.getNameAsString();

                if (finches.get(resolvedClass) != null) {
                    Variable v = new Variable(t);
                    v.setValue(finches.get(resolvedClass));
                    fields.put(variable.getNameAsString(), v);
                } else if (resolvedClass != null && resolvedClass.startsWith("java")) {
                    setupPrimitiveOrBoxedField(variable, t);
                } else {
                    CompilationUnit compilationUnit = AntikytheraRunTime.getCompilationUnit(resolvedClass);
                    if (compilationUnit != null) {
                        resolveFieldRepresentedByCode(variable, resolvedClass);
                    } else {
                        logger.debug("Unsolved {}", resolvedClass);
                    }
                }
            }
        }
    }

    private void setupPrimitiveOrBoxedField(VariableDeclarator variable, Type t) throws ReflectiveOperationException {
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

                for (ImportDeclaration importDeclaration : cu.getImports()) {
                    Name importedName = importDeclaration.getName();
                    String[] parts = importedName.toString().split("\\.");

                    if (importedName.toString().equals(name)) {
                        ExpressionEvaluator eval = createEvaluator(importedName.toString());
                        v = eval.getFields().get(name);
                        break;
                    }
                    else if(parts.length > 1 && parts[parts.length - 1].equals(name)) {
                        int last = importedName.toString().lastIndexOf(".");
                        String cname = importedName.toString().substring(0, last);
                        CompilationUnit dep = AntikytheraRunTime.getCompilationUnit(cname);
                        ExpressionEvaluator eval = createEvaluator(cname);
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
            v.setType(t);
        }
        fields.put(variable.getNameAsString(), v);
    }

    /**
     * Try to identify the compilation unit that represents the given field
     * @param variable a variable declaration statement
     * @param resolvedClass the name of the class that the field is of
     * @return true if successfully resolved
     *
     * @throws AntikytheraException if something goes wrong
     * @throws ReflectiveOperationException if a reflective operation fails
     */
    boolean resolveFieldRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws ReflectiveOperationException {
        Optional<Expression> init = variable.getInitializer();
        if (init.isPresent()) {
            if(init.get().isObjectCreationExpr()) {
                Variable v = createObject(variable, variable, init.get().asObjectCreationExpr());
                v.setType(variable.getType());
                fields.put(variable.getNameAsString(), v);
            }
            else {
                ExpressionEvaluator eval = createEvaluator(resolvedClass);
                Variable v = new Variable(eval);
                v.setType(variable.getType());
                fields.put(variable.getNameAsString(), v);
            }
            return true;
        }
        return false;
    }

    private void resolvePrimitiveFields(VariableDeclarator variable) throws ReflectiveOperationException {
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

    public void visit(MethodDeclaration md) throws ReflectiveOperationException {
        executeMethod(md);
    }

    /**
     * Execute - or rather interpret the code within a constructor found in source code
     * @param md ConstructorDeclaration
     * @throws AntikytheraException if the evaluator fails
     * @throws ReflectiveOperationException when a reflection operation fails
     */
    public void executeConstructor(CallableDeclaration<?> md) throws ReflectiveOperationException {
        if (md instanceof ConstructorDeclaration cd) {
            List<Statement> statements = cd.getBody().getStatements();
            NodeList<Parameter> parameters = md.getParameters();

            returnValue = null;
            for (int i = parameters.size() - 1; i >= 0; i--) {
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
                } else {
                    setLocal(cd.getBody(), p.getNameAsString(), AntikytheraRunTime.pop());
                }
            }

            executeBlock(statements);

            if (!AntikytheraRunTime.isEmptyStack()) {
                AntikytheraRunTime.pop();

            }
        }
    }

    public void setupFields(CompilationUnit cu)  {
        cu.accept(new ControllerFieldVisitor(), null);
    }

    /**
     * Java parser visitor used to setup the fields in the class.
     *
     * When we initialize a class the fields also need to be initialized, so hwere we are
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
                    identifyFieldDeclarations(variable);
                } catch (UnsolvedSymbolException e) {
                    logger.debug("ignore {}", variable);
                } catch (IOException e) {
                    String action = Settings.getProperty("dependencies.on_error").toString();
                    if(action == null || action.equals("exit")) {
                        throw new GeneratorException("Exception while processing fields", e);
                    }

                    logger.error("Exception while processing fields\n\t\t{}", e.getMessage());

                } catch (AntikytheraException|ReflectiveOperationException e) {
                    throw new GeneratorException(e);
                }
            }
        }
    }


    public ExpressionEvaluator createEvaluator(String className) {
        return new Evaluator(className);
    }
}
