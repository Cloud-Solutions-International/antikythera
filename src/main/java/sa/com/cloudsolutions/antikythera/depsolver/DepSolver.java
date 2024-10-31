package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
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
    private Map<String, CompilationUnit> dependencies = new HashMap<>();
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

    private void dfs(GraphNode node) {
        if (node.isVisited()) {
            return;
        }
        g.put(node.hashCode(), node);

        CompilationUnit cu = node.getDestination();
        ClassOrInterfaceDeclaration decl = AbstractCompiler.getEnclosingClassOrInterface(node.getNode());
        dependencies.put(decl.toString(), cu);

        fieldSearch(node);
        methodSearch(node);
    }

    private void methodSearch(GraphNode node) {
        if(node.getNode() instanceof MethodDeclaration md && !g.containsKey(node.hashCode())) {
            for(Parameter p : md.getParameters()) {
                ImportDeclaration imp = AbstractCompiler.findImport(node.getCompilationUnit(), p.getType().asString());
                if (imp != null) {
                    node.getDestination().addImport(imp);
                    String className = imp.getNameAsString();
                    CompilationUnit compilationUnit = AntikytheraRunTime.getCompilationUnit(className);
                    if (compilationUnit != null) {
                        AbstractCompiler.getMatchingClass(compilationUnit, className);
                    }
                }
                for(AnnotationExpr ann : p.getAnnotations()) {
                    ImportDeclaration imp2 = AbstractCompiler.findImport(node.getCompilationUnit(), ann.getNameAsString());
                    if (imp2 != null) {
                        node.getDestination().addImport(imp2);
                    }
                }
            }
        }
    }

    private void fieldSearch(GraphNode node) {
        if(node.getNode() instanceof FieldDeclaration fd && !g.containsKey(node.hashCode())) {
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
