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
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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

    private static DepSolver solver;

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

            if (md.getAnnotationByName("Override").isPresent()) {
                findParentMethods(node, md);
            }

            if(node.getEnclosingType().isClassOrInterfaceDeclaration() && node.getEnclosingType().asClassOrInterfaceDeclaration().isInterface()) {
                findImplementations(node, md);
            }
        }
    }

    private static void findImplementations(GraphNode node, MethodDeclaration md) throws AntikytheraException {
        ClassOrInterfaceDeclaration cdecl = node.getEnclosingType().asClassOrInterfaceDeclaration();
        for (String t : AntikytheraRunTime.findImplementations(cdecl.getFullyQualifiedName().get())) {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(t);
            if (cu != null) {
                TypeDeclaration<?> td = AbstractCompiler.getPublicType(cu);
                if (td != null) {
                    for (MethodDeclaration m : td.getMethodsByName(md.getNameAsString())) {
                        if (m.getParameters().size() == md.getParameters().size()) {
                            Graph.createGraphNode(m);
                        }
                    }
                }
            }
        }
    }

    private static void findParentMethods(GraphNode node, MethodDeclaration md) throws AntikytheraException {
        TypeDeclaration<?> td = node.getTypeDeclaration();
        if (td.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration cdecl = td.asClassOrInterfaceDeclaration();
            for(ClassOrInterfaceType parent : cdecl.getImplementedTypes()) {
                String fqName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), parent.getNameAsString());
                if (fqName != null) {
                    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqName);
                    if (cu != null) {
                        TypeDeclaration<?> parentType = AbstractCompiler.getMatchingType(cu, parent.getNameAsString());
                        if (parentType != null) {
                            for (MethodDeclaration pmd : parentType.getMethodsByName(md.getNameAsString())) {
                                if(pmd.getParameters().size() == md.getParameters().size()) {
                                    Graph.createGraphNode(pmd);
                                }
                            }
                        }
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
            if (cd.isAbstract() && node.getEnclosingType().getFullyQualifiedName().isPresent()) {
                methodOverrides(cd, node.getEnclosingType().getFullyQualifiedName().get());
            }
        }
        searchMethodParameters(node, cd.getParameters());

        names.clear();
        cd.accept(new VariableVisitor(), node);
        cd.accept(new Visitor(), node);
    }

    private static void methodOverrides(CallableDeclaration<?> cd, String className) throws AntikytheraException {

        for (String s : AntikytheraRunTime.findSubClasses(className)) {
            addOverRide(cd, s);
        }

        for (String s : AntikytheraRunTime.findImplementations(className)) {
            addOverRide(cd, s);
        }
    }

    private static void addOverRide(CallableDeclaration<?> cd, String s) throws AntikytheraException {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(s);
        if (cu != null) {
            TypeDeclaration<?> parent = cu.findFirst(TypeDeclaration.class, td -> td.isPublic()).orElse(null);
            if (parent != null) {
                for (MethodDeclaration md : parent.getMethodsByName(cd.getNameAsString())) {
                    if (md.getParameters().size() == cd.getParameters().size()) {
                        Graph.createGraphNode(md);
                    }
                }
            }
        }
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
        List<FieldDeclaration> fields = new ArrayList<>();
        List<ConstructorDeclaration> constructors = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();

        for (BodyDeclaration<?> member : classOrInterface.getMembers()) {
            if (member instanceof FieldDeclaration) {
                fields.add((FieldDeclaration) member);
            } else if (member instanceof ConstructorDeclaration) {
                constructors.add((ConstructorDeclaration) member);
            } else if (member instanceof MethodDeclaration) {
                methods.add((MethodDeclaration) member);
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
                try {
                    if (vd.getType().isClassOrInterfaceType()) {
                        node.addTypeArguments(vd.getType().asClassOrInterfaceType());
                    }
                    else if (vd.getType().isArrayType()) {
                        Type t = vd.getType().asArrayType().getComponentType();
                        if (t.isClassOrInterfaceType()) {
                            node.addTypeArguments(t.asClassOrInterfaceType());
                        }
                    }
                } catch (AntikytheraException e) {
                    throw new DepsolverException(e);
                }

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

    /**
     * See if this can be replaced with addTypeArguments of GraphNode
     * @param vd
     * @param node
     * @return
     */
    @Deprecated
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
        public void visit(ExplicitConstructorInvocationStmt n, GraphNode node) {
            if (node.getNode() instanceof ConstructorDeclaration cd && node.getEnclosingType().isClassOrInterfaceDeclaration()) {
                for (ClassOrInterfaceType cdecl : node.getEnclosingType().asClassOrInterfaceDeclaration().getExtendedTypes()) {
                    String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), cdecl.getNameAsString());
                    if (fullyQualifiedName != null) {
                        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);
                        if (cu != null) {
                            TypeDeclaration<?> cid = AbstractCompiler.getMatchingType(cu, cdecl.getNameAsString());
                            if (cid != null) {
                                for (ConstructorDeclaration constructorDeclaration : cid.getConstructors()) {
                                    if (constructorDeclaration.getParameters().size() == cd.getParameters().size()) {
                                        try {
                                            Graph.createGraphNode(constructorDeclaration);
                                        } catch (AntikytheraException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }
                            }
                        }
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
        public void visit(ExpressionStmt n, GraphNode arg) {
            if (n.getExpression().isAssignExpr()) {
                Expression expr = n.getExpression().asAssignExpr().getValue();
                if (expr.isNameExpr()) {
                    addImport(arg, expr.asNameExpr().getNameAsString());
                }
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(ReturnStmt n, GraphNode node) {
            n.getExpression().ifPresent(e -> {
                try {
                    processExpression(node, e, new NodeList<>());
                } catch (AntikytheraException ex) {
                    throw new DepsolverException(ex);
                }
            });

            super.visit(n, node);
        }

        @Override
        public void visit(MethodCallExpr mce, GraphNode node) {
            try {
                MCEWrapper mceWrapper = solveArgumentTypes(node, mce);
                chainedMethodCall(node, mceWrapper);
            } catch (Exception e) {
                throw new DepsolverException(e);
            }

            super.visit(mce, node);
        }

        private MCEWrapper solveArgumentTypes(GraphNode node, NodeWithArguments<?> mce) throws AntikytheraException {
            MCEWrapper mw = new MCEWrapper();
            NodeList<Type> types = new NodeList<>();

            NodeList<Expression> arguments = mce.getArguments();

            for(Expression arg : arguments) {
                processExpression(node, arg, types);
            }
            if (types.size() == arguments.size()) {
                mw.setArgumentTypes(types);
            }

            mw.setMethodCallExpr(mce);
            return mw;
        }

        private void processExpression(GraphNode node, Expression expr, NodeList<Type> types) throws AntikytheraException {
            if (expr.isNameExpr()) {
                expressionAsNameExpr(node, expr, types);
            }
            else if (expr.isLiteralExpr()) {
                types.add(AbstractCompiler.convertLiteralToType(expr.asLiteralExpr()));
            }
            else if (expr.isFieldAccessExpr()) {
                expressionAsFieldAccess(node, expr, types);
            }
            else if (expr.isMethodCallExpr()) {
                wrapCallable(node, expr.asMethodCallExpr(), types);
            }
            else if (expr.isObjectCreationExpr()) {
                wrapCallable(node, expr.asObjectCreationExpr(), types);
            }
            else if (expr.isMethodReferenceExpr()) {
                expressionAsMethodReference(node, expr);
            }
            else if (expr.isConditionalExpr()) {
                ConditionalExpr ce = expr.asConditionalExpr();
                if (ce.getThenExpr().isNameExpr()) {
                    expressionAsNameExpr(node, ce.getThenExpr(), types);
                }
                if (ce.getElseExpr().isNameExpr()) {
                    expressionAsNameExpr(node, ce.getElseExpr(), types);
                }
            }
            else if (expr.isAssignExpr()) {
                System.out.println("Assign " + expr);
            }
            else {
                // seems other types dont need special handling they are caught else where
            }
        }

        private void expressionAsFieldAccess(GraphNode node, Expression expr, NodeList<Type> types) throws AntikytheraException {
            FieldAccessExpr fae = expr.asFieldAccessExpr();
            Expression scope = fae.getScope();
            if (scope.isNameExpr()) {
                Optional<Type> t = resolveScopedNameExpression(scope, fae, node);
                if (t.isPresent()) {
                    types.add(t.get());
                }
            }
            else {
                if (scope.isFieldAccessExpr()) {
                    expressionAsFieldAccess(node, scope, types);
                }
                else {
                    ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), fae.getNameAsString());
                    if (imp != null) {
                        node.getDestination().addImport(imp.getImport());
                        if (imp.isExternal()) {
                            Optional<Type> ct = getExternalType(fae, imp);
                            if (ct.isPresent()) {
                                types.add(ct.get());
                            }
                        }
                    }
                }
            }
        }

        private void expressionAsMethodReference(GraphNode node, Expression arg) throws AntikytheraException {
            MethodReferenceExpr mre = arg.asMethodReferenceExpr();
            Expression scope = mre.getScope();
            if (scope.isNameExpr()) {
                addImport(node, scope.asNameExpr().getNameAsString());
            }
            else if (scope.isThisExpr()) {
                for (MethodDeclaration m : node.getEnclosingType().getMethodsByName(mre.getIdentifier())) {
                    Graph.createGraphNode(m);
                }
            }
            else if (scope.isTypeExpr()) {
                TypeExpr te = scope.asTypeExpr();
                addImport(node, te.getType().asString());
            }
        }

        private void wrapCallable(GraphNode node, NodeWithArguments<?> arg, NodeList<Type> types) throws AntikytheraException {
            if (arg instanceof MethodCallExpr argMethodCall) {
                MCEWrapper wrap = solveArgumentTypes(node, arg);
                GraphNode gn = chainedMethodCall(node, wrap);
                if (gn != null) {
                    if (gn.getNode() instanceof MethodDeclaration md) {
                        Type t = md.getType();
                        if (t.isClassOrInterfaceType()) {
                            addImport(node, t.asClassOrInterfaceType().getNameAsString());
                        }
                        types.add(md.getType());
                    } else if (gn.getNode() instanceof ClassOrInterfaceDeclaration cid) {
                        Optional<CallableDeclaration<?>> omd = AbstractCompiler.findCallableDeclaration(wrap, cid);
                        if (omd.isPresent()) {
                            CallableDeclaration<?> cd = omd.get();
                            if (cd instanceof MethodDeclaration md) {
                                Type t = md.getType();
                                types.add(md.getType());
                                if (t.isClassOrInterfaceType()) {
                                    addImport(gn, t.asClassOrInterfaceType().getNameAsString());
                                }
                            }
                            cd.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(c -> {
                                addImport(gn, c.getNameAsString());
                            });
                        } else {
                            Type t = lombokSolver(argMethodCall, cid, gn);
                            if (t != null) {
                                types.add(t);
                            }
                        }
                    }
                }
            }
        }

        private Type lombokSolver(MethodCallExpr argMethodCall, ClassOrInterfaceDeclaration cid, GraphNode gn) {
            if (argMethodCall.getNameAsString().startsWith("get") &&
                    cid.getAnnotationByName("Data").isPresent() ||
                    cid.getAnnotationByName("Getter").isPresent()
            ) {
                String field = argMethodCall.getNameAsString().substring(3);
                Optional<FieldDeclaration> fd = cid.getFieldByName(ClassProcessor.classToInstanceName(field));
                if (fd.isPresent()) {
                    Type t = fd.get().getElementType();
                    if (t.isClassOrInterfaceType()) {
                        addImport(gn, t.asClassOrInterfaceType().getNameAsString());
                    }
                    return t;
                }
            }
            return null;
        }

        private Optional<Type> resolveScopedNameExpression(Expression scope, NodeWithSimpleName<?> fae, GraphNode node) throws AntikytheraException {
            Type t = names.get(scope.asNameExpr().getNameAsString());
            if (t != null) {
                return Optional.of(t);
            }
            else {
                ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), scope.asNameExpr().getNameAsString());
                if (imp != null) {
                    node.getDestination().addImport(imp.getImport());
                    if (imp.isExternal()) {
                        return getExternalType(fae, imp);
                    }
                    if (imp.getField() == null ) {
                        if (imp.getImport().isAsterisk()) {
                            TypeDeclaration<?> td = imp.getType();
                            createFieldNode(fae, td);
                        }
                        else {
                            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp.getImport().getNameAsString());
                            if (cu != null) {
                                TypeDeclaration<?> td = AbstractCompiler.getPublicType(cu);
                                createFieldNode(fae, td);
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        }

        private static void createFieldNode(NodeWithSimpleName<?> fae, TypeDeclaration<?> td) throws AntikytheraException {
            if (td != null) {
                Optional<FieldDeclaration> fieldByName = td.getFieldByName(fae.getNameAsString());
                if (fieldByName.isPresent()) {
                    Graph.createGraphNode(fieldByName.get());
                }
            }
        }

        private GraphNode copyMethod(MCEWrapper mceWrapper, GraphNode node) throws AntikytheraException {
            TypeDeclaration<?> cdecl = node.getEnclosingType();
            if (cdecl != null) {
                Optional<CallableDeclaration<?>> md = AbstractCompiler.findCallableDeclaration(
                        mceWrapper, cdecl
                );

                if (md.isPresent()) {
                    CallableDeclaration<?> method = md.get();
                    for (Type ex : method.getThrownExceptions()) {
                        addImport(node, ex.asString());
                    }

                    if (method.isAbstract()) {
                        Optional<ClassOrInterfaceDeclaration> parent = method.findAncestor(ClassOrInterfaceDeclaration.class);

                        if (!parent.get().isInterface()) {
                            Optional<CallableDeclaration<?>> overRides = AbstractCompiler.findMethodDeclaration(mceWrapper, cdecl, false);
                            if (overRides.isPresent()) {
                                Graph.createGraphNode(overRides.get());
                            }
                        }
                    }
                    return Graph.createGraphNode(method);
                } else if (mceWrapper.getMethodCallExpr() instanceof MethodCallExpr mce && cdecl instanceof ClassOrInterfaceDeclaration decl) {
                    Type t = lombokSolver(mce, decl, node);
                    if (t != null && t.isClassOrInterfaceType()) {
                        return addImport(node, t.asClassOrInterfaceType().getNameAsString());
                    } else {
                        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), mce.getNameAsString());
                        if (imp != null) {
                            if (imp.getMethodDeclaration() != null) {
                                node.getDestination().addImport(imp.getImport());
                                Graph.createGraphNode(imp.getMethodDeclaration());
                            }
                        }
                        else {
                            // desperate measure hack
                            // todo remove this
                            for (MethodDeclaration method : decl.getMethodsByName(mce.getNameAsString())) {
                                if (method.getParameters().size() == mce.getArguments().size()) {
                                    Graph.createGraphNode(method);
                                }
                            }
                        }
                    }
                }
            }

            return null;
        }

        @Override
        public void visit(BinaryExpr n, GraphNode node) {
            Optional<Node> parent = n.getParentNode();
            if (parent.isPresent() && !(parent.get() instanceof IfStmt || parent.get() instanceof ConditionalExpr)) {
                Expression left = n.getLeft();
                Expression right = n.getRight();
                try {
                    processExpression(node, left, new NodeList<>());
                    processExpression(node, right, new NodeList<>());
                } catch (AntikytheraException e) {
                    throw new DepsolverException(e);
                }
            }
            super.visit(n, node);
        }

        @Override
        public void visit(CastExpr n, GraphNode node) {
            Type t = n.getType();
            if (t.isClassOrInterfaceType()) {
                addImport(node, t.asClassOrInterfaceType().getNameAsString());
            }
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
            try {
                MCEWrapper mceWrapper = solveArgumentTypes(node, oce);
                chainedMethodCall(node, mceWrapper);
            } catch (Exception e) {
                throw new DepsolverException(e);
            }


            super.visit(oce, node);
        }

        private GraphNode chainedMethodCall(GraphNode node, MCEWrapper mceWrapper) throws AntikytheraException {
            if (mceWrapper.getMethodCallExpr() instanceof MethodCallExpr mce) {
                LinkedList<Expression> chain = Evaluator.findScopeChain(mce);

                if (chain.isEmpty()) {
                    copyMethod(mceWrapper, node);
                    return node;
                } else {
                    GraphNode gn = evaluateScopeChain(node, chain);
                    if (gn != null) {
                        copyMethod(mceWrapper, gn);
                        return gn;
                    }
                }
            }
            else if (mceWrapper.getMethodCallExpr() instanceof ObjectCreationExpr oce) {
                if (oce.getType().isClassOrInterfaceType()) {
                    GraphNode gn = addImport(node, oce.getType().asClassOrInterfaceType().getNameAsString());
                    if (gn != null) {
                        copyMethod(mceWrapper, gn);
                        return gn;
                    }
                }
            }
            return null;
        }

        private GraphNode evaluateScopeChain(GraphNode node, LinkedList<Expression> chain) throws AntikytheraException {
            GraphNode gn = node;
            while (!chain.isEmpty() && gn != null) {
                Expression expr = chain.pollLast();
                if (expr.isFieldAccessExpr()) {
                    FieldAccessExpr fieldAccessExpr = expr.asFieldAccessExpr();
                    gn = resolveField(gn, fieldAccessExpr);
                }
                else if (expr.isMethodCallExpr()) {
                     gn = copyMethod(solveArgumentTypes(gn, expr.asMethodCallExpr()), gn);
                }
                else if (expr.isNameExpr()) {
                    gn = evaluateNameExpr(expr, gn);
                }
            }

            return gn;
        }

        private GraphNode evaluateNameExpr(Expression expr, GraphNode gn) throws AntikytheraException {
            NameExpr nameExpr = expr.asNameExpr();
            TypeDeclaration<?> cdecl = gn.getEnclosingType();
            Type t = names.get(expr.toString());
            if (t == null) {
                Optional<FieldDeclaration> fd = cdecl.getFieldByName(nameExpr.getNameAsString());

                if (fd.isPresent()) {
                    Type field = fd.get().getElementType();
                    gn.addField(fd.get());

                    if (field != null) {
                        for (AnnotationExpr ann : field.getAnnotations()) {
                            addImport(gn, ann.getNameAsString());
                        }

                        Type elementType = field.getElementType();
                        if (elementType.isClassOrInterfaceType()) {
                            Optional<NodeList<Type>> types = elementType.asClassOrInterfaceType().getTypeArguments();
                            if (types.isPresent()) {
                                for (Type type : types.get()) {
                                    if (type.isClassOrInterfaceType()) {
                                        addImport(gn, type.asClassOrInterfaceType().getNameAsString());
                                    }
                                }

                            }
                            gn = addImport(gn, elementType.asClassOrInterfaceType().getName().toString());
                        } else {
                            gn = addImport(gn, elementType.toString());
                        }
                    }
                }
                else {
                    gn = addImport(gn, nameExpr.getName().toString());
                }
            }
            else {
                gn = addImport(gn, t.asString());
            }
            return gn;
        }

    }

    private void expressionAsNameExpr(GraphNode node, Expression arg, NodeList<Type> types) throws AntikytheraException {
        Type t = names.get(arg.asNameExpr().getNameAsString());
        if (t != null) {
            types.add(t);
        }
        else if (node.getEnclosingType().isClassOrInterfaceDeclaration()){
            ClassOrInterfaceDeclaration cdecl = node.getEnclosingType().asClassOrInterfaceDeclaration();
            Optional<FieldDeclaration> fd = cdecl.getFieldByName(arg.asNameExpr().getNameAsString());

            if (fd.isPresent()) {
                node.addField(fd.get());
                Type field = fd.get().getElementType();

                if (field != null) {
                    types.add(field);
                    addImport(node, field.getElementType().asString());
                    for (AnnotationExpr ann : field.getAnnotations()) {
                        addImport(node, ann.getNameAsString());
                    }
                }
            }
            else {
                addImport(node, arg.asNameExpr().getNameAsString());
            }
        }
    }

    private static Optional<Type> getExternalType(NodeWithSimpleName<?> fae, ImportWrapper imp) {
        try {
            Class<?> c = Class.forName(imp.getNameAsString());
            Field f = c.getField(fae.getNameAsString());
            ClassOrInterfaceType ct = new ClassOrInterfaceType(null, f.getType().getTypeName());
            return Optional.of(ct);

        } catch (ReflectiveOperationException e) {
            System.err.println(e.getMessage());
        }
        return Optional.empty();
    }

    public static GraphNode addImport(GraphNode node, String name) {
        GraphNode returnValue = null;
        try {
            ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), name);
            if (imp != null) {
                node.getDestination().addImport(imp.getImport());
                if (imp.getType() != null) {
                    returnValue = Graph.createGraphNode(imp.getType());
                }
                if (imp.getField() != null) {
                    Graph.createGraphNode(imp.getField());
                }
                else if (imp.getImport().isAsterisk() && !imp.isExternal())
                {
                    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp.getImport().getNameAsString());
                    if (cu != null) {
                        TypeDeclaration<?> td = AbstractCompiler.getMatchingType(cu, name);
                        if (td != null) {
                            Graph.createGraphNode(td);
                        }
                    }
                }
            } else {
                String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), name);
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);

                if (cu != null) {
                    TypeDeclaration<?> t = AbstractCompiler.getMatchingType(cu, name);
                    if (t != null) {
                        returnValue = Graph.createGraphNode(t);
                    }
                }
            }
        } catch (AntikytheraException e) {
            throw new DepsolverException(e);
        }
        return returnValue;
    }
    public static void initializeField(FieldDeclaration field, GraphNode node) throws AntikytheraException {
        solver.initField(field, node);
    }

    public void initField(FieldDeclaration field, GraphNode node) throws AntikytheraException {
        Optional<Expression> init = field.getVariables().get(0).getInitializer();
        if (init.isPresent()) {
            Expression initializer = init.get();
            if (initializer.isObjectCreationExpr() || initializer.isMethodCallExpr()) {
                initializer.accept(new Visitor(), node);
            }
            else if(initializer.isNameExpr()) {
                expressionAsNameExpr(node, initializer, new NodeList<>());
            }
        }
    }

    public static DepSolver createSolver() {
        if(solver == null) {
            solver = new DepSolver();
        }
        else {
            solver.names.clear();
            DepSolver.stack.clear();
        }
        return solver;
    }

    protected DepSolver() {}

    public static void main(String[] args) throws IOException, AntikytheraException {
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
}
