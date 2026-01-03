package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Primary purpose to encapsulate the AST node.
 * <p>
 * The {@link GraphNode} class represents an AST node together with its enclosing type and destination copy,
 * ensuring that declarations, annotations, and companion members are migrated into the target
 * {@link CompilationUnit}. Rather than handling detached metadata, the class works with the concrete
 * {@link Node} instances that make up a type.
 * <p>
 * Lifecycle of a {@link GraphNode} instance:
 * <ol>
 *     <li>Creation: A new instance is created for an AST node via {@link #graphNodeFactory(Node)} and linked to
 *     its enclosing {@link TypeDeclaration}.</li>
 *     <li>Building: {@link #buildNode()} clones the enclosing declaration into {@link #destination}, wiring
 *     inheritance, imports, and annotations.</li>
 *     <li>Processing: Fields, constructors, enum constants, and nested references are added by invoking helper
 *     methods such as {@link #copyFields()}, {@link #copyConstructors()}, and {@link #processTypeArgument(Type)}.</li>
 *     <li>Completion: {@link #getDestination()} returns the populated compilation unit ready for downstream
 *     generators once copying is complete.</li>
 * </ol>
 * <p>
 * The class also guards against cyclic dependencies, keeps track of visited nodes, and resolves abstract
 * members, providing a resilient bridge between parsed source and generated artifacts.
 */
public class GraphNode {
    private static final Logger logger = LoggerFactory.getLogger(GraphNode.class);

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
     * This is the Abstract Syntax Tree node for the method, class, or field
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
     * However, it will not really be ready for use until you call the buildNode method
     * @param node an AST Node
     */
    private GraphNode(Node node) {
        this.node = node;
        enclosingType = AbstractCompiler.getEnclosingType(node);
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
     * Creates or retrieves a {@link GraphNode} for the supplied AST {@link Node}.
     * Ensures a single instance per node by consulting {@link Graph#getNodes()}.
     * @param node AST node that should be wrapped
     * @return cached or newly created {@link GraphNode}
     */
    public static GraphNode graphNodeFactory(Node node) {
        GraphNode tmp = new GraphNode(node);
        GraphNode g = Graph.getNodes().get(tmp.hashCode());
        if (g == null) {
            Graph.getNodes().put(tmp.hashCode(), tmp);
            return tmp;
        }
        return g;
    }

    /**
     * Builds the destination type by cloning the structure from the enclosing declaration.
     * Copies packages, annotations, constructors, and fields while respecting cycle guards.
     */
    public void buildNode()  {
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

    private void copyConstructors()  {
        if (enclosingType.isClassOrInterfaceDeclaration()) {
            for (ConstructorDeclaration constructor : enclosingType.asClassOrInterfaceDeclaration().getConstructors()) {
                Graph.createGraphNode(constructor);
            }
        }
    }

    /*
     * Handles implementations and extensions
     */
    private void inherit()  {
        ClassOrInterfaceDeclaration enclosingDeclaration = enclosingType.asClassOrInterfaceDeclaration();

        ClassOrInterfaceDeclaration cdecl = typeDeclaration.asClassOrInterfaceDeclaration();
        if (enclosingDeclaration.isInterface()) {
            cdecl.setInterface(true);
        } else {
            cdecl.setInterface(false);

            if (cdecl.getImplementedTypes().isEmpty()) {
                for (ClassOrInterfaceType ifc : enclosingDeclaration.getImplementedTypes()) {
                    cdecl.addImplementedType(ifc.clone());
                    processTypeArgument(ifc);
                }
            }
        }
        if (cdecl.getExtendedTypes().isEmpty()) {
            /*
             * this empty check is in place to make sure that we do not repeat the process.
             * cdecl is the target, if it contains the extensions that means its completed.
             */
            for (ClassOrInterfaceType ifc : enclosingDeclaration.getExtendedTypes()) {
                cdecl.addExtendedType(ifc.clone());
                TypeWrapper wrapper = AbstractCompiler.findType(compilationUnit,ifc);
                if (wrapper != null) {
                    Class<?> clz = wrapper.getClazz();
                    if (clz != null && Modifier.isAbstract(clz.getModifiers())) {
                        for (MethodDeclaration md : enclosingDeclaration.getMethods()) {
                            addAbstractMethods(md, clz);
                        }
                    }
                }
                else {
                    logger.error("Class not found: {}", ifc.getNameAsString());
                }

                processTypeArgument(ifc);
            }
        }
    }


    private void addAbstractMethods(MethodDeclaration md, Class<?> parent) {
        if (!Modifier.isAbstract(parent.getModifiers())) {
            return;
        }
        Method[] methods = parent.getDeclaredMethods();

        for (Method method :  methods) {
            if (method.getName().equals(md.getNameAsString())) {
                if (method.getParameters().length == md.getParameters().size()) {
                    boolean match = true;
                 //todo need to find a way to compare the parameters
                    if (match) {
                        if(Modifier.isAbstract(method.getModifiers())) {
                            Graph.createGraphNode(md);
                        }
                        return;
                    }
                }
            }
        }
        if(parent.getSuperclass() !=null){
             addAbstractMethods(md,parent.getSuperclass() );
        }

    }

    private void copyFields()  {
        for(FieldDeclaration field : enclosingType.asClassOrInterfaceDeclaration().getFields()) {

            for (VariableDeclarator declarator : field.getVariables()) {
                Type type = declarator.getType();
                if (type.isClassOrInterfaceType()) {
                    ClassOrInterfaceType ct = type.asClassOrInterfaceType();
                    if (!(ct.isBoxedType() || ct.isPrimitiveType())) {
                        processTypeArgument(ct);
                    }
                }
            }
            DepSolver.initializeField(field, this);

            addField(field);
        }
    }

    private void processClassAnnotations()  {
        for (AnnotationExpr ann : enclosingType.getAnnotations()) {
            typeDeclaration.addAnnotation(ann);
            ann.accept(new AnnotationVisitor(), this);
        }
    }

    /**
     * Adds the type arguments to the graph.
     * We are dealing with parameterized types. things like {@code Map<String, Integer>} or {@code List<String>}
     * Will make recursive calls to the searchType method which will result in the imports
     * being eventually added.
     * @param typeArg an AST type argument which may or may not contain parameterized types
     */
    public GraphNode processTypeArgument(Type typeArg) {
        if (typeArg.isClassOrInterfaceType()) {
            ClassOrInterfaceType ctype = typeArg.asClassOrInterfaceType();
            ctype.getTypeArguments().ifPresent(types ->{
                for (Type t : types) {
                    processTypeArgument(t);
                }
            });
            if (ctype.getScope().isPresent()) {
                ImportUtils.addImport(this, ctype.getScope().orElseThrow());
            }
            return ImportUtils.addImport(this, ctype.getNameAsString());
        }
        return ImportUtils.addImport(this, typeArg);
    }

    /**
     * Indicates whether the corresponding {@link #typeDeclaration} already contains this {@link #node}.
     * @return true if the node has been incorporated into the destination declaration
     */
    public boolean isVisited() {
        return typeDeclaration != null && typeDeclaration.findFirst(node.getClass(), n -> n.equals(node)).isPresent();
    }

    /**
     * Marks this node as visited during graph traversal.
     * @param visited new visited flag value
     */
    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    /**
     * Exposes the destination {@link CompilationUnit} receiving cloned members.
     * @return populated compilation unit, or {@code null} if build not complete
     */
    public CompilationUnit getDestination() {
        return destination;
    }

    /**
     * Returns the wrapped AST {@link Node} instance.
     * @return current node reference
     */
    public Node getNode() {
        return node;
    }

    /**
     * Updates the wrapped AST node reference.
     * @param node new node to manage
     */
    public void setNode(Node node) {
        this.node = node;
    }

    /**
     * Provides the {@link CompilationUnit} that originally contained the node.
     * @return source compilation unit
     */
    public CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    /**
     * Returns the enclosing {@link TypeDeclaration} from which members are copied.
     * @return enclosing type, or {@code null} when not available
     */
    @SuppressWarnings("java:S1452")
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

    /**
     * Compares two {@link GraphNode} instances by their wrapped {@link Node}.
     * @param obj candidate object
     * @return true when both nodes reference the same AST element
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GraphNode other) {
            return node.equals(other.node);
        } else {
            return false;
        }
    }

    /**
     * Provides the destination {@link TypeDeclaration} receiving cloned members.
     * @return destination type declaration
     */
    public TypeDeclaration<?> getTypeDeclaration() {
        return typeDeclaration;
    }

    /**
     * Sets the destination compilation unit to populate.
     * @param destination compilation unit clone that will host generated members
     */
    public void setDestination(CompilationUnit destination) {
        this.destination = destination;
    }

    /**
     * Sets the destination type declaration that will receive copied members.
     * @param typeDeclaration target type declaration
     */
    public void setTypeDeclaration(TypeDeclaration<?> typeDeclaration) {
        this.typeDeclaration = typeDeclaration;
    }

    /**
     * Builds a stable identifier composed of package, type, and member signature.
     * @return human-readable identifier for logging/debugging
     */
    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder();
        if(compilationUnit != null ) {
            compilationUnit.getPackageDeclaration().ifPresent(pd -> b.append(pd.getNameAsString()));
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

    /**
     * Adds a new enum constant to the destination enum declaration if it is missing.
     * Also wires constructor arguments by seeding dependent graph nodes.
     * @param enumConstant enum constant to copy
     */
    public void addEnumConstant(EnumConstantDeclaration enumConstant) {
        if (!typeDeclaration.isEnumDeclaration()) {
            return;
        }

        EnumDeclaration ed = typeDeclaration.asEnumDeclaration();
        for (EnumConstantDeclaration ecd : ed.getEntries()) {
            if (ecd.getNameAsString().equals(enumConstant.getNameAsString())) {
                return;
            }
        }
        if (enumConstant.getArguments().isNonEmpty()) {
            addEnumConstantHelper(enumConstant);
        }
        ed.addEntry(enumConstant.clone());
    }

    private void addEnumConstantHelper(EnumConstantDeclaration enumConstant) {
        Class<?>[] paramTypes = new Class<?>[enumConstant.getArguments().size()];

        for (int i = 0 ; i < paramTypes.length ; i++) {
            Expression arg = enumConstant.getArguments().get(i);
            if (arg.isLiteralExpr()) {
                paramTypes[i] = Reflect.literalExpressionToClass(arg.asLiteralExpr());
            }
            else if (arg.isFieldAccessExpr()) {
                FieldAccessExpr fae = arg.asFieldAccessExpr();
                TypeWrapper wrapper = AbstractCompiler.findType(compilationUnit, fae.getScope().asNameExpr().getNameAsString());
                if (wrapper != null) {
                    if (wrapper.getClazz() != null) {
                        paramTypes[i] = wrapper.getClazz();
                    } else {
                        logger.error("Class not found for {}", fae.getNameAsString());
                    }
                }
            }
        }

        enclosingType.getConstructorByParameterTypes(paramTypes).ifPresent(Graph::createGraphNode);
    }

    /**
     * Adds a cloned field declaration to the destination type and ensures imports are present.
     * @param fieldDeclaration field declaration sourced from the enclosing type
     */
    public void addField(FieldDeclaration fieldDeclaration)  {
        fieldDeclaration.accept(new AnnotationVisitor(), this);
        VariableDeclarator variable = fieldDeclaration.getVariable(0);
        if(typeDeclaration.getFieldByName(variable.getNameAsString()).isEmpty()) {
            typeDeclaration.addMember(fieldDeclaration.clone());

            if (variable.getType().isClassOrInterfaceType()) {
                processTypeArgument(variable.getType().asClassOrInterfaceType());

                variable.getType().asClassOrInterfaceType().getScope()
                    .ifPresent(scp -> ImportUtils.addImport(this, scp.getNameAsString()));
            }
            else {
                ImportUtils.addImport(this, variable.getType());
            }
        }
        DepSolver.initializeField(fieldDeclaration, this);
    }
}
