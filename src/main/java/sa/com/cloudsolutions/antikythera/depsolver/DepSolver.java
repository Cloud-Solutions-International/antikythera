package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DepSolver {
    /**
     * Map of fully qualified class names and their generated compilation units.
     *
     * For most classes the generated compilation unit will only be a subset of the input
     * compilation unit.
     */
    private final Map<String, CompilationUnit> dependencies = new HashMap<>();

    /**
     * Map of nodes with their hash code as the key.
     * This is essentially our graph.
     */
    private final Map<Integer, GraphNode> graph = new HashMap<>();

    /**
     * The stack for the depth first search.
     */
    private final LinkedList<GraphNode> stack = new LinkedList<>();

    GraphNode nodeBuilder(Node n) throws AntikytheraException {
        GraphNode g = new GraphNode(n);
        stack.addAll(g.buildNode());
        stack.push(g);
        return g;
    }

    private void solve() throws IOException, AntikytheraException {
        AbstractCompiler.preProcess();
        String s = Settings.getProperty("methods").toString();
        String[] parts = s.split("#");

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(parts[0] );

        Optional<MethodDeclaration> method = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(parts[1]))
                .findFirst();

        if (method.isPresent()) {
            dfs(nodeBuilder(method.get()));
        }
    }

    /**
     * Recursive Depth first search
     * @param v the node to search from
     * @throws AntikytheraException if any of the code inspections fails.
     */
    private void dfs(GraphNode v) throws AntikytheraException {
        stack.push(v);

        while (! stack.isEmpty()) {
            GraphNode node = stack.pollLast();
            if (!node.isVisited()) {
                setVisited(node);

                CompilationUnit cu = node.getDestination();
                node.getDestination().getClassByName(node.getEnclosingType().getNameAsString()).ifPresent(c -> {
                    if (c.isClassOrInterfaceDeclaration() && node.getNode() instanceof MethodDeclaration md) {
                        node.getClassDeclaration().addMember(md);
                    }
                });

                ClassOrInterfaceDeclaration decl = AbstractCompiler.getEnclosingClassOrInterface(node.getNode());
                dependencies.put(decl.getFullyQualifiedName().get(), cu);

                fieldSearch(node);
                methodSearch(node);
            }
        }
    }

    /**
     * Marks the node as visited and keeps track nodes in the graph to avoid duplication.
     * @param node the graph node to mark as visited
     */
    private void setVisited(GraphNode node) {
        node.setVisited(true);
        graph.put(node.hashCode(), node);
    }

    /**
     * Search in methods.
     * The return type, all the locals declared inside the method and arguments are searchable.
     * There maybe decorators for the method or some of the arguments. These need to be searched as
     * well.
     * @param node A graph node that represents a method in the code.
     */
    private void methodSearch(GraphNode node) throws AntikytheraException {
        if(node.getNode() instanceof MethodDeclaration md ) {
            searchMethodParameters(node, md);
            String returns = md.getTypeAsString();
            if(!returns.equals("void")) {
                ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(), returns);
                searchClass(node, imp);
            }
            md.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(VariableDeclarator vd, Void arg) {
                    solveType(vd.getType(), node);
                    System.out.println("VD: "  + vd);

                    vd.getInitializer().ifPresent(init -> {
                        try {
                            searchMethodCall(init, node);
                        } catch (AntikytheraException e) {
                            throw new RuntimeException(e);
                        }
                    });


                    super.visit(vd, arg);
                }

                private void solveType(Type vd, GraphNode node) {

                    if (vd.isClassOrInterfaceType()) {
                        List<ImportDeclaration> imports = AbstractCompiler.findImport(node.getCompilationUnit(), vd);
                        for (ImportDeclaration imp : imports) {
                            try {
                                searchClass(node, imp);
                            } catch (AntikytheraException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }

                @Override
                public void visit(ObjectCreationExpr oce, Void arg) {
                    solveType(oce.getType(), node);
                    System.out.println("OCE:" + oce);
                    super.visit(oce, arg);
                }
            }, null);
        }
    }

    private void searchMethodCall(Expression init, GraphNode node) throws AntikytheraException {
        if (init.isMethodCallExpr()) {
            MethodCallExpr mce = init.asMethodCallExpr();
            Optional<Expression> scope = mce.getScope();
            if(scope.isPresent()) {
                externalMethod(node, scope.get(), mce);
            }
        }
    }

    private void externalMethod(GraphNode node, Expression scope, MethodCallExpr mce) throws AntikytheraException {
        ClassOrInterfaceDeclaration cdecl = node.getEnclosingType();

        if (scope.isNameExpr()) {
            NameExpr expr = scope.asNameExpr();
            Optional<FieldDeclaration> fd = cdecl.getFieldByName(expr.getNameAsString());
            if(fd.isPresent()) {
                /*
                 * We have found a matching field declaration, next up we need to find the
                 * CompilationUnit. If the cu is absent that means the class comes from a
                 * compiled binary and we can just include the whole thing as a dependency.
                 *
                 * The other side of the coin is a lot harder. If we have a cu, we need to
                 * find the corresponding class declaration for the field and then go
                 * looking in it for the method of interest.
                 */
                String fqname = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(),
                        fd.get().getElementType().toString());
                if (fqname != null) {
                    ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(), fqname);
                    if (imp != null) {
                        node.getDestination().addImport(imp);
                    }
                    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqname);
                    if (cu != null) {
                        String cname = fd.get().getElementType().asClassOrInterfaceType().getNameAsString();
                        TypeDeclaration<?> otherDecl = AbstractCompiler.getMatchingClass(cu, cname);
                        if (otherDecl != null && otherDecl.isClassOrInterfaceDeclaration()) {
                            Optional<MethodDeclaration> md = AbstractCompiler.findMethodDeclaration(
                                    mce, otherDecl.asClassOrInterfaceDeclaration());
                            if (md.isPresent()) {
                                GraphNode g = this.graph.get(md.get().hashCode());
                                if (g != null) {
                                    if (!g.isVisited()) {
                                        stack.push(g);
                                    }
                                }
                                else {
                                    nodeBuilder(md.get());
                                }
                            }
                        }
                    }
                }
                /*
                 * Now we mark the field declaration as part of the source code to preserve from
                 * current class.
                 */
                node.getClassDeclaration().addMember(fd.get());

                for(AnnotationExpr ann : fd.get().getAnnotations()) {
                    ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(), ann.getNameAsString());
                    if (imp != null) {
                        node.getDestination().addImport(imp);
                    }
                }

            }
        }
    }

    private void searchMethodParameters(GraphNode node, MethodDeclaration md) throws AntikytheraException {
        for(Parameter p : md.getParameters()) {
            List<ImportDeclaration> imports = AbstractCompiler.findImport(node.getCompilationUnit(), p.getType());
            for(ImportDeclaration imp : imports) {
                searchClass(node, imp);
            }

            for(AnnotationExpr ann : p.getAnnotations()) {
                ImportDeclaration imp2 = AbstractCompiler.findImport(node.getCompilationUnit(), ann.getNameAsString());
                searchClass(node, imp2);
            }
        }
    }

    /**
     * Search for an outgoing edge to another class
     * @param node the current node
     * @param imp the import declaration for the other class.
     * @throws AntikytheraException
     */
    private void searchClass(GraphNode node, ImportDeclaration imp) throws AntikytheraException {
        /*
         * It is likely that this is a DTO an Entity or a model. So we will assume that all the
         * fields are required along with their respective annotations.
         */
        if (imp != null) {
            node.getDestination().addImport(imp);
            String className = imp.getNameAsString();
            CompilationUnit compilationUnit = AntikytheraRunTime.getCompilationUnit(className);
            if (compilationUnit != null) {
                /*
                 * The presence of a compilation unit indicates that this class is available as
                 * source code. Therefore it becomes part of the dependency graph of classes that
                 * we are trying to extract.
                 */
                TypeDeclaration<?> cls = AbstractCompiler.getMatchingClass(compilationUnit, className);
                if(cls != null) {
                    GraphNode n = graph.get(cls.hashCode());
                    if (n == null) {
                        nodeBuilder(cls);
                    }
                }
            }
            node.getDestination().addImport(imp);
        }
    }

    private void fieldSearch(GraphNode node) {
        if(node.getNode() instanceof FieldDeclaration fd) {
            for(AnnotationExpr ann : fd.getAnnotations()) {
                ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(), ann.getNameAsString());
                if (imp != null) {
                    node.getDestination().addImport(imp);
                }
            }
        }
    }

    private void writeFiles() {
        for (Map.Entry<String, CompilationUnit> entry : dependencies.entrySet()) {
            try {
                CopyUtils.writeFile(AbstractCompiler.classToPath(entry.getKey()), entry.getValue().toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
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
}
