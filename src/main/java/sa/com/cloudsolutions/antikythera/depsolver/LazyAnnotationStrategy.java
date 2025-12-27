package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.ImportDeclaration;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Strategy for breaking circular dependencies by adding @Lazy annotation.
 * 
 * <p>
 * Works for field and setter injection. For constructor injection,
 * use SetterInjectionStrategy or InterfaceExtractionStrategy instead.
 * </p>
 */
public class LazyAnnotationStrategy {

    private static final String LAZY_ANNOTATION = "Lazy";
    private static final String LAZY_IMPORT = "org.springframework.context.annotation.Lazy";

    private final Set<CompilationUnit> modifiedCUs = new HashSet<>();
    private boolean dryRun = false;

    public LazyAnnotationStrategy() {
    }

    public LazyAnnotationStrategy(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Apply @Lazy to a dependency edge.
     * 
     * @param edge The dependency edge to fix
     * @return true if @Lazy was successfully added
     */
    public boolean apply(BeanDependency edge) {
        Node astNode = edge.astNode();
        if (astNode == null) {
            System.out.println("❌ No AST node for edge: " + edge);
            return false;
        }

        boolean result = switch (edge.injectionType()) {
            case FIELD -> addLazyToField(astNode, edge);
            case SETTER -> addLazyToSetter(astNode, edge);
            case CONSTRUCTOR -> addLazyToConstructorParameter(astNode, edge);
            case BEAN_METHOD -> addLazyToBeanMethodParameter(astNode, edge);
        };

        if (result) {
            System.out.println("✅ Added @Lazy to " + edge);
        }

        return result;
    }

    /**
     * Add @Lazy to a field declaration.
     */
    private boolean addLazyToField(Node node, BeanDependency edge) {
        if (!(node instanceof FieldDeclaration field)) {
            System.out.println("❌ Expected FieldDeclaration but got: " + node.getClass().getSimpleName());
            return false;
        }

        // Check if already has @Lazy
        if (field.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            System.out.println("ℹ️  Already has @Lazy: " + edge);
            return true;
        }

        // Add @Lazy annotation
        field.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        field.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add @Lazy to a setter method's parameter.
     */
    private boolean addLazyToSetter(Node node, BeanDependency edge) {
        if (!(node instanceof MethodDeclaration method)) {
            System.out.println("❌ Expected MethodDeclaration but got: " + node.getClass().getSimpleName());
            return false;
        }

        // Check if already has @Lazy on method
        if (method.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            System.out.println("ℹ️  Already has @Lazy: " + edge);
            return true;
        }

        // Add @Lazy to the setter method (not the parameter)
        method.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        method.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add @Lazy to a constructor parameter.
     * Spring supports @Lazy on constructor parameters to break cycles.
     */
    private boolean addLazyToConstructorParameter(Node node, BeanDependency edge) {
        if (!(node instanceof ConstructorDeclaration ctor)) {
            System.out.println("❌ Expected ConstructorDeclaration but got: " + node.getClass().getSimpleName());
            return false;
        }

        String paramName = edge.fieldName();
        Parameter param = ctor.getParameters().stream()
                .filter(p -> p.getNameAsString().equals(paramName))
                .findFirst()
                .orElse(null);

        if (param == null) {
            System.out.println("❌ Parameter not found: " + paramName);
            return false;
        }

        // Check if already has @Lazy
        if (param.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            System.out.println("ℹ️  Already has @Lazy: " + edge);
            return true;
        }

        // Add @Lazy to the parameter
        param.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        ctor.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add @Lazy to a @Bean method parameter.
     * Spring supports @Lazy on @Bean method parameters to break cycles.
     */
    private boolean addLazyToBeanMethodParameter(Node node, BeanDependency edge) {
        if (!(node instanceof MethodDeclaration method)) {
            System.out.println("❌ Expected MethodDeclaration but got: " + node.getClass().getSimpleName());
            return false;
        }

        // Verify this is a @Bean method
        if (method.getAnnotationByName("Bean").isEmpty()) {
            System.out.println("❌ Method is not a @Bean method: " + method.getNameAsString());
            return false;
        }

        String paramName = edge.fieldName();
        Parameter param = method.getParameters().stream()
                .filter(p -> p.getNameAsString().equals(paramName))
                .findFirst()
                .orElse(null);

        if (param == null) {
            System.out.println("❌ Parameter not found in @Bean method: " + paramName);
            return false;
        }

        // Check if already has @Lazy
        if (param.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            System.out.println("ℹ️  Already has @Lazy: " + edge);
            return true;
        }

        // Add @Lazy to the parameter
        param.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        method.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add the @Lazy import if not already present.
     */
    private void addLazyImport(CompilationUnit cu) {
        boolean hasImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(LAZY_IMPORT));

        if (!hasImport) {
            cu.addImport(new ImportDeclaration(LAZY_IMPORT, false, false));
        }
    }

    /**
     * Write all modified compilation units to disk.
     * 
     * @param basePath The base path for the source files
     * @throws IOException if writing fails
     */
    public void writeChanges(String basePath) throws IOException {
        if (dryRun) {
            System.out.println("\n🔍 Dry run - " + modifiedCUs.size() + " file(s) would be modified");
            return;
        }

        System.out.println("\n📝 Writing " + modifiedCUs.size() + " modified file(s)...");

        for (CompilationUnit cu : modifiedCUs) {
            // Get the original file path from JavaParser's storage
            if (cu.getStorage().isPresent()) {
                Path filePath = cu.getStorage().get().getPath();
                CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
                System.out.println("   ✓ " + filePath);
            } else {
                // Fallback: compute path from basePath + package + class
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString().replace('.', '/'))
                        .orElse("");
                // Use findFirst to get the class name since getPrimaryTypeName may be empty
                String className = cu.findFirst(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                        .map(c -> c.getNameAsString())
                        .orElse(cu.getPrimaryTypeName().orElse("Unknown"));
                Path filePath = Path.of(basePath, packageName, className + ".java");
                CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
                System.out.println("   ✓ " + filePath);
            }
        }
    }

    /**
     * Get the set of modified compilation units.
     */
    public Set<CompilationUnit> getModifiedCUs() {
        return modifiedCUs;
    }

    /**
     * Check if running in dry-run mode.
     */
    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
