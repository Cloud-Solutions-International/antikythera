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
                resolveField(node, n.getMemberValue().asFieldAccessExpr());
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
                annotationBinaryExpr(node, n.getMemberValue());
            }
            else if (n.getMemberValue().isArrayInitializerExpr()) {
                annotationArray(node, n.getMemberValue());
            }
        }
        super.visit(n, node);
    }

    @Override
    public void visit(final MarkerAnnotationExpr n, final GraphNode node) {
        String[] fullName = n.getNameAsString().split("\\.");
        addImport(node, fullName[0]);
        super.visit(n, node);
    }

    private static void addImport(GraphNode node, String fullName) {
        ImportUtils.addImport(node, fullName);
    }

    @Override
    public void visit(final NormalAnnotationExpr n, final GraphNode node) {
        normalAnnotationExpr(n, node);
        super.visit(n, node);
    }

    private static void normalAnnotationExpr(NormalAnnotationExpr n, GraphNode node) {
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), n.getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
        }
        for(MemberValuePair pair : n.getPairs()) {
            Expression value = pair.getValue();
            if (value.isFieldAccessExpr()) {
                resolveField(node, value.asFieldAccessExpr());
            }
            else if (value.isBinaryExpr()) {
                annotationBinaryExpr(node, value);
            }
            else if (value.isNameExpr()) {
                annotationNameExpression(node, value);
            }
            else if (value.isArrayInitializerExpr()) {
                annotationArray(node, value);
            }
            else if (value.isClassExpr()) {
                ClassOrInterfaceType ct = value.asClassExpr().getType().asClassOrInterfaceType();
                addImport(node, ct.getName().toString());
            }
        }
    }

    private static void annotationBinaryExpr(GraphNode node, Expression value) {
        Expression left = value.asBinaryExpr().getLeft();
        if (left.isFieldAccessExpr()) {
            resolveField(node, left.asFieldAccessExpr());
        }
        else if (left.isNameExpr()) {
            node.getEnclosingType().getFieldByName(left.asNameExpr().getNameAsString()).ifPresentOrElse(
                    f -> {
                        try {
                            node.addField(f);
                        } catch (AntikytheraException e) {
                            throw new DepsolverException(e);
                        }
                    },
                    () -> annotationNameExpression(node, left)
            );
        }

        Expression right = value.asBinaryExpr().getRight();
        if (right.isFieldAccessExpr()) {
            resolveField(node, right.asFieldAccessExpr());
        }
    }

    private static void annotationNameExpression(GraphNode node, Expression value) {
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
                    t = AbstractCompiler.getMatchingType(cu, imp2.getName().getIdentifier());
                }
                else {
                    cu = AntikytheraRunTime.getCompilationUnit(imp2.getName().getQualifier().get().toString());
                    if (cu != null) {
                        t = AbstractCompiler.getMatchingType(cu, imp2.getName().getQualifier().get().getIdentifier());
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

    private static void annotationArray(GraphNode node, Expression value) {
        ArrayInitializerExpr aie = value.asArrayInitializerExpr();
        for (Expression expr : aie.getValues()) {
            if (expr.isAnnotationExpr()) {
                AnnotationExpr anne = expr.asAnnotationExpr();
                String fqName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), anne.getName().toString());
                if (fqName != null) {
                    node.getDestination().addImport(fqName);
                }
                if (anne.isNormalAnnotationExpr()) {
                    normalAnnotationExpr(anne.asNormalAnnotationExpr(), node);
                }
            }
            else if(expr.isFieldAccessExpr()) {
                resolveField(node, expr.asFieldAccessExpr());
            }
        }
    }

    protected static GraphNode resolveField(GraphNode node, FieldAccessExpr value) {
        Expression scope = value.asFieldAccessExpr().getScope();
        if (scope.isNameExpr()) {

            ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(),
                    scope.asNameExpr().getNameAsString()
            );
            if (imp2 != null) {
                node.getDestination().addImport(imp2.getImport());
                try {
                    if(imp2.getType() != null) {
                        Graph.createGraphNode(imp2.getType());
                    }
                    if(imp2.getField() != null) {
                        Graph.createGraphNode(imp2.getField());
                    }
                    else {
                        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp2.getNameAsString());
                        if (cu != null) {
                            TypeDeclaration<?> t = AbstractCompiler.getMatchingType(cu, scope.asNameExpr().getNameAsString());
                            if (t != null) {
                                FieldDeclaration f = t.getFieldByName(value.getNameAsString()).orElse(null);
                                if (f != null) {
                                    return Graph.createGraphNode(f);
                                }
                            }
                        }
                    }

                } catch (AntikytheraException e) {
                    throw new GeneratorException(e);
                }
            }
            else {
                return resolveThisField(node, value);
            }
        }
        else if (scope.isThisExpr()) {
            return  resolveThisField(node, value);
        }
        return null;
    }

    private static GraphNode resolveThisField(GraphNode node, FieldAccessExpr value) {
        TypeDeclaration<?> decl = node.getEnclosingType();
        if (decl != null && decl.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration cdecl = decl.asClassOrInterfaceDeclaration();
            FieldDeclaration f = cdecl.getFieldByName(value.getNameAsString()).orElse(null);
            if (f != null) {
                try {
                    node.addField(f);
                    Type t = f.getElementType();
                    String fqname = AbstractCompiler.findFullyQualifiedName(
                            node.getCompilationUnit(), t.asClassOrInterfaceType().getNameAsString()
                    );
                    if (fqname != null) {
                        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqname);
                        if (cu != null) {
                            TypeDeclaration<?> p = AbstractCompiler.getPublicType(cu);
                            if (p != null) {
                                return Graph.createGraphNode(p);
                            }
                        }
                    }
                } catch (AntikytheraException e) {
                    throw new GeneratorException(e);
                }
            }
        }
        return null;
    }
}
