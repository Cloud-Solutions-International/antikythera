package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Dependency analyzer for pure analysis (no code generation).
 * 
 * <p>
 * Provides core dependency discovery capabilities without code generation.
 * This class focuses purely on building a dependency graph by analyzing
 * method calls, field references, constructor invocations, and type usage.
 * </p>
 * 
 * <p>
 * This class can be instantiated directly for analysis-only use cases
 * (e.g., MethodExtractionStrategy, dependency queries, visualization).
 * </p>
 * 
 * <p>
 * Subclasses (like {@link DepSolver}) can extend this to add code generation
 * or other transformation capabilities by overriding hook methods.
 * </p>
 */
public class DependencyAnalyzer {

    /**
     * Stack for DFS traversal (static, shared with DepSolver).
     */
    protected static final LinkedList<GraphNode> stack = new LinkedList<>();

    /**
     * Variable name to type mapping for scope resolution (static, shared with
     * DepSolver).
     */
    protected static final Map<String, Type> names = new HashMap<>();

    /**
     * Tracks nodes discovered during the current analysis run.
     * This is an instance field so each analyzer can track its own results.
     */
    protected Set<GraphNode> discoveredNodes = new HashSet<>();

    /**
     * Create a new DependencyAnalyzer instance.
     */
    public DependencyAnalyzer() {
        /* no action required here. Child classes can add functionality */
    }

    // ============ Public API (Gap 2) ============

    /**
     * Collect dependencies for a set of methods.
     * This is the primary entry point for analysis-only use cases.
     * 
     * @param methods The methods to analyze
     * @return Set of GraphNodes representing all discovered dependencies
     */
    public Set<GraphNode> collectDependencies(Collection<MethodDeclaration> methods) {
        return collectDependencies(methods, null);
    }

    /**
     * Collect dependencies for a set of methods with optional filtering.
     * 
     * @param methods The methods to analyze
     * @param filter  Optional filter to exclude certain nodes from results
     * @return Set of GraphNodes representing all discovered dependencies
     */
    public Set<GraphNode> collectDependencies(
            Collection<MethodDeclaration> methods,
            Predicate<GraphNode> filter) {
        resetAnalysis();

        // Initialize graph nodes for input methods
        for (MethodDeclaration method : methods) {
            createAnalysisNode(method);
        }

        // Run DFS to discover all dependencies
        dfs();

        // Return discovered nodes
        if (filter != null) {
            return discoveredNodes.stream()
                    .filter(filter)
                    .collect(Collectors.toSet());
        }
        return new HashSet<>(discoveredNodes);
    }

    // ============ Hook Methods (override in DepSolver) ============

    /**
     * Create a graph node for analysis.
     * 
     * Uses Graph.createGraphNode() for proper node initialization including
     * destination CU setup and buildNode() call. The difference between
     * analysis-only and code-generation modes is in what we do with results,
     * not in how nodes are created.
     * 
     * Subclasses (like DepSolver) can override if they need custom behavior.
     * 
     * Note: The created node is NOT added to discoveredNodes here because it
     * will be added during the DFS traversal in the dfs() method.
     * 
     * @param node AST node to wrap
     * @return GraphNode for analysis
     */
    protected GraphNode createAnalysisNode(Node node) {
        GraphNode g = Graph.createGraphNode(node);
        return g;
    }

    /**
     * Hook method for subclasses to handle discovered callables.
     * Default implementation does nothing (analysis-only).
     * 
     * Subclasses (like DepSolver) override this to add callables to destination
     * CompilationUnits for code generation.
     * 
     * @param node Graph node
     * @param cd   Callable declaration
     */
    protected void onCallableDiscovered(GraphNode node, CallableDeclaration<?> cd) {
        // Override in subclasses for code generation
    }

    /**
     * Hook method for subclasses to handle discovered imports.
     * Default implementation does nothing (analysis-only).
     * 
     * @param node Graph node
     * @param imp  Import wrapper
     */
    protected void onImportDiscovered(GraphNode node, ImportWrapper imp) {
        // Override in subclasses to add imports to destination CU
    }

