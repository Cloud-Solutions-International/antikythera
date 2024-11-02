package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import javax.swing.text.html.Option;
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
    private ClassOrInterfaceDeclaration classDeclaration;

    /**
     * This is the Abstract Syntax Tree node for the method, class or field
     */
    Node node;
    /**
     * The Compilation Unit that will be used to generate the new class.
     */
    CompilationUnit destination;

    /**
     * Has this node been visited before?
     */
    boolean visited;

    /**
     * Creates a new GraphNode
     * but it will not really be ready for use until you call the buildNode method
     * @param node an AST Node
     * @throws AntikytheraException if something cannot be resolved.
     */
    public GraphNode(Node node) throws AntikytheraException {
        this.node = node;
        enclosingType = AbstractCompiler.getEnclosingClassOrInterface(node);
        if (enclosingType != null) {
            Optional<CompilationUnit> cu = enclosingType.findCompilationUnit();

            if (cu.isPresent()) {
                compilationUnit = cu.get();
            } else {
                throw new AntikytheraException("CompilationUnit not found for " + enclosingType.getNameAsString());
            }
        }
        else {
            compilationUnit = node.findCompilationUnit().orElseThrow();
            destination = compilationUnit.clone();
        }
    }

    /**
     * Builds the graph node from the information available in enclosing type
     * @return a list of nodes that need to be explored
     */
    public List<GraphNode> buildNode() throws AntikytheraException {
        List<GraphNode> list = new ArrayList<>();
        if(enclosingType == null) {
            return list;
        }
        classDeclaration.setInterface(enclosingType.isInterface());
        compilationUnit.getPackageDeclaration().ifPresent(destination::setPackageDeclaration);

        if(classDeclaration.getImplementedTypes().isEmpty()) {
            for (ClassOrInterfaceType ifc : enclosingType.getImplementedTypes()) {
                classDeclaration.addImplementedType(ifc.getNameAsString());
                list.addAll(addTypeArguments(ifc));
            }
        }

        if (classDeclaration.getExtendedTypes().isEmpty()) {
            for (ClassOrInterfaceType ifc : enclosingType.getExtendedTypes()) {
                if (classDeclaration.isInterface()) {
                    classDeclaration.addExtendedType(ifc.toString());
                } else {
                    classDeclaration.addImplementedType(ifc.clone());
                }
                list.addAll(addTypeArguments(ifc));
            }
        }

        if (classDeclaration.getAnnotations().isEmpty()) {
            processClassAnnotations();
        }

        /*
         * If the class is an entity, we will need to preserve all the fields.
         */
        if (classDeclaration.getFields().isEmpty() &&
                (classDeclaration.getAnnotationByName("Entity").isPresent()) ) {
            copyFields(list);
        }

        return list;
    }

    private void copyFields(List<GraphNode> list) throws AntikytheraException {
        for(FieldDeclaration field : enclosingType.getFields()) {
            for (VariableDeclarator declarator : field.getVariables()) {
                Type type = declarator.getType();
                if (type.isClassOrInterfaceType()) {
                    ClassOrInterfaceType ct = type.asClassOrInterfaceType();
                    if (!(ct.isBoxedType() || ct.isPrimitiveType())) {
                        list.addAll(addTypeArguments(ct));
                    }
                }
            }
            Optional<Expression> init = field.getVariables().get(0).getInitializer();
            if (init.isPresent()) {
                if (init.get().isObjectCreationExpr()) {
                    ObjectCreationExpr oce = init.get().asObjectCreationExpr();
                    List<ImportDeclaration> imports = AbstractCompiler.findImport(compilationUnit, oce.getType());
                    destination.getImports().addAll(imports);
                }
            }
            field.accept(new AnnotationVisitor(), this);
            classDeclaration.addMember(field.clone());
        }
    }

    private void processClassAnnotations() {
        for (AnnotationExpr ann : enclosingType.getAnnotations()) {
            classDeclaration.addAnnotation(ann);
            String fqName = AbstractCompiler.findFullyQualifiedName(compilationUnit, ann.getName().toString());
            if (fqName != null) {
                destination.addImport(fqName);
            }

            if (ann.isSingleMemberAnnotationExpr()) {
                Expression expr = ann.asSingleMemberAnnotationExpr().getMemberValue();
                if (expr.isArrayInitializerExpr()) {
                    ArrayInitializerExpr aie = expr.asArrayInitializerExpr();
                    for (Expression e : aie.getValues()) {
                        if (e.isAnnotationExpr()) {
                            AnnotationExpr anne = e.asAnnotationExpr();
                            fqName = AbstractCompiler.findFullyQualifiedName(compilationUnit, anne.getName().toString());
                            if (fqName != null) {
                                destination.addImport(fqName);
                            }
                            if (anne.isNormalAnnotationExpr()) {
                                NormalAnnotationExpr norm = anne.asNormalAnnotationExpr();
                                for (MemberValuePair value : norm.getPairs()) {
                                    if (value.getValue().isAnnotationExpr()) {
                                        AnnotationExpr ae = value.getValue().asAnnotationExpr();
                                        fqName = AbstractCompiler.findFullyQualifiedName(compilationUnit, ae.getName().toString());
                                        if (fqName != null) {
                                            destination.addImport(fqName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds the type arguments to the graph
     * @param ifc interface or class
     * @return a list of graph nodes.
     * @throws AntikytheraException if the types cannot be fully resolved.
     */
    public List<GraphNode> addTypeArguments(ClassOrInterfaceType ifc) throws AntikytheraException {
        List<GraphNode> list = new ArrayList<>();
        Optional<NodeList<Type>> typeArguments = ifc.getTypeArguments();
        if (typeArguments.isPresent()) {
            for (Type typeArg : typeArguments.get()) {
                searchType(typeArg, list);
            }
        }
        searchType(ifc, list);
        return list;
    }

    /**
     * Search for the type and put it on the stack.
     * This method is only intended to be called by addTypeArguments
     * @param typeArg
     * @param list
     * @throws AntikytheraException
     */
    private void searchType(Type typeArg, List<GraphNode> list) throws AntikytheraException {
        String name = typeArg.isClassOrInterfaceType()
                ? typeArg.asClassOrInterfaceType().getNameAsString()
                : typeArg.toString();

        ImportDeclaration imp = AbstractCompiler.findImport(compilationUnit, name);
        if (imp != null) {
            destination.addImport(imp.getNameAsString());
        }
        String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(compilationUnit, name);
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);

        if (cu != null) {
            TypeDeclaration<?> t = AbstractCompiler.getMatchingClass(cu, typeArg.asString());
            if (t != null) {
                GraphNode g = Graph.createGraphNode(t);
                list.addAll(g.buildNode());
                list.add(g);
            }
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

    public void setDestination(CompilationUnit destination) {
        this.destination = destination;
    }

    public void setClassDeclaration(ClassOrInterfaceDeclaration classDeclaration) {
        this.classDeclaration = classDeclaration;
    }
}
