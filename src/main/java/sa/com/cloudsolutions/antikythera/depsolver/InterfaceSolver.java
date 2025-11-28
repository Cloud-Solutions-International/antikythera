package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Builds the relation between interfaces and classes that implement them.
 */
public class InterfaceSolver extends AbstractCompiler {

    // Tracks visited interface FQNs to avoid infinite recursion in cyclic/interface hierarchies.
    private final Set<String> visitedInterfaces = new HashSet<>();

    /**
     * Constructs an InterfaceSolver instance and initializes the superclass.
     *
     * @throws IOException if an I/O error occurs during initialization.
     */
    public InterfaceSolver() throws IOException {
        super();
    }

    /**
     * Compiles the Java source file and builds interface relations
     *
     * @param relativePath the relative path to the Java source file.
     * @return true if the compilation has been already been done. (We are finding interface
     *      implementation for the cached result)
     * @throws FileNotFoundException if the source file is not found.
     */
    @Override
    public boolean compile(String relativePath) throws FileNotFoundException {
        boolean b = super.compile(relativePath);

        for (TypeDeclaration<?> t : cu.getTypes()) {
            if (t.isClassOrInterfaceDeclaration() && t.getFullyQualifiedName().isPresent()) {
                ClassOrInterfaceDeclaration cdecl = t.asClassOrInterfaceDeclaration();
                solveInterfaces(cdecl); // now guarded against cycles
                solveExtends(cdecl);
            }
        }
        return b;
    }

    private void solveExtends(ClassOrInterfaceDeclaration cdecl) {
        for (ClassOrInterfaceType parent : cdecl.getExtendedTypes()) {
            String parentName = AbstractCompiler.findFullyQualifiedName(cu, parent.getNameAsString());
            if (parentName != null) {
                AntikytheraRunTime.addSubClass(parentName, cdecl.getFullyQualifiedName().orElseThrow());
            }
        }
    }

    private void solveInterfaces(ClassOrInterfaceDeclaration cdecl) {
        // Delegate with a shared visited set so nested/interface lookups don't loop endlessly.
        solveInterfaces(cdecl, visitedInterfaces);
    }

    /**
     * Resolves interface implementations for the given class or interface declaration.
     * Adds implementation records for the current class against all directly implemented interfaces
     * and their parent interfaces. Uses a visited set to prevent infinite recursion in cases of
     * cyclic interface inheritance (e.g., A extends B, B extends A) or repeated re-entry while
     * loading compilation units that reference each other.
     */
    private void solveInterfaces(ClassOrInterfaceDeclaration cdecl, Set<String> visited) {
        // If we are examining an interface itself (not a concrete class) guard against cycles.
        if (cdecl.isInterface()) {
            String ifaceName = cdecl.getFullyQualifiedName().orElse(null);
            if (ifaceName != null) {
                // If already visited, bail out to prevent infinite recursion.
                if (!visited.add(ifaceName)) {
                    return;
                }
            }
        }

        // Only concrete classes (or records) will have implemented interfaces; for interfaces this loop is a no-op.
        for (ClassOrInterfaceType iface : cdecl.getImplementedTypes()) {
            String interfaceName = AbstractCompiler.findFullyQualifiedName(cu, iface.getNameAsString());
            if (interfaceName != null) {
                /*
                 * The interfaceName variable represents an interface that has been implemented by
                 * the class declaration represented by the cdecl variable. Calling the
                 * addImplementation method will result in a record being created that cdecl is an
                 * implementation of the interface.
                 * This allows us to substitute the concrete class whenever we encounter an
                 * @Autowired type
                 */
                AntikytheraRunTime.addImplementation(interfaceName, cdecl.getFullyQualifiedName().orElseThrow());
                /*
                 * Some interfaces have their own parent interface and this class will have to be
                 * identified as an implementation of that parent as well.
                 */
                CompilationUnit interfaceCu = AntikytheraRunTime.getCompilationUnit(interfaceName);
                if (interfaceCu != null) {
                    for (TypeDeclaration<?> ifaceType : interfaceCu.getTypes()) {
                        if (ifaceType.isClassOrInterfaceDeclaration()) {
                            ClassOrInterfaceDeclaration ifaceDecl = ifaceType.asClassOrInterfaceDeclaration();
                            // Only recurse into interfaces; recursing into concrete classes declared in the same
                            // source can cause infinite loops (class -> interface -> class ...).
                            if (ifaceDecl.isInterface()) {
                                solveInterfaces(ifaceDecl, visited);
                            }
                            for (ClassOrInterfaceType parent : ifaceDecl.getExtendedTypes()) {
                                String parentName = AbstractCompiler.findFullyQualifiedName(interfaceCu, parent.getNameAsString());
                                if (parentName != null) {
                                    AntikytheraRunTime.addImplementation(parentName, cdecl.getFullyQualifiedName().orElseThrow());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
