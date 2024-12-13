package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.TruthTable;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.depsolver.ClassProcessor;
import sa.com.cloudsolutions.antikythera.generator.ControllerResponse;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.TestGenerator;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extends the basic evaluator to provide support for JPA repositories and their special behavior.
 */
public class SpringEvaluator extends Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(SpringEvaluator.class);

    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static final Map<String, RepositoryParser> repositories = new HashMap<>();

    /**
     * List of generators that we have.
     *
     * Generators ought to be seperated from the parsers/evaluators because different kinds of
     * tests can be created. They can be unit tests, integration tests, api tests and end to
     * end tests.
     */
    private final List<TestGenerator> generators  = new ArrayList<>();

    /**
     * The method currently being analyzed
     */
    private MethodDeclaration currentMethod;

    /**
     * The lines of code already looked at in the method.
     */
    private static HashMap<Integer, LineOfCode> lines = new HashMap<>();

    /**
     * Sort of a stack that keeps track of the conditional code blocks.
     * We consider IF THEN ELSE statements as branching statements. However, we also consider JPA
     * queries to be branching statements as well. There are two branches with the 'A' branch being
     * executing the query with the default parameters that were passed in and the 'B' branch will
     * be executing the query with parameters that will have a valid result set being returned
     *
     * Of course there will be situations where no parameters exist to give a valid resultset
     * and these will just be ignored.
     *
     * The values that are used in the A branch for the query will just be arbitrary non null values
     * integers like 0 will usually not have matching entries in the database and will result in
     * empty responses. If due to some reason there are valid resultsets of integers like 0 for
     * primary keys there's nothing we can do about it, we just move onto the next test.
     */
    private final Set<IfStmt> branching = new HashSet<>();

    private boolean onTest;

    private ArgumentGenerator argumentGenerator;
    /**
     * It is better to use create evaluator
     * @param className the name of the class associated with this evaluator
     */
    public SpringEvaluator(String className) {
        super(className);
    }

    public static Map<String, RepositoryParser> getRepositories() {
        return repositories;
    }

    /**
     * Called by the java parser method visitor.
     *
     * This is where the code evaluation begins. Note that we may run the same code repeatedly
     * so that we can excercise all the paths in the code. This is done by setting the values
     * of variables so that different branches in conditional statements are taken.
     *
     * @param md The MethodDeclaration being worked on
     * @throws AntikytheraException
     * @throws ReflectiveOperationException
     */
    @Override
    public void visit(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        branching.clear();
        md.getParentNode().ifPresent(p -> {
            if (p instanceof ClassOrInterfaceDeclaration cdecl && cdecl.isAnnotationPresent("RestController")) {
                currentMethod = md;
            }
        });

        NodeList<Statement> statements = md.getBody().get().getStatements();
        for (int i = 0; i < statements.size(); i++) {
            Statement st = statements.get(i);
            if (!lines.containsKey(st.hashCode())) {
                mockURIVariables(md);
                executeMethod(md);
                if (returnFrom != null) {
                    // rewind!

                    i = 0;
                }
            }
        }
    }

    @Override
    public Variable executeMethod(MethodDeclaration md) throws AntikytheraException, ReflectiveOperationException {
        returnFrom = null;
        returnValue = null;

        List<Statement> statements = md.getBody().orElseThrow().getStatements();
        if (setupParameters(md)) {
            executeBlock(statements);
        }
        else {
            ControllerResponse cr = new ControllerResponse();
            ResponseEntity<String> response = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            cr.setResponse(new Variable(response));
            return createTests(cr);
        }
        return returnValue;
    }

    /**
     * Execute a block of statements.
     *
     * We may end up executing the same block of statements repeatedly until all the branches
     * have been covered.
     *
     * @param statements the collection of statements that make up the block
     * @throws AntikytheraException if there are situations where we cannot process the block
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    protected void executeBlock(List<Statement> statements) throws AntikytheraException, ReflectiveOperationException {
        try {
            for (Statement stmt : statements) {
                executeStatement(stmt);
                if (returnFrom != null) {
                    break;
                }
            }
        } catch (EvaluatorException|ReflectiveOperationException ex) {
            throw ex;
        } catch (Exception e) {
            handleApplicationException(e);
        }
    }

    /**
     * Executes a single statement
     * OVerrides the superclass method to keep track of the lines of code that have been
     * executed. While the basic Evaluator class does not run the same code over and over again,
     * this class does!
     * @param stmt the statement to execute
     * @throws Exception
     */
    @Override
    void executeStatement(Statement stmt) throws Exception {
        if(!stmt.isIfStmt()) {
            boolean repo =  (stmt.isExpressionStmt() && isRepositoryMethod(stmt.asExpressionStmt()));

            LineOfCode l = lines.get(stmt.hashCode());
            if (l == null) {
                l = new LineOfCode(stmt);
                l.setColor(repo ? LineOfCode.GREY : LineOfCode.BLACK);
                lines.put(stmt.hashCode(), l);
            }
            else {
                l.setColor(LineOfCode.BLACK);
            }
        }

        super.executeStatement(stmt);
    }

    /**
     * The URL contains Path variables, Query string parameters and post bodies. We mock them here
     * @param md The method declaration representing an HTTP API end point
     * @throws ReflectiveOperationException if the variables cannot be mocked.
     */
    private void mockURIVariables(MethodDeclaration md) throws ReflectiveOperationException {
        for (int i = md.getParameters().size() - 1; i >= 0; i--) {
            var param = md.getParameter(i);
            argumentGenerator.generateArgument(param);
        }
    }


    /**
     * Execute a query on a repository.
     * @param name the name of the repository
     * @param methodCall the method call expression
     * @return the result set
     */
    private RepositoryQuery executeQuery(String name, MethodCallExpr methodCall) {
        RepositoryParser repository = repositories.get(name);
        if(repository != null) {
            MethodDeclaration repoMethod = repository.findMethodDeclaration(methodCall);
            RepositoryQuery q = repository.get(repoMethod);

            try {
                /*
                 * We have one more challenge; to find the parameters that are being used in the repository
                 * method. These will then have to be mapped to the jdbc placeholders and reverse mapped
                 * to the arguments that are passed in when the method is actually being called.
                 */
                String nameAsString = repoMethod.getNameAsString();
                if ( !(nameAsString.contains("save") || nameAsString.contains("delete") || nameAsString.contains("update"))) {
                    for (int i = 0, j = methodCall.getArguments().size(); i < j; i++) {
                        Expression argument = methodCall.getArgument(i);
                        q.getMethodArguments().add(new QueryMethodArgument(argument, i, evaluateExpression(argument)));
                    }

                    repository.executeQuery(repoMethod);
                    for(TestGenerator gen : generators) {
                        gen.setQuery(q);
                    }
                }
                else {
                    // todo do some fake work here
                }
            } catch (Exception e) {
                logger.warn(e.getMessage());
                logger.warn("Could not execute query {}", methodCall);
            }
            return q;
        }
        return null;
    }

    /**
     * Identify fields in the class.
     * This process needs to be carried out before executing any code.
     * @param field the field declaration
     * @throws IOException if the file cannot be read
     * @throws AntikytheraException if there is an error in the code
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    public void identifyFieldDeclarations(VariableDeclarator field) throws IOException, AntikytheraException, ReflectiveOperationException {
        super.identifyFieldDeclarations(field);

        if (field.getType().isClassOrInterfaceType()) {
            detectRepository(field);
        }
    }

    /**
     * Detect a JPA repository.
     * @param variable the variable declaration
     * @throws IOException if the file cannot be read
     */
    private static void detectRepository(VariableDeclarator variable) throws IOException {
        String shortName = variable.getType().asClassOrInterfaceType().getNameAsString();
        if (SpringEvaluator.getRepositories().containsKey(shortName)) {
            return;
        }
        Type t = variable.getType().asClassOrInterfaceType();
        String className = t.resolve().describe();

        if (!className.startsWith("java.")) {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(className);
            if (cu != null) {
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
                                    repositories.put(variable.getNameAsString(), parser);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Execute a return statement.
     * Over rides the super class method to create tests.
     *
     * @param statement the statement to execute
     * @return the variable that is returned
     * @throws AntikytheraException if there is an error in the code
     * @throws ReflectiveOperationException if a reflection operation fails
     */
    @Override
    Variable executeReturnStatement(Statement statement) throws AntikytheraException, ReflectiveOperationException {
        /*
         * Leg work is done in the overloaded method.
         */
        if (AntikytheraRunTime.isControllerClass(getClassName()) || onTest) {
            ReturnStmt stmt = statement.asReturnStmt();
            Optional<Node> parent = stmt.getParentNode();
            buildPreconditions();
            super.executeReturnStatement(stmt);
            if (parent.isPresent()) {
                // the return statement will have a parent no matter what but the optionals approach
                // requires the use of isPresent.
                return createTests((ControllerResponse) returnValue.getValue());
            }
        }
        else {
            return super.executeReturnStatement(statement);
        }

        return null;
    }

    /**
     * Build the list of expressions that will be the precondition for the test.
     * This is done by looking at the branching statements in the code. The list of expressions
     * become a part of the test setup.
     */
    private void buildPreconditions() {
        List<Expression> expressions = new ArrayList<>();
        for (LineOfCode l : lines.values()) {
            if(branching.contains(l.getStatement())) {
                expressions.addAll(l.getPrecondition(false));
            }
        }
        for(TestGenerator gen : generators) {
            gen.setPreconditions(expressions);
        }
    }

    /**
     * Finally create the tests by calling each of the test generators.
     * There maybe multiple test generators, one of unit tests, one of API tests aec.
     * @param response the response from the controller
     * @return a variable that encloses the response
     */
    private Variable createTests(ControllerResponse response) {
        if (response != null) {
            for (TestGenerator generator : generators) {
                generator.createTests(currentMethod, response);
            }
            return new Variable(response);
        }
        return null;
    }

    public void addGenerator(TestGenerator generator) {
        generators.add(generator);
    }

    /**
     * Resolves fields while taking into consideration the AutoWired annotation of spring.
     * When the field declaration is an interface, will try to find a suitable implementation.
     *
     * @param variable a variable declaration statement
     * @param resolvedClass the name of the class that the field is of
     * @return true if the resolution was successful
     * @throws AntikytheraException when the field cannot be resolved
     * @throws ReflectiveOperationException if a reflective operation goes wrong
     */
    @Override
    boolean resolveFieldRepresentedByCode(VariableDeclarator variable, String resolvedClass) throws AntikytheraException, ReflectiveOperationException {
        /*
         * Try to substitute an implementation for the interface.
         */
        String name = AbstractCompiler.findFullyQualifiedName(
                AntikytheraRunTime.getCompilationUnit(resolvedClass),
                variable.getType().asString());

        Set<String> implementations = AntikytheraRunTime.findImplementations(name);
        if (implementations != null) {
            for (String impl : implementations) {
                if (super.resolveFieldRepresentedByCode(variable, impl)) {
                    return true;
                }
                else {
                    if (autoWire(variable, impl)) return true;
                }
            }
        }

        if(super.resolveFieldRepresentedByCode(variable, resolvedClass)) {
            return true;
        }
        return autoWire(variable, resolvedClass);
    }

    private boolean autoWire(VariableDeclarator variable, String resolvedClass) {
        Optional<Node> parent = variable.getParentNode();
        if (parent.isPresent() && parent.get() instanceof FieldDeclaration fd
                && fd.getAnnotationByName("Autowired").isPresent()) {

            Variable v = AntikytheraRunTime.getAutoWire(resolvedClass);
            if (v == null) {
                Evaluator eval = new SpringEvaluator(resolvedClass);
                CompilationUnit dependant = AntikytheraRunTime.getCompilationUnit(resolvedClass);
                v = new Variable(eval);
                AntikytheraRunTime.autoWire(resolvedClass, v);
                eval.setupFields(dependant);
            }
            fields.put(variable.getNameAsString(), v);

            return true;
        }
        return false;
    }


    @Override
    public Variable evaluateMethodCall(Variable v, MethodCallExpr methodCall) throws EvaluatorException, ReflectiveOperationException {
        try {
            if(methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                if(repositories.get(scope.toString()) != null) {
                    return executeSource(methodCall);
                }
            }
            return super.evaluateMethodCall(v, methodCall);
        } catch (AntikytheraException aex) {
            if (aex instanceof EvaluatorException eex) {
                ControllerResponse controllerResponse = new ControllerResponse();
                if (eex.getError() != 0 && onTest) {
                    Variable r = new Variable(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
                    controllerResponse.setResponse(r);
                    createTests(controllerResponse);
                    returnFrom = methodCall;
                }
                else {
                    throw eex;
                }
            }
        }
        return null;
    }

    @Override
    public Evaluator createEvaluator(String name) {
        return new SpringEvaluator(name);
    }

    /**
     * Handle if then else statements.
     *
     * We use truth tables to analyze the condition and to set values on the DTOs accordingly.
     *
     * @param ifst If / Then statement
     * @throws Exception
     */
    @Override
    Variable ifThenElseBlock(IfStmt ifst) throws Exception {
        for(TestGenerator gen : generators) {
            gen.setBranched(true);
        }

        LineOfCode l = lines.get(ifst.hashCode());
        if (l == null) {
            /*
             * This if condition has never been executed before. First we will determine if the condition
             * evaluates to true or false. Then we will use the truth table to find out what values will
             * result in it going from true to false or false to true.
             */
            l = new LineOfCode(ifst);
            lines.put(ifst.hashCode(), l);
            l.setColor(LineOfCode.GREY);

            branching.add(ifst);
            Variable v = super.ifThenElseBlock(ifst);
            if ((Boolean)v.getValue()) {
                setupIfCondition(ifst, false);
            } else {
                setupIfCondition(ifst, true);
            }
            return v;
        }
        else if (l.getColor() == LineOfCode.GREY) {
            /*
             * We have been here before but only traversed the then part of the if statement.
             */
            for (Expression st : l.getPrecondition(false)) {
                evaluateExpression(st);
            }
            if (allVisited(ifst)) {
                l.setColor(LineOfCode.BLACK);
            }
            else {
                return super.ifThenElseBlock(ifst);
            }
        } else if (ifst.getElseStmt().isPresent()) {
            l = lines.get(ifst.getElseStmt().get());
            if (l == null || l.getColor() != LineOfCode.BLACK) {
                l.setColor(LineOfCode.GREY);
                return super.ifThenElseBlock(ifst);
            }
        } else {
            l.setColor(LineOfCode.BLACK);
        }
        return null;
    }

    /**
     * Setup an if condition so that it will evaluate to true or false in future executions.
     * @param ifst the if statement to mess with
     * @param state the desired state.
     */
    private void setupIfCondition(IfStmt ifst, boolean state)  {
        TruthTable tt = new TruthTable(ifst.getCondition());

        LineOfCode l = lines.get(ifst.hashCode());
        List<Map<Expression, Object>> values = tt.findValuesForCondition(state);

        if (!values.isEmpty()) {
            Map<Expression, Object> value = values.getFirst();
            for (var entry : value.entrySet()) {
                if(entry.getKey().isMethodCallExpr()) {

                    LinkedList<Expression> chain = findScopeChain(entry.getKey());
                    if (!chain.isEmpty()) {
                        Expression expr = chain.getFirst();
                        Variable v = getValue(ifst, expr.toString());
                        if (v == null && expr.isNameExpr()) {
                            /*
                             * This is likely to be a static method.
                             */
                            String fullname = AbstractCompiler.findFullyQualifiedName(cu, expr.asNameExpr().getNameAsString());
                            if(fullname != null) {
                                /*
                                 * The only other possibility is static access on a class
                                 */
                                try {
                                    Class<?> clazz = Class.forName(fullname);

                                } catch (ReflectiveOperationException e) {
                                    /*
                                     * Can probably be ignroed
                                     */
                                    logger.info("Could not create class for {}", fullname);
                                }
                            }
                        }

                        if (v != null && v.getValue() instanceof Evaluator eval) {
                            MethodCallExpr setter = new MethodCallExpr();
                            String name = entry.getKey().asMethodCallExpr().getNameAsString().substring(3);
                            setter.setName("set" + name);
                            setter.setScope(expr);
                            Variable field = eval.getFields().get(ClassProcessor.classToInstanceName(name));

                            setter.addArgument(
                                    switch (field.getType().asString()) {
                                        case "String" -> "\"Hello\"";
                                        case "int", "Integer" -> "0";
                                        case "long", "Long" -> "0L";
                                        case "float", "Float" -> "0.0f";
                                        case "double", "Double" -> "0.0";
                                        case "boolean", "Boolean" -> "false";
                                        default -> "null";
                                    }
                            );
                            l.addPrecondition(setter, state);
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if a collection of statements have been previously executed or not.
     * @param statements The collection of statements to check.
     * @return
     */
    private boolean checkStatements(List<Statement> statements) {
        for (Statement line : statements) {
            if (line.isIfStmt()) {
                if (!allVisited(line.asIfStmt())) {
                    return false;
                }
            } else if (!isLineVisited(line)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if all the code in the then branch as well as the else branch have been executed.
     * @param stmt
     * @return
     */
    public boolean allVisited(IfStmt stmt) {
        LineOfCode l = lines.get(stmt.hashCode());
        if (l == null) {
            return false;
        }
        if (l.getColor() == LineOfCode.BLACK) {
            return true;
        }
        Statement then = stmt.getThenStmt();
        if (then.isBlockStmt()) {
            if (!checkStatements(then.asBlockStmt().getStatements())) {
                return false;
            }
        } else {
            if (!isLineVisited(then)) {
                return false;
            }
        }
        if (stmt.getElseStmt().isPresent()) {
            Statement elseStmt = stmt.getElseStmt().get();
            if (elseStmt.isBlockStmt()) {
                if (!checkStatements(elseStmt.asBlockStmt().getStatements())) {
                    return false;
                }
            } else {
                if (!isLineVisited(elseStmt)) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Has thsi line of code being executed before
     * @param stmt statement representing a line of code (except that a multiline statments are not supported)
     * @return
     */
    private boolean isLineVisited(Statement stmt) {
        LineOfCode l = lines.get(stmt.hashCode());
        if (l == null) {
            return false;
        }
        return l.getColor() == LineOfCode.BLACK;
    }

    public void resetColors() {
        lines.clear();
    }

    /**
     * Execute a method that's only available to us in source code format.
     * @param methodCall the method call whose execution is to be simulated.
     * @return the result from the execution as a Variable instance
     * @throws AntikytheraException
     * @throws ReflectiveOperationException
     */
    @Override
    Variable executeSource(MethodCallExpr methodCall) throws AntikytheraException, ReflectiveOperationException {
        if (AntikytheraRunTime.isControllerClass(getClassName())) {
            for(TestGenerator gen : generators) {
                gen.setBranched(false);
            }
        }
        Expression expression = methodCall.getScope().orElseGet(null);
        if (expression != null && expression.isNameExpr()) {
            String fieldName = expression.asNameExpr().getNameAsString();
            RepositoryParser rp = repositories.get(fieldName);
            if (rp != null) {
                RepositoryQuery q = executeQuery(fieldName, methodCall);
                if (q != null) {
                    LineOfCode l = findExpressionStatement(methodCall);
                    if (l != null) {
                        ExpressionStmt stmt = l.getStatement().asExpressionStmt();
                        Variable v = (q.getCachedResult() == null)
                                ? processResult(stmt, q.getResultSet())
                                : q.getCachedResult();

                        if (l.getRepositoryQuery() == null) {
                            l.setRepositoryQuery(q);
                            l.setColor(LineOfCode.GREY);
                        }
                        else {
                            l.setColor(LineOfCode.BLACK);
                        }
                        return v;
                    }
                    return null;
                }
            }
        }
        return super.executeSource(methodCall);
    }

    private Variable processResult(ExpressionStmt stmt, ResultSet rs) throws AntikytheraException, ReflectiveOperationException {
        if (stmt.getExpression().isVariableDeclarationExpr()) {
            VariableDeclarationExpr vdecl = stmt.getExpression().asVariableDeclarationExpr();
            VariableDeclarator v = vdecl.getVariable(0);

            Type elementType = vdecl.getElementType();
            if (elementType.isClassOrInterfaceType()) {
                ClassOrInterfaceType classType = elementType.asClassOrInterfaceType();
                NodeList<Type> secondaryType = classType.getTypeArguments().orElse(null);

                if (secondaryType != null) {
                    String mainType = classType.getNameAsString();
                    String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(cu,mainType);

                    if (fullyQualifiedName != null) {
                        ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null,
                                secondaryType.get(0).asClassOrInterfaceType(), new NodeList<>());

                        Variable variable = Reflect.variableFactory(fullyQualifiedName);
                        if (mainType.endsWith("List") || mainType.endsWith("Map") || mainType.endsWith("Set")) {
                            for(int i = 0 ; i < 10 ; i++) {
                                Variable row = createObject(stmt, v, objectCreationExpr);
                                if(resultToEntity(row, rs)) {
                                    ((Collection) variable.getValue()).add(row);
                                }
                            }
                        }
                        return variable;
                    }
                }
                else {
                    ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, classType, new NodeList<>());
                    Variable row = createObject(stmt, v, objectCreationExpr);
                    if(resultToEntity(row, rs)) {
                        return row;
                    } else {
                        return new Variable(null);
                    }

                }
            }
        }
        return null;
    }

    /**
     * Converts an SQL row to an Entity.
     * @param variable copy the data from the record into this variable.
     * @param rs the sql result set
     */
    private boolean resultToEntity(Variable variable, ResultSet rs) {

        try {
            if (variable.getValue() instanceof Evaluator evaluator && rs.next() && cu != null) {
                Map<String, Variable> fields = evaluator.getFields();

                for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                    for (VariableDeclarator var : field.getVariables()) {
                        String fieldName = var.getNameAsString();
                        try {
                            if (rs.findColumn(RepositoryParser.camelToSnake(fieldName)) > 0) {
                                Object value = rs.getObject(RepositoryParser.camelToSnake(fieldName));
                                fields.put(fieldName, new Variable(value));
                            }
                        } catch (SQLException e) {
                            logger.warn(e.getMessage());
                        }
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.warn(e.getMessage());
        }
        return false;
    }

    private boolean isRepositoryMethod(ExpressionStmt stmt) {
        Expression expr = stmt.getExpression();
        if (expr.isVariableDeclarationExpr()) {
            VariableDeclarationExpr vdecl = expr.asVariableDeclarationExpr();
            VariableDeclarator v = vdecl.getVariable(0);
            Optional<Expression> init = v.getInitializer();
            if (init.isPresent() && init.get().isMethodCallExpr()) {
                MethodCallExpr methodCall = init.get().asMethodCallExpr();
                Expression scope = methodCall.getScope().orElse(null);
                if (scope != null && scope.isNameExpr()) {
                    return repositories.containsKey(scope.asNameExpr().getNameAsString());
                }

            }
        }
        return false;
    }

    private LineOfCode findExpressionStatement(MethodCallExpr methodCall) {
        Node n = methodCall;
        while (n != null && !(n instanceof MethodDeclaration)) {
            if (n instanceof ExpressionStmt stmt) {
                /*
                 * We have found the expression statement corresponding to this query
                 */
                return lines.get(stmt.hashCode());
            }
            n = n.getParentNode().orElse(null);
        }
        return null;
    }

    public void setOnTest(boolean b) {
        onTest = b;
    }

    @Override
    Variable createObject(Node instructionPointer, VariableDeclarator decl, ObjectCreationExpr oce) throws AntikytheraException, ReflectiveOperationException {
        Variable v = super.createObject(instructionPointer, decl, oce);
        ClassOrInterfaceType type = oce.getType();
        if (type.getNameAsString().equals("ResponseEntity")) {
            ControllerResponse response = new ControllerResponse(v);
            response.setBody(evaluateExpression(oce.getArguments().get(0)));
            response.setType(type);
            return new Variable(response);
        }
        return v;
    }

    public ArgumentGenerator getArgumentGenerator() {
        return argumentGenerator;
    }

    public void setArgumentGenerator(ArgumentGenerator argumentGenerator) {
        this.argumentGenerator = argumentGenerator;
    }
}

