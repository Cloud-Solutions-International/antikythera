package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

/**
 * Visitor to help r
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
                ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(),
                        n.getMemberValue().asNameExpr().getNameAsString()
                );
                if (imp2 != null) {
                    node.getDestination().addImport(imp2.getImport());
                }
            }
            else if (n.getMemberValue().isBinaryExpr()) {
                Resolver.annotationBinaryExpr(node, n.getMemberValue());
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
        Resolver.normalAnnotationExpr(n, node);
        super.visit(n, node);
    }

}