    // ============ DFS Logic ============

    /**
     * Iterative Depth first search.
     * 
     * Operates in three stages:
     * 1. Check if the node is a field in the class being studied
     * 2. Check if the node is a method, process parameters and return type
     * 3. Do the same for constructors
     */
    public void dfs() {
        while (!stack.isEmpty()) {
            GraphNode node = stack.pollLast();

            if (!node.isVisited()) {
                node.setVisited(true);

                // Track all nodes processed during DFS
                // This captures nodes created by Resolver via Graph.createGraphNode()
                discoveredNodes.add(node);

                fieldSearch(node);
                methodSearch(node);
                constructorSearch(node);
            }
        }
    }

    /**
     * Checks if the node is a field and adds it to the class or enum.
     *
     * @param node the graph node that is being inspected
     */
    void fieldSearch(GraphNode node) {
        if (node.getNode() instanceof FieldDeclaration fd) {
            node.addField(fd);
        }
    }

    /**
     * Check if the node is a method and add it to the class.
     *
     * @param node A graph node that represents a method in the code.
     */
    void methodSearch(GraphNode node) {
        if (node.getEnclosingType() != null && node.getNode() instanceof MethodDeclaration md) {
            methodSearchHelper(node, md);
        }
    }

    private void methodSearchHelper(GraphNode node, MethodDeclaration md) {
        callableSearch(node, md);

        Type returnType = md.getType();
        String returns = md.getTypeAsString();
        if (!returns.equals("void") && returnType.isClassOrInterfaceType()) {
            node.processTypeArgument(returnType.asClassOrInterfaceType());
        }

        // Handle generic type parameters
        for (com.github.javaparser.ast.type.TypeParameter typeParameter : md.getTypeParameters()) {
            for (ClassOrInterfaceType bound : typeParameter.getTypeBound()) {
                node.processTypeArgument(bound);
            }
        }

        for (Type thrownException : md.getThrownExceptions()) {
            ImportUtils.addImport(node, thrownException);
        }

        if (md.getAnnotationByName("Override").isPresent()) {
            findParentMethods(node, md);
        }

        if (node.getEnclosingType().isClassOrInterfaceDeclaration()
                && node.getEnclosingType().asClassOrInterfaceDeclaration().isInterface()) {
            findImplementations(node, md);
        }
    }

    /**
     * Find implementations of interface methods.
     */
    protected void findImplementations(GraphNode node, MethodDeclaration md) {
        ClassOrInterfaceDeclaration cdecl = node.getEnclosingType().asClassOrInterfaceDeclaration();
        for (String t : AntikytheraRunTime.findImplementations(cdecl.getFullyQualifiedName().orElseThrow())) {
            AntikytheraRunTime.getTypeDeclaration(t).ifPresent(td -> {
                for (MethodDeclaration m : td.getMethodsByName(md.getNameAsString())) {
                    if (m.getParameters().size() == md.getParameters().size()) {
                        createAnalysisNode(m);
                    }
                }
            });
        }
    }

    /**
     * Find parent methods for @Override methods.
     */
    protected void findParentMethods(GraphNode node, MethodDeclaration md) {
        TypeDeclaration<?> td = node.getTypeDeclaration();
        if (td == null || !td.isClassOrInterfaceDeclaration()) {
            return;
        }

        for (ClassOrInterfaceType parent : td.asClassOrInterfaceDeclaration().getImplementedTypes()) {
            TypeWrapper wrapper = AbstractCompiler.findType(node.getCompilationUnit(), parent.getNameAsString());
            if (wrapper != null && wrapper.getType() != null) {
                for (MethodDeclaration pmd : wrapper.getType().getMethodsByName(md.getNameAsString())) {
                    if (pmd.getParameters().size() == md.getParameters().size()) {
                        createAnalysisNode(pmd);
                    }
                }
            }
        }
    }

