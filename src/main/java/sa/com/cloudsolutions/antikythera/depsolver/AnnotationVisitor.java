package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

/**
 * Visitor to help process annotations on classes, methods, fields, etc.
 */
public class AnnotationVisitor extends VoidVisitorAdapter<GraphNode> {
    @Override
    public void visit(final SingleMemberAnnotationExpr n, final GraphNode node) {
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), n.getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
        }
        if (n.getMemberValue() != null) {
            if (n.getMemberValue().isFieldAccessExpr()) {
                Resolver.resolveField(node, n.getMemberValue().asFieldAccessExpr());
            }
            else if (n.getMemberValue().isNameExpr()) {
                Resolver.resolveNameExpr(node, n.getMemberValue().asNameExpr(),new NodeList<>());
            }
            else if (n.getMemberValue().isClassExpr()) {
                ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(),
                        n.getMemberValue().asClassExpr().getTypeAsString()
                );
                if (imp2 != null) {
                    node.getDestination().addImport(imp2.getImport());
                }
            }
            else if (n.getMemberValue().isBinaryExpr()) {
                Resolver.resolveBinaryExpr(node, n.getMemberValue());
            }
            else if (n.getMemberValue().isArrayInitializerExpr()) {
                Resolver.resolveArrayExpr(node, n.getMemberValue());
            }
        }
        super.visit(n, node);
    }

    @Override
    public void visit(final MarkerAnnotationExpr n, final GraphNode node) {
        String[] fullName = n.getNameAsString().split("\\.");
        ImportUtils.addImport(node, fullName[0]);
        super.visit(n, node);
    }

    @Override
    public void visit(final NormalAnnotationExpr n, final GraphNode node) {
        Resolver.resolveNormalAnnotationExpr(node, n);
        super.visit(n, node);
    }

}
