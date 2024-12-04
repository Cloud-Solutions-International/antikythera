package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

public class Resolver {

    public static GraphNode resolveThisFieldAccess(GraphNode node, FieldAccessExpr value) {
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


    public static GraphNode resolveField(GraphNode node, FieldAccessExpr value) {
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
                return Resolver.resolveThisFieldAccess(node, value);
            }
        }
        else if (scope.isThisExpr()) {
            return  Resolver.resolveThisFieldAccess(node, value);
        }
        return null;
    }


    static void resolveArrayExpr(GraphNode node, Expression value) {
        ArrayInitializerExpr aie = value.asArrayInitializerExpr();
        for (Expression expr : aie.getValues()) {
            if (expr.isAnnotationExpr()) {
                AnnotationExpr anne = expr.asAnnotationExpr();
                String fqName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), anne.getName().toString());
                if (fqName != null) {
                    node.getDestination().addImport(fqName);
                }
                if (anne.isNormalAnnotationExpr()) {
                    resolveNormalAnnotationExpr(node, anne.asNormalAnnotationExpr());
                }
            }
            else if(expr.isFieldAccessExpr()) {
                Resolver.resolveField(node, expr.asFieldAccessExpr());
            }
        }
    }


    static void resolveNormalAnnotationExpr(GraphNode node, NormalAnnotationExpr n) {
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), n.getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
        }
        for(MemberValuePair pair : n.getPairs()) {
            Expression value = pair.getValue();
            if (value.isFieldAccessExpr()) {
                Resolver.resolveField(node, value.asFieldAccessExpr());
            }
            else if (value.isBinaryExpr()) {
                resolveBinaryExpr(node, value);
            }
            else if (value.isNameExpr()) {
                resolveNameExpression(node, value);
            }
            else if (value.isArrayInitializerExpr()) {
                Resolver.resolveArrayExpr(node, value);
            }
            else if (value.isClassExpr()) {
                ClassOrInterfaceType ct = value.asClassExpr().getType().asClassOrInterfaceType();
                ImportUtils.addImport(node, ct.getName().toString());
            }
        }
    }


    static void resolveBinaryExpr(GraphNode node, Expression value) {
        Expression left = value.asBinaryExpr().getLeft();
        if (left.isFieldAccessExpr()) {
            Resolver.resolveField(node, left.asFieldAccessExpr());
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
                    () -> resolveNameExpression(node, left)
            );
        }

        Expression right = value.asBinaryExpr().getRight();
        if (right.isFieldAccessExpr()) {
            Resolver.resolveField(node, right.asFieldAccessExpr());
        }
    }

    private static void resolveNameExpression(GraphNode node, Expression value) {
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

    public static Optional<Type> resolveScopedNameExpression(Expression scope, NodeWithSimpleName<?> fae,
                                                       GraphNode node, final Map<String, Type> names) throws AntikytheraException {
        if (names != null ) {
            Type t = names.get(scope.asNameExpr().getNameAsString());

            if (t != null) {
                return Optional.of(t);
            }
        }

        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), scope.asNameExpr().getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
            if (imp.isExternal()) {
                return getExternalType(fae, imp);
            }
            if (imp.getField() == null ) {
                if (imp.getImport().isAsterisk()) {
                    TypeDeclaration<?> td = imp.getType();
                    createFieldNode(fae, td);
                }
                else {
                    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp.getImport().getNameAsString());
                    if (cu != null) {
                        TypeDeclaration<?> td = AbstractCompiler.getPublicType(cu);
                        createFieldNode(fae, td);
                    }
                }
            }
        }
        return Optional.empty();
    }


    static Optional<Type> getExternalType(NodeWithSimpleName<?> fae, ImportWrapper imp) {
        try {
            Class<?> c = Class.forName(imp.getNameAsString());
            Field f = c.getField(fae.getNameAsString());
            ClassOrInterfaceType ct = new ClassOrInterfaceType(null, f.getType().getTypeName());
            return Optional.of(ct);

        } catch (ReflectiveOperationException e) {
            System.err.println(e.getMessage());
        }
        return Optional.empty();
    }


    static void createFieldNode(NodeWithSimpleName<?> fae, TypeDeclaration<?> td) throws AntikytheraException {
        if (td != null) {
            Optional<FieldDeclaration> fieldByName = td.getFieldByName(fae.getNameAsString());
            if (fieldByName.isPresent()) {
                Graph.createGraphNode(fieldByName.get());
            }
        }
    }

    public static void expressionAsFieldAccess(GraphNode node, Expression expr, NodeList<Type> types) throws AntikytheraException {
        FieldAccessExpr fae = expr.asFieldAccessExpr();
        Expression scope = fae.getScope();
        if (scope.isNameExpr()) {
            Optional<Type> t = resolveScopedNameExpression(scope, fae, node, null);
            if (t.isPresent()) {
                types.add(t.get());
            }
        }
        else {
            if (scope.isFieldAccessExpr()) {
                expressionAsFieldAccess(node, scope, types);
            }
            else {
                ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), fae.getNameAsString());
                if (imp != null) {
                    node.getDestination().addImport(imp.getImport());
                    if (imp.isExternal()) {
                        Optional<Type> ct = getExternalType(fae, imp);
                        if (ct.isPresent()) {
                            types.add(ct.get());
                        }
                    }
                }
            }
        }
    }
}
