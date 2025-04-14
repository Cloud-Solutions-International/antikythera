package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
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
import org.mockito.quality.Strictness;

import sa.com.cloudsolutions.antikythera.evaluator.functional.FPEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.functional.FunctionEvaluator;
import sa.com.cloudsolutions.antikythera.evaluator.functional.SupplierEvaluator;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

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
    protected String className;

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

    protected Deque<TryStmt> catching = new LinkedList<>();

    /**
     * The preconditions that need to be met before the test can be executed.
     */
    protected Map<MethodDeclaration, Set<Expression>> preConditions = new HashMap<>();

    /**
     * The preconditions that we are building based on the current branches covered.
     * These will be copied to the preConditions map
     */
    protected List<Expression> preconditionsInProgress = new ArrayList<>();
    protected String variableName;

    protected Evaluator() {
        locals = new HashMap<>();
        fields = new HashMap<>();
    }

    protected Evaluator(EvaluatorFactory.Context context) {
        this();
        this.className = context.getClassName();
        cu = AntikytheraRunTime.getCompilationUnit(className);
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
            return evaluateBinaryExpression(expr.asBinaryExpr());
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
        } else if (expr.isClassExpr()) {
            return evaluateClassExpression(expr);
        } else if (expr.isLambdaExpr()) {
            return FPEvaluator.create(expr.asLambdaExpr(), this);
        }
        return null;
    }

    private Variable evaluateClassExpression(Expression expr) throws ClassNotFoundException {
        ClassExpr classExpr = expr.asClassExpr();
        String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(cu, classExpr.getType().asString());

        if (fullyQualifiedName != null) {
            try {
                // Try to load the class as a compiled binary
                Class<?> loadedClass = AbstractCompiler.loadClass(fullyQualifiedName);
                return new Variable(loadedClass);
            } catch (ClassNotFoundException e) {
                // Class not found as binary, check if available as source
                CompilationUnit sourceCU = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);
                if (sourceCU != null) {
                    Evaluator evaluator = EvaluatorFactory.createLazily(fullyQualifiedName, Evaluator.class);
                    Class<?> dynamicClass = DTOBuddy.createDynamicClass(new MethodInterceptor(evaluator));

                    Variable v = new Variable(dynamicClass);
                    v.setClazz(Class.class);
                    return v;
                }
            }
        }
        return null;
    }

    private Variable evaluateBinaryExpression(BinaryExpr binaryExpr) throws ReflectiveOperationException {
        Expression left = binaryExpr.getLeft();
        Expression right = binaryExpr.getRight();

        return evaluateBinaryExpression(binaryExpr.getOperator(), left, right);
    }

    private Variable evaluateConditionalExpression(ConditionalExpr conditionalExpr) throws ReflectiveOperationException {
        Variable v = evaluateBinaryExpression(conditionalExpr.getCondition().asBinaryExpr());
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
            return v;
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
            return v;
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

    @SuppressWarnings("java:S3011")
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
                    Variable v = evaluateFieldAccessExpression(fae, dep);
                    if (v != null) {
                        return v;
                    }
                }
            }
            Variable v = evaluateExpression(fae.getScope());
            if (v != null && v.getValue() instanceof  Evaluator eval) {
                return eval.getFields().get(fae.getNameAsString());
            }
            logger.warn("Could not resolve {} for field access", fae.getScope());
        }

        return null;
    }

    private Variable evaluateFieldAccessExpression(FieldAccessExpr fae, CompilationUnit dep)  {
        Optional<TypeDeclaration<?>> typeDeclaration = AbstractCompiler.getMatchingType(dep, fae.getScope().toString());
        if (typeDeclaration.isPresent()) {
            Optional<FieldDeclaration> fieldDeclaration = typeDeclaration.get().getFieldByName(fae.getNameAsString());

            if (fieldDeclaration.isPresent()) {
                FieldDeclaration field = fieldDeclaration.get();
                for (var variable : field.getVariables()) {
                    if (variable.getNameAsString().equals(fae.getNameAsString())) {
                        if (field.isStatic()) {
                            return AntikytheraRunTime.getStaticVariable(
                                    getClassName() + "." + fae.getScope().toString(), variable.getNameAsString());
                        }
                        Variable v = new Variable(field.getVariable(0).getType().asString());
                        variable.getInitializer().ifPresent(f -> v.setValue(f.toString()));
                        return v;
                    }
                }
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

    @SuppressWarnings("java:S3011")
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
            if (obj instanceof Evaluator eval) {
                eval.getFields().put(fae.getNameAsString(), v);
            } else {
                try {
                    Field field = obj.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(obj, v.getValue());
                } catch (ReflectiveOperationException | NullPointerException e) {
                    /*
                     * This is not something that was created with class.forName or byte buddy.
                     */
                    this.fields.put(fieldName, v);
                }
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
        } else if (init.isLambdaExpr()) {
            v = FPEvaluator.create(init.asLambdaExpr(), this);
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
        }
        if (decl != null) {
            setLocal(instructionPointer, decl.getNameAsString(), vx);
        }

        return vx;
    }

    /**
     * Create a new object as an evaluator instance.
     * @param type the class or interface type that we need to create an instance of
     * @param oce the object creation expression.
     */
    private Variable createUsingEvaluator(ClassOrInterfaceType type, ObjectCreationExpr oce, Node context) throws ReflectiveOperationException {
        TypeDeclaration<?> match = AbstractCompiler.resolveTypeSafely(type, context).orElse(null);
        if (match != null) {
            Evaluator eval = EvaluatorFactory.create(match.getFullyQualifiedName().get(), this);
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
        Deque<Type> args = new ArrayDeque<>();
        mce.setArgumentTypes(argTypes);

        for (int i = oce.getArguments().size() - 1; i >= 0; i--) {
            /*
             * Push method arguments
             */
            Expression expr = oce.getArguments().get(i);
            if (expr.isLambdaExpr()) {
                Variable variable = FPEvaluator.create(expr.asLambdaExpr(), this);
                args.push(variable.getType());
                AntikytheraRunTime.push(variable);
            }
            else {
                Variable variable = evaluateExpression(expr);
                args.push(variable.getType());
                AntikytheraRunTime.push(variable);
            }
        }

        while(!args.isEmpty()) {
            argTypes.add(args.pop());
        }

        return mce;
    }

    private static void annonymousOverrides(ClassOrInterfaceType type, ObjectCreationExpr oce, Evaluator eval) {

        Optional<NodeList<BodyDeclaration<?>>> anonymousClassBody = oce.getAnonymousClassBody();
        if (anonymousClassBody.isPresent()) {
            /*
             * Merge the anon class stuff into the parent
             */
            CompilationUnit cu = eval.getCompilationUnit().clone();
            eval.setCompilationUnit(cu);
            AbstractCompiler.getMatchingType(cu, type.getNameAsString()).ifPresent(match ->
                injectMethods(match, anonymousClassBody.get())
            );
        }
    }

    private static void injectMethods(TypeDeclaration<?> match, NodeList<BodyDeclaration<?>> anonymousClassBody) {
        for(BodyDeclaration<?> body : anonymousClassBody) {
            if (body.isMethodDeclaration()) {
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
            String resolvedClass = AbstractCompiler.findFullyQualifiedName(cu, type.getNameAsString());

            Class<?> clazz = AbstractCompiler.loadClass(resolvedClass);
            ReflectionArguments reflectionArguments = Reflect.buildArguments(oce, this, null);

            Constructor<?> cons = Reflect.findConstructor(clazz, reflectionArguments.getArgumentTypes(),
                    reflectionArguments.getArguments());
            if(cons !=  null) {
                Object instance = cons.newInstance(reflectionArguments.getArguments());
                return new Variable(type, instance);
            }
            else {
                throw new EvaluatorException("Could not find a constructor for class " + clazz.getName());
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
     * @param node the node representing the current expression.
     *             It's primary purpose is to help identify the current block
     * @param name the name of the variable to look up
     * @return the Variable if it's found or null.
     */
    public Variable getLocal(Node node, String name) {
        Node n = node;

        while (n != null) {
            BlockStmt block = AbstractCompiler.findBlockStatement(n);
            int hash = (block != null) ? block.hashCode() : 0;
            if (hash == 0) {
                for(Map<String, Variable> entry : locals.values()) {
                    Variable v = entry.get(name);
                    if (v != null) {
                        return v;
                    }
                }
                break;
            }
            else {
                Map<String, Variable> localsVars = this.locals.get(hash);

                if (localsVars != null) {
                    Variable v = localsVars.get(name);
                    if (v != null)
                        return v;
                }
                if (n instanceof MethodDeclaration) {
                    localsVars = this.locals.get(hash);
                    return localsVars == null ? null : localsVars.get(name);
                }

                n = block.getParentNode().orElse(null);
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
            BlockStmt block = AbstractCompiler.findBlockStatement(node);
            int hash = (block != null) ? block.hashCode() : 0;

            Map<String, Variable> localVars = this.locals.computeIfAbsent(hash, k -> new HashMap<>());
            localVars.put(nameAsString, v);
        }
    }

    /**
     * <p>Evaluate a method call.</p>
     *
     * <p>There are two types of method calls, those that return values and those that do not.
     * The ones that return values will typically reach here through a flow such as initialize
     * variables. Void method calls will typically reach this method through the evaluate
     * expression flow.</p>
     *
     * Evaluates a method call by finding the method declaration and executing all the code
     * contained in that method where possible.
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

        ScopeChain chain = ScopeChain.findScopeChain(methodCall);

        if (chain.isEmpty()) {
            MCEWrapper wrapper = wrapCallExpression(methodCall);
            return executeLocalMethod(wrapper);
        }

        return evaluateScopedMethodCall(chain);
    }

    private Variable evaluateScopedMethodCall(ScopeChain chain) throws ReflectiveOperationException {
        MethodCallExpr methodCall = chain.getExpression().asMethodCallExpr();
        Variable variable = evaluateScopeChain(chain);
        if (variable.getValue() instanceof Optional<?> optional && optional.isEmpty()) {
            Variable o = handleOptionalEmpties(chain);
            if (o != null) {
                return o;
            }
        }
        ScopeChain.Scope scope = chain.getChain().getLast();
        scope.setScopedMethodCall(methodCall);
        scope.setVariable(variable);
        return evaluateMethodCall(scope);
    }

    Variable handleOptionals(ScopeChain.Scope scope) throws ReflectiveOperationException {
        MethodDeclaration md = scope.getMCEWrapper().getMatchingCallable().asMethodDeclaration();
        Variable v = executeMethod(md);
        if (v != null && v.getValue() == null) {
            v.setType(md.getType());
        }
        return v;
    }

    Variable handleOptionalEmpties(ScopeChain chain) throws ReflectiveOperationException {
        MethodCallExpr methodCall = chain.getExpression().asMethodCallExpr();
        String methodName = methodCall.getNameAsString();

        if (methodName.equals("orElseThrow")) {
            Optional<Expression> args = methodCall.getArguments().getFirst();

            if (args.isEmpty()) {
                /*
                 * Simulation of throwing a no such element exception when the optional
                 * is empty.
                 */
                throw new NoSuchElementException();
            }
            Expression expr = args.get();
            if (expr.isMethodCallExpr()) {
                return evaluateMethodCall(expr.asMethodCallExpr());
            }
            else if (expr.isLambdaExpr()) {
                Variable e = FPEvaluator.create(expr.asLambdaExpr(), this);
                return executeLambda(e);
            }
        }

        return null;
    }

    protected static Variable executeLambda(Variable e) {
        if (e.getValue() instanceof SupplierEvaluator<?> supplier) {
            Object result =  supplier.get();
            if (result instanceof RuntimeException exception) {
                throw exception;
            }
            return new Variable(result);
        } else if (e.getValue() instanceof FunctionEvaluator<?, ?> function) {
            return new Variable(function.apply(null));
        }
        return null;
    }

    public Variable evaluateScopeChain(ScopeChain chain) throws ReflectiveOperationException {
        Variable variable = null;
        for (ScopeChain.Scope scope : chain.getChain().reversed()) {
            Expression expr2 = scope.getExpression();
            if (expr2.isNameExpr()) {
                variable = resolveExpression(expr2.asNameExpr());
            }
            else if(expr2.isFieldAccessExpr() && variable != null) {
                /*
                 * getValue should have returned to us a valid field. That means
                 * we will have an evaluator instance as the 'value' in the variable v
                 */
                variable = evaluateScopedFieldAccess(variable, expr2);
            }
            else if(expr2.isMethodCallExpr()) {
                scope.setVariable(variable);
                scope.setScopedMethodCall(expr2.asMethodCallExpr());
                variable = evaluateMethodCall(scope);
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

    private Variable evaluateScopedFieldAccess(Variable variable, Expression expr2) throws ReflectiveOperationException {
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
        return variable;
    }

    @SuppressWarnings("java:S106")
    private Object findScopeType(String s) {
        return switch (s) {
            case "System.out" -> System.out;
            case "System.err" -> System.err;
            case "System.in" -> System.in;
            default -> {
                String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(cu, s);
                CompilationUnit targetCu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);
                if (targetCu != null) {
                    Optional<TypeDeclaration<?>> typeDecl = AbstractCompiler.getMatchingType(targetCu, s);
                    if (typeDecl.isPresent()) {
                        yield EvaluatorFactory.create(typeDecl.get().getFullyQualifiedName().orElse(null), this);
                    }
                }
                yield null;
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
                    Evaluator eval = EvaluatorFactory.create(fullyQualifiedName, this);
                    eval.setupFields();
                    v = new Variable(eval);
                }
            }

            return v;
        }
    }

    public Variable evaluateMethodCall(ScopeChain.Scope scope) throws ReflectiveOperationException {
        Variable v = scope.getVariable();
        MethodCallExpr methodCall = scope.getScopedMethodCall();
        if (v != null) {
            Object value = v.getValue();
            if (value instanceof Evaluator eval && eval.getCompilationUnit() != null) {
                MCEWrapper wrapper = wrapCallExpression(scope.getScopedMethodCall());
                scope.setMCEWrapper(wrapper);
                return eval.executeMethod(scope);
            }

            ReflectionArguments reflectionArguments = Reflect.buildArguments(methodCall, this, v);
            return reflectiveMethodCall(v, reflectionArguments);
        } else {
            MCEWrapper wrapper = wrapCallExpression(scope.getScopedMethodCall());
            scope.setMCEWrapper(wrapper);
            return executeMethod(scope);
        }
    }

    Variable reflectiveMethodCall(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
        Method method = Reflect.findAccessibleMethod(v.getClazz(), reflectionArguments);
        validateReflectiveMethod(v, reflectionArguments, method);
        reflectionArguments.setMethod(method);
        reflectionArguments.finalizeArguments();
        invokeReflectively(v, reflectionArguments);
        return returnValue;
    }

    private void invokeReflectively(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
        Method method = reflectionArguments.getMethod();
        Object[] finalArgs = reflectionArguments.getFinalArgs();
        try {

            returnValue = new Variable(method.invoke(v.getValue(), finalArgs));
            if (returnValue.getValue() == null && returnValue.getClazz() == null) {
                returnValue.setClazz(method.getReturnType());
            }

        } catch (IllegalAccessException e) {
            invokeinAccessibleMethod(v, reflectionArguments);
        }
    }

    private static void validateReflectiveMethod(Variable v, ReflectionArguments reflectionArguments, Method method) {
        if (method == null) {
            if (v.getValue() == null) {
                throw new EvaluatorException("Application NPE: " + reflectionArguments.getMethodName(), EvaluatorException.NPE);
            }
            throw new EvaluatorException("Error evaluating method call: " + reflectionArguments.getMethodName());
        }
    }

    @SuppressWarnings("java:S3011")
    private void invokeinAccessibleMethod(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException {
        Method method = reflectionArguments.getMethod();
        Object[] finalArgs = reflectionArguments.getFinalArgs();
        try {
            method.setAccessible(true);

            returnValue = new Variable(method.invoke(v.getValue(), finalArgs));
            if (returnValue.getValue() == null && returnValue.getClazz() == null) {
                returnValue.setClazz(method.getReturnType());
            }
        } catch (InaccessibleObjectException ioe) {
            // If module access fails, try to find a public interface or superclass method
            Method publicMethod = Reflect.findPublicMethod(v.getClazz(), reflectionArguments.getMethodName(), reflectionArguments.getArgumentTypes());
            if (publicMethod != null) {
                returnValue = new Variable(publicMethod.invoke(v.getValue(), finalArgs));
                if (returnValue.getValue() == null && returnValue.getClazz() == null) {
                    returnValue.setClazz(publicMethod.getReturnType());
                }
            }
        }
    }

    /**
     * Execute a method that is part of a chain of method
     * @param sc the methods scope
     * @return the result from executing the method or null if the method is void.
     * @throws ReflectiveOperationException if the execution involves a class available only
     *      in byte code format and an exception occurs in reflecting.
     */
    public Variable executeMethod(ScopeChain.Scope sc) throws ReflectiveOperationException {
        returnFrom = null;
        Optional<TypeDeclaration<?>> cdecl = AbstractCompiler.getMatchingType(cu, getClassName());
        MCEWrapper mceWrapper = sc.getMCEWrapper();
        Optional<Callable> n = AbstractCompiler.findCallableDeclaration(mceWrapper, cdecl.orElseThrow().asClassOrInterfaceDeclaration());
        if (n.isPresent()) {
            Callable callable = n.get();
            mceWrapper.setMatchingCallable(callable);
            if (callable.isMethodDeclaration()) {
                MethodDeclaration methodDeclaration = callable.asMethodDeclaration();
                Type returnType = methodDeclaration.getType();

                if (returnType.asString().startsWith("Optional") ||
                        returnType.asString().startsWith("java.util.Optional")) {
                    return handleOptionals(sc);
                }
                else {
                    Variable v = executeMethod(methodDeclaration);
                    if (v != null && v.getValue() == null) {

                        v.setType(returnType);
                    }
                    return v;
                }
            }
            else {
               return executeMethod(callable.getMethod());
            }
        }

        return null;
    }

    @SuppressWarnings("java:S1172")
    Variable executeMethod(Method m) {
        logger.error("NOt implemented yet");
        throw new AntikytheraException("Not yet implemented"); // but see MockingEvaluator
    }

    /**
     * Execute a method that has not been prefixed by a scope.
     * That means the method being called is a member of the current class or a parent of the current class.
     * @param methodCallWrapper the method call expression to be executed
     * @return a Variable containing the result of the method call
     * @throws AntikytheraException if there are parsing related errors
     * @throws ReflectiveOperationException if there are reflection related errors
     */
    public Variable executeLocalMethod(MCEWrapper methodCallWrapper) throws ReflectiveOperationException {
        returnFrom = null;
        NodeWithArguments<?> call = methodCallWrapper.getMethodCallExpr();
        if (call instanceof MethodCallExpr methodCall) {
            Optional<ClassOrInterfaceDeclaration> cdecl = methodCall.findAncestor(ClassOrInterfaceDeclaration.class);
            if (cdecl.isEmpty()) {
                Optional<TypeDeclaration<?>> t = AbstractCompiler.getMatchingType(cu, getClassName());
                if (t.isPresent() && t.get().isClassOrInterfaceDeclaration()) {
                    cdecl = Optional.of(t.get().asClassOrInterfaceDeclaration());
                }
            }
            if (cdecl.isPresent()) {
                /*
                 * At this point we are searching for the method call in the current class. For example,
                 * it maybe a getter or setter that has been defined through lombok annotations.
                 */

                Optional<Callable> mdecl = AbstractCompiler.findMethodDeclaration(methodCallWrapper, cdecl.get());

                if (mdecl.isPresent()) {
                    return executeMethod(mdecl.get().getCallableDeclaration());
                } else {
                    return executeViaDataAnnotation(cdecl.get(), methodCall);
                }
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
                ClassProcessor.instanceToClassName(ClassProcessor.fullyQualifiedToShortName(className))).orElse(null);
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
            return AbstractCompiler.loadClass(className);
        } catch (ClassNotFoundException e) {
            logger.info("Could not find class {}", className);
        }
        return null;
    }

    Variable evaluateBinaryExpression(BinaryExpr.Operator operator,
                                      Expression leftExpression, Expression rightExpression) throws ReflectiveOperationException {
        Variable left = evaluateExpression(leftExpression);
        if(operator.equals(BinaryExpr.Operator.OR) && (boolean)left.getValue()) {
             return new Variable(Boolean.TRUE);
        }

        Variable right = evaluateExpression(rightExpression);
        return BinaryOps.binaryOps(operator, leftExpression, rightExpression, left, right);
    }

    @SuppressWarnings({"java:S3776", "java:S1130"})
    Variable identifyFieldDeclarations(VariableDeclarator variable) throws ReflectiveOperationException, IOException {
        if (AntikytheraRunTime.isMocked(AbstractCompiler.findFullyQualifiedTypeName(variable))) {
            String fqn = AbstractCompiler.findFullyQualifiedTypeName(variable);
            Variable v;
            if (AntikytheraRunTime.getCompilationUnit(fqn) != null) {
                Evaluator eval = EvaluatorFactory.createLazily(fqn, MockingEvaluator.class);
                eval.setVariableName(variable.getNameAsString());
                v = new Variable(eval);
            }
            else {
                v = useMockito(fqn);
            }
            v.setType(variable.getType());
            return v;
        }
        else {
            if (variable.getType().isClassOrInterfaceType()) {
                return resolveNonPrimitiveFields(variable);
            } else {
                return resolvePrimitiveFields(variable);
            }
        }
    }

    void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    private static Variable useMockito(String fqdn) throws ClassNotFoundException {
        Variable v;
        Class<?> cls = AbstractCompiler.loadClass(fqdn);
        v = new Variable(Mockito.mock(cls, withSettings().defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT)));
        v.setClazz(cls);
        return v;
    }

    public Map<Integer, Map<String, Variable>> getLocals() {
        return locals;
    }

    protected void invokeDefaultConstructor() {
        String[] parts = className.split("\\.");
        String shortName = parts[parts.length - 1];

        for(ConstructorDeclaration decl : cu.findAll(ConstructorDeclaration.class)) {

            if (decl.getParameters().isEmpty()) {
                decl.findAncestor(TypeDeclaration.class).ifPresent(t -> {
                    if(shortName.equals(t.getNameAsString())) {
                        try {
                            executeConstructor(decl);
                        } catch (ReflectiveOperationException e) {
                            throw new AntikytheraException(e);
                        }
                    }
                });
            }
        }
    }

    Variable resolveNonPrimitiveFields(VariableDeclarator variable) throws ReflectiveOperationException {
        ClassOrInterfaceType t = variable.getType().asClassOrInterfaceType();
        List<ImportWrapper> imports = AbstractCompiler.findImport(cu, t);
        if (imports.isEmpty()) {
            String fqn = AbstractCompiler.findFullyQualifiedName(cu, t.getNameAsString());
            if (fqn != null) {
                return resolveNonPrimitiveField(fqn, variable, t);
            }
            return setupPrimitiveOrBoxedField(variable, t);
        }
        else {
            for (ImportWrapper imp : imports) {
                String resolvedClass = imp.getNameAsString();
                Variable v = resolveNonPrimitiveField(resolvedClass, variable, t);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    Variable resolveNonPrimitiveField(String resolvedClass, VariableDeclarator variable, ClassOrInterfaceType t) throws ReflectiveOperationException {
        Object f = Finch.getFinch(resolvedClass);
        if (f != null) {
            Variable v = new Variable(t);
            v.setValue(f);
            return v;
        } else if (resolvedClass != null) {
            CompilationUnit compilationUnit = AntikytheraRunTime.getCompilationUnit(resolvedClass);
            if (compilationUnit != null) {
                return resolveFieldRepresentedByCode(variable, resolvedClass);
            }
            else {
                return setupPrimitiveOrBoxedField(variable, t);
            }
        }
        return null;
    }

    private Variable setupPrimitiveOrBoxedField(VariableDeclarator variable, Type t) throws ReflectiveOperationException {
        Variable v;
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
                        Evaluator eval = EvaluatorFactory.create(importedName.toString(), this);
                        v = eval.getFields().get(name);
                        break;
                    }
                    else if(parts.length > 1 && parts[parts.length - 1].equals(name)) {
                        /* todo : change this to use abstractcompiler methods */
                        int last = importedName.toString().lastIndexOf(".");
                        String cname = importedName.toString().substring(0, last);
                        Evaluator eval = EvaluatorFactory.create(cname, this);

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
        return v;
    }

    /**
     * Try to identify the compilation unit that represents the given field
     * @param variable a variable declaration statement
     * @param resolvedClass the name of the class that the field is of
     * @return The variable or null
     *
     * @throws AntikytheraException if something goes wrong
     * @throws ReflectiveOperationException if a reflective operation fails
     */
    Variable resolveFieldRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws ReflectiveOperationException {
        Optional<Expression> init = variable.getInitializer();
        if (init.isPresent()) {
            if(init.get().isObjectCreationExpr()) {
                Variable v = createObject(variable, variable, init.get().asObjectCreationExpr());
                v.setType(variable.getType());
                return v;
            }
            else {
                Evaluator eval = EvaluatorFactory.create(resolvedClass, this);
                Variable v = new Variable(eval);
                v.setType(variable.getType());
                return v;

            }
        }
        return null;
    }

    private Variable resolvePrimitiveFields(VariableDeclarator variable) throws ReflectiveOperationException {
        Variable v;
        Optional<Expression> init = variable.getInitializer();
        if(init.isPresent()) {
            v = evaluateExpression(init.get());
            v.setType(variable.getType());
        }
        else {
            v = new Variable(variable.getType(), Reflect.getDefault(variable.getType().toString()));
        }
        return v;
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

    protected void setupParameters(MethodDeclaration md) throws ReflectiveOperationException {
        NodeList<Parameter> parameters = md.getParameters();

        for(int i = parameters.size() - 1 ; i >= 0 ; i--) {
            setupParameter(md, parameters.get(i));
        }
    }

    /**
     * Copies a parameter from the stack into the local variable space of the method.
     *
     * @param md the method declaration into whose variable space this parameter will be copied
     * @param p the parameter in question.
     * @throws ReflectiveOperationException is not really thrown here but the sub classes might.
     */
    @SuppressWarnings("java:S1130")
    void setupParameter(MethodDeclaration md, Parameter p) throws ReflectiveOperationException {
        Variable va = AntikytheraRunTime.pop();
        md.getBody().ifPresent(body ->
            setLocal(body, p.getNameAsString(), va)
        );
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
                setLocal(cd.getBody(), p.getNameAsString(), AntikytheraRunTime.pop());
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
            executeSwitchStatement(stmt.asSwitchStmt());

        } else if(stmt.isWhileStmt()) {
            /*
             * Old-fashioned while statement
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

    private void executeSwitchStatement(SwitchStmt switchStmt) throws Exception {
        boolean matchFound = false;
        Statement defaultStmt = null;

        for (var entry : switchStmt.getEntries()) {
            NodeList<Expression> labels = entry.getLabels();
            for (Expression label : labels) {
                if(label.isIntegerLiteralExpr()) {
                    BinaryExpr bin = new BinaryExpr(switchStmt.getSelector(), label.asIntegerLiteralExpr(), BinaryExpr.Operator.EQUALS);
                    Variable v = evaluateExpression(bin);
                    if ((boolean) v.getValue()) {
                        executeBlock(entry.getStatements());
                        matchFound = true;
                        break;
                    }
                }
            }
            if (labels.isEmpty()) {
                defaultStmt = entry.getStatements().get(0);
            }
        }

        if (!matchFound && defaultStmt != null) {
            executeStatement(defaultStmt);
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

    @SuppressWarnings("java:S112")
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

    public void setupFields()  {
        cu.accept(new LazyFieldVisitor(), null);
    }

    public void initializeFields() {
        cu.accept(new FieldVisitor(), null);
    }

    protected String getClassName() {
        return className;
    }

    /**
     * <p>Java parser visitor used to set up the fields in the class.</p>
     *
     * When we initialize a class the fields also need to be initialized, so here we are
     */
    private class LazyFieldVisitor extends VoidVisitorAdapter<Void> {
        /**
         * The field visitor will be used to identify the fields that are being used in the class
         *
         * @param field the field to inspect
         * @param arg   not used
         */
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            for (var variable : field.getVariables()) {
                field.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(cdecl -> {
                    if (cdecl.getFullyQualifiedName().isPresent() && cdecl.getFullyQualifiedName().get().equals(className)) {
                        setupField(field, variable);
                    }
                });
            }
        }
    }

    private class FieldVisitor extends VoidVisitorAdapter<Void> {
        @SuppressWarnings("unchecked")
        @Override
        public void visit(InitializerDeclaration init, Void arg) {
            super.visit(init, arg);
            init.findAncestor(ClassOrInterfaceDeclaration.class)
                    .flatMap(ClassOrInterfaceDeclaration::getFullyQualifiedName)
                    .ifPresent(name -> {
                        if (name.equals(getClassName())) {
                            try {
                                executeBlock(init.getBody().getStatements());
                            } catch (ReflectiveOperationException e) {
                                throw new AntikytheraException(e);
                            }
                        }
                    });
        }
    }

    void setupField(FieldDeclaration field, VariableDeclarator variableDeclarator) {
        try {
            if (field.isStatic()) {
                Variable s = AntikytheraRunTime.getStaticVariable(getClassName(), variableDeclarator.getNameAsString());
                if (s != null) {
                    fields.put(variableDeclarator.getNameAsString(), s);
                    return;
                }
            }
            Variable v = identifyFieldDeclarations(variableDeclarator);
            if (v != null) {
                fields.put(variableDeclarator.getNameAsString(), v);
                if (field.isStatic()) {
                    v.setStatic(true);
                    AntikytheraRunTime.setStaticVariable(getClassName(), variableDeclarator.getNameAsString(), v);
                }
            }
        } catch (UnsolvedSymbolException e) {
            logger.debug("ignore {}", variableDeclarator);
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

    public void reset() {
        locals.clear();
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
}
