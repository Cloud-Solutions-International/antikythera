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
import com.github.javaparser.ast.type.TypeParameter;
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
import java.util.Map;
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
 * or other transformation capabilities.
 * </p>
 */
public class DependencyAnalyzer {
    
    /**
     * Stack for DFS traversal (protected so subclasses can access).
     */
    protected final LinkedList<GraphNode> stack = new LinkedList<>();
    
    /**
     * Variable name to type mapping for scope resolution (protected for subclasses).
     */
    protected final java.util.HashMap<String, Type> names = new java.util.HashMap<>();
    
    /**
     * Tracks nodes discovered during the current analysis run.
     * Used by collectDependencies() to return only nodes from this specific analysis.
     */
    protected final Set<GraphNode> discoveredNodes = new HashSet<>();
    
    /**
     * Create a new DependencyAnalyzer instance.
     * 
     * <p>
     * Can be instantiated directly for analysis-only use cases.
     * </p>
     */
    public DependencyAnalyzer() {
        // Constructor is public so instances can be created directly
    }
    
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
     * @param filter Optional filter to exclude certain nodes from results
     * @return Set of GraphNodes representing all discovered dependencies
     */
    public Set<GraphNode> collectDependencies(
        Collection<MethodDeclaration> methods,
        Predicate<GraphNode> filter
    ) {
        reset();
        
        // Initialize graph nodes for input methods
        for (MethodDeclaration method : methods) {
            GraphNode node = createAnalysisNode(method);
            // createAnalysisNode() already tracks in discoveredNodes
            stack.push(node);
        }
        
        // Run DFS to discover all dependencies
        dfs();
        
        // Return discovered nodes (all nodes that were processed during this analysis)
        Set<GraphNode> results = new HashSet<>(discoveredNodes);
        
        if (filter != null) {
            results = results.stream()
                .filter(filter)
                .collect(Collectors.toSet());
        }
        
        return results;
    }
    
    /**
     * Create a graph node for analysis (no code generation).
     * Uses GraphNode.graphNodeFactory() directly and skips buildNode().
     * 
     * Subclasses (like DepSolver) can override to create nodes with code generation
     * by calling Graph.createGraphNode() which includes buildNode().
     * 
     * @param node AST node to wrap
     * @return GraphNode for analysis (no destination CU created)
     */
    protected GraphNode createAnalysisNode(Node node) {
        // Use GraphNode.graphNodeFactory() directly - this creates the node
        // and tracks it in Graph.getNodes(), but does NOT call buildNode()
        GraphNode g = GraphNode.graphNodeFactory(node);
        
        // Track this node as discovered in this analysis run
        discoveredNodes.add(g);
        
        // For analysis-only, we skip buildNode() which is where code generation happens
        // The node is ready for dependency tracking without creating destination CUs
        
        return g;
    }
    
    /**
     * Run depth-first search to discover dependencies.
     * This method is the core traversal algorithm.
     */
    public void dfs() {
        /*
         * Operates in three stages.
         *
         * First up will try to identify if the node is a field in the class being studied. In that
         * case it will be added to the node
         *
         * The second search we will check if the node is a method, here we will check all the
         * parameters in the method call as well as the return type.
         *
         * Thirdly, it will do the same sort of thing for constructors.
         */
        while (!stack.isEmpty()) {
            GraphNode node = stack.pollLast();
            
            if (!node.isVisited()) {
                node.setVisited(true);
                
                // Discover dependencies
                fieldSearch(node);
                methodSearch(node);
                constructorSearch(node);
            }
        }
    }
    
    /**
     * Search for field dependencies.
     * 
     * @param node Current graph node
     */
    protected void fieldSearch(GraphNode node) {
        if (node.getNode() instanceof FieldDeclaration fd) {
            node.addField(fd);
        }
    }
    
    /**
     * Search for method dependencies.
     * 
     * @param node Current graph node
     */
    protected void methodSearch(GraphNode node) {
        if (node.getEnclosingType() != null && node.getNode() instanceof MethodDeclaration md) {
            methodSearchHelper(node, md);
        }
    }
    
