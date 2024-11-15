package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
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
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
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

    private final Map<String, Type> names = new HashMap<>();

    /**
     * Main entry point for the dependency solver
     * @throws IOException if files could not be read
     * @throws AntikytheraException if a dependency could not be resolved.
     */
    private void solve() throws IOException, AntikytheraException {
        AbstractCompiler.preProcess();
        Object methods = Settings.getProperty("methods");
        if (methods instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    processMethod(s);
                }
            }
        }
        else {
            processMethod(methods.toString());
        }
    }

    /**
     * Process the dependencies of a method that was declared in the application configuration
     * @param s the method name
     * @throws AntikytheraException
     */
     void processMethod(String s) throws AntikytheraException {
        String[] parts = s.split("#");

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(parts[0] );
        if (cu != null) {
            Optional<MethodDeclaration> method = cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.getNameAsString().equals(parts[1]))
                    .findFirst();

            if (method.isPresent()) {
                Graph.createGraphNode(method.get());
                dfs();
            }
        }
    }

    /**
     * Iterative Depth first search
     * @throws AntikytheraException if any of the code inspections fails.
     */
    private void dfs() throws AntikytheraException {
        /*
         * Operates in three stages.
         *
         * First up will try to identify if the node is a field in the class being studied. In that
         * case it will be added to the node
         *
         * The second search we will check if the node is a method, here we will check all the
         * parameters in the method call as well as the return type.
         *
         * Thirdly it will do the same sort of thing for constructors.
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
     * Check if he node is a method and add it to the class.
     *
     * The return type, all the locals declared inside the method and arguments are searchable.
     * There maybe decorators for the method or some of the arguments. Seperate graph nodes will
     * be created for all of these things and pushed onto the stack.
     *
     * @param node A graph node that represents a method in the code.
     */
     void methodSearch(GraphNode node) throws AntikytheraException {
        if (node.getEnclosingType() != null && node.getNode() instanceof MethodDeclaration md) {
            callableSearch(node, md);

            Type returnType = md.getType();
            String returns = md.getTypeAsString();
            if (!returns.equals("void") && returnType.isClassOrInterfaceType()) {
                node.addTypeArguments(returnType.asClassOrInterfaceType());
            }
        }
    }

    /**
     * Search in constructors.
     * All the locals declared inside the constructor and arguments are searchable.
     * Any annotations for the arguments or the constructor will be searched as well.
     * @param node A graph node that represents a constructor
     */
    private void constructorSearch(GraphNode node) throws AntikytheraException {
        if (node.getEnclosingType() != null && node.getNode() instanceof ConstructorDeclaration cd) {
            callableSearch(node, cd);
        }
    }

    private void callableSearch(GraphNode node, CallableDeclaration<?> cd) throws AntikytheraException {
        String className = node.getEnclosingType().getNameAsString();
        Optional<TypeDeclaration> c = node.getDestination().findFirst(TypeDeclaration.class,
                t -> t.getNameAsString().equals(className));

        if (c.isPresent()) {
            node.getTypeDeclaration().addMember(cd);
        }
        searchMethodParameters(node, cd.getParameters());

        names.clear();
        cd.accept(new VariableVisitor(), node);
        cd.accept(new Visitor(), node);
    }

    /**
     * Search method parameters for dependencies.
     * @param node GraphNode representing a method.
     * @param parameters the list of paremeters of that method
     * @throws AntikytheraException if some resolution problem crops up
     */
    private void searchMethodParameters(GraphNode node, NodeList<Parameter> parameters) throws AntikytheraException {
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
     * @throws AntikytheraException
     */
    private void searchClass(GraphNode node, ImportWrapper imp) throws AntikytheraException {
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
     * @throws AntikytheraException if the dependencies cannot be resolved.
     */
     void fieldSearch(GraphNode node) throws AntikytheraException {
        if(node.getNode() instanceof FieldDeclaration fd) {
            node.addField(fd);
        }
    }

    private void sortClass(ClassOrInterfaceDeclaration classOrInterface) {
        List<FieldDeclaration> fields = classOrInterface.getMembers().stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .sorted(Comparator.comparing(f -> f.getVariable(0).getNameAsString()))
                .toList();

        List<ConstructorDeclaration> constructors = classOrInterface.getMembers().stream()
                .filter(ConstructorDeclaration.class::isInstance)
                .map(ConstructorDeclaration.class::cast)
                .sorted(Comparator.comparing(ConstructorDeclaration::getNameAsString))
                .toList();

        List<MethodDeclaration> methods = classOrInterface.getMembers().stream()
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .sorted(Comparator.comparing(MethodDeclaration::getNameAsString))
                .toList();

        // Clear original members
        classOrInterface.getMembers().clear();

        // Add sorted fields and methods back
        classOrInterface.getMembers().addAll(fields);
        classOrInterface.getMembers().addAll(constructors);
        classOrInterface.getMembers().addAll(methods);
    }

    private void writeFiles() throws IOException {
        Files.copy(Paths.get(Settings.getProperty("base_path").toString().replace("src/main/java",""), "pom.xml"),
                Paths.get(Settings.getProperty("output_path").toString().replace("src/main/java",""), "pom.xml"),
                StandardCopyOption.REPLACE_EXISTING);

        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            CompilationUnit cu = entry.getValue();
            List<ImportDeclaration> list = new ArrayList<>(cu.getImports());
            cu.getImports().clear();
            list.sort(Comparator.comparing(NodeWithName::getNameAsString));
            cu.getImports().addAll(list);

            for(TypeDeclaration<?> decl : cu.getTypes()) {
                if (decl.isClassOrInterfaceDeclaration()) {
                    sortClass(decl.asClassOrInterfaceDeclaration());
                }
            }
            CopyUtils.writeFile(
                        AbstractCompiler.classToPath(entry.getKey()), cu.toString());
        }
    }

    public void reset() {
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
                solveType(vd.getType(), node);
                Optional<Expression> init = vd.getInitializer();
                if (init.isPresent()) {
                    if (init.get().isNameExpr()) {
                        addImport(node, init.get().asNameExpr().getNameAsString());
                    }
                }
            }
            super.visit(n, node);
        }
    }

    private List<ImportWrapper> solveType(Type vd, GraphNode node) {
        if (vd.isClassOrInterfaceType()) {
            List<ImportWrapper> imports = AbstractCompiler.findImport(node.getCompilationUnit(), vd);
            for (ImportWrapper imp : imports) {
                try {
                    searchClass(node, imp);
                    FieldDeclaration fieldDeclaration = imp.getField();
                    if (fieldDeclaration != null) {
                        Graph.createGraphNode(fieldDeclaration);
                    }
                } catch (AntikytheraException e) {
                    throw new DepsolverException(e);
                }
            }
            return imports;
        }
        return List.of();
    }

    private class Visitor extends AnnotationVisitor {

        @Override
        public void visit(CatchClause n, GraphNode node) {

            Parameter param = n.getParameter();
            if (param.getType().isUnionType()) {
                UnionType ut = param.getType().asUnionType();
                for (Type t : ut.getElements()) {
                    if (t.isClassOrInterfaceType()) {
                        addImport(node, t.asClassOrInterfaceType().getNameAsString());
                    }
                }
            } else {
                Type t = param.getType();
                if (t.isClassOrInterfaceType()) {
                    ClassOrInterfaceType ct = t.asClassOrInterfaceType();
                    addImport(node, ct.getNameAsString());
                }
            }
            super.visit(n, node);
        }

        @Override
        public void visit(MethodCallExpr mce, GraphNode node) {
            Optional<Expression> scope = mce.getScope();
            try {
                if (scope.isPresent()) {
                    if (scope.get().isThisExpr()) {
                        copyMethod(mce, node.getEnclosingType());
                    }
                    else {
                        externalMethod(node, scope.get(), mce);
                    }
                } else {
                    copyMethod(mce, node.getEnclosingType());
                }

                for (Expression arg : mce.getArguments()) {
                    if (arg.isFieldAccessExpr()) {
                        resolveField(node, arg.asFieldAccessExpr());
                    } else if (arg.isNameExpr()) {
                        if (!names.containsKey(arg.toString())) {
                            addImport(node, arg.asNameExpr().getNameAsString());
                        }
                    } else if (arg.isMethodCallExpr()) {
                        visit(arg.asMethodCallExpr(), node);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                throw new DepsolverException("aa");
            }

            super.visit(mce, node);
        }

        private static void copyMethod(MethodCallExpr mce, TypeDeclaration<?> cdecl) throws AntikytheraException {
            Optional<MethodDeclaration> md = AbstractCompiler.findMethodDeclaration(
                    mce, cdecl
            );
            if (md.isPresent()) {
                Graph.createGraphNode(md.get());
            }
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
            for (Expression arg : oce.getArguments()) {
                if (arg.isFieldAccessExpr()) {
                    resolveField(node, arg.asFieldAccessExpr());
                } else if (arg.isMethodCallExpr()) {
                    Optional<Expression> scope = arg.asMethodCallExpr().getScope();

                    if (scope.isPresent()) {
                        if (scope.get().isNameExpr()) {
                            ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(),
                                    scope.get().asNameExpr().getNameAsString());
                            try {
                                searchClass(node, imp);

                                /*
                                 * We need to find the method declaration and then add it to the stack.
                                 * First step is to find the CompilationUnit. We cannot rely on using the
                                 * import declaration as the other class maybe in the same package and may
                                 * not have an import.
                                 */
                                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                                        AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(),
                                                scope.get().asNameExpr().getNameAsString()));

                                if (cu != null) {
                                    Optional<ClassOrInterfaceDeclaration> cdecl = cu.findFirst(ClassOrInterfaceDeclaration.class,
                                            c -> c.getNameAsString().equals(scope.get().asNameExpr().getNameAsString()));

                                    if (cdecl.isPresent()) {
                                        copyMethod(arg.asMethodCallExpr(), cdecl.get());
                                    }
                                }
                            } catch (AntikytheraException e) {
                                throw new DepsolverException(e);
                            }
                        } else if (scope.get().isFieldAccessExpr()) {
                            resolveField(node, scope.get().asFieldAccessExpr());
                        }
                    }
                } else if (arg.isNameExpr()) {

                    List<FieldDeclaration> fields = node.getEnclosingType().getFields();
                    for (FieldDeclaration fd : fields) {
                        if (fd.getVariable(0).getNameAsString().equals(arg.asNameExpr().getNameAsString())) {
                            try {
                                Graph.createGraphNode(fd);
                            } catch (AntikytheraException e) {
                                throw new DepsolverException(e);
                            }
                        }
                    }

                }
            }

            if (node.getEnclosingType().isClassOrInterfaceDeclaration()) {
                for (ImportWrapper imp : imports) {
                    if (imp.getType() != null) {
                        for (ConstructorDeclaration cdecl : imp.getType().getConstructors()) {
                            try {
                                Graph.createGraphNode(cdecl);
                            } catch (AntikytheraException e) {
                                throw new DepsolverException(e);
                            }
                        }
                    }
                }
            }
            super.visit(oce, node);
        }

        /**
         * Handles calling an external method.
         * An external method will typically have a field, a local variable or a method parameter as
         * scope. If the scope is a field, that will be preserved.
         *
         * @param node  a graph node that represents a method in the code.
         * @param scope the scope of the method call.
         * @param mce   the method call expression
         * @throws AntikytheraException
         */
        private void externalMethod(GraphNode node, Expression scope, MethodCallExpr mce) throws AntikytheraException {
            TypeDeclaration<?> cdecl = node.getEnclosingType();

            if (scope.isNameExpr()) {
                externalViaName(node, scope, mce, cdecl);
            } else if (scope.isFieldAccessExpr()) {
                resolveField(node, scope.asFieldAccessExpr());
            } else if (scope.isMethodCallExpr()) {
                chainedMethodCall(scope);
            }
        }

        private void chainedMethodCall(Expression scope) {
            Optional<Expression> v = scope.asMethodCallExpr().getScope();
            if (v.isPresent()) {
                Expression expr = v.get();
                if (expr.isNameExpr()) {
                    Type t = names.get(expr.asNameExpr().getNameAsString());
                    if (t != null && t.isClassOrInterfaceType()) {
                        ClassOrInterfaceType ct = t.asClassOrInterfaceType();
                        String fullyQualifiedName = ct.getName().getIdentifier() + "." + ct.getName().toString();
                        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);
                        if (cu != null) {
                            TypeDeclaration<?> td = AbstractCompiler.getMatchingType(cu, ct.getNameAsString());
                            if (td != null) {
                                // todo finish this
                            }
                        }
                        // todo finish this
                    }
                }
            }
        }

        private void externalViaName(GraphNode node, Expression scope, MethodCallExpr mce, TypeDeclaration<?> cdecl) throws AntikytheraException {
            NameExpr expr = scope.asNameExpr();
            Optional<FieldDeclaration> fd = cdecl.getFieldByName(expr.getNameAsString());

            Type field = null;

            if (fd.isPresent()) {
                field = fd.get().getElementType();
                node.addField(fd.get());

            } else {
                field = names.get(expr.getNameAsString());
            }


            if (field != null) {
                if (field.isClassOrInterfaceType()) {
                    ImportWrapper im = AbstractCompiler.findImport(node.getCompilationUnit(), field.asClassOrInterfaceType().getNameAsString());
                    if (im != null && im.getType() != null) {
                        try {
                            copyMethod(mce, im.getType());
                        } catch (AntikytheraException e) {
                            throw new DepsolverException(e);
                        }
                    }
                    pushField(node, mce, im);
                }
                for (AnnotationExpr ann : field.getAnnotations()) {
                    addImport(node, ann.getNameAsString());
                }
            } else {
                /*
                 * Can be either a call related to a local or a static call.
                 */
                addImport(node, expr.getNameAsString());
            }
        }

        private static void pushField(GraphNode node, MethodCallExpr mce, ImportWrapper im) {
            if (im != null) {
                if (im.getType() != null) {
                    node.getDestination().addImport(im.getImport());
                    if (mce.isMethodCallExpr()) {
                        try {
                            copyMethod(mce, im.getType());
                        } catch (AntikytheraException e) {
                            throw new DepsolverException(e);
                        }
                    }
                }
            } else if (mce.hasScope()){
                node.getDestination().getPackageDeclaration().ifPresentOrElse(
                        p -> {

                        },
                        () -> {
                            if (im != null) {
                                node.getDestination().addImport(im.getImport());
                            }
                        }
                );
            }
        }
    }

    public static void addImport(GraphNode node, String name) {
        try {
            ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), name);
            if (imp != null) {
                node.getDestination().addImport(imp.getImport());
                if (imp.getType() != null) {
                    Graph.createGraphNode(imp.getType());
                }
                if (imp.getField() != null) {
                    Graph.createGraphNode(imp.getField());
                }
            } else {
                String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), name);
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);

                if (cu != null) {
                    TypeDeclaration<?> t = AbstractCompiler.getMatchingType(cu, name);
                    if (t != null) {
                        Graph.createGraphNode(t);
                    }
                }
            }
        } catch (AntikytheraException e) {
            throw new DepsolverException(e);
        }
    }

    public static void main(String[] args) throws IOException, AntikytheraException {
        File yamlFile = new File(Settings.class.getClassLoader().getResource("depsolver.yml").getFile());
        Settings.loadConfigMap(yamlFile);
        DepSolver depSolver = new DepSolver();
        depSolver.solve();

        CopyUtils.createMavenProjectStructure(Settings.getBasePackage(), Settings.getProperty("output_path").toString());
        depSolver.writeFiles();
    }

    public static void push(GraphNode g) {
        stack.push(g);
    }
}