    /**
     * Search in constructors.
     * 
     * @param node A graph node that represents a constructor
     */
    private void constructorSearch(GraphNode node) {
        if (node.getEnclosingType() != null && node.getNode() instanceof ConstructorDeclaration cd) {
            callableSearch(node, cd);
        }
    }

    /**
     * Common logic for processing callables (methods and constructors).
     */
    private void callableSearch(GraphNode node, CallableDeclaration<?> cd) {
        // Hook for code generation
        onCallableDiscovered(node, cd);

        searchMethodParameters(node, cd.getParameters());

        names.clear();
        cd.accept(new VariableVisitor(), node);
        cd.accept(new DependencyVisitor(), node);
    }

    /**
     * Handle method overrides for abstract methods.
     */
    protected static void methodOverrides(CallableDeclaration<?> cd, String className) {
        for (String s : AntikytheraRunTime.findSubClasses(className)) {
            addOverRide(cd, s);
        }

        for (String s : AntikytheraRunTime.findImplementations(className)) {
            addOverRide(cd, s);
        }
    }

    /**
     * Helper for finding override implementations.
     */
    protected static void addOverRide(CallableDeclaration<?> cd, String s) {
        AntikytheraRunTime.getTypeDeclaration(s).ifPresent(parent -> {
            for (MethodDeclaration md : parent.getMethodsByName(cd.getNameAsString())) {
                if (md.getParameters().size() == cd.getParameters().size()) {
                    Graph.createGraphNode(md);
                }
            }
        });
    }

