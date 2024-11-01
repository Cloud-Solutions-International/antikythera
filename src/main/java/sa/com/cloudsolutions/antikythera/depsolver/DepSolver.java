package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DepSolver {
    /**
     * Map of fully qualified class names and their generated compilation units.
     *
     * For most classes the generated compilation unit will only be a subset of the input
     * compilation unit.
     */
    private Map<String, CompilationUnit> dependencies = new HashMap<>();

    /**
     * Map of nodes with their hash code as the key.
     * This is essentially our graph.
     */
    private Map<Integer, GraphNode> g = new HashMap<>();

    private void solve() throws IOException, AntikytheraException {
        String basePath = Settings.getBasePath();
        AbstractCompiler.preProcess();
        String s = Settings.getProperty("methods").toString();
        String[] parts = s.split("#");

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(parts[0] );

        Optional<MethodDeclaration> method = cu.findAll(MethodDeclaration.class).stream()
                .filter(m -> m.getNameAsString().equals(parts[1]))
                .findFirst();

        GraphNode g = new GraphNode(method.get());
        dfs(g);
        System.out.println(g);

    }

    private void dfs(GraphNode node) throws AntikytheraException {
        if (node.isVisited()) {
            return;
        }
        node.setVisited(true);
        g.put(node.hashCode(), node);

        CompilationUnit cu = node.getDestination();
        node.getDestination().getClassByName(node.getEnclosingType().getNameAsString()).ifPresent(c -> {
            if (c.isClassOrInterfaceDeclaration() && node.getNode() instanceof  MethodDeclaration md) {
                c.asClassOrInterfaceDeclaration().addMember(md);
            }
        });

        ClassOrInterfaceDeclaration decl = AbstractCompiler.getEnclosingClassOrInterface(node.getNode());
        dependencies.put(decl.getFullyQualifiedName().get(), cu);

        fieldSearch(node);
        methodSearch(node);
    }

    /**
     * Search in methods.
     * The return type, all the locals declared inside the method and arguments are searchable.
     * There maybe decorators for the method or some of the arguments. These need to be searched as
     * well.
     * @param node
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
                public void visit(VariableDeclarationExpr vd, Void arg) {
                    Type type = vd.getElementType();
                    if (type.isClassOrInterfaceType()) {
                        ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(), type.asClassOrInterfaceType().getNameAsString());
                        try {
                            searchClass(node, imp);
                        } catch (AntikytheraException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    System.out.println(vd);
                    super.visit(vd, arg);
                }

                @Override
                public void visit(ObjectCreationExpr vd, Void arg) {
                    System.out.println(vd);
                    super.visit(vd, arg);
                }
            }, null);
        }
    }

    private void searchMethodParameters(GraphNode node, MethodDeclaration md) throws AntikytheraException {
        for(Parameter p : md.getParameters()) {
            ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(), p.getType());
            searchClass(node, imp);
            for(AnnotationExpr ann : p.getAnnotations()) {
                ImportDeclaration imp2 = AbstractCompiler.findImport(node.getCompilationUnit(), ann.getNameAsString());
                searchClass(node, imp2);
            }
        }
    }

    /**
     * Search the class
     * @param node
     * @param imp
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
                TypeDeclaration<?> cls = AbstractCompiler.getMatchingClass(compilationUnit, className);
                GraphNode n = new GraphNode(cls);
                dfs(n);
            }
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

    public static void main(String[] args) throws IOException, AntikytheraException {
        File yamlFile = new File(Settings.class.getClassLoader().getResource("depsolver.yml").getFile());
        Settings.loadConfigMap(yamlFile);
        DepSolver depSolver = new DepSolver();
        depSolver.solve();
    }
}
