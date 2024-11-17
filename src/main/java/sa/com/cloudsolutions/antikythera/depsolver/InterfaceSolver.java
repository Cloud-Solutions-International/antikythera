package sa.com.cloudsolutions.antikythera.depsolver;

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

                for (ClassOrInterfaceType iface : cdecl.getImplementedTypes()) {
                    String interfaceName = AbstractCompiler.findFullyQualifiedName(cu, iface.getNameAsString());
                    if (interfaceName != null) {
                        /*
                         * The interfaceName variable represents an interface that has been implemented by the
                         * cdecl class. The call to addImplementation will result in record being created that
                         * cdecl is an implementation of the interface. Thus when ever @Autowired is encountered
                         * we can make use of one of the implementing classes.
                         */
                        AntikytheraRunTime.addImplementation(interfaceName, t.getFullyQualifiedName().get());
                    }
                }

                for (ClassOrInterfaceType parent : cdecl.getExtendedTypes()) {
                    String parentName = AbstractCompiler.findFullyQualifiedName(cu, parent.getNameAsString());
                    if (parentName != null) {
                        AntikytheraRunTime.addSubClass(parentName, t.getFullyQualifiedName().get());
                    }
                }
            }
        }
        return b;
    }

}
