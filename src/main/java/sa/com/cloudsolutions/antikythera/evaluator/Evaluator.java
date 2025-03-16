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
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.functional.FunctionalConverter;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static org.mockito.Mockito.withSettings;

/**
 * Expression evaluator engine.
 */
public class Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);
    /**
     * <p>Local variables.</p>
     *
     * <p>These are specific to a block statement. A block statement may also be an
     * entire method. The primary key will be the hashcode of the block statement.</p>
     */
    private final Map<Integer, Map<String, Variable>> locals ;

    /**
     * The fields that were encountered in the current class.
     */
    protected final Map<String, Variable> fields;

    /**
     * The fully qualified name of the class for which we created this evaluator.
     */
    private final String className;

    /**
     * The compilation unit that is being processed by the expression engine
     */
    protected CompilationUnit cu;

    /**
     * The most recent return value that was encountered.
     */
    protected Variable returnValue;

    /**
     * The parent block of the last executed return statement.
     */
    protected Node returnFrom;

    protected LinkedList<Boolean> loops = new LinkedList<>();

    protected final Deque<TryStmt> catching = new LinkedList<>();
    /**
     * The preconditions that need to be met before the test can be executed.
     */
    protected final Map<MethodDeclaration, Set<Expression>> preConditions = new HashMap<>();


    public Evaluator (String className) {
        this.className = className;
        cu = AntikytheraRunTime.getCompilationUnit(className);
        locals = new HashMap<>();
        fields = new HashMap<>();
        Finch.loadFinches();
    }

    /**
     * <p>Get the value for the given variable in the current scope.</p>
     *
     * The variable may have been defined as a local (which could be a variable defined in the current block
     * or an argument to the function, or in the enclosing block) or a field.
     *
     * @param n a node depicting the current statement. It will be used to identify the current block
     * @param name the name of the variable.
     * @return the value for the variable in the current scope
     */
    public Variable getValue(Node n, String name) {
        Variable value = getLocal(n, name);
        if (value == null && fields.get(name) != null) {
            return fields.get(name);
        }
        return value;
    }

    /**
     * <p>Evaluate an expression.</p>
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
        } else if (expr.isMethodReferenceExpr()) {
            return convertMethodReference(expr.asMethodReferenceExpr());
        }
        return null;
    }

    private Variable createLambdaExpression(LambdaExpr lambdaExpr) throws ReflectiveOperationException {
        FPEvaluator<?> eval = FPEvaluator.create(lambdaExpr, this);

        Variable v = new Variable(eval);
        v.setType(eval.getType());
        return v;
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

    Variable evaluateFieldAccessExpression(Expression expr) throws ReflectiveOperationException {
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
            Variable v = evaluateExpression(fae.getScope());
            if (v != null && v.getValue() instanceof  Evaluator eval) {
                return eval.getFields().get(fae.getNameAsString());
            }
            logger.warn("Could not resolve {} for field access", fae.getScope());
        }
        else {
            throw new AntikytheraException("THIS CODE IS A DELETION CANDIDATE");
            /*
            Variable v = getFields().get(fae.getScope().toString());
            if (v != null) {
                Object obj = v.getValue();
                Field field = obj.getClass().getDeclaredField(fae.getNameAsString());
                field.setAccessible(true);
                return new Variable(field.get(obj));
            }*/
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
            v = evaluateMethodCall(init.asMethodCallExpr());
        } else if (init.isObjectCreationExpr()) {
            v = createObject(init, decl, init.asObjectCreationExpr());
        } else {
            v = evaluateExpression(init);
        }

        if (v != null) {
            v.setType(decl.getType());
            setLocal(decl, decl.getNameAsString(), v);
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
     * @param oce The expression to be evaluated and assigned as the initial value
     * @return The object that's created will be in the value field of the Variable
     */
    Variable createObject(Node instructionPointer, VariableDeclarator decl, ObjectCreationExpr oce) throws ReflectiveOperationException {
        ClassOrInterfaceType type = oce.getType();
        Variable vx = createUsingEvaluator(type, oce, instructionPointer);

        if (vx == null) {
            vx = createUsingReflection(type, oce);
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
    private Variable createUsingEvaluator(ClassOrInterfaceType type, ObjectCreationExpr oce, Node context) throws ReflectiveOperationException {
        TypeDeclaration<?> match = AbstractCompiler.resolveTypeSafely(type, context);
        if (match != null) {
            Evaluator eval = createEvaluator(match.getFullyQualifiedName().get());
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

    private static void annonymousOverrides(ClassOrInterfaceType type, ObjectCreationExpr oce, Evaluator eval) {
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
                resolvedClass = importDeclaration.getImport().isAsterisk()
                        ? importDeclaration.getSimplified().getNameAsString()
                        : importDeclaration.getNameAsString();
            }

            Class<?> clazz = AbstractCompiler.loadClass(resolvedClass);
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
                ReflectionArguments reflectionArguments = Reflect.buildArguments(oce, this, null);

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
            if (hash == 0) {
                for(Map.Entry<Integer, Map<String, Variable>> entry : locals.entrySet()) {
                    v = entry.getValue().get(name);
                    if (v != null) {
                        return v;
                    }
                }
                break;
            }
            else {
                Map<String, Variable> localsVars = this.locals.get(hash);

                if (localsVars != null) {
                    v = localsVars.get(name);
                    if (v != null)
                        return v;
                }
                if (n instanceof MethodDeclaration) {
                    localsVars = this.locals.get(hash);
                    if (localsVars != null) {
                        v = localsVars.get(name);
                        return v;
                    }
                    break;
                }
                if (block == null) {
                    break;
                }
                n = block.getParentNode().orElse(null);
                if (n == null) {
                    break;
                }
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
        Variable old = getLocal(node, nameAsString);
        if (old != null) {
            old.setValue(v.getValue());
        }
        else {
            BlockStmt block = findBlockStatement(node);
            int hash = (block != null) ? block.hashCode() : 0;

            Map<String, Variable> localVars = this.locals.computeIfAbsent(hash, k -> new HashMap<>());
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
            if (currentNode instanceof MethodDeclaration md) {
                return md.getBody().orElse(null);
            }
            currentNode = currentNode.getParentNode().orElse(null);
        }
        return null; // No block statement found
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

        LinkedList<Expression> chain = Evaluator.findScopeChain(methodCall);

        if (chain.isEmpty()) {
            return executeLocalMethod(methodCall);
        }

        Variable variable = evaluateScopeChain(chain);

        return evaluateMethodCall(variable, methodCall);
    }

    public Variable evaluateScopeChain(LinkedList<Expression> chain) throws ReflectiveOperationException {
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
                if (variable.getClazz() != null && variable.getClazz().equals(System.class)) {
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
                String s = expr2.toString();
                variable = new Variable(findScopeType(s));
            }
        }
        return variable;
    }

    private Object findScopeType(String s) {
        return switch (s) {
            case "System.out" -> System.out;
            case "System.err" -> System.err;
            case "System.in" -> System.in;
            default -> {
                String fullQulifiedName = AbstractCompiler.findFullyQualifiedName(cu, s);
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullQulifiedName);
                if (cu != null) {
                    TypeDeclaration<?> typeDecl = AbstractCompiler.getMatchingType(cu, s);
                    yield createEvaluator(typeDecl.getFullyQualifiedName().get());
                } else {
                    yield null;
                }
            }
        };
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
                    Evaluator eval = createEvaluator(fullyQualifiedName);
                    eval.setupFields(AntikytheraRunTime.getCompilationUnit(fullyQualifiedName));
                    v = new Variable(eval);
                }
            }

            return v;
        }
    }

    public Variable evaluateMethodCall(Variable v, MethodCallExpr methodCall) throws ReflectiveOperationException {
        if (v != null) {
            if (v.getValue() instanceof Evaluator eval && eval.getCompilationUnit() != null) {
                MCEWrapper wrapper = wrapCallExpression(methodCall);
                return eval.executeMethod(wrapper);
            }
            ReflectionArguments reflectionArguments = Reflect.buildArguments(methodCall, this, v);
            return reflectiveMethodCall(v, reflectionArguments);
        } else {
            MCEWrapper wrapper = wrapCallExpression(methodCall);
            return executeMethod(wrapper);
        }
    }

    Variable convertMethodReference(MethodReferenceExpr expr) throws ReflectiveOperationException {
        LambdaExpr lambda = FunctionalConverter.convertToLambda(expr);
        return createLambdaExpression(lambda);
    }

    Variable reflectiveMethodCall(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
        Method method = Reflect.findAccessibleMethod(v.getClazz(), reflectionArguments);
        validateReflectiveMethod(v, reflectionArguments, method);
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

    private static void validateReflectiveMethod(Variable v, ReflectionArguments reflectionArguments, Method method) {
        if (method == null) {
            if (v.getValue() == null) {
                throw new EvaluatorException("Application NPE: " + reflectionArguments.getMethodName(), EvaluatorException.NPE);
            }
            throw new EvaluatorException("Error evaluating method call: " + reflectionArguments.getMethodName());
        }
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
            Method publicMethod = Reflect.findPublicMethod(v.getClazz(), reflectionArguments.getMethodName(), reflectionArguments.getParamTypes());
            if (publicMethod != null) {
                returnValue = new Variable(publicMethod.invoke(v.getValue(), finalArgs));
                if (returnValue.getValue() == null && returnValue.getClazz() == null) {
                    returnValue.setClazz(publicMethod.getReturnType());
                }
            }
        }
    }

    /**
     * Execute a method call.
     * @param wrapper the method call expression wrapped so that the argument types are available
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
    public Variable executeMethod(MCEWrapper wrapper) throws ReflectiveOperationException {
        returnFrom = null;

        Optional<Callable> n = AbstractCompiler.findCallableDeclaration(wrapper, cu.getType(0).asClassOrInterfaceDeclaration());
        if (n.isPresent() && n.get().isMethodDeclaration()) {
            Variable v = executeMethod(n.get().asMethodDeclaration());
            if (v != null && v.getValue() == null) {
                v.setType(n.get().asMethodDeclaration().getType());
            }
            return v;
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
    public Variable executeLocalMethod(MethodCallExpr methodCall) throws ReflectiveOperationException {
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
                return executeViaDataAnnotation(cdecl.get(), methodCall);
            }
        }
        return null;
    }

    Variable executeViaDataAnnotation(ClassOrInterfaceDeclaration c, MethodCallExpr methodCall) throws ReflectiveOperationException {
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
            Expression arg = methodCall.getArguments().get(0);
            fields.put(field, evaluateExpression(arg));
            return new Variable(getValue(methodCall, field).getValue());
        }
        else if (methodCall.getScope().isPresent()){
            /*
             * At this point we switch to searching for the method call in other classes in the AUT
             */
            return executeSource(methodCall);
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

        return BinaryOps.binaryOps(operator, leftExpression, rightExpression, left, right);
    }

    void identifyFieldDeclarations(VariableDeclarator variable) throws ReflectiveOperationException, IOException {
        if (AntikytheraRunTime.isMocked(variable.getType())) {
            String fqdn = AbstractCompiler.findFullyQualifiedTypeName(variable);
            Variable v;
            if (AntikytheraRunTime.getCompilationUnit(fqdn) != null) {
                v = new Variable(new MockingEvaluator(fqdn));
            }
            else {
                v = useMockito(fqdn);
            }
            v.setType(variable.getType());
            fields.put(variable.getNameAsString(), v);
        }
        else {
            if (variable.getType().isClassOrInterfaceType()) {
                resolveNonPrimitiveFields(variable);
            } else {
                resolvePrimitiveFields(variable);
            }
        }
    }

    private static Variable useMockito(String fqdn) throws ClassNotFoundException {
        Variable v;
        Class<?> cls = AbstractCompiler.loadClass(fqdn);
        v = new Variable(Mockito.mock(cls, withSettings().defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT)));
        v.setClazz(cls);
        return v;
    }

    private static class MockReturnValueHandler implements Answer<Object> {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Class<?> returnType = invocation.getMethod().getReturnType();
            String clsName = returnType.getName();
            if (AntikytheraRunTime.getCompilationUnit(clsName) != null) {
                return new Evaluator(clsName);
            }
            else {
                Object obj = Reflect.getDefault(returnType);
                if (obj == null) {
                    Class<?> cls = AbstractCompiler.loadClass(clsName);
                    return Mockito.mock(cls, withSettings().defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT));
                }
                return obj;
            }
        }
    }

    void resolveNonPrimitiveFields(VariableDeclarator variable) throws ReflectiveOperationException {
        ClassOrInterfaceType t = variable.getType().asClassOrInterfaceType();
        List<ImportWrapper> imports = AbstractCompiler.findImport(cu, t);
        if (imports.isEmpty()) {
            setupPrimitiveOrBoxedField(variable, t);
        }
        else {
            for (ImportWrapper imp : imports) {
                String resolvedClass = imp.getNameAsString();
                Object f = Finch.getFinch(resolvedClass);
                if (f != null) {
                    Variable v = new Variable(t);
                    v.setValue(f);
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
                        Evaluator eval = createEvaluator(importedName.toString());
                        v = eval.getFields().get(name);
                        break;
                    }
                    else if(parts.length > 1 && parts[parts.length - 1].equals(name)) {
                        int last = importedName.toString().lastIndexOf(".");
                        String cname = importedName.toString().substring(0, last);
                        CompilationUnit dep = AntikytheraRunTime.getCompilationUnit(cname);
                        Evaluator eval = createEvaluator(cname);
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
                Evaluator eval = createEvaluator(resolvedClass);
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
            v = new Variable(variable.getType(), Reflect.getDefault(variable.getType().toString()));
        }
        v.setPrimitive(true);
        fields.put(variable.getNameAsString(), v);
    }

    public Map<String, Variable> getFields() {
        return fields;
    }

    public void visit(MethodDeclaration md) throws ReflectiveOperationException {
        executeMethod(md);
    }

    /**
     * Execute a method represented by the CallableDeclaration
     * @param cd a callable declaration
     * @return the result of the method execution. If the method is void, this will be null
     * @throws AntikytheraException if the method cannot be executed as source
     * @throws ReflectiveOperationException if various reflective operations associated with the
     *      method execution fails
     */
    public Variable executeMethod(CallableDeclaration<?> cd) throws ReflectiveOperationException {
        if (cd instanceof MethodDeclaration md) {

            returnFrom = null;
            returnValue = null;

            List<Statement> statements = md.getBody().orElseThrow().getStatements();
            setupParameters(md);

            executeBlock(statements);

            return returnValue;
        }
        return null;
    }

    protected boolean setupParameters(MethodDeclaration md) {
        NodeList<Parameter> parameters = md.getParameters();
        ArrayList<Boolean> missing = new ArrayList<>();
        for(int i = parameters.size() - 1 ; i >= 0 ; i--) {
            Parameter p = parameters.get(i);
            /*
             * Our implementation differs from a standard Expression Evaluation engine in that we do not
             * throw an exception if the stack is empty.
             *
             * The primary purpose of this is to generate tests. Those tests are sometimes generated for
             * very complex classes. We are not trying to achieve 100% efficiency. If we can get close and
             * allow the developer to make a few manual edits that's more than enough.
             */
            if (AntikytheraRunTime.isEmptyStack()) {
                logger.warn("Stack is empty");
                missing.add(true);
            }
            else {
                Variable va = AntikytheraRunTime.pop();
                if (md.getBody().isPresent()) {
                    // repository methods for example don't have bodies
                    setLocal(md.getBody().get(), p.getNameAsString(), va);
                    p.getAnnotationByName("RequestParam").ifPresent(ann -> setupRequestParam(ann, va, missing));
                }
            }
        }
        return missing.isEmpty();
    }

    private static void setupRequestParam(AnnotationExpr a , Variable va, ArrayList<Boolean> missing) {
        if (a.isNormalAnnotationExpr()) {
            NormalAnnotationExpr ne = a.asNormalAnnotationExpr();
            for (MemberValuePair pair : ne.getPairs()) {
                if (pair.getNameAsString().equals("required") && pair.getValue().toString().equals("false")) {
                    return;
                }
            }
        }
        if (va == null) {
            missing.add(true);
        }
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

    /**
     * Execute a block of statements.
     * @param statements the collection of statements that make up the block
     * @throws AntikytheraException if there are situations where we cannot process the block
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    protected void executeBlock(List<Statement> statements) throws ReflectiveOperationException {
        try {
            for (Statement stmt : statements) {
                if(loops.isEmpty() || loops.peekLast().equals(Boolean.TRUE)) {
                    executeStatement(stmt);
                    if (returnFrom != null) {
                        break;
                    }
                }
            }
        } catch (EvaluatorException|ReflectiveOperationException ex) {
            throw ex;
        } catch (Exception e) {
            handleApplicationException(e);
        }
    }

    /**
     * Execute a statement.
     * In the java parser architecture a statement is not always a single line of code. They can be
     * block statements as well. For example when an IF condition is encountered that counts as
     * statement. It's child elements the then and else blocks are also block statements.
     *
     * @param stmt the statement to execute
     * @throws Exception if the execution fails.
     */
    void executeStatement(Statement stmt) throws Exception {
        if (stmt.isExpressionStmt()) {
            /*
             * A line of code that is an expression. The expression itself can fall into various different
             * categories, and we let the evaluateExpression method take care of all that
             */
            evaluateExpression(stmt.asExpressionStmt().getExpression());

        } else if (stmt.isIfStmt()) {
            /*
             * If then Else are all treated together
             */
            ifThenElseBlock(stmt.asIfStmt());

        } else if (stmt.isTryStmt()) {
            /*
             * Try takes a bit of trying
             */
            catching.addLast(stmt.asTryStmt());
            executeBlock(stmt.asTryStmt().getTryBlock().getStatements());
        } else if (stmt.isThrowStmt()) {
            /*
             * Throw is tricky because we need to distinguish between what exceptions were raised by
             * issues in Antikythera and what are exceptions that are part of the application
             */
            executeThrow(stmt);
        } else if (stmt.isReturnStmt()) {
            /*
             * When returning we need to know if a value has been returned.
             */
            returnValue = executeReturnStatement(stmt);

        } else if (stmt.isForStmt()) {
            /*
             * Traditional for loop
             */
            executeForLoop(stmt.asForStmt());
        } else if (stmt.isForEachStmt()) {
            /*
             * Python style for each
             */
            executeForEach(stmt);
        } else if (stmt.isDoStmt()) {
            /*
             * It may not be used all that much but we still have to support do while.
             */
            executeDoWhile(stmt.asDoStmt());

        } else if(stmt.isSwitchStmt()) {
            SwitchStmt switchExpr = stmt.asSwitchStmt();
            System.out.println("switch missing");
        } else if(stmt.isWhileStmt()) {
            /*
             * Old fashioned while statement
             */
            executeWhile(stmt.asWhileStmt());

        } else if (stmt.isBlockStmt()) {
            /*
             * in C like languages it's possible to have a block that is not directly
             * associated with a condtional, loop or method etc.
             */
            executeBlock(stmt.asBlockStmt().getStatements());
        } else if (stmt.isBreakStmt()) {
            /*
             * Breaking means signalling that the loop has to be ended for that we keep a stack
             * in with a flag for all the loops that are in our trace
             */
            loops.pollLast();
            loops.addLast(Boolean.FALSE);
        } else {
            logger.info("Unhandled statement: {}", stmt);
        }
    }

    private void executeForEach(Statement stmt) throws ReflectiveOperationException {
        loops.addLast(true);
        ForEachStmt forEachStmt = stmt.asForEachStmt();
        Variable iter = evaluateExpression(forEachStmt.getIterable());
        Object arr = iter.getValue();
        evaluateExpression(forEachStmt.getVariable());

        for(int i = 0 ; i < Array.getLength(arr) ; i++) {
            Object value = Array.get(arr, i);
            for(VariableDeclarator vdecl : forEachStmt.getVariable().getVariables()) {
                Variable v = getLocal(forEachStmt, vdecl.getNameAsString());
                v.setValue(value);
            }

            executeBlock(forEachStmt.getBody().asBlockStmt().getStatements());
        }

        loops.pollLast();
    }

    private void executeThrow(Statement stmt) throws Exception {
        ThrowStmt t = stmt.asThrowStmt();
        if (t.getExpression().isObjectCreationExpr()) {
            ObjectCreationExpr oce = t.getExpression().asObjectCreationExpr();
            Variable v = createObject(stmt, null, oce);
            if (v.getValue() instanceof Exception ex) {
                throw ex;
            } else {
                logger.error("Should have an exception");
            }
        }
    }

    private void executeForLoop(ForStmt forStmt) throws ReflectiveOperationException {
        loops.addLast(true);

        for (Node n : forStmt.getInitialization()) {
            if (n instanceof VariableDeclarationExpr vdecl) {
                evaluateExpression(vdecl);
            }
        }
        while ((boolean) evaluateExpression(forStmt.getCompare().orElseThrow()).getValue() &&
                Boolean.TRUE.equals(loops.peekLast())) {
            executeBlock(forStmt.getBody().asBlockStmt().getStatements());
            for (Node n : forStmt.getUpdate()) {
                if(n instanceof Expression e) {
                    evaluateExpression(e);
                }
            }
        }
        loops.pollLast();
    }

    private void executeDoWhile(DoStmt whileStmt) throws ReflectiveOperationException {
        loops.push(true);
        do {
            executeBlock(whileStmt.getBody().asBlockStmt().getStatements());
        } while((boolean)evaluateExpression(whileStmt.getCondition()).getValue() && Boolean.TRUE.equals(loops.peekLast()));
        loops.pollLast();
    }

    /**
     * Execute a while loop.
     * @param whileStmt the while block to execute
     * @throws AntikytheraException if there is an error in the execution
     * @throws ReflectiveOperationException if the classes cannot be instantiated as needed with reflection
     */
    private void executeWhile(WhileStmt whileStmt) throws ReflectiveOperationException {
        loops.push(true);
        while((boolean)evaluateExpression(whileStmt.getCondition()).getValue() && Boolean.TRUE.equals(loops.peekLast())) {
            executeBlock(whileStmt.getBody().asBlockStmt().getStatements());
        }
        loops.pollLast();
    }

    /**
     * Execute a statement that represents an If - Then or If - Then - Else
     * @param ifst If / Then statement
     * @throws Exception
     */
    Variable ifThenElseBlock(IfStmt ifst) throws Exception {

        Variable v = evaluateExpression(ifst.getCondition());
        if ((boolean) v.getValue()) {
            executeStatement(ifst.getThenStmt());
        } else {
            Optional<Statement> elseBlock = ifst.getElseStmt();
            if(elseBlock.isPresent()) {
                executeStatement(elseBlock.get());
            }
        }
        return v;
    }

    protected void handleApplicationException(Exception e) throws ReflectiveOperationException {
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

    Variable executeReturnStatement(Statement stmt) throws ReflectiveOperationException {
        Optional<Expression> expr = stmt.asReturnStmt().getExpression();
        if(expr.isPresent()) {
            returnValue = evaluateExpression(expr.get());
        }
        else {
            returnValue = null;
        }
        returnFrom = stmt.getParentNode().orElse(null);
        return returnValue;
    }

    public void setupFields(CompilationUnit cu)  {
        cu.accept(new ControllerFieldVisitor(), null);
    }

    protected String getClassName() {
        return className;
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

    public void reset() {
        locals.clear();
    }

    public Evaluator createEvaluator(String className) {
        return new Evaluator(className);
    }


    @Override
    public String toString() {
        return getClass().getName() + " : " + getClassName();
    }

    public CompilationUnit getCompilationUnit() {
        return cu;
    }

    public void setCompilationUnit(CompilationUnit compilationUnit) {
        this.cu = compilationUnit;
    }

    /**
     * People have a nasty habit of chaining a sequence of method calls.
     *
     * If you are a D3.js programmer, this is probably the only way you do things. Even
     * Byte Buddy seems to behave the same. But at the end of the day how so you handle this?
     * You need to place them in a stack and pop them off one by one!
     *
     * @param expr
     * @return
     */
    public static LinkedList<Expression> findScopeChain(Expression expr) {
        LinkedList<Expression> chain = new LinkedList<>();
        while (true) {
            if (expr.isMethodCallExpr()) {
                MethodCallExpr mce = expr.asMethodCallExpr();
                Optional<Expression> scopeD = mce.getScope();
                if (scopeD.isEmpty()) {
                    break;
                }
                chain.addLast(scopeD.get());
                expr = scopeD.get();
            }
            else if (expr.isFieldAccessExpr()) {
                FieldAccessExpr mce = expr.asFieldAccessExpr();
                chain.addLast(mce.getScope());
                expr = mce.getScope();
            }
            else if (expr.isMethodReferenceExpr()) {
                MethodReferenceExpr mexpr = expr.asMethodReferenceExpr();
                chain.addLast(mexpr.getScope());
                expr = mexpr.getScope();
            }
            else {
                break;
            }
        }
        return chain;
    }

}
