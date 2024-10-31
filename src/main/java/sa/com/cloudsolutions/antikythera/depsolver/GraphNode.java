package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

public class GraphNode {
    Node node;
    CompilationUnit destination;

    public GraphNode(Node node) throws AntikytheraException {
        this.node = node;

        ClassOrInterfaceDeclaration enclosing = AbstractCompiler.getEnclosingClassOrInterface(node);

        this.destination = new CompilationUnit();

        ClassOrInterfaceDeclaration cdecl = destination.addClass(enclosing.getNameAsString());
        CompilationUnit src = cdecl.findCompilationUnit().get();
        cdecl.setInterface(enclosing.isInterface());

        for (ClassOrInterfaceType ifc : enclosing.getImplementedTypes()) {
            cdecl.addImplementedType(ifc.getNameAsString());
            ImportDeclaration imp = AbstractCompiler.findImport(src, ifc.getNameAsString());
            if (imp != null) {
                src.addImport(imp);
            }
        }

        for (ClassOrInterfaceType ifc : enclosing.getExtendedTypes()) {
            cdecl.addImplementedType(ifc.getNameAsString());
            ImportDeclaration imp = AbstractCompiler.findImport(src, ifc.getNameAsString());
            if (imp != null) {
                src.addImport(imp);
            }
        }
    }
}
