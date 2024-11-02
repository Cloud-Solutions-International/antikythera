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
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DepSolver {
    /**
     * The stack for the depth first search.
     */
    private final LinkedList<GraphNode> stack = new LinkedList<>();

    GraphNode nodeBuilder(Node n) throws AntikytheraException {
        GraphNode g = Graph.createGraphNode(n);
        stack.addAll(g.buildNode());
        stack.push(g);
        return g;
    }

    /**
     * Main entry point for the dependency solver
     * @throws IOException if files could not be read
     * @throws AntikytheraException if a dependency could not be resolved.
     */
    private void solve() throws IOException, AntikytheraException {
        AbstractCompiler.preProcess();
        String s = Settings.getProperty("methods").toString();
        String[] parts = s.split("#");

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(parts[0] );
        if (cu != null) {
            Optional<MethodDeclaration> method = cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.getNameAsString().equals(parts[1]))
                    .findFirst();

            if (method.isPresent()) {
                dfs(nodeBuilder(method.get()));
            }
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
                node.setVisited(true);

                fieldSearch(node);
                methodSearch(node);
            }
        }
    }


    /**
     * Search in methods.
     * The return type, all the locals declared inside the method and arguments are searchable.
     * There maybe decorators for the method or some of the arguments. These need to be searched as
     * well.
     * @param node A graph node that represents a method in the code.
     */
    private void methodSearch(GraphNode node) throws AntikytheraException {
        if (node.getEnclosingType() != null) {
            Optional<ClassOrInterfaceDeclaration> c = node.getDestination().getClassByName(
                    node.getEnclosingType().getNameAsString());
            if (node.getNode() instanceof MethodDeclaration md) {
                if (c.isPresent()) {
                    node.getClassDeclaration().addMember(md);
                }

                searchMethodParameters(node, md);
                Type returnType = md.getType();
                String returns = md.getTypeAsString();
                if (!returns.equals("void") && returnType.isClassOrInterfaceType()) {
                    stack.addAll(node.addTypeArguments(returnType.asClassOrInterfaceType()));
                }
                md.accept(new Visitor(), node);
            }
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

    /**
     * Handles calling an external method.
     * An external method will typically have a field, a local variable or a method parameter as
     * scope. If the scope is a field, that will be preserved.
     * @param node a graph node that represents a method in the code.
     * @param scope the scope of the method call.
     * @param mce the method call expression
     * @throws AntikytheraException
     */
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
                                nodeBuilder(md.get());
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
                    nodeBuilder(cls);
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
        PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
        config.setColumnAlignFirstMethodChain(true);
        config.setColumnAlignParameters(true);
        config.setIndentSize(4);
        config.setPrintComments(true);
        config.setPrintJavadoc(true);
        config.setEndOfLineCharacter("\n");
        config.setOrderImports(true);
        PrettyPrinter prettyPrinter = new PrettyPrinter(config);

        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            try {
                CopyUtils.writeFile(
                        AbstractCompiler.classToPath(entry.getKey()), prettyPrinter.print(entry.getValue()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class Visitor extends AnnotationVisitor {

        @Override
        public void visit(VariableDeclarator vd, GraphNode node) {
            solveType(vd.getType(), node);
            vd.getInitializer().ifPresent(init -> {
                try {
                    searchMethodCall(init, node);
                } catch (AntikytheraException e) {
                    throw new DepsolverException(e);
                }
            });
            super.visit(vd, node);
        }

        private void solveType(Type vd, GraphNode node) {

            if (vd.isClassOrInterfaceType()) {
                List<ImportDeclaration> imports = AbstractCompiler.findImport(node.getCompilationUnit(), vd);
                for (ImportDeclaration imp : imports) {
                    try {
                        searchClass(node, imp);
                    } catch (AntikytheraException e) {
                        throw new DepsolverException(e);
                    }
                }
            }
        }

        /**
         * Resolve dependencies for an object creation expression
         * @param oce the object creation expression
         * @param node the graph node.
         */
        @Override
        public void visit(ObjectCreationExpr oce, GraphNode node) {
            solveType(oce.getType(), node);
            for(Expression arg : oce.getArguments()) {
                if(arg.isFieldAccessExpr()) {
                    resolveField(node, arg);
                }
                else if(arg.isMethodCallExpr()) {
                    Optional<Expression> scope = arg.asMethodCallExpr().getScope();
                    if(scope.isPresent() && scope.get().isNameExpr()) {
                        ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(),
                                scope.get().asNameExpr().getNameAsString());
                        try {
                            searchClass(node, imp);
                        } catch (AntikytheraException e) {
                            throw new DepsolverException(e);
                        }
                    }
                }
            }
            super.visit(oce, node);
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