    /**
     * Search method parameters for dependencies.
     */
    private void searchMethodParameters(GraphNode node, NodeList<Parameter> parameters) {
        for (Parameter p : parameters) {
            List<ImportWrapper> imports = AbstractCompiler.findImport(node.getCompilationUnit(), p.getType());
            for (ImportWrapper imp : imports) {
                searchClass(node, imp);
            }

            for (AnnotationExpr ann : p.getAnnotations()) {
                ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(), ann.getNameAsString());
                searchClass(node, imp2);
            }
        }
    }

    /**
     * Search for an outgoing edge to another class.
     */
    protected void searchClass(GraphNode node, ImportWrapper imp) {
        if (imp != null) {
            onImportDiscovered(node, imp);

            TypeDeclaration<?> decl = imp.getType();
            if (decl != null) {
                createAnalysisNode(decl);
            }
        }
    }

    /**
     * Initialize field dependencies.
     */
    public void initField(FieldDeclaration field, GraphNode node) {
        Optional<Expression> init = field.getVariables().get(0).getInitializer();
        if (init.isPresent()) {
            Expression initializer = init.get();
            if (initializer.isObjectCreationExpr() || initializer.isMethodCallExpr()) {
                initializer.accept(new DependencyVisitor(), node);
            } else if (initializer.isNameExpr()) {
                Resolver.resolveNameExpr(node, initializer.asNameExpr(), new NodeList<>());
            }
        }
    }

    /**
     * Reset analysis state.
     */
    protected void resetAnalysis() {
        stack.clear();
        names.clear();
        discoveredNodes.clear();
    }

    // ============ Inner Visitor Classes ============

    /**
     * Processes variable declarations.
     * This visitor identifies variables so that resolving method call scopes
     * becomes easier.
     */
    protected static class VariableVisitor extends VoidVisitorAdapter<GraphNode> {
        @Override
        public void visit(final Parameter n, GraphNode node) {
            names.put(n.getNameAsString(), n.getType());
            node.processTypeArgument(n.getType());
            super.visit(n, node);
        }

        @Override
        public void visit(final VariableDeclarationExpr n, GraphNode node) {
            for (VariableDeclarator vd : n.getVariables()) {
                names.put(vd.getNameAsString(), vd.getType());
                if (vd.getType().isClassOrInterfaceType()) {
                    node.processTypeArgument(vd.getType().asClassOrInterfaceType());
                } else if (vd.getType().isArrayType()) {
                    Type t = vd.getType().asArrayType().getComponentType();
                    if (t.isClassOrInterfaceType()) {
                        node.processTypeArgument(t.asClassOrInterfaceType());
                    }
                }
                vd.getInitializer().ifPresent(init -> ImportUtils.addImport(node, init));
            }
            super.visit(n, node);
        }
    }

    /**
     * Visitor for processing dependencies in expressions and statements.
     */
    protected class DependencyVisitor extends AnnotationVisitor {
        @Override
        public void visit(ExplicitConstructorInvocationStmt n, GraphNode node) {
            if (node.getNode() instanceof ConstructorDeclaration cd
                    && node.getEnclosingType().isClassOrInterfaceDeclaration()) {
                for (ClassOrInterfaceType cdecl : node.getEnclosingType()
                        .asClassOrInterfaceDeclaration().getExtendedTypes()) {
                    String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(
                            node.getCompilationUnit(), cdecl.getNameAsString());
                    if (fullyQualifiedName != null) {
                        AntikytheraRunTime.getTypeDeclaration(fullyQualifiedName).ifPresent(cid -> {
                            for (ConstructorDeclaration constructorDeclaration : cid.getConstructors()) {
                                if (constructorDeclaration.getParameters().size() == cd.getParameters().size()) {
                                    createAnalysisNode(constructorDeclaration);
                                }
                            }
                        });
                    }
                }
            }
        }

        @Override
        public void visit(CatchClause n, GraphNode node) {
            Parameter param = n.getParameter();
            if (param.getType().isUnionType()) {
                UnionType ut = param.getType().asUnionType();
                for (Type t : ut.getElements()) {
                    ImportUtils.addImport(node, t);
                }
            } else {
                Type t = param.getType();
                ImportUtils.addImport(node, t);
            }
            super.visit(n, node);
        }

        @Override
        public void visit(ExpressionStmt n, GraphNode arg) {
            if (n.getExpression().isAssignExpr()) {
                AssignExpr assignExpr = n.getExpression().asAssignExpr();
                Expression expr = assignExpr.getValue();
                Resolver.processExpression(arg, expr, new NodeList<>());

                if (assignExpr.getTarget().isFieldAccessExpr()) {
                    FieldAccessExpr fae = assignExpr.getTarget().asFieldAccessExpr();
                    SimpleName nmae = fae.getName();
                    arg.getEnclosingType().findFirst(FieldDeclaration.class,
                            f -> f.getVariable(0).getNameAsString().equals(nmae.asString()))
                            .ifPresent(field -> createAnalysisNode(field));
                    ImportUtils.addImport(arg, fae);
                }
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(ReturnStmt n, GraphNode node) {
            n.getExpression().ifPresent(e -> Resolver.processExpression(node, e, new NodeList<>()));
            super.visit(n, node);
        }

        @Override
        public void visit(MethodCallExpr mce, GraphNode node) {
            MCEWrapper mceWrapper = Resolver.resolveArgumentTypes(node, mce);
            Resolver.chainedMethodCall(node, mceWrapper);
            super.visit(mce, node);
        }

        @Override
        public void visit(BinaryExpr n, GraphNode node) {
            Optional<Node> parent = n.getParentNode();
            if (parent.isPresent() && !(parent.get() instanceof IfStmt
                    || parent.get() instanceof ConditionalExpr)) {
                Expression left = n.getLeft();
                Expression right = n.getRight();

                Resolver.processExpression(node, left, new NodeList<>());
                Resolver.processExpression(node, right, new NodeList<>());
            }
            super.visit(n, node);
        }

        @Override
        public void visit(CastExpr n, GraphNode node) {
            ImportUtils.addImport(node, n.getType());
            super.visit(n, node);
        }

        @Override
        public void visit(ObjectCreationExpr oce, GraphNode node) {
            node.processTypeArgument(oce.getType());

            MCEWrapper mceWrapper = Resolver.resolveArgumentTypes(node, oce);
            Resolver.chainedMethodCall(node, mceWrapper);

            super.visit(oce, node);
        }
    }
}
