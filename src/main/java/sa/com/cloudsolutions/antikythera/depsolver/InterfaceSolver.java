package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Builds the relation between interfaces and classes that implement them.
 */
public class InterfaceSolver extends AbstractCompiler {

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
                solveInterfaces(cdecl);
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
                            solveInterfaces(ifaceDecl);
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

