package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import java.io.FileNotFoundException;
import java.io.IOException;

public class InterfaceSolver extends AbstractCompiler {
    public InterfaceSolver() throws IOException {
        super();
    }

    @Override
    public boolean compile(String relativePath) throws FileNotFoundException {
        boolean b = super.compile(relativePath);

        for (TypeDeclaration<?> t : cu.getTypes()) {
            if (t.isClassOrInterfaceDeclaration() && t.getFullyQualifiedName().isPresent()) {
                ClassOrInterfaceDeclaration cdecl = t.asClassOrInterfaceDeclaration();
                for (ClassOrInterfaceType iface : cdecl.getImplementedTypes()) {
                    String interfaceName = AbstractCompiler.findFullyQualifiedName(cu, iface.getNameAsString());
                    if (interfaceName != null) {
                        AntikytheraRunTime.addImplementation(interfaceName, t.getFullyQualifiedName().get());
                    }
                }
            }
        }

        return b;
    }
}
