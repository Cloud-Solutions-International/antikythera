package sa.com.cloudsolutions.antikythera.evaluator;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;

import com.github.javaparser.ast.stmt.WhileStmt;
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
import com.github.javaparser.resolution.types.ResolvedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.parser.ClassProcessor;

import java.io.IOException;
import java.lang.reflect.Array;
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

    /**
     * The fully qualified name of the class for which we created this evaluator.
     */
    private final String className;

    /**
     * The most recent return value that was encountered.
     */
    protected Variable returnValue;

    /**
     * The parent block of the last executed return statement.
     */
    protected Node returnFrom;

    protected LinkedList<Boolean> loops = new LinkedList<>();

    private Deque<TryStmt> catching = new LinkedList<>();

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
            return  evaluateUnaryExpression(expr);
        } else if (expr.isMethodCallExpr()) {

            /*
             * Method calls are the toughest nuts to crack. Some method calls will be from the Java api
             * or from other libraries. Or from classes that have not been compiled.
             */
            MethodCallExpr methodCall = expr.asMethodCallExpr();
            return evaluateMethodCall(methodCall);

        } else if (expr.isMethodReferenceExpr()) {
            return evaluateMethodReference(expr.asMethodReferenceExpr());
        } else if (expr.isAssignExpr()) {
            return evaluateAssignment(expr);
        } else if (expr.isObjectCreationExpr()) {
            return createObject(expr, null, expr);
        } else if(expr.isFieldAccessExpr()) {
            return evaluateFieldAccessExpression(expr);
        } else if(expr.isArrayInitializerExpr()) {

            /*
             * Array Initializersions are tricky
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
        }
        return null;
    }

    Variable evaluateMethodReference(MethodReferenceExpr expr) {
        return resolveExpression(expr.getScope().asTypeExpr());
    }

    /**
     * Create an array using reflection
     * @param arrayInitializerExpr the ArrayInitializerExpr which describes how the array will be build
     * @return a Variable which holds the generated array as a value
     * @throws ReflectiveOperationException
     * @throws AntikytheraException
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

    private Variable evaluateUnaryExpression(Expression expr) throws AntikytheraException, ReflectiveOperationException {
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

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
        if (cu != null) {
            ImportDeclaration imp = ClassProcessor.findImport(cu, fae.getScope().toString());
            if (imp != null) {
                CompilationUnit dep = AntikytheraRunTime.getCompilationUnit(imp.getNameAsString());
                if (dep == null) {
                    /*
                     * Use class loader
                     */
                    Class<?> clazz = Class.forName(imp.getNameAsString());
                    Field field = clazz.getDeclaredField(fae.getNameAsString());
                    field.setAccessible(true);
                    return new Variable(field.get(null));
                } else {
                    TypeDeclaration<?> typeDeclaration = AbstractCompiler.getMatchingClass(dep, fae.getScope().toString());
                    if (typeDeclaration != null) {
                        Optional<FieldDeclaration> fieldDeclaration = typeDeclaration.getFieldByName(fae.getNameAsString());

                        if (fieldDeclaration.isPresent()) {
                            FieldDeclaration field = fieldDeclaration.get();
                            Variable v = new Variable(field.getVariable(0).getType().asString());
                            v.setValue(field.getVariable(0).getInitializer().get().toString());
                            return v;
                        }
                    }
                }
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
     * @throws EvaluatorException if there is an error evaluating the expression
     */
    Variable evaluateVariableDeclaration(Expression expr) throws AntikytheraException, ReflectiveOperationException {
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

    private Variable initializeVariable(VariableDeclarator decl, Expression init) throws AntikytheraException, ReflectiveOperationException {
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
            v = createObject(init, decl, init);
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
        Variable vx;

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
        CompilationUnit cu = null;
        if (res.isPresent()) {
            ResolvedType resolved = type.resolve();
            String resolvedClass = resolved.describe();
            cu = AntikytheraRunTime.getCompilationUnit(resolvedClass);
        }
        else {
            ImportDeclaration importDeclaration = AbstractCompiler.findImport(
                    AntikytheraRunTime.getCompilationUnit(this.className), type.getNameAsString());
            if (importDeclaration != null) {
                cu = AntikytheraRunTime.getCompilationUnit(importDeclaration.getNameAsString());
            }
        }
        if (cu != null) {
            TypeDeclaration<?> match = AbstractCompiler.getMatchingClass(cu, type.getNameAsString());
            if (match != null) {
                Evaluator eval = createEvaluator(match.getFullyQualifiedName().get());

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
            String resolvedClass = null;
            if (res.isPresent()) {
                ResolvedType resolved = type.resolve();
                resolvedClass = resolved.describe();

                if (resolved.isReferenceType()) {
                    var typeDecl = resolved.asReferenceType().getTypeDeclaration();
                    if (typeDecl.isPresent() && typeDecl.get().getClassName().contains(".")) {
                        resolvedClass = resolvedClass.replaceFirst("\\.([^\\.]+)$", "\\$$1");
                    }
                }
            }
            else {
                ImportDeclaration importDeclaration = AbstractCompiler.findImport(
                    AntikytheraRunTime.getCompilationUnit(this.className), type.getNameAsString());
                if (importDeclaration != null) {
                    resolvedClass = importDeclaration.getNameAsString();
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
     *
     * Does so by executing all the code contained in that method where possible.
     *
     * @param methodCall the method call expression
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
    public Variable evaluateMethodCall(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        Optional<Expression> scoped = methodCall.getScope();
        if (scoped.isPresent() && scoped.get().toString().equals("logger")) {
            return null;
        }


        Variable variable = null;
        LinkedList<Expression> chain = findScopeChain(methodCall);

        while(!chain.isEmpty()) {
            Expression expr2 = chain.pollLast();
            if (expr2.isNameExpr()) {
                variable = resolveExpression(expr2.asNameExpr());
            }
            else if(expr2.isFieldAccessExpr()) {
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
                    variable = evaluateFieldAccessExpression(expr2.asFieldAccessExpr());

                }
            }
            else if(expr2.isMethodCallExpr()) {
                variable = evaluateMethodCall(variable, expr2.asMethodCallExpr());
            }
            else if (expr2.isLiteralExpr()) {
                variable = evaluateLiteral(expr2);
            }
        }

        variable = evaluateMethodCall(variable, methodCall);
        return variable;
    }

    protected LinkedList<Expression> findScopeChain(Expression expr) {
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
            else {
                break;
            }
        }
        return chain;
    }

    private Variable resolveExpression(TypeExpr expr) {
        Type t = expr.getType();
        return null;
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
                 * presence of an import, or this is part of java.lang package
                 */
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(this.className);
                ImportDeclaration imp = AbstractCompiler.findImport(cu, expr.getNameAsString());
                String fullyQualifiedName;
                if (imp == null) {
                    /*
                     * Guessing this to be java.lang
                     */
                    fullyQualifiedName = "java.lang." + expr.getNameAsString();
                }
                else {
                    fullyQualifiedName = imp.getNameAsString();
                }
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

    public Variable evaluateMethodCall(Variable v, MethodCallExpr methodCall) throws AntikytheraException {
        try {
            if (v != null) {
                ReflectionArguments reflectionArguments = Reflect.buildArguments(methodCall, this);

                if (v.getValue() instanceof Evaluator eval) {
                    for (int i = reflectionArguments.getArgs().length - 1; i >= 0; i--) {
                        /*
                         * Push method arguments
                         */
                        AntikytheraRunTime.push(new Variable(reflectionArguments.getArgs()[i]));
                    }
                    return eval.executeMethod(methodCall);
                }

                return reflectiveMethodCall(v, reflectionArguments);
            } else {
                return executeMethod(methodCall);
            }
        } catch (ReflectiveOperationException ex) {
            throw new EvaluatorException("Error evaluating method call", ex);
        }
    }

    Variable reflectiveMethodCall(Variable v, ReflectionArguments reflectionArguments) throws ReflectiveOperationException, EvaluatorException {
        Class<?> clazz = v.getClazz();
        String methodName = reflectionArguments.getMethodName();
        Class<?>[] paramTypes = reflectionArguments.getParamTypes();
        Object[] args = reflectionArguments.getArgs();
        Method method = Reflect.findMethod(clazz, methodName, paramTypes);
        if (method != null) {
            Object[] finalArgs = args;
            if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0].equals(Object[].class)) {
                finalArgs = new Object[]{args};
            }

            returnValue = new Variable(method.invoke(v.getValue(), finalArgs));
            if (returnValue.getValue() == null && returnValue.getClazz() == null) {
                returnValue.setClazz(method.getReturnType());
            }
            return returnValue;
        }

        throw new EvaluatorException("Error evaluating method call: " + methodName);
    }

    /**
     * Execute a method call.
     *
     * This method is called when we have a AUT method call that maybe a part of the current class.
     *
     * @param methodCall the method call expression
     * @return the result of executing that code.
     * @throws EvaluatorException if there is an error evaluating the method call or if the
     *          feature is not yet implemented.
     */
     Variable executeMethod(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        returnFrom = null;
        Optional<ClassOrInterfaceDeclaration> cdecl = methodCall.findAncestor(ClassOrInterfaceDeclaration.class);
        if (cdecl.isPresent()) {
            /*
             * At this point we are searching for the method call in the current class. For example it
             * maybe a getter or setter that has been defined through lombok annotations.
             */
            ClassOrInterfaceDeclaration c = cdecl.get();
            Optional<MethodDeclaration> mdecl = c.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(methodCall.getNameAsString()));
            if (mdecl.isPresent()) {
                return executeMethod(mdecl.get());
            }
            else {
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
        else {
            try {
                Optional<Node> n = methodCall.resolve().toAst();
                if (n.isPresent() && n.get() instanceof MethodDeclaration md) {
                    return executeMethod(md);
                }
            } catch (IllegalStateException ise) {
                return executeSource(methodCall);
            }
        }
        return null;
    }

     Variable executeSource(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(this.className);
        TypeDeclaration<?> decl = AbstractCompiler.getMatchingClass(cu,
                ClassProcessor.instanceToClassName(ClassProcessor.fullyQualifiedToShortName(className)));
        Optional<MethodDeclaration> md = decl.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals(methodCall.getNameAsString()));
        if (md.isPresent()) {
            return executeMethod(md.get());
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
                                      Expression leftExpression, Expression rightExpression) throws AntikytheraException, ReflectiveOperationException {
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
    boolean resolveFieldRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws AntikytheraException, ReflectiveOperationException {
        Optional<Expression> init = variable.getInitializer();
        if (init.isPresent()) {
            if(init.get().isObjectCreationExpr()) {
                Variable v = createObject(variable, variable, init.get());
                fields.put(variable.getNameAsString(), v);
            }
            else {
                Evaluator eval = createEvaluator(resolvedClass);
                Variable v = new Variable(eval);
                fields.put(variable.getNameAsString(), v);
            }
            return true;
        }
        return false;
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

    public void visit(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        executeMethod(md);
    }

    public Variable executeMethod(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        returnFrom = null;
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

    /**
     * Execute a block of statements.
     * @param statements the collection of statements that make up the block
     * @throws AntikytheraException if there are situations where we cannot process the block
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    protected void executeBlock(List<Statement> statements) throws AntikytheraException, ReflectiveOperationException {
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
            e.printStackTrace();
            handleApplicationException(e);
        }
    }

    /**
     * Execute a statment.
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
             * A line of code that is an expression. The expresion itself can fall into various different
             * categories and we let the evaluateExpression method take care of all that
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
            System.out.println("bada");
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

    private void executeForEach(Statement stmt) throws AntikytheraException, ReflectiveOperationException {
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

    private void executeForLoop(ForStmt forStmt) throws AntikytheraException, ReflectiveOperationException {
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

    private void executeDoWhile(DoStmt whileStmt) throws AntikytheraException, ReflectiveOperationException {
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
    private void executeWhile(WhileStmt whileStmt) throws AntikytheraException, ReflectiveOperationException {
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
        return v;
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

    Variable executeReturnStatement(Statement stmt) throws AntikytheraException, ReflectiveOperationException {
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
                    identifyFieldVariables(variable);
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
