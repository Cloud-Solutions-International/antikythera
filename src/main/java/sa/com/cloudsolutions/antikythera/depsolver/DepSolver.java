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
import com.github.javaparser.ast.nodeTypes.NodeWithName;
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
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

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

public class DepSolver {
    /**
     * The stack for the depth first search.
     */
    private static final LinkedList<GraphNode> stack = new LinkedList<>();

    private static final Map<String, Type> names = new HashMap<>();

    private static DepSolver solver;

    /**
     * Main entry point for the dependency solver
     * @throws IOException if files could not be read
     */
    private void solve() throws IOException {
        AbstractCompiler.preProcess();
        for (String method : Settings.getPropertyList("methods", String.class)) {
            processMethod(method);
        }
    }

    /**
     * Process the dependencies of a method that was declared in the application configuration
     * @param s the method name
     */
     public void processMethod(String s)  {
        String[] parts = s.split("#");

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(parts[0] );
        if (cu != null) {
            cu.findAll(MethodDeclaration.class, m -> m.getNameAsString().equals(parts[1]))
                    .forEach(Graph::createGraphNode);
            dfs();
        }
    }

    /**
     * Iterative Depth first search
     * @ if any of the code inspections fails.
     */
    public void dfs()  {
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
        while (! stack.isEmpty()) {
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
     void methodSearch(GraphNode node)  {
        if (node.getEnclosingType() != null && node.getNode() instanceof MethodDeclaration md) {
            callableSearch(node, md);

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

            for (Type thrownException : md.getThrownExceptions()) {
                ImportUtils.addImport(node, thrownException);

            }

            if (md.getAnnotationByName("Override").isPresent()) {
                findParentMethods(node, md);
            }

            if(node.getEnclosingType().isClassOrInterfaceDeclaration() && node.getEnclosingType().asClassOrInterfaceDeclaration().isInterface()) {
                findImplementations(node, md);
            }
        }
    }

    private static void findImplementations(GraphNode node, MethodDeclaration md)  {
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

    private static void findParentMethods(GraphNode node, MethodDeclaration md)  {
        TypeDeclaration<?> td = node.getTypeDeclaration();
        if (td.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration cdecl = td.asClassOrInterfaceDeclaration();
            for(ClassOrInterfaceType parent : cdecl.getImplementedTypes()) {
                String fqName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), parent.getNameAsString());
                if (fqName != null) {
                    AntikytheraRunTime.getTypeDeclaration(fqName).ifPresent(parentType -> {
                        for (MethodDeclaration pmd : parentType.getMethodsByName(md.getNameAsString())) {
                            if(pmd.getParameters().size() == md.getParameters().size()) {
                                Graph.createGraphNode(pmd);
                            }
                        }
                    });
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
    private void constructorSearch(GraphNode node)  {
        if (node.getEnclosingType() != null && node.getNode() instanceof ConstructorDeclaration cd) {
            callableSearch(node, cd);
        }
    }

    private void callableSearch(GraphNode node, CallableDeclaration<?> cd)  {
        String className = node.getEnclosingType().getNameAsString();
        node.getDestination().findFirst(TypeDeclaration.class,
                t -> t.getNameAsString().equals(className)).ifPresent(c -> {
                    node.getTypeDeclaration().addMember(cd);
                    if (cd.isAbstract() && node.getEnclosingType().getFullyQualifiedName().isPresent()) {
                        methodOverrides(cd, node.getEnclosingType().getFullyQualifiedName().get());
                    }
                });

        searchMethodParameters(node, cd.getParameters());

        names.clear();
        cd.accept(new VariableVisitor(), node);
        cd.accept(new Visitor(), node);
    }

    private static void methodOverrides(CallableDeclaration<?> cd, String className)  {

        for (String s : AntikytheraRunTime.findSubClasses(className)) {
            addOverRide(cd, s);
        }

        for (String s : AntikytheraRunTime.findImplementations(className)) {
            addOverRide(cd, s);
        }
    }

    private static void addOverRide(CallableDeclaration<?> cd, String s)  {
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
     * @ if some resolution problem crops up
     */
    private void searchMethodParameters(GraphNode node, NodeList<Parameter> parameters)  {
        for(Parameter p : parameters) {
            List<ImportWrapper> imports = AbstractCompiler.findImport(node.getCompilationUnit(), p.getType());
            for(ImportWrapper imp : imports) {
                searchClass(node, imp);
            }

            for(AnnotationExpr ann : p.getAnnotations()) {
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
     * @
     */
    private void searchClass(GraphNode node, ImportWrapper imp)  {
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
     * @ if the dependencies cannot be resolved.
     */
    void fieldSearch(GraphNode node)  {
        if(node.getNode() instanceof FieldDeclaration fd) {
            node.addField(fd);
        }
    }

    private void sortClass(ClassOrInterfaceDeclaration classOrInterface) {
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
        methods.sort(Comparator.comparing(MethodDeclaration::getNameAsString));

        classOrInterface.getMembers().clear();
        classOrInterface.getMembers().addAll(fields);
        classOrInterface.getMembers().addAll(constructors);
        classOrInterface.getMembers().addAll(methods);
        classOrInterface.getMembers().addAll(inners);
    }

    private void writeFiles() throws IOException {
        Files.copy(Paths.get(Settings.getProperty("base_path").toString().replace("src/main/java",""), "pom.xml"),
                Paths.get(Settings.getProperty("output_path").toString().replace("src/main/java",""), "pom.xml"),
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

    public static void reset() {
         stack.clear();
         names.clear();
         Graph.getDependencies().clear();
         Graph.getNodes().clear();
    }

    /**
     * Processes variable declarations.
     * This visitor is intended to be used before the Visitor class. It will identify the variables
     * so that resolving the scope of the method calls becomes a lot easier.
     */
    private class VariableVisitor extends VoidVisitorAdapter<GraphNode> {

        /**
         * Deals with parameters in method declarations.
         */
        @Override
        public void visit(final Parameter n, GraphNode node) {
            names.put(n.getNameAsString(), n.getType());
            solveType(n.getType(), node);
            super.visit(n, node);
        }


        @Override
        public void visit(final VariableDeclarationExpr n, GraphNode node) {

            for(VariableDeclarator vd : n.getVariables()) {

                names.put(vd.getNameAsString(), vd.getType());
                if (vd.getType().isClassOrInterfaceType()) {
                    node.processTypeArgument(vd.getType().asClassOrInterfaceType());
                }
                else if (vd.getType().isArrayType()) {
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
     * See if this can be replaced with addTypeArguments of GraphNode
     * @param vd
     * @param node
     * @return
     */
    @Deprecated
    private List<ImportWrapper> solveType(Type vd, GraphNode node)  {
        if (vd.isClassOrInterfaceType()) {
            ClassOrInterfaceType ctype = vd.asClassOrInterfaceType();
            node.processTypeArgument(ctype);
        } else {
            ImportUtils.addImport(node, vd);
        }
        return List.of();
    }

    private class Visitor extends AnnotationVisitor {

        @Override
        public void visit(ExplicitConstructorInvocationStmt n, GraphNode node) {
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
                            .ifPresent(Graph::createGraphNode);
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
            Optional<Node> parent = n.getParentNode();
            if (parent.isPresent() && !(parent.get() instanceof IfStmt || parent.get() instanceof ConditionalExpr)) {
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

        /**
         * Resolve dependencies for an object creation expression
         *
         * @param oce  the object creation expression
         * @param node the graph node.
         */
        @Override
        public void visit(ObjectCreationExpr oce, GraphNode node) {
            List<ImportWrapper> imports = solveType(oce.getType(), node);
            for (ImportWrapper imp : imports) {
                node.getDestination().addImport(imp.getImport());
            }
            MCEWrapper mceWrapper = Resolver.resolveArgumentTypes(node, oce);
            Resolver.chainedMethodCall(node, mceWrapper);

            super.visit(oce, node);
        }
    }

    public static void initializeField(FieldDeclaration field, GraphNode node)  {
        solver.initField(field, node);
    }

    public void initField(FieldDeclaration field, GraphNode node)  {
        Optional<Expression> init = field.getVariables().get(0).getInitializer();
        if (init.isPresent()) {
            Expression initializer = init.get();
            if (initializer.isObjectCreationExpr() || initializer.isMethodCallExpr()) {
                initializer.accept(new Visitor(), node);
            }
            else if(initializer.isNameExpr()) {
                Resolver.resolveNameExpr(node, initializer.asNameExpr(), new NodeList<>());
            }
        }
    }

    public static DepSolver createSolver() {
        if(solver == null) {
            solver = new DepSolver();
        }
        else {
            DepSolver.names.clear();
            DepSolver.stack.clear();
        }
        return solver;
    }

    protected DepSolver() {}

    public static void main(String[] args) throws IOException{
        File yamlFile = new File(Settings.class.getClassLoader().getResource("depsolver.yml").getFile());
        Settings.loadConfigMap(yamlFile);
        DepSolver depSolver = DepSolver.createSolver();
        depSolver.solve();

        CopyUtils.createMavenProjectStructure(Settings.getBasePackage(), Settings.getProperty("output_path").toString());
        depSolver.writeFiles();
    }

    public static void push(GraphNode g) {
        stack.push(g);
    }

    public static Map<String, Type> getNames() {
        return names;
    }

    /**
     * Only for testing. Don't use for anything else
     * @return the element at the top of the stack.
     */
    public GraphNode peek() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }
}
