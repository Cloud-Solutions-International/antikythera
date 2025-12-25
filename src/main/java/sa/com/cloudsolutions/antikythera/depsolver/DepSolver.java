package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dependency solver with code generation capabilities.
 * 
 * <p>
 * NOTE: Currently keeping static methods for backward compatibility.
 * TODO: Later refactor to extend DependencyAnalyzer and share common code.
 * </p>
 * 
 * <p>
 * This class maintains backward compatibility with existing code that uses static methods
 * like {@link #createSolver()}, {@link #push(GraphNode)}, {@link #getNames()}, etc.
 * </p>
 */
public class DepSolver {
    
    /**
     * The stack for the depth first search.
     */
    private static final LinkedList<GraphNode> stack = new LinkedList<>();
    
    private static final Map<String, Type> names = new HashMap<>();
    
    /**
     * Static singleton instance for backward compatibility.
     * Used by static methods like createSolver(), push(), getNames().
     */
    private static DepSolver solver;
    
    /**
     * Protected constructor for backward compatibility.
     * Use {@link #createSolver()} to create instances.
     */
    protected DepSolver() {
    }
    
    /**
     * Create a DepSolver instance (backward compatibility).
     * Returns a singleton instance, reusing the same instance across calls.
     * 
     * @return DepSolver instance
     */
    public static DepSolver createSolver() {
        if (solver == null) {
            solver = new DepSolver();
        } else {
            solver.reset();
        }
        return solver;
    }
    
    /**
     * Push a node onto the stack (static method).
     * 
     * @param g GraphNode to push
     */
    public static void push(GraphNode g) {
        stack.push(g);
    }
    
    /**
     * Get the names map (static method).
     * 
     * @return names map
     */
    public static Map<String, Type> getNames() {
        return names;
    }
    
    /**
     * Initialize field dependencies (static method).
     * 
     * @param field Field declaration
     * @param node Graph node
     */
    public static void initializeField(FieldDeclaration field, GraphNode node) {
        if (solver == null) {
            solver = new DepSolver();
        }
        solver.initField(field, node);
    }
    
    /**
     * Reset state (static method).
     * Clears both analyzer state and Graph code generation state.
     */
    public static void reset() {
        stack.clear();
        names.clear();
        Graph.getDependencies().clear();
        Graph.getNodes().clear();
    }
    
    /**
     * Main entry point for the dependency solver.
     * 
     * @throws IOException if files could not be read
     */
    private void solve() throws IOException {
        AbstractCompiler.preProcess();
        for (String method : Settings.getPropertyList("methods", String.class)) {
            processMethod(method);
        }
    }
    
    /**
     * Process the dependencies of a method that was declared in the application configuration.
     * 
     * @param s the method name
     */
    public void processMethod(String s) {
        String[] parts = s.split("#");
        
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(parts[0]);
        if (cu != null) {
            cu.findAll(MethodDeclaration.class, m -> m.getNameAsString().equals(parts[1]))
                .forEach(Graph::createGraphNode);
            dfs();
        }
    }
    
    /**
     * Iterative Depth first search
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
                
                fieldSearch(node);
                methodSearch(node);
                constructorSearch(node);
            }
        }
    }
    
    /**
     * Check if the node is a method and add it to the class.
     *
     * The return type, all the locals declared inside the method and arguments are searchable.
     * There maybe decorators for the method or some of the arguments. Separate graph nodes will
     * be created for all of these things and pushed onto the stack.
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
            sa.com.cloudsolutions.antikythera.parser.ImportUtils.addImport(node, thrownException);
        }
        
        if (md.getAnnotationByName("Override").isPresent()) {
            findParentMethods(node, md);
        }
        
        if (node.getEnclosingType().isClassOrInterfaceDeclaration() && node.getEnclosingType().asClassOrInterfaceDeclaration().isInterface()) {
            findImplementations(node, md);
        }
    }
    
    private static void findImplementations(GraphNode node, MethodDeclaration md) {
        ClassOrInterfaceDeclaration cdecl = node.getEnclosingType().asClassOrInterfaceDeclaration();
        for (String t : AntikytheraRunTime.findImplementations(cdecl.getFullyQualifiedName().orElseThrow())) {
            AntikytheraRunTime.getTypeDeclaration(t).ifPresent(td -> {
                for (MethodDeclaration m : td.getMethodsByName(md.getNameAsString())) {
                    if (m.getParameters().size() == md.getParameters().size()) {
                        Graph.createGraphNode(m);
                    }
                }
            });
        }
    }
    
    private static void findParentMethods(GraphNode node, MethodDeclaration md) {
        TypeDeclaration<?> td = node.getTypeDeclaration();
        if (td == null || !td.isClassOrInterfaceDeclaration()) {
            return;
        }
        
        for (ClassOrInterfaceType parent : td.asClassOrInterfaceDeclaration().getImplementedTypes()) {
            sa.com.cloudsolutions.antikythera.generator.TypeWrapper wrapper = AbstractCompiler.findType(node.getCompilationUnit(), parent.getNameAsString());
            if (wrapper != null && wrapper.getType() != null) {
                for (MethodDeclaration pmd : wrapper.getType().getMethodsByName(md.getNameAsString())) {
                    if (pmd.getParameters().size() == md.getParameters().size()) {
                        Graph.createGraphNode(pmd);
                    }
                }
            }
        }
    }
    
    /**
     * Search in constructors.
     * All the locals declared inside the constructor and arguments are searchable.
     * Any annotations for the arguments or the constructor will be searched as well.
     * @param node A graph node that represents a constructor
     */
    private void constructorSearch(GraphNode node) {
        if (node.getEnclosingType() != null && node.getNode() instanceof ConstructorDeclaration cd) {
            callableSearch(node, cd);
        }
    }
    
    private void callableSearch(GraphNode node, CallableDeclaration<?> cd) {
        String className = node.getEnclosingType().getNameAsString();
        node.getDestination().findFirst(TypeDeclaration.class,
                t -> t.getNameAsString().equals(className)).ifPresent(c -> {
                    TypeDeclaration<?> typeDecl = node.getTypeDeclaration();
                    if (typeDecl != null) {
                        typeDecl.addMember(cd);
                        if (cd.isAbstract() && node.getEnclosingType().getFullyQualifiedName().isPresent()) {
                            methodOverrides(cd, node.getEnclosingType().getFullyQualifiedName().get());
                        }
                    }
                });
        
        searchMethodParameters(node, cd.getParameters());
        
        names.clear();
        cd.accept(new VariableVisitor(), node);
        cd.accept(new Visitor(), node);
    }
    
    private static void methodOverrides(CallableDeclaration<?> cd, String className) {
        for (String s : AntikytheraRunTime.findSubClasses(className)) {
            addOverRide(cd, s);
        }
        
        for (String s : AntikytheraRunTime.findImplementations(className)) {
            addOverRide(cd, s);
        }
    }
    
    private static void addOverRide(CallableDeclaration<?> cd, String s) {
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
     * @param node GraphNode representing a method.
     * @param parameters the list of parameters of that method
     */
    private void searchMethodParameters(GraphNode node, NodeList<Parameter> parameters) {
        for (Parameter p : parameters) {
            List<ImportWrapper> imports = AbstractCompiler.findImport(node.getCompilationUnit(), p.getType());
            for (ImportWrapper imp : imports) {
                searchClass(node, imp);
            }
            
            for (com.github.javaparser.ast.expr.AnnotationExpr ann : p.getAnnotations()) {
                ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(), ann.getNameAsString());
                searchClass(node, imp2);
            }
        }
    }
    
    /**
     * Search for an outgoing edge to another class
     * @Deprecated
     * @param node the current node
     * @param imp the import declaration for the other class.
     */
    private void searchClass(GraphNode node, ImportWrapper imp) {
        /*
         * It is likely that this is a DTO an Entity or a model. So we will assume that all the
         * fields are required along with their respective annotations.
         */
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
            
            TypeDeclaration<?> decl = imp.getType();
            if (decl != null) {
                Graph.createGraphNode(decl);
            }
        }
    }
    
    /**
     * Checks if the node is a field and adds it to the class or enum.
     *
     * Also adds all the imports for the field itself as well as the direct annotations.
     * Identifying the initializer is not the responsibility of this method but that of the
     * visitor. Similarly, if there are arguments to the initializer these are also identified
     * and the imports are added by the visitor.
     * @param node the graph node that is being inspected.
     *             It may or may not be a field. If it is a field, it will be added to the class
     *             along with the required imports.
     */
    void fieldSearch(GraphNode node) {
        if (node.getNode() instanceof FieldDeclaration fd) {
            node.addField(fd);
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
                initializer.accept(new Visitor(), node);
            } else if (initializer.isNameExpr()) {
                sa.com.cloudsolutions.antikythera.depsolver.Resolver.resolveNameExpr(node, initializer.asNameExpr(), new NodeList<>());
            }
        }
    }
    
    /**
     * Processes variable declarations.
     * This visitor is intended to be used before the Visitor class. It will identify the variables
     * so that resolving the scope of the method calls becomes a lot easier.
     */
    private static class VariableVisitor extends com.github.javaparser.ast.visitor.VoidVisitorAdapter<GraphNode> {
        @Override
        public void visit(final Parameter n, GraphNode node) {
            DepSolver.names.put(n.getNameAsString(), n.getType());
            node.processTypeArgument(n.getType());
            super.visit(n, node);
        }
        
        @Override
        public void visit(final VariableDeclarationExpr n, GraphNode node) {
            for (VariableDeclarator vd : n.getVariables()) {
                DepSolver.names.put(vd.getNameAsString(), vd.getType());
                if (vd.getType().isClassOrInterfaceType()) {
                    node.processTypeArgument(vd.getType().asClassOrInterfaceType());
                } else if (vd.getType().isArrayType()) {
                    Type t = vd.getType().asArrayType().getComponentType();
                    if (t.isClassOrInterfaceType()) {
                        node.processTypeArgument(t.asClassOrInterfaceType());
                    }
                }
                vd.getInitializer().ifPresent(init -> sa.com.cloudsolutions.antikythera.parser.ImportUtils.addImport(node, init));
            }
            super.visit(n, node);
        }
    }
    
    private class Visitor extends AnnotationVisitor {
        @Override
        public void visit(com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt n, GraphNode node) {
            if (node.getNode() instanceof ConstructorDeclaration cd && node.getEnclosingType().isClassOrInterfaceDeclaration()) {
                for (ClassOrInterfaceType cdecl : node.getEnclosingType().asClassOrInterfaceDeclaration().getExtendedTypes()) {
                    String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), cdecl.getNameAsString());
                    if (fullyQualifiedName != null) {
                        AntikytheraRunTime.getTypeDeclaration(fullyQualifiedName).ifPresent(cid -> {
                            for (ConstructorDeclaration constructorDeclaration : cid.getConstructors()) {
                                if (constructorDeclaration.getParameters().size() == cd.getParameters().size()) {
                                    Graph.createGraphNode(constructorDeclaration);
                                }
                            }
                        });
                    }
                }
            }
        }
        
        @Override
        public void visit(com.github.javaparser.ast.stmt.CatchClause n, GraphNode node) {
            Parameter param = n.getParameter();
            if (param.getType().isUnionType()) {
                com.github.javaparser.ast.type.UnionType ut = param.getType().asUnionType();
                for (Type t : ut.getElements()) {
                    sa.com.cloudsolutions.antikythera.parser.ImportUtils.addImport(node, t);
                }
            } else {
                Type t = param.getType();
                sa.com.cloudsolutions.antikythera.parser.ImportUtils.addImport(node, t);
            }
            super.visit(n, node);
        }
        
        @Override
        public void visit(com.github.javaparser.ast.stmt.ExpressionStmt n, GraphNode arg) {
            if (n.getExpression().isAssignExpr()) {
                com.github.javaparser.ast.expr.AssignExpr assignExpr = n.getExpression().asAssignExpr();
                Expression expr = assignExpr.getValue();
                sa.com.cloudsolutions.antikythera.depsolver.Resolver.processExpression(arg, expr, new NodeList<>());
                
                if (assignExpr.getTarget().isFieldAccessExpr()) {
                    com.github.javaparser.ast.expr.FieldAccessExpr fae = assignExpr.getTarget().asFieldAccessExpr();
                    com.github.javaparser.ast.expr.SimpleName nmae = fae.getName();
                    arg.getEnclosingType().findFirst(FieldDeclaration.class,
                        f -> f.getVariable(0).getNameAsString().equals(nmae.asString()))
                        .ifPresent(Graph::createGraphNode);
                    sa.com.cloudsolutions.antikythera.parser.ImportUtils.addImport(arg, fae);
                }
            }
            super.visit(n, arg);
        }
        
        @Override
        public void visit(com.github.javaparser.ast.stmt.ReturnStmt n, GraphNode node) {
            n.getExpression().ifPresent(e ->
                sa.com.cloudsolutions.antikythera.depsolver.Resolver.processExpression(node, e, new NodeList<>())
            );
            super.visit(n, node);
        }
        
        @Override
        public void visit(com.github.javaparser.ast.expr.MethodCallExpr mce, GraphNode node) {
            sa.com.cloudsolutions.antikythera.parser.MCEWrapper mceWrapper = sa.com.cloudsolutions.antikythera.depsolver.Resolver.resolveArgumentTypes(node, mce);
            sa.com.cloudsolutions.antikythera.depsolver.Resolver.chainedMethodCall(node, mceWrapper);
            super.visit(mce, node);
        }
        
        @Override
        public void visit(com.github.javaparser.ast.expr.BinaryExpr n, GraphNode node) {
            Optional<Node> parent = n.getParentNode();
            if (parent.isPresent() && !(parent.get() instanceof com.github.javaparser.ast.stmt.IfStmt || parent.get() instanceof com.github.javaparser.ast.expr.ConditionalExpr)) {
                Expression left = n.getLeft();
                Expression right = n.getRight();
                
                sa.com.cloudsolutions.antikythera.depsolver.Resolver.processExpression(node, left, new NodeList<>());
                sa.com.cloudsolutions.antikythera.depsolver.Resolver.processExpression(node, right, new NodeList<>());
            }
            super.visit(n, node);
        }
        
        @Override
        public void visit(com.github.javaparser.ast.expr.CastExpr n, GraphNode node) {
            sa.com.cloudsolutions.antikythera.parser.ImportUtils.addImport(node, n.getType());
            super.visit(n, node);
        }
        
        @Override
        public void visit(com.github.javaparser.ast.expr.ObjectCreationExpr oce, GraphNode node) {
            node.processTypeArgument(oce.getType());
            
            sa.com.cloudsolutions.antikythera.parser.MCEWrapper mceWrapper = sa.com.cloudsolutions.antikythera.depsolver.Resolver.resolveArgumentTypes(node, oce);
            sa.com.cloudsolutions.antikythera.depsolver.Resolver.chainedMethodCall(node, mceWrapper);
            
            super.visit(oce, node);
        }
    }
    
    /**
     * Write generated files to disk.
     * 
     * @throws IOException if files could not be written
     */
    private void writeFiles() throws IOException {
        Files.copy(Paths.get(Settings.getProperty(Settings.BASE_PATH).toString().replace("src/main/java", ""), "pom.xml"),
            Paths.get(Settings.getProperty(Settings.OUTPUT_PATH).toString().replace("src/main/java", ""), "pom.xml"),
            StandardCopyOption.REPLACE_EXISTING);
        
        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            boolean write = false;
            CompilationUnit cu = entry.getValue();
            
            List<ImportDeclaration> list = new ArrayList<>(cu.getImports());
            cu.getImports().clear();
            list.sort(Comparator.comparing(NodeWithName::getNameAsString));
            cu.getImports().addAll(list);
            
            for (TypeDeclaration<?> decl : cu.getTypes()) {
                if (decl.isClassOrInterfaceDeclaration()) {
                    if (entry.getKey().endsWith(decl.asClassOrInterfaceDeclaration().getNameAsString())) {
                        write = true;
                    }
                    sortClass(decl.asClassOrInterfaceDeclaration());
                } else if (decl.isEnumDeclaration()) {
                    write = true;
                }
            }
            if (write) {
                CopyUtils.writeFile(AbstractCompiler.classToPath(entry.getKey()), cu.toString());
            }
        }
    }
    
    /**
     * Sorts the members of a class or interface declaration.
     * Fields are sorted alphabetically by their variable name, constructors by their name,
     * and methods by their name. Inner classes are added at the end.
     *
     * @param classOrInterface the class or interface declaration to sort
     */
    public static void sortClass(ClassOrInterfaceDeclaration classOrInterface) {
        List<FieldDeclaration> fields = new ArrayList<>();
        List<ConstructorDeclaration> constructors = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> inners = new ArrayList<>();
        
        for (BodyDeclaration<?> member : classOrInterface.getMembers()) {
            if (member instanceof FieldDeclaration fd) {
                fields.add(fd);
            } else if (member instanceof ConstructorDeclaration cd) {
                constructors.add(cd);
            } else if (member instanceof MethodDeclaration md) {
                methods.add(md);
            } else if (member instanceof ClassOrInterfaceDeclaration md) {
                inners.add(md);
            }
        }
        
        if (!(classOrInterface.getAnnotationByName("NoArgsConstructor").isPresent()
            || classOrInterface.getAnnotationByName("AllArgsConstructor").isPresent()
            || classOrInterface.getAnnotationByName("data").isPresent())) {
            fields.sort(Comparator.comparing(f -> f.getVariable(0).getNameAsString()));
        }
        
        constructors.sort(Comparator.comparing(ConstructorDeclaration::getNameAsString));
        // Sort methods with BeforeEach first, then alphabetically
        methods.sort((m1, m2) -> {
            boolean m1HasBeforeEach = m1.getAnnotationByName("BeforeEach").isPresent();
            boolean m2HasBeforeEach = m2.getAnnotationByName("BeforeEach").isPresent();
            
            if (m1HasBeforeEach && !m2HasBeforeEach) {
                return -1;
            } else if (!m1HasBeforeEach && m2HasBeforeEach) {
                return 1;
            } else {
                return m1.getNameAsString().compareTo(m2.getNameAsString());
            }
        });
        
        classOrInterface.getMembers().clear();
        classOrInterface.getMembers().addAll(fields);
        classOrInterface.getMembers().addAll(constructors);
        classOrInterface.getMembers().addAll(methods);
        classOrInterface.getMembers().addAll(inners);
    }
    
    /**
     * Main entry point for command-line execution.
     * 
     * @param args command line arguments
     * @throws IOException if files could not be read/written
     */
    public static void main(String[] args) throws IOException {
        File yamlFile = new File(Settings.class.getClassLoader().getResource("depsolver.yml").getFile());
        Settings.loadConfigMap(yamlFile);
        DepSolver depSolver = DepSolver.createSolver();
        depSolver.solve();
        
        CopyUtils.createMavenProjectStructure(Settings.getBasePackage(), Settings.getProperty("output_path").toString());
        depSolver.writeFiles();
    }
    
    /**
     * Only for testing. Don't use for anything else.
     * 
     * @return the element at the top of the stack
     */
    public GraphNode peek() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }
}
