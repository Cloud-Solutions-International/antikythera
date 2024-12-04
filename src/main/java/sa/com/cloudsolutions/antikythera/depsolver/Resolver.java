package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.lang.reflect.Field;
import java.util.LinkedList;
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


    static GraphNode chainedMethodCall(GraphNode node, MCEWrapper mceWrapper) throws AntikytheraException {
        if (mceWrapper.getMethodCallExpr() instanceof MethodCallExpr mce) {
            LinkedList<Expression> chain = Evaluator.findScopeChain(mce);

            if (chain.isEmpty()) {
                copyMethod(mceWrapper, node);
                return node;
            } else {
                GraphNode gn = evaluateScopeChain(node, chain);
                if (gn != null) {
                    copyMethod(mceWrapper, gn);
                    return gn;
                }
            }
        }
        else if (mceWrapper.getMethodCallExpr() instanceof ObjectCreationExpr oce) {
            return ImportUtils.addImport(node, oce.getType());
        }
        return null;
    }

    static GraphNode evaluateScopeChain(GraphNode node, LinkedList<Expression> chain) throws AntikytheraException {
        GraphNode gn = node;
        while (!chain.isEmpty() && gn != null) {
            Expression expr = chain.pollLast();
            if (expr.isFieldAccessExpr()) {
                FieldAccessExpr fieldAccessExpr = expr.asFieldAccessExpr();
                gn = Resolver.resolveField(gn, fieldAccessExpr);
            }
            else if (expr.isMethodCallExpr()) {
                gn = copyMethod(solveArgumentTypes(gn, expr.asMethodCallExpr()), gn);
            }
            else if (expr.isNameExpr()) {
                gn = evaluateNameExpr(expr, gn);
            }
        }

        return gn;
    }

    static GraphNode evaluateNameExpr(Expression expr, GraphNode gn) throws AntikytheraException {
        NameExpr nameExpr = expr.asNameExpr();
        TypeDeclaration<?> cdecl = gn.getEnclosingType();
        Type t = DepSolver.getNames().get(expr.toString());
        if (t == null) {
            Optional<FieldDeclaration> fd = cdecl.getFieldByName(nameExpr.getNameAsString());

            if (fd.isPresent()) {
                Type field = fd.get().getElementType();
                gn.addField(fd.get());

                if (field != null) {
                    for (AnnotationExpr ann : field.getAnnotations()) {
                        ImportUtils.addImport(gn, ann.getNameAsString());
                    }

                    Type elementType = field.getElementType();
                    if (elementType.isClassOrInterfaceType()) {
                        Optional<NodeList<Type>> types = elementType.asClassOrInterfaceType().getTypeArguments();
                        if (types.isPresent()) {
                            for (Type type : types.get()) {
                                ImportUtils.addImport(gn, type);
                            }

                        }
                        gn = ImportUtils.addImport(gn, elementType.asClassOrInterfaceType().getName().toString());
                    } else {
                        gn = ImportUtils.addImport(gn, elementType);

                    }
                }
            }
            else {
                gn = ImportUtils.addImport(gn, nameExpr.getName().toString());
            }
        }
        else {
            gn = ImportUtils.addImport(gn, t.asString());
        }
        return gn;
    }


    static MCEWrapper solveArgumentTypes(GraphNode node, NodeWithArguments<?> mce) throws AntikytheraException {
        MCEWrapper mw = new MCEWrapper();
        NodeList<Type> types = new NodeList<>();

        NodeList<Expression> arguments = mce.getArguments();

        for(Expression arg : arguments) {
            processExpression(node, arg, types);
        }
        if (types.size() == arguments.size()) {
            mw.setArgumentTypes(types);
        }

        mw.setMethodCallExpr(mce);
        return mw;
    }

    static void processExpression(GraphNode node, Expression expr, NodeList<Type> types) throws AntikytheraException {
        if (expr.isNameExpr()) {
            expressionAsNameExpr(node, expr, types);
        }
        else if (expr.isLiteralExpr()) {
            types.add(AbstractCompiler.convertLiteralToType(expr.asLiteralExpr()));
        }
        else if (expr.isFieldAccessExpr()) {
            Resolver.expressionAsFieldAccess(node, expr, types);
        }
        else if (expr.isMethodCallExpr()) {
            wrapCallable(node, expr.asMethodCallExpr(), types);
        }
        else if (expr.isObjectCreationExpr()) {
            wrapCallable(node, expr.asObjectCreationExpr(), types);
        }
        else if (expr.isMethodReferenceExpr()) {
            expressionAsMethodReference(node, expr);
        }
        else if (expr.isConditionalExpr()) {
            ConditionalExpr ce = expr.asConditionalExpr();
            if (ce.getThenExpr().isNameExpr()) {
                expressionAsNameExpr(node, ce.getThenExpr(), types);
            }
            if (ce.getElseExpr().isNameExpr()) {
                expressionAsNameExpr(node, ce.getElseExpr(), types);
            }
        }
        else if (expr.isAssignExpr()) {
            System.out.println("Assign " + expr);
        }
        else {
            // seems other types dont need special handling they are caught else where
        }
    }


    static void expressionAsMethodReference(GraphNode node, Expression arg) throws AntikytheraException {
        MethodReferenceExpr mre = arg.asMethodReferenceExpr();
        Expression scope = mre.getScope();
        if (scope.isNameExpr()) {
            ImportUtils.addImport(node, scope.asNameExpr().getNameAsString());
        }
        else if (scope.isThisExpr()) {
            for (MethodDeclaration m : node.getEnclosingType().getMethodsByName(mre.getIdentifier())) {
                Graph.createGraphNode(m);
            }
        }
        else if (scope.isTypeExpr()) {
            ImportUtils.addImport(node, scope.asTypeExpr().getType().asString());
        }
    }

    static void wrapCallable(GraphNode node, NodeWithArguments<?> arg, NodeList<Type> types) throws AntikytheraException {
        if (arg instanceof MethodCallExpr argMethodCall) {
            MCEWrapper wrap = solveArgumentTypes(node, arg);
            GraphNode gn = Resolver.chainedMethodCall(node, wrap);
            if (gn != null) {
                if (gn.getNode() instanceof MethodDeclaration md) {
                    Type t = md.getType();
                    ImportUtils.addImport(node, t);
                    types.add(t);
                } else if (gn.getNode() instanceof ClassOrInterfaceDeclaration cid) {
                    Optional<CallableDeclaration<?>> omd = AbstractCompiler.findCallableDeclaration(wrap, cid);
                    if (omd.isPresent()) {
                        CallableDeclaration<?> cd = omd.get();
                        if (cd instanceof MethodDeclaration md) {
                            Type t = md.getType();
                            types.add(t);
                            ImportUtils.addImport(node, t);

                        }
                        cd.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(c -> {
                            ImportUtils.addImport(node, c.getNameAsString());
                        });
                    } else {
                        Type t = lombokSolver(argMethodCall, cid, gn);
                        if (t != null) {
                            types.add(t);
                        }
                    }
                }
            }
        }
    }

    static Type lombokSolver(MethodCallExpr argMethodCall, ClassOrInterfaceDeclaration cid, GraphNode gn) {
        if (argMethodCall.getNameAsString().startsWith("get") &&
                cid.getAnnotationByName("Data").isPresent() ||
                cid.getAnnotationByName("Getter").isPresent()
        ) {
            String field = argMethodCall.getNameAsString().substring(3);
            Optional<FieldDeclaration> fd = cid.getFieldByName(ClassProcessor.classToInstanceName(field));
            if (fd.isPresent()) {
                Type t = fd.get().getElementType();
                ImportUtils.addImport(gn, t);
                return t;
            }
        }
        return null;
    }

    static GraphNode copyMethod(MCEWrapper mceWrapper, GraphNode node) throws AntikytheraException {
        TypeDeclaration<?> cdecl = node.getEnclosingType();
        if (cdecl != null) {
            Optional<CallableDeclaration<?>> md = AbstractCompiler.findCallableDeclaration(
                    mceWrapper, cdecl
            );

            if (md.isPresent()) {
                CallableDeclaration<?> method = md.get();
                for (Type ex : method.getThrownExceptions()) {
                    ImportUtils.addImport(node, ex.asString());
                }

                if (method.isAbstract()) {
                    Optional<ClassOrInterfaceDeclaration> parent = method.findAncestor(ClassOrInterfaceDeclaration.class);

                    if (!parent.get().isInterface()) {
                        Optional<CallableDeclaration<?>> overRides = AbstractCompiler.findMethodDeclaration(mceWrapper, cdecl, false);
                        if (overRides.isPresent()) {
                            Graph.createGraphNode(overRides.get());
                        }
                    }
                }
                return Graph.createGraphNode(method);
            } else if (mceWrapper.getMethodCallExpr() instanceof MethodCallExpr mce && cdecl instanceof ClassOrInterfaceDeclaration decl) {
                Type t = lombokSolver(mce, decl, node);
                if (t != null && t.isClassOrInterfaceType()) {
                    return ImportUtils.addImport(node, t.asClassOrInterfaceType().getNameAsString());
                } else {
                    ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), mce.getNameAsString());
                    if (imp != null) {
                        if (imp.getMethodDeclaration() != null) {
                            node.getDestination().addImport(imp.getImport());
                            Graph.createGraphNode(imp.getMethodDeclaration());
                        }
                    }
                    else {
                        // desperate measure hack
                        // todo remove this
                        for (MethodDeclaration method : decl.getMethodsByName(mce.getNameAsString())) {
                            if (method.getParameters().size() == mce.getArguments().size()) {
                                Graph.createGraphNode(method);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }


    static void expressionAsNameExpr(GraphNode node, Expression arg, NodeList<Type> types) throws AntikytheraException {
        Type t = DepSolver.getNames().get(arg.asNameExpr().getNameAsString());
        if (t != null) {
            types.add(t);
        }
        else if (node.getEnclosingType().isClassOrInterfaceDeclaration()){
            ClassOrInterfaceDeclaration cdecl = node.getEnclosingType().asClassOrInterfaceDeclaration();
            Optional<FieldDeclaration> fd = cdecl.getFieldByName(arg.asNameExpr().getNameAsString());

            if (fd.isPresent()) {
                node.addField(fd.get());
                Type field = fd.get().getElementType();

                if (field != null) {
                    types.add(field);
                    ImportUtils.addImport(node, field.getElementType().asString());
                    for (AnnotationExpr ann : field.getAnnotations()) {
                        ImportUtils.addImport(node, ann.getNameAsString());
                    }
                }
            }
            else {
                ImportUtils.addImport(node, arg.asNameExpr().getNameAsString());
            }
        }
    }

}