    /**
     * Helper method for method search.
     */
    protected void methodSearchHelper(GraphNode node, MethodDeclaration md) {
        callableSearch(node, md);
        
        // Process return type
        Type returnType = md.getType();
        String returns = md.getTypeAsString();
        if (!returns.equals("void") && returnType.isClassOrInterfaceType()) {
            node.processTypeArgument(returnType.asClassOrInterfaceType());
        }
        
        // Handle generic type parameters
        for (TypeParameter typeParameter : md.getTypeParameters()) {
            for (ClassOrInterfaceType bound : typeParameter.getTypeBound()) {
                node.processTypeArgument(bound);
            }
        }
        
        // Handle thrown exceptions
        for (Type thrownException : md.getThrownExceptions()) {
            ImportUtils.addImport(node, thrownException);
        }
        
        // Handle @Override - find parent methods
        if (md.getAnnotationByName("Override").isPresent()) {
            findParentMethods(node, md);
        }
        
        // Handle interface methods - find implementations
        if (node.getEnclosingType().isClassOrInterfaceDeclaration() 
            && node.getEnclosingType().asClassOrInterfaceDeclaration().isInterface()) {
            findImplementations(node, md);
        }
    }
    
    /**
     * Search for constructor dependencies.
     * 
     * @param node Current graph node
     */
    protected void constructorSearch(GraphNode node) {
        if (node.getEnclosingType() != null && node.getNode() instanceof ConstructorDeclaration cd) {
            callableSearch(node, cd);
        }
    }
    
    /**
     * Common logic for analyzing callables (methods and constructors).
     * This is the core shared logic - moved from DepSolver to avoid duplication.
     */
    protected void callableSearch(GraphNode node, CallableDeclaration<?> cd) {
        // Hook for subclasses to add code generation logic (e.g., add to destination CU)
        onCallableDiscovered(node, cd);
        
        // Search method/constructor parameters
        searchMethodParameters(node, cd.getParameters());
        
        // Process variable declarations and method calls
        names.clear();
        cd.accept(new VariableVisitor(), node);
        cd.accept(new DependencyVisitor(), node);
    }
    
    /**
     * Hook method for subclasses to handle discovered callables.
     * Default implementation does nothing (analysis-only).
     * 
     * Subclasses (like DepSolver) override this to add callables to destination
     * CompilationUnits for code generation.
     * 
     * @param node Graph node
     * @param cd Callable declaration
     */
    protected void onCallableDiscovered(GraphNode node, CallableDeclaration<?> cd) {
        // Override in subclasses for code generation
    }
    
