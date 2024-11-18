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

        if (typeDeclaration.getAnnotations().isEmpty()) {
            processClassAnnotations();
        }

        /*
         * If the class is an entity, we will need to preserve all the fields.
         */
        if (typeDeclaration.getFields().isEmpty() &&
                (typeDeclaration.getAnnotationByName("Entity").isPresent()) ||
                typeDeclaration.getAnnotationByName("AllArgsConstructor").isPresent() ||
                typeDeclaration.getAnnotationByName("JsonInclude").isPresent() ||
                typeDeclaration.getAnnotationByName("MappedSuperclass").isPresent() ||
                typeDeclaration.getAnnotationByName("Data").isPresent() ||
                (typeDeclaration.getAnnotationByName("Setter").isPresent()
                        && typeDeclaration.getAnnotationByName("Getter").isPresent())) {

            copyFields();
            copyConstructors();
        }
    }

    private void copyConstructors() throws AntikytheraException {
        for (ConstructorDeclaration constructor : enclosingType.asClassOrInterfaceDeclaration().getConstructors()) {
            Graph.createGraphNode(constructor);
        }
    }

    /*
     * Handles implementations and extensions
     */
    private void inherit() throws AntikytheraException {
        ClassOrInterfaceDeclaration enclosingDeclaration = enclosingType.asClassOrInterfaceDeclaration();

        if (enclosingDeclaration.isInterface()) {
            if (typeDeclaration.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cdecl = typeDeclaration.asClassOrInterfaceDeclaration();
                cdecl.setInterface(true);

                if (cdecl.getExtendedTypes().isEmpty()) {
                    for (ClassOrInterfaceType ifc : enclosingDeclaration.getExtendedTypes()) {
                        cdecl.addExtendedType(ifc.clone());
                        addTypeArguments(ifc);
                    }
                }
            }
        } else {
            if (typeDeclaration.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cdecl = typeDeclaration.asClassOrInterfaceDeclaration();
                cdecl.setInterface(false);

                if (cdecl.getImplementedTypes().isEmpty()) {
                    for (ClassOrInterfaceType ifc : enclosingDeclaration.getImplementedTypes()) {
                        cdecl.addImplementedType(ifc.clone());
                        addTypeArguments(ifc);
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
            initializeField(field);

            addField(field);
        }
    }

    private void initializeField(FieldDeclaration field) throws AntikytheraException {
        Optional<Expression> init = field.getVariables().get(0).getInitializer();
        if (init.isPresent()) {
            Expression initializer = init.get();
            if (initializer.isObjectCreationExpr()) {
                ObjectCreationExpr oce = initializer.asObjectCreationExpr();
                List<ImportWrapper> imports = AbstractCompiler.findImport(compilationUnit, oce.getType());
                for(ImportWrapper imp : imports) {
                    destination.getImports().add(imp.getImport());
                }
            }
            else if(initializer.isNameExpr()) {
                setupFieldInitializer(initializer);
            }
            else if(initializer.isMethodCallExpr()) {
                MethodCallExpr mce = initializer.asMethodCallExpr();
                Optional<Expression> scope = mce.getScope();
                if (scope.isPresent()) {
                    if (scope.get().isNameExpr()) {
                        setupFieldInitializer(scope.get());
                    }
                    else if (scope.get().isFieldAccessExpr()) {
                        setupFieldInitializer(scope.get().asFieldAccessExpr().getScope().asNameExpr());
                    }
                }
            }
        }
    }

    private void setupFieldInitializer(Expression initializer) throws AntikytheraException {
        ImportWrapper iw = AbstractCompiler.findImport(compilationUnit, initializer.asNameExpr().getNameAsString());
        if (iw != null) {
            ImportDeclaration imp = iw.getImport();
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp.getNameAsString());
            if (imp.isStatic()) {
                if (cu != null) {
                    TypeDeclaration<?> t = AbstractCompiler.getMatchingType(cu, imp.getName().getIdentifier());
                    fieldInitializer(t, initializer);
                }
                else {
                    cu = AntikytheraRunTime.getCompilationUnit(imp.getName().getQualifier().get().toString());
                    if (cu != null) {
                        TypeDeclaration<?> t = AbstractCompiler.getMatchingType(cu, imp.getName().getQualifier().get().getIdentifier());
                        fieldInitializer(t, initializer);
                    }
                }
            }
            else {
                if (cu != null) {
                    TypeDeclaration<?> t = AbstractCompiler.getMatchingType(cu, imp.getName().getIdentifier());
                    Graph.createGraphNode(t);
                }
            }
            destination.addImport(imp);
        }
    }

    private void fieldInitializer(TypeDeclaration<?> t, Expression initializer) throws AntikytheraException {
        if (t != null) {
            FieldDeclaration f = t.getFieldByName(initializer.asNameExpr().getNameAsString()).orElse(null);
            if (f != null) {
                Graph.createGraphNode(f);
            }
        }
    }

    private void processClassAnnotations() throws AntikytheraException {
        for (AnnotationExpr ann : enclosingType.getAnnotations()) {
            typeDeclaration.addAnnotation(ann);
            DepSolver.addImport(this, ann.getNameAsString());

            if (ann.isSingleMemberAnnotationExpr()) {
                singleMemeberAnnotation(ann);
            }
            else if (ann.isNormalAnnotationExpr()) {
                normalAnnotation(ann);
            }
        }
    }

    private void singleMemeberAnnotation(AnnotationExpr ann) {
        Expression expr = ann.asSingleMemberAnnotationExpr().getMemberValue();
        if (expr.isArrayInitializerExpr()) {
            ArrayInitializerExpr aie = expr.asArrayInitializerExpr();
            for (Expression e : aie.getValues()) {
                if (e.isAnnotationExpr()) {
                    nestedAnnotation(e);
                }
                else if (e.isFieldAccessExpr()) {
                    annotationFieldAccess(e);
                }
            }
        }
        else if (expr.isFieldAccessExpr()) {
            annotationFieldAccess(expr);
        }
    }

    private void normalAnnotation(AnnotationExpr ann) throws AntikytheraException {
        NormalAnnotationExpr norm = ann.asNormalAnnotationExpr();
        for (MemberValuePair value : norm.getPairs()) {
            if (value.getValue().isAnnotationExpr()) {
                nestedAnnotation(value.getValue());
            }
            else if (value.getValue().isFieldAccessExpr()) {
                annotationFieldAccess(value.getValue());
            }
            else if (value.getValue().isClassExpr()) {
                ClassOrInterfaceType ct = value.getValue().asClassExpr().getType().asClassOrInterfaceType();
                addTypeArguments(ct);
            }
        }
    }

    private void nestedAnnotation(Expression e) {
        String fqName;
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

    private void annotationFieldAccess(Expression expr) {
        FieldAccessExpr fae = expr.asFieldAccessExpr();
        Expression scope = fae.getScope();
        if (scope.isNameExpr()) {
            ImportWrapper imp = AbstractCompiler.findImport(compilationUnit, scope.asNameExpr().getNameAsString());
            if (imp != null) {
                destination.addImport(imp.getImport());
            }
        }
    }

    /**
     * Adds the type arguments to the graph.
     * Will make multpile calls to the searchType method which will result in the imports
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
                    for(Type t : typeArg.asClassOrInterfaceType().getTypeArguments().get()) {
                        searchType(t);
                    }
                    DepSolver.addImport(this, typeArg.asClassOrInterfaceType().getNameAsString());
                }
                else {
                    searchType(typeArg);
                }
            }
        }
        searchType(ifc);
    }

    /**
     * Search for the type and put it on the stack.
     * This method is only intended to be called by addTypeArguments
     * @param typeArg the class or interface type that we are looking for
     */
    private void searchType(Type typeArg)  {
        String name = typeArg.isClassOrInterfaceType()
                ? typeArg.asClassOrInterfaceType().getNameAsString()
                : typeArg.toString();

        DepSolver.addImport(this, name);
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
                DepSolver.addImport(this, variable.getTypeAsString());
            }
        }
        initializeField(fieldDeclaration);
    }
}
