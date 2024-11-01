package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.Optional;

/**
 * Primary purpose to encapsulate the AST node.
 */
public class GraphNode {
    /**
     * This is the compilation unit for the class that contains the node.
     */
    private final CompilationUnit compilationUnit;
    /**
     * This is the enclosing class for the original node.
     */
    private final ClassOrInterfaceDeclaration enclosingType;

    /**
     * This is the class declaration that is the target.
     */
    private final ClassOrInterfaceDeclaration classDeclaration;

    /**
     * This is the Abstract Syntax Tree node for the method, class or field
     */
    Node node;
    /**
     * The Compilation Unit that will be used to generate the new class.
     */
    CompilationUnit destination;


    boolean visited;

    public GraphNode(Node node) throws AntikytheraException {
        this.node = node;

        enclosingType = AbstractCompiler.getEnclosingClassOrInterface(node);

        this.destination = new CompilationUnit();

        classDeclaration = destination.addClass(enclosingType.getNameAsString());
        Optional<CompilationUnit> cu = enclosingType.findCompilationUnit();

        if (cu.isPresent()) {
            compilationUnit = cu.get();
            classDeclaration.setInterface(enclosingType.isInterface());
            if (compilationUnit.getPackageDeclaration().isPresent()) {
                destination.setPackageDeclaration(compilationUnit.getPackageDeclaration().get());
            }

            for (ClassOrInterfaceType ifc : enclosingType.getImplementedTypes()) {
                classDeclaration.addImplementedType(ifc.getNameAsString());
                ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, ifc.getNameAsString());
                if (imp != null) {
                    destination.addImport(imp);
                }
            }

            for (ClassOrInterfaceType ifc : enclosingType.getExtendedTypes()) {
                classDeclaration.addImplementedType(ifc.getNameAsString());
                ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, ifc.getNameAsString());
                if (imp != null) {
                    destination.addImport(imp);
                }
            }

            for (AnnotationExpr ann : enclosingType.getAnnotations()) {
                classDeclaration.addAnnotation(ann);
                ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, ann.getNameAsString());
                if (imp != null) {
                    destination.addImport(imp);
                }
            }
        }
        else {
            throw new AntikytheraException("CompilationUnit not found for " + enclosingType.getNameAsString());
        }
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public CompilationUnit getDestination() {
        return destination;
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public ClassOrInterfaceDeclaration getEnclosingType() {
        return enclosingType;
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GraphNode other) {
            return node.equals(other.node);
        } else {
            return false;
        }
    }

    public ClassOrInterfaceDeclaration getClassDeclaration() {
        return classDeclaration;
    }
}