    /**
     * Search method parameters for dependencies.
     */
    protected void searchMethodParameters(GraphNode node, NodeList<Parameter> parameters) {
        for (Parameter p : parameters) {
            java.util.List<ImportWrapper> imports = AbstractCompiler.findImport(node.getCompilationUnit(), p.getType());
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
     * Search for a class dependency.
     * Uses createAnalysisNode() hook so subclasses can use their own graph.
     * 
     * Hook method for subclasses to handle imports (e.g., add to destination CU).
     */
    protected void searchClass(GraphNode node, ImportWrapper imp) {
        if (imp != null) {
            // Hook for subclasses to add imports to destination CU
            onImportDiscovered(node, imp);
            
            TypeDeclaration<?> decl = imp.getType();
            if (decl != null) {
                createAnalysisNode(decl);
                // createAnalysisNode() already tracks in discoveredNodes
            }
        }
    }
    
    /**
     * Hook method for subclasses to handle discovered imports.
     * Default implementation does nothing (analysis-only).
     * 
     * @param node Graph node
     * @param imp Import wrapper
     */
    protected void onImportDiscovered(GraphNode node, ImportWrapper imp) {
        // Override in subclasses to add imports to destination CU
    }
    
    /**
     * Find parent methods for @Override methods.
     * Uses createAnalysisNode() hook so subclasses can use their own graph.
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
                        // createAnalysisNode() already tracks in discoveredNodes
                    }
                }
            }
        }
    }
    
    /**
     * Find implementations of interface methods.
     * Uses createAnalysisNode() hook so subclasses can use their own graph.
     */
    protected void findImplementations(GraphNode node, MethodDeclaration md) {
        ClassOrInterfaceDeclaration cdecl = node.getEnclosingType().asClassOrInterfaceDeclaration();
        for (String t : AntikytheraRunTime.findImplementations(cdecl.getFullyQualifiedName().orElseThrow())) {
            AntikytheraRunTime.getTypeDeclaration(t).ifPresent(td -> {
                for (MethodDeclaration m : td.getMethodsByName(md.getNameAsString())) {
                    if (m.getParameters().size() == md.getParameters().size()) {
                        createAnalysisNode(m);
                        // createAnalysisNode() already tracks in discoveredNodes
                    }
                }
            });
        }
    }
    
    /**
     * Handle method overrides for abstract methods.
     * Uses createAnalysisNode() hook.
     */
    protected void methodOverrides(CallableDeclaration<?> cd, String className) {
        for (String s : AntikytheraRunTime.findSubClasses(className)) {
            addOverride(cd, s);
        }
        
        for (String s : AntikytheraRunTime.findImplementations(className)) {
            addOverride(cd, s);
        }
    }
    
    /**
     * Helper for finding override implementations.
     */
    private void addOverride(CallableDeclaration<?> cd, String s) {
        AntikytheraRunTime.getTypeDeclaration(s).ifPresent(parent -> {
            for (MethodDeclaration md : parent.getMethodsByName(cd.getNameAsString())) {
                if (md.getParameters().size() == cd.getParameters().size()) {
                    createAnalysisNode(md);
                    // createAnalysisNode() already tracks in discoveredNodes
                }
            }
        });
    }
    
    /**
     * Reset analyzer state.
     * Note: Does NOT clear Graph.getNodes() - that is shared with code generation.
     * For analysis-only runs, nodes remain in Graph.getNodes() but that's fine
     * since we only use Graph.getDependencies() for code generation.
     */
    public void reset() {
        stack.clear();
        names.clear();
        discoveredNodes.clear();
        // Don't clear Graph.getNodes() - it's shared state
        // Analysis nodes can coexist with code generation nodes
        // Nodes from previous runs remain, but discoveredNodes tracks only current run
    }
    
    /**
     * Push a node onto the analysis stack.
     */
    protected void push(GraphNode node) {
        stack.push(node);
    }
    
    /**
     * Initialize field dependencies (for field initializers).
     * 
     * @param field Field declaration
     * @param node Graph node
     */
    public void initializeField(FieldDeclaration field, GraphNode node) {
        java.util.Optional<Expression> init = field.getVariables().get(0).getInitializer();
        if (init.isPresent()) {
            Expression initializer = init.get();
            if (initializer.isObjectCreationExpr() || initializer.isMethodCallExpr()) {
                initializer.accept(new DependencyVisitor(), node);
            } else if (initializer.isNameExpr()) {
                Resolver.resolveNameExpr(node, initializer.asNameExpr(), new NodeList<>());
            }
        }
    }
    
    // ==================== Inner Visitor Classes ====================
    // These are extracted from DepSolver and moved here to avoid duplication
    
    /**
     * Visitor for processing variable declarations.
     * Identifies variables so that resolving method call scopes becomes easier.
     * Must be instance class (not static) to access the 'names' map.
     */
    protected class VariableVisitor extends VoidVisitorAdapter<GraphNode> {
        @Override
        public void visit(final Parameter n, GraphNode node) {
            names.put(n.getNameAsString(), n.getType());
            node.processTypeArgument(n.getType());
            super.visit(n, node);
        }
        
        @Override
        public void visit(final VariableDeclarationExpr n, GraphNode node) {
            for (com.github.javaparser.ast.body.VariableDeclarator vd : n.getVariables()) {
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
                                    GraphNode ctorNode = createAnalysisNode(constructorDeclaration);
                                    push(ctorNode);
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
                        .ifPresent(field -> {
                            GraphNode fieldNode = createAnalysisNode(field);
                            push(fieldNode);
                        });
                    ImportUtils.addImport(arg, fae);
                }
            }
            super.visit(n, arg);
        }
        
        @Override
        public void visit(ReturnStmt n, GraphNode node) {
            n.getExpression().ifPresent(e ->
                Resolver.processExpression(node, e, new NodeList<>())
            );
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
            java.util.Optional<Node> parent = n.getParentNode();
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

