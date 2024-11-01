package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.ArrayList;
import java.util.List;
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
        }
        else {
            throw new AntikytheraException("CompilationUnit not found for " + enclosingType.getNameAsString());
        }
    }

    /**
     * Builds the graph node from the information available in enclosing type
     * @return a list of nodes that need to be explored
     */
    public List<GraphNode> buildNode() throws AntikytheraException {
        List<GraphNode> list = new ArrayList<>();

        classDeclaration.setInterface(enclosingType.isInterface());
        compilationUnit.getPackageDeclaration().ifPresent(destination::setPackageDeclaration);

        for (ClassOrInterfaceType ifc : enclosingType.getImplementedTypes()) {
            classDeclaration.addImplementedType(ifc.getNameAsString());
            list.addAll(inherit(ifc));
        }

        for (ClassOrInterfaceType ifc : enclosingType.getExtendedTypes()) {
            if (classDeclaration.isInterface()) {
                classDeclaration.addExtendedType(ifc.toString());
            }
            else {
                classDeclaration.addImplementedType(ifc.clone());
            }
            list.addAll(inherit(ifc));
        }

        for (AnnotationExpr ann : enclosingType.getAnnotations()) {
            classDeclaration.addAnnotation(ann);
            ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, ann.getNameAsString());
            if (imp != null) {
                destination.addImport(imp);
            }
        }

        return list;
    }

    private List<GraphNode> inherit(ClassOrInterfaceType ifc) throws AntikytheraException {
        List<GraphNode> list = new ArrayList<>();
        list.addAll(addTypeArguments(ifc));

        ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, ifc.getNameAsString());
        if (imp != null) {
            destination.addImport(imp);
        }
        list.addAll(addClassDependency(ifc, imp == null ?  ifc.getNameAsString() : imp.getNameAsString()));
        return list;
    }

    private List<GraphNode> addTypeArguments(ClassOrInterfaceType ifc) throws AntikytheraException {
        List<GraphNode> list = new ArrayList<>();
        Optional<NodeList<Type>> typeArguments = ifc.getTypeArguments();
        if (typeArguments.isPresent()) {
            for (Type typeArg : typeArguments.get()) {
                ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, typeArg.asString());
                if (imp != null) {
                    destination.addImport(imp);
                    list.addAll(addClassDependency(typeArg, imp.getNameAsString()));
                }
            }
        }
        return list;
    }

    private List<GraphNode> addClassDependency(Type typeArg, String className) throws AntikytheraException {
        List<GraphNode> list = new ArrayList<>();
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(
                AbstractCompiler.findFullyQualifiedName(compilationUnit, className)
        );
        if (cu != null) {
            TypeDeclaration<?> t = AbstractCompiler.getMatchingClass(cu, typeArg.asString());
            if (t != null) {
                GraphNode g = new GraphNode(t);
                list.addAll(g.buildNode());
                list.add(g);
            }
        }
        return list;
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
