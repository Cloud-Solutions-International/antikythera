package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Dependency solver with code generation capabilities.
 * 
 * <p>
 * Extends {@link DependencyAnalyzer} to add code generation. Overrides hook
 * methods
 * to add discovered callables and imports to destination CompilationUnits.
 * </p>
 * 
 * <p>
 * This class maintains backward compatibility with existing code that uses
 * static methods
 * like {@link #createSolver()}, {@link #push(GraphNode)}, {@link #getNames()},
 * etc.
 * </p>
 */
public class DepSolver extends DependencyAnalyzer {

    /**
     * Static singleton instance for backward compatibility.
     */
    private static DepSolver solver;

    /**
     * Protected constructor for backward compatibility.
     * Use {@link #createSolver()} to create instances.
     */
    protected DepSolver() {
        super();
    }

    // ============ Static Factory and Facade (backward compatibility) ============

    /**
     * Create a DepSolver instance (backward compatibility).
     * Returns a singleton instance, reusing the same instance across calls.
     * 
     * @return DepSolver instance
     */
    public static DepSolver createSolver() {
        if (solver == null) {
            solver = new DepSolver();
        } else {
            reset();
        }
        return solver;
    }

    /**
     * Push a node onto the stack (static method).
     * 
     * @param g GraphNode to push
     */
    public static void push(GraphNode g) {
        stack.push(g);
    }

    /**
     * Get the names map (static method).
     * 
     * @return names map
     */
    public static Map<String, Type> getNames() {
        return names;
    }

    /**
     * Initialize field dependencies (static method).
     * 
     * @param field Field declaration
     * @param node  Graph node
     */
    public static void initializeField(FieldDeclaration field, GraphNode node) {
        if (solver == null) {
            solver = new DepSolver();
        }
        solver.initField(field, node);
    }

    /**
     * Reset state (static method).
     * Clears both analyzer state and Graph code generation state.
     */
    public static void reset() {
        stack.clear();
        names.clear();
        Graph.getDependencies().clear();
        Graph.getNodes().clear();
    }

    // ============ Override Hook Methods for Code Generation ============

    /**
     * Create a graph node for code generation.
     * Uses Graph.createGraphNode() which includes buildNode() and destination CU
     * creation.
     */
    @Override
    protected GraphNode createAnalysisNode(Node node) {
        // Graph.createGraphNode() handles buildNode() and pushes to stack
        return Graph.createGraphNode(node);
    }

    /**
     * Add callable to destination compilation unit for code generation.
     */
    @Override
    protected void onCallableDiscovered(GraphNode node, CallableDeclaration<?> cd) {
        String className = node.getEnclosingType().getNameAsString();
        node.getDestination().findFirst(TypeDeclaration.class,
                t -> t.getNameAsString().equals(className)).ifPresent(c -> {
                    TypeDeclaration<?> typeDecl = node.getTypeDeclaration();
                    if (typeDecl != null) {
                        typeDecl.addMember(cd);
                        if (cd.isAbstract() && node.getEnclosingType().getFullyQualifiedName().isPresent()) {
                            methodOverrides(cd, node.getEnclosingType().getFullyQualifiedName().get());
                        }
                    }
                });
    }

    /**
     * Add imports to destination CU for code generation.
     */
    @Override
    protected void onImportDiscovered(GraphNode node, ImportWrapper imp) {
        node.getDestination().addImport(imp.getImport());
    }

    // ============ DepSolver-specific Methods ============

    /**
     * Main entry point for the dependency solver.
     * 
     * @throws IOException if files could not be read
     */
    private void solve() throws IOException {
        AbstractCompiler.preProcess();
        for (String method : Settings.getPropertyList("methods", String.class)) {
            processMethod(method);
        }
    }

    /**
     * Process the dependencies of a method that was declared in the application
     * configuration.
     * 
     * @param s the method name
     */
    public void processMethod(String s) {
        String[] parts = s.split("#");

        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(parts[0]);
        if (cu != null) {
            cu.findAll(MethodDeclaration.class, m -> m.getNameAsString().equals(parts[1]))
                    .forEach(Graph::createGraphNode);
            dfs();
        }
    }

    /**
     * Write generated files to disk.
     * 
     * @throws IOException if files could not be written
     */
    private void writeFiles() throws IOException {
        Files.copy(
                Paths.get(Settings.getProperty(Settings.BASE_PATH).toString().replace("src/main/java", ""), "pom.xml"),
                Paths.get(Settings.getProperty(Settings.OUTPUT_PATH).toString().replace("src/main/java", ""),
                        "pom.xml"),
                StandardCopyOption.REPLACE_EXISTING);

        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            boolean write = false;
            CompilationUnit cu = entry.getValue();

            List<ImportDeclaration> list = new ArrayList<>(cu.getImports());
            cu.getImports().clear();
            list.sort(Comparator.comparing(NodeWithName::getNameAsString));
            cu.getImports().addAll(list);

            for (TypeDeclaration<?> decl : cu.getTypes()) {
                if (decl.isClassOrInterfaceDeclaration()) {
                    if (entry.getKey().endsWith(decl.asClassOrInterfaceDeclaration().getNameAsString())) {
                        write = true;
                    }
                    sortClass(decl.asClassOrInterfaceDeclaration());
                } else if (decl.isEnumDeclaration()) {
                    write = true;
                }
            }
            if (write) {
                CopyUtils.writeFile(AbstractCompiler.classToPath(entry.getKey()), cu.toString());
            }
        }
    }

    /**
     * Sorts the members of a class or interface declaration.
     * Fields are sorted alphabetically by their variable name, constructors by
     * their name,
     * and methods by their name. Inner classes are added at the end.
     *
     * @param classOrInterface the class or interface declaration to sort
     */
    public static void sortClass(ClassOrInterfaceDeclaration classOrInterface) {
        List<FieldDeclaration> fields = new ArrayList<>();
        List<ConstructorDeclaration> constructors = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> inners = new ArrayList<>();

        for (BodyDeclaration<?> member : classOrInterface.getMembers()) {
            if (member instanceof FieldDeclaration fd) {
                fields.add(fd);
            } else if (member instanceof ConstructorDeclaration cd) {
                constructors.add(cd);
            } else if (member instanceof MethodDeclaration md) {
                methods.add(md);
            } else if (member instanceof ClassOrInterfaceDeclaration md) {
                inners.add(md);
            }
        }

        if (!(classOrInterface.getAnnotationByName("NoArgsConstructor").isPresent()
                || classOrInterface.getAnnotationByName("AllArgsConstructor").isPresent()
                || classOrInterface.getAnnotationByName("data").isPresent())) {
            fields.sort(Comparator.comparing(f -> f.getVariable(0).getNameAsString()));
        }

        constructors.sort(Comparator.comparing(ConstructorDeclaration::getNameAsString));
        // Sort methods with BeforeEach first, then alphabetically
        methods.sort((m1, m2) -> {
            boolean m1HasBeforeEach = m1.getAnnotationByName("BeforeEach").isPresent();
            boolean m2HasBeforeEach = m2.getAnnotationByName("BeforeEach").isPresent();

            if (m1HasBeforeEach && !m2HasBeforeEach) {
                return -1;
            } else if (!m1HasBeforeEach && m2HasBeforeEach) {
                return 1;
            } else {
                return m1.getNameAsString().compareTo(m2.getNameAsString());
            }
        });

        classOrInterface.getMembers().clear();
        classOrInterface.getMembers().addAll(fields);
        classOrInterface.getMembers().addAll(constructors);
        classOrInterface.getMembers().addAll(methods);
        classOrInterface.getMembers().addAll(inners);
    }

    /**
     * Main entry point for command-line execution.
     * 
     * @param args command line arguments
     * @throws IOException if files could not be read/written
     */
    public static void main(String[] args) throws IOException {
        File yamlFile = new File(Settings.class.getClassLoader().getResource("depsolver.yml").getFile());
        Settings.loadConfigMap(yamlFile);
        DepSolver depSolver = DepSolver.createSolver();
        depSolver.solve();

        CopyUtils.createMavenProjectStructure(Settings.getBasePackage(),
                Settings.getProperty("output_path").toString());
        depSolver.writeFiles();
    }

    /**
     * Only for testing. Don't use for anything else.
     * 
     * @return the element at the top of the stack
     */
    public GraphNode peek() {
        if (stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }
}
