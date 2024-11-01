package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

/**
 * Primary purpose to encapsulate the AST node.
 */
public class GraphNode {
    private final CompilationUnit compilationUnit;
    private final ClassOrInterfaceDeclaration enclosingType;
    Node node;
    CompilationUnit destination;


    boolean visited;

    public GraphNode(Node node) throws AntikytheraException {
        this.node = node;

        enclosingType = AbstractCompiler.getEnclosingClassOrInterface(node);

        this.destination = new CompilationUnit();

        ClassOrInterfaceDeclaration cdecl = destination.addClass(enclosingType.getNameAsString());
        compilationUnit = enclosingType.findCompilationUnit().get();
        cdecl.setInterface(enclosingType.isInterface());

        for (ClassOrInterfaceType ifc : enclosingType.getImplementedTypes()) {
            cdecl.addImplementedType(ifc.getNameAsString());
            ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, ifc.getNameAsString());
            if (imp != null) {
                compilationUnit.addImport(imp);
            }
        }

        for (ClassOrInterfaceType ifc : enclosingType.getExtendedTypes()) {
            cdecl.addImplementedType(ifc.getNameAsString());
            ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, ifc.getNameAsString());
            if (imp != null) {
                compilationUnit.addImport(imp);
            }
        }

        for (AnnotationExpr ann : enclosingType.getAnnotations()) {
            cdecl.addAnnotation(ann);
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

    public void setDestination(CompilationUnit destination) {
        this.destination = destination;
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
}
