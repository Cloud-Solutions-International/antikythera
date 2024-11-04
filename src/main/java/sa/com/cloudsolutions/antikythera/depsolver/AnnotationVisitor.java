package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

public class AnnotationVisitor extends VoidVisitorAdapter<GraphNode> {
    @Override
    public void visit(final SingleMemberAnnotationExpr n, final GraphNode node) {
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), n.getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
        }
        if (n.getMemberValue() != null) {
            if (n.getMemberValue().isFieldAccessExpr()) {
                resolveField(node, n.getMemberValue());
            }
            else if (n.getMemberValue().isNameExpr()) {
                ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(),
                        n.getMemberValue().asNameExpr().getNameAsString()
                );
                if (imp2 != null) {
                    node.getDestination().addImport(imp2.getImport());
                }
            }
        }
        super.visit(n, node);
    }

    @Override
    public void visit(final MarkerAnnotationExpr n, final GraphNode node) {
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), n.getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
        }
        super.visit(n, node);
    }

    @Override
    public void visit(final NormalAnnotationExpr n, final GraphNode node) {
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), n.getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
        }
        for(MemberValuePair pair : n.getPairs()) {
            Expression value = pair.getValue();
            if (value.isFieldAccessExpr()) {
                resolveField(node, value);
            }
            else if (value.isNameExpr()) {
                ImportWrapper iw2 = AbstractCompiler.findImport(node.getCompilationUnit(),
                        value.asNameExpr().getNameAsString()
                );
                if (iw2 != null) {
                    ImportDeclaration imp2 = iw2.getImport();
                    node.getDestination().addImport(imp2);
                    if (imp2.isStatic()) {
                        TypeDeclaration<?> t = null;
                        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp2.getNameAsString());
                        if (cu != null) {
                            t = AbstractCompiler.getMatchingClass(cu, imp2.getName().getIdentifier());
                        }
                        else {
                            cu = AntikytheraRunTime.getCompilationUnit(imp2.getName().getQualifier().get().toString());
                            if (cu != null) {
                                t = AbstractCompiler.getMatchingClass(cu, imp2.getName().getQualifier().get().getIdentifier());
                            }
                        }
                        if(t != null) {
                            FieldDeclaration f = t.getFieldByName(value.asNameExpr().getNameAsString()).orElse(null);
                            if (f != null) {

                                try {
                                    Graph.createGraphNode(f);

                                } catch (AntikytheraException e) {
                                    throw new DepsolverException(e);
                                }
                            }
                        }
                    }
                }
            }
            else if (value.isArrayInitializerExpr()) {
                annotationArray(node, value);
            }
        }
        super.visit(n, node);
    }

    private static void annotationArray(GraphNode node, Expression value) {
        ArrayInitializerExpr aie = value.asArrayInitializerExpr();
        for (Expression e : aie.getValues()) {
            if (e.isAnnotationExpr()) {
                AnnotationExpr anne = e.asAnnotationExpr();
                String fqName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), anne.getName().toString());
                if (fqName != null) {
                    node.getDestination().addImport(fqName);
                }
                if (anne.isNormalAnnotationExpr()) {
                    NormalAnnotationExpr norm = anne.asNormalAnnotationExpr();
                    for (MemberValuePair value2 : norm.getPairs()) {
                        if (value2.getValue().isAnnotationExpr()) {
                            AnnotationExpr ae = value2.getValue().asAnnotationExpr();
                            fqName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), ae.getName().toString());
                            if (fqName != null) {
                                node.getDestination().addImport(fqName);
                            }
                        }
                    }
                }
            }
            else if(e.isFieldAccessExpr()) {
                resolveField(node, e);
            }
        }
    }

    protected static void resolveField(GraphNode node, Expression value) {
        Expression scope = value.asFieldAccessExpr().getScope();
        if (scope.isNameExpr()) {
            ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(),
                    scope.asNameExpr().getNameAsString()
            );
            if (imp2 != null) {
                node.getDestination().addImport(imp2.getImport());
            }
        }
    }
}
