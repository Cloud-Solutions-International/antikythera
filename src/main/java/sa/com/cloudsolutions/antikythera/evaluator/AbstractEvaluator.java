package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AUTException;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

public abstract class AbstractEvaluator implements ExpressionEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(AbstractEvaluator.class);

    /**
     * The fully qualified name of the class for which we created this evaluator.
     */
    protected String className;

    /**
     * The compilation unit that is being processed by the expression engine
     */
    protected CompilationUnit cu;

    /**
     * Local variables.
     *
     * These are specific to a block statement. A block statement may also be an
     * entire method. The primary key will be the hashcode of the block statement.
     */
    protected final Map<Integer, Map<String, Variable>> locals ;


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


    public AbstractEvaluator(String className) {
        this.className = className;
        cu = AntikytheraRunTime.getCompilationUnit(className);
        locals = new HashMap<>();
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
             * A line of code that is an expression. The expression itself can fall into various different
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

        for(int i = 0; i < Array.getLength(arr) ; i++) {
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
        Variable vx;

        vx = createUsingEvaluator(type, oce, instructionPointer);

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


    public ExpressionEvaluator createEvaluator(String className) {
        throw new UnsupportedOperationException("To be implemented by sub classes");
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
            executeThenBlock(ifst);
        } else {
            executeElseBlock(ifst);
        }
        return v;
    }

    private void executeElseBlock(IfStmt ifst) throws Exception {
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

    private void executeThenBlock(IfStmt ifst) throws Exception {
        Statement then = ifst.getThenStmt();
        if (then.isBlockStmt()) {
            executeBlock(then.asBlockStmt().getStatements());
        }
        else {
            executeStatement(then);
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
                setLocal(md.getBody().get(), p.getNameAsString(), va);
                p.getAnnotationByName("RequestParam").ifPresent(a -> {
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
                });
            }
        }
        return missing.isEmpty();
    }


    /**
     * Sets a local variable
     * @param node An expression representing the code being currently executed. It will be used to identify the
     *             encapsulating block.
     * @param nameAsString the variable name.
     *                     If the variable is already available as a local it's value will be replaced.
     * @param v The value to be set for the variable.
     */
    public void setLocal(Node node, String nameAsString, Variable v) {
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

    public void reset() {
        locals.clear();
    }

    protected String getClassName() {
        return className;
    }

    @Override
    public String toString() {
        return hashCode() + " : " + getClassName();
    }

    @Override
    public CompilationUnit getCompilationUnit() {
        return cu;
    }

    @Override
    public void setCompilationUnit(CompilationUnit compilationUnit) {
        this.cu = compilationUnit;
    }
}
