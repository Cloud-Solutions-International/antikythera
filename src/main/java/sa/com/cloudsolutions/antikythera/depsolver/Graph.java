package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Graph {
    /**
     * Map of fully qualified class names and their generated compilation units.
     *
     * For most classes the generated compilation unit will only be a subset of the input
     * compilation unit.
     */
    private static final Map<String, CompilationUnit> dependencies = new HashMap<>();
    /**
     * Map of nodes with their hash code as the key.
     * This is essentially our graph.
     */
    private static final Map<Integer, GraphNode> nodes = new HashMap<>();

    private Graph() {

    }

    /**
     * Creates a new graph node from the AST node if required.
     * If the GraphNode is already present in the graph, the same object is returned.
     * @param n AST node
     * @return a GraphNode that may have already existed.
     * @throws AntikytheraException if resolution fails
     */
    public static GraphNode createGraphNode(Node n) throws AntikytheraException {
        GraphNode g = GraphNode.graphNodeFactory(n);

        TypeDeclaration<?> cdecl = g.getEnclosingType();
        if (cdecl != null) {
            Optional<String> fullyQualifiedName = cdecl.getFullyQualifiedName();
            if (fullyQualifiedName.isPresent()) {
                String fqn = fullyQualifiedName.get();
                if (dependencies.containsKey(fqn)) {
                    /*
                     * This class has been processed before, but this particular method or field
                     * may not have been considered
                     */
                    if (g.getDestination() == null) {

                        CompilationUnit destination = dependencies.get(fqn);
                        g.setDestination(destination);
                        g.setTypeDeclaration(destination.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow());
                    }
                }
                else {
                    /*
                     * We have not previously processed this class.
                     */

                    unseenType(g, cdecl);
                    dependencies.put(fqn, g.getDestination());
                }
            }
        }


        g.buildNode();
        DepSolver.push(g);
        return g;
    }

    /**
     * Handle processing a graph node for an AST node that has not been seen before.
     * @param g the graph node
     * @param cdecl TypeDeclaration of the node
     */
    private static void unseenType(GraphNode g, TypeDeclaration<?> cdecl) {
        if (g.getDestination() == null) {
            g.setDestination(new CompilationUnit());
            TypeDeclaration<?> target;
            if (cdecl.isAnnotationDeclaration()) {
                target = g.getDestination().addAnnotationDeclaration(cdecl.getNameAsString());
            }
            else {
                target = g.getDestination().addClass(cdecl.getNameAsString());
                target.setModifiers(cdecl.getModifiers());
                if (cdecl.isClassOrInterfaceDeclaration()) {
                    target.asClassOrInterfaceDeclaration().setTypeParameters(cdecl.asClassOrInterfaceDeclaration().getTypeParameters());
                }
            }
            Optional<JavadocComment> comment = cdecl.getJavadocComment();
            comment.ifPresent(target::setJavadocComment);

            g.setTypeDeclaration(target);
        }
    }


    public static Map<String, CompilationUnit> getDependencies() {
        return dependencies;
    }

    public static Map<Integer, GraphNode> getNodes() {
        return nodes;
    }
}
