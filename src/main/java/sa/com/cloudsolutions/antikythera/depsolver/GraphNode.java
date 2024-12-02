package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

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
     * Our objective is to copy things from here to the typeDeclaration below
     */
    private TypeDeclaration<?> enclosingType;

    /**
     * This is the class declaration that is the target.
     * We will copy things from the enclosingType to here.
     */
    private TypeDeclaration<?> typeDeclaration;

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
     * Has this node been processed?
     * There are cyclic dependencies with in entities and DTOs. If we did not have a flag like this
     * we would end up in an infinite loop.
     */
    boolean preProcessed;

    /**
     * Creates a new GraphNode
     * However it will not really be ready for use until you call the buildNode method
     * @param node an AST Node
     * @throws AntikytheraException if something cannot be resolved.
     */
    private GraphNode(Node node) throws AntikytheraException {
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
            preProcessed = true;

            if(node instanceof EnumDeclaration ed) {
                enclosingType = ed;
            }
        }
    }

    /**
     * Create s new GraphNode from the AST node or returns the previously created one.
     * @param node AST node
     * @return a GraphNode
     * @throws AntikytheraException if the node cannot be processed.
     */
    public static GraphNode graphNodeFactory(Node node) throws AntikytheraException {
        GraphNode tmp = new GraphNode(node);
        GraphNode g = Graph.getNodes().get(tmp.hashCode());
        if (g == null) {
            Graph.getNodes().put(tmp.hashCode(), tmp);
            return tmp;
        }
        return g;
    }

    /**
     * Builds the graph node from the information available in enclosing type
     *
     */
    public void buildNode() throws AntikytheraException {
        if(enclosingType == null || preProcessed) {
            return;
        }
        /*
         * Set it here before we actually do the processing. If we dont' the cyclic dependencies
         * will kill us.
         */
        preProcessed = true;

       if (enclosingType.isClassOrInterfaceDeclaration()) {
           inherit();
       }

        compilationUnit.getPackageDeclaration().ifPresent(destination::setPackageDeclaration);

        if (typeDeclaration.getAnnotations().isEmpty() && !enclosingType.getAnnotations().isEmpty()) {
            processClassAnnotations();
        }

        /*
         * If the class is an entity, we will need to preserve all the fields.
         */
        if (enclosingType.getFields().isEmpty() &&
                (enclosingType.getAnnotationByName("Entity").isPresent()) ||
                enclosingType.getAnnotationByName("AllArgsConstructor").isPresent() ||
                enclosingType.getAnnotationByName("JsonInclude").isPresent() ||
                enclosingType.getAnnotationByName("MappedSuperclass").isPresent() ||
                enclosingType.getAnnotationByName("Data").isPresent() ||
                (enclosingType.getAnnotationByName("Setter").isPresent()
                        && enclosingType.getAnnotationByName("Getter").isPresent())) {

            copyFields();

        }
        copyConstructors();
    }

    private void copyConstructors() throws AntikytheraException {
        if (enclosingType.isClassOrInterfaceDeclaration()) {
            for (ConstructorDeclaration constructor : enclosingType.asClassOrInterfaceDeclaration().getConstructors()) {
                Graph.createGraphNode(constructor);
            }
        }
    }

    /*
     * Handles implementations and extensions
     */
    private void inherit() throws AntikytheraException {
        ClassOrInterfaceDeclaration enclosingDeclaration = enclosingType.asClassOrInterfaceDeclaration();
        if (typeDeclaration.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration cdecl = typeDeclaration.asClassOrInterfaceDeclaration();
            if (enclosingDeclaration.isInterface()) {
                cdecl.setInterface(true);
            } else {
                cdecl.setInterface(false);

                if (cdecl.getImplementedTypes().isEmpty()) {
                    for (ClassOrInterfaceType ifc : enclosingDeclaration.getImplementedTypes()) {
                        cdecl.addImplementedType(ifc.clone());
                        addTypeArguments(ifc);
                    }
                }
            }
            if (cdecl.getExtendedTypes().isEmpty()) {
                for (ClassOrInterfaceType ifc : enclosingDeclaration.getExtendedTypes()) {
                    cdecl.addExtendedType(ifc.clone());
                    addTypeArguments(ifc);
                }
            }
        }
    }

    private void copyFields() throws AntikytheraException {
        for(FieldDeclaration field : enclosingType.asClassOrInterfaceDeclaration().getFields()) {

            for (VariableDeclarator declarator : field.getVariables()) {
                Type type = declarator.getType();
                if (type.isClassOrInterfaceType()) {
                    ClassOrInterfaceType ct = type.asClassOrInterfaceType();
                    if (!(ct.isBoxedType() || ct.isPrimitiveType())) {
                        addTypeArguments(ct);
                    }
                }
            }
            DepSolver.initializeField(field, this);

            addField(field);
        }
    }


    private void processClassAnnotations() throws AntikytheraException {
        for (AnnotationExpr ann : enclosingType.getAnnotations()) {
            typeDeclaration.addAnnotation(ann);
        }
        enclosingType.accept(new AnnotationVisitor(), this);
    }

    /**
     * Adds the type arguments to the graph.
     * Will make multiple calls to the searchType method which will result in the imports
     * being eventually added.
     * @param ifc interface or class

     * @throws AntikytheraException if the types cannot be fully resolved.
     */
    public void addTypeArguments(ClassOrInterfaceType ifc) throws AntikytheraException {
        Optional<NodeList<Type>> typeArguments = ifc.getTypeArguments();
        if (typeArguments.isPresent()) {
            for (Type typeArg : typeArguments.get()) {
                if (typeArg.isClassOrInterfaceType() && typeArg.asClassOrInterfaceType().getTypeArguments().isPresent())
                {
                    ClassOrInterfaceType ctype = typeArg.asClassOrInterfaceType();
                    for(Type t : ctype.getTypeArguments().get()) {
                        ImportUtils.addImport(this, t);
                    }
                    if (ctype.getScope().isPresent()) {
                        ImportUtils.addImport(this, ctype.getScope().get().getNameAsString());
                    }
                    ImportUtils.addImport(this, ctype.getNameAsString());
                }
                else {
                    ImportUtils.addImport(this, typeArg);
                }
            }
        }
        ImportUtils.addImport(this, ifc);
    }

    public boolean isVisited() {
        return typeDeclaration != null && typeDeclaration.findFirst(node.getClass(), n -> n.equals(node)).isPresent();
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

    public TypeDeclaration<?> getEnclosingType() {
        return enclosingType;
    }

    /**
     * Override the hashcode method to do our own.
     * @return the generated hashcode for the instance
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GraphNode other) {
            return node.equals(other.node);
        } else {
            return false;
        }
    }

    public TypeDeclaration<?> getTypeDeclaration() {
        return typeDeclaration;
    }

    public void setDestination(CompilationUnit destination) {
        this.destination = destination;
    }

    public void setTypeDeclaration(TypeDeclaration<?> typeDeclaration) {
        this.typeDeclaration = typeDeclaration;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if(compilationUnit != null && compilationUnit.getPackageDeclaration().isPresent()) {
            b.append(compilationUnit.getPackageDeclaration().get().getNameAsString());
        }
        b.append(".");
        if(enclosingType != null) {
            b.append(enclosingType.getNameAsString());
        }

        if(node instanceof  MethodDeclaration md) {
            b.append("#");
            b.append(md.getSignature().toString());
        }
        else if(node instanceof FieldDeclaration fd) {
            b.append(".");
            b.append(fd.getVariable(0).getNameAsString());
        }
        else if(node instanceof ConstructorDeclaration cd) {
            b.append("@");
            b.append(cd.getSignature().toString());
        }
        return b.toString();
    }

    public void addField(FieldDeclaration fieldDeclaration) throws AntikytheraException {

        fieldDeclaration.accept(new AnnotationVisitor(), this);
        VariableDeclarator variable = fieldDeclaration.getVariable(0);
        if(typeDeclaration.getFieldByName(variable.getNameAsString()).isEmpty()) {
            typeDeclaration.addMember(fieldDeclaration.clone());

            if (variable.getType().isClassOrInterfaceType()) {
                addTypeArguments(variable.getType().asClassOrInterfaceType());
            }
            else {
                ImportUtils.addImport(this, variable.getTypeAsString());
            }
        }
        DepSolver.initializeField(fieldDeclaration, this);
    }
}
