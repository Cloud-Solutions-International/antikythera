package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Scope;
import sa.com.cloudsolutions.antikythera.evaluator.ScopeChain;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.Callable;
import sa.com.cloudsolutions.antikythera.parser.ImportUtils;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;
import sa.com.cloudsolutions.antikythera.parser.MCEWrapper;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * The Resolver class is responsible for resolving various JavaParser AST
 * expressions into meaningful graph nodes or types within the dependency graph.
 * It handles field accesses, method calls, variable names, and type lookups,
 * often delegating to AbstractCompiler for cross-compilation unit resolution.
 */
public class Resolver {

    public static record ScopedResolution(Type type, ImportWrapper importWrapper, TypeDeclaration<?> resolvedTypeDecl) {

        public boolean hasType() {
            return type != null;
        }

        public boolean hasImportWrapper() {
            return importWrapper != null;
        }

        public boolean hasResolvedTypeDecl() {
            return resolvedTypeDecl != null;
        }
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Resolver() {
    }

    /**
     * Resolve a field that is accessed through the <code>this.</code> prefix
     *
     * @param node  a graph node representing the current context
     * @param value the field access expression
     * @return a graph node representing the resolved field
     */
    public static GraphNode resolveThisFieldAccess(GraphNode node, FieldAccessExpr value) {
        TypeDeclaration<?> decl = node.getEnclosingType();
        if (decl == null) {
            return null;
        }
        if (decl.isEnumDeclaration()) {
            for (EnumConstantDeclaration ecd : decl.asEnumDeclaration().getEntries()) {
                if (ecd.getNameAsString().equals(value.getNameAsString())) {
                    node.addEnumConstant(ecd);
                    break;
                }
            }
        } else {
            FieldDeclaration f = decl.getFieldByName(value.getNameAsString()).orElse(null);
            if (f != null) {
                node.addField(f);
                TypeWrapper wrapper = AbstractCompiler.findType(node.getCompilationUnit(), f.getElementType());

                if (wrapper != null && wrapper.getType() != null) {
                    return Graph.createGraphNode(wrapper.getType());
                }
            }
        }

        return null;
    }

    /**
     * Resolve a field access expression
     * If the expression has a <code>this.</code> prefix, then the field is resolved
     * within the
     * current class with help from the resolveThisField method
     *
     * @param node  represents a type
     * @param value a field access expression
     * @return a graph node representing the resolved field
     */
    public static GraphNode resolveField(GraphNode node, FieldAccessExpr value) {
        Expression scope = value.asFieldAccessExpr().getScope();
        if (scope.isThisExpr()) {
            return Resolver.resolveThisFieldAccess(node, value);
        }

        if (scope.isNameExpr()) {
            ImportWrapper imp2 = AbstractCompiler.findImport(node.getCompilationUnit(),
                    scope.asNameExpr().getNameAsString());
            if (imp2 != null) {
                node.getDestination().addImport(imp2.getImport());

                if (imp2.getType() != null) {
                    Graph.createGraphNode(imp2.getType());
                }
                if (imp2.getField() != null) {
                    Graph.createGraphNode(imp2.getField());
                } else {
                    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp2.getNameAsString());
                    if (cu != null) {
                        AbstractCompiler.getMatchingType(cu, scope.asNameExpr().getNameAsString())
                                .ifPresent(t -> createFieldNode(value, t));
                    }
                }
            }
        }
        return Resolver.resolveThisFieldAccess(node, value);
    }

    /**
     * Resolves expressions within an array initializer.
     * Iterates through the array initializer and delegates resolution
     * based on whether the value is an annotation or a field access.
     *
     * @param node  the graph node representing the current context
     * @param value the array initializer expression
     */
    static void resolveArrayExpr(GraphNode node, Expression value) {
        ArrayInitializerExpr aie = value.asArrayInitializerExpr();
        for (Expression expr : aie.getValues()) {
            if (expr.isAnnotationExpr()) {
                AnnotationExpr anne = expr.asAnnotationExpr();
                String fqName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(),
                        anne.getName().toString());
                if (fqName != null) {
                    node.getDestination().addImport(fqName);
                }
                if (anne.isNormalAnnotationExpr()) {
                    resolveNormalAnnotationExpr(node, anne.asNormalAnnotationExpr());
                }
            } else if (expr.isFieldAccessExpr()) {
                Resolver.resolveField(node, expr.asFieldAccessExpr());
            }
        }
    }

    /**
     * Resolves a normal annotation expression.
     * Checks for imports related to the annotation and resolves its member value
     * pairs
     * recursively processing field accesses, binary expressions, names, etc.
     *
     * @param node the graph node representing the current context
     * @param n    the normal annotation expression to resolve
     */
    static void resolveNormalAnnotationExpr(GraphNode node, NormalAnnotationExpr n) {
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), n.getNameAsString());
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
        }
        for (MemberValuePair pair : n.getPairs()) {
            Expression value = pair.getValue();
            if (value.isFieldAccessExpr()) {
                Resolver.resolveField(node, value.asFieldAccessExpr());
            } else if (value.isBinaryExpr()) {
                resolveBinaryExpr(node, value);
            } else if (value.isNameExpr()) {
                resolveNameExpression(node, value);
            } else if (value.isArrayInitializerExpr()) {
                Resolver.resolveArrayExpr(node, value);
            } else if (value.isClassExpr()) {
                ClassOrInterfaceType ct = value.asClassExpr().getType().asClassOrInterfaceType();
                ImportUtils.addImport(node, ct.getName().toString());
            }
        }
    }

    /**
     * Resolves a binary expression by processing both the left and right sides.
     *
     * @param node  the graph node representing the current context
     * @param value the binary expression to resolve
     */
    static void resolveBinaryExpr(GraphNode node, Expression value) {
        Expression left = value.asBinaryExpr().getLeft();
        Expression right = value.asBinaryExpr().getRight();

        resolveBinaryExpressionSide(node, left);
        resolveBinaryExpressionSide(node, right);
    }

    /**
     * Helper method to resolve one side of a binary expression.
     *
     * @param node the graph node representing the current context
     * @param expr the expression side to resolve
     */
    private static void resolveBinaryExpressionSide(GraphNode node, Expression expr) {
        processExpression(node, expr, new NodeList<>());
        if (expr.isFieldAccessExpr()) {
            Resolver.resolveField(node, expr.asFieldAccessExpr());
        } else if (expr.isNameExpr()) {
            resolveNameExpression(node, expr);
        } else if (expr.isBinaryExpr()) {
            resolveBinaryExpr(node, expr);
        }
    }

    /**
     * Resolves a name expression by delegating to the main resolveNameExpr method.
     *
     * @param node  the graph node representing the current context
     * @param value the expression containing the name to resolve
     */
    private static void resolveNameExpression(GraphNode node, Expression value) {
        resolveNameExpr(node, value.asNameExpr(), new NodeList<>());
    }

    /**
     * Resolves a scoped name expression, such as a static field access or class
     * reference.
     * Tries to find the type from the provided map of names or by looking up local
     * imports.
     *
     * @param scope the scope expression (e.g., the class name in `ClassName.field`)
     * @param fae   the field access expression or simple name being accessed
     * @param node  the graph node representing the current context
     * @param names a map of known names to types for resolution
     * @return an Optional containing the resolved Type if found, otherwise empty
     * @throws AntikytheraException if resolution fails in a critical way
     */
    public static Optional<Type> resolveScopedNameExpression(Expression scope, NodeWithSimpleName<?> fae,
            GraphNode node, final Map<String, Type> names) throws AntikytheraException {

        Optional<ScopedResolution> resolution = SymbolResolver.resolveScopedName(scope, node.getCompilationUnit(),
                names);

        if (resolution.isPresent()) {
            ScopedResolution res = resolution.get();
            if (res.hasType()) {
                return Optional.of(res.type());
            }

            if (res.hasImportWrapper()) {
                ImportWrapper imp = res.importWrapper();
                node.getDestination().addImport(imp.getImport());

                if (imp.isExternal()) {
                    return SymbolResolver.getExternalType(fae, imp);
                }

                if (res.hasResolvedTypeDecl()) {
                    createFieldNode(fae, res.resolvedTypeDecl());
                } else if (imp.getField() == null && !imp.getImport().isAsterisk()) {
                    // Fallback for when type decl is not in resolution but it's an internal import
                    // (handled in original via logic)
                    // The original logic for non-asterisk was: find CU, get public type.
                    // SymbolResolver does this. If resolvedTypeDecl is null here, it means CU was
                    // null or no public type.
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to resolve a type from an external library using reflection.
     *
     * @param fae the node containing the simple name of the field to resolve
     * @param imp the import wrapper containing information about the external
     *            import
     * @return an Optional containing the resolved ClassOrInterfaceType if
     *         successful, otherwise empty
     */

    static Optional<Type> getExternalType(NodeWithSimpleName<?> fae, ImportWrapper imp) {
        return SymbolResolver.getExternalType(fae, imp);
    }

    /**
     * Creates a graph node for a field found within a TypeDeclaration.
     *
     * @param fae the node containing the name of the field
     * @param td  the TypeDeclaration where the field is expected to exist
     */
    static void createFieldNode(NodeWithSimpleName<?> fae, TypeDeclaration<?> td) {
        if (td != null) {
            td.getFieldByName(fae.getNameAsString()).ifPresent(Graph::createGraphNode);
        }
    }

    /**
     * Resolves a field access expression and populates a list of resolved types.
     * Handles different scopes like NameExpr or another FieldAccessExpr.
     *
     * @param node  the graph node representing the current context
     * @param expr  the expression to resolve (expected to be a FieldAccessExpr)
     * @param types a list to collect resolved types
     * @return a GraphNode representing the resolved field or null if not resolved
     */
    public static GraphNode resolveFieldAccess(GraphNode node, Expression expr, NodeList<Type> types) {
        final FieldAccessExpr fae = expr.asFieldAccessExpr();
        Expression scope = fae.getScope();

        if (scope.isNameExpr()) {
            return handleNameExprScope(node, fae, scope, types);
        } else if (scope.isFieldAccessExpr()) {
            return handleFieldAccessExprScope(node, fae, scope, types);
        } else {
            ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), fae.getNameAsString());
            if (imp != null) {
                node.getDestination().addImport(imp.getImport());
                if (imp.isExternal()) {
                    SymbolResolver.getExternalType(fae, imp).ifPresent(types::add);
                }
            }
            return null;
        }
    }

    /**
     * Handles field access resolution when the scope is a NameExpr.
     *
     * @param node  the graph node representing the current context
     * @param fae   the field access expression
     * @param scope the scope expression (as a NameExpr)
     * @param types a list to collect resolved types
     * @return the resolved GraphNode or null
     */
    private static GraphNode handleNameExprScope(GraphNode node, FieldAccessExpr fae, Expression scope,
            NodeList<Type> types) {
        Optional<Type> resolvedType = resolveScopedNameExpression(scope, fae, node, DepSolver.getNames());
        if (resolvedType.isPresent()) {
            Type t = resolvedType.get();
            ImportWrapper wrapper = AbstractCompiler.findImport(node.getCompilationUnit(), t.asString());
            if (wrapper != null && wrapper.getType() != null) {
                SimpleName name = fae.getName();
                Optional<FieldDeclaration> field = wrapper.getType().findFirst(FieldDeclaration.class,
                        f -> f.getVariable(0).getNameAsString().equals(name.asString()));
                if (field.isPresent()) {
                    return Graph.createGraphNode(field.get());
                }
            }
            types.add(t);
        }
        return null;
    }

    /**
     * Handles field access resolution when the scope is itself a FieldAccessExpr
     * (chained access).
     *
     * @param node  the graph node representing the current context
     * @param fae   the field access expression
     * @param scope the scope expression (as a FieldAccessExpr)
     * @param types a list to collect resolved types
     * @return the resolved GraphNode or null
     */
    private static GraphNode handleFieldAccessExprScope(GraphNode node, FieldAccessExpr fae, Expression scope,
            NodeList<Type> types) {
        GraphNode scopeNode = resolveFieldAccess(node, scope, types);
        if (scopeNode != null) {
            FieldDeclaration scopeField = ((FieldDeclaration) scopeNode.getNode()).asFieldDeclaration();
            ClassOrInterfaceType resolvedType = scopeField.getElementType().asClassOrInterfaceType();
            if (resolvedType != null) {
                String fqn = AbstractCompiler.findFullyQualifiedName(scopeNode.getCompilationUnit(),
                        resolvedType.getName().asString());
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqn);
                if (cu != null) {
                    Optional<TypeDeclaration<?>> resolvedClass = AbstractCompiler.getMatchingType(cu,
                            resolvedType.getName().asString());
                    if (resolvedClass.isPresent()) {
                        Optional<FieldDeclaration> field = resolvedClass.get().getFieldByName(fae.getNameAsString());
                        if (field.isPresent()) {
                            return Graph.createGraphNode(field.get());
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves a chained method call or object creation within a method call
     * wrapper.
     * Evaluates the scope chain to find the target of the call or creation.
     *
     * @param node       the graph node representing the current context
     * @param mceWrapper the wrapper containing the method call or object creation
     * @return a GraphNode representing the result of the call or null
     * @throws AntikytheraException if resolution errors occur
     */
    static GraphNode chainedMethodCall(GraphNode node, MCEWrapper mceWrapper) throws AntikytheraException {
        if (mceWrapper.getMethodCallExpr() instanceof MethodCallExpr mce) {
            ScopeChain chain = ScopeChain.findScopeChain(mce);

            if (chain.isEmpty()) {
                copyMethod(mceWrapper, node);
                return node;
            } else {
                GraphNode gn = evaluateScopeChain(node, chain);
                if (gn != null) {
                    GraphNode result = copyMethod(mceWrapper, gn);
                    return createNodeForReturnType(node, result, gn);
                }
            }
        } else if (mceWrapper.getMethodCallExpr() instanceof ObjectCreationExpr oce) {
            return ImportUtils.addImport(node, oce.getType());
        }
        return null;
    }

    /**
     * Helper to determine which node to return based on the method's return type.
     * If the method returns a non-void type, imports it and returns that.
     *
     * @param node   the original context node
     * @param result the result of the method copy/resolution
     * @param gn     the graph node from the evaluated scope
     * @return the appropriate GraphNode
     */
    private static GraphNode createNodeForReturnType(GraphNode node, GraphNode result, GraphNode gn) {
        if (result != null && result.getNode() instanceof MethodDeclaration md) {
            Type t = md.getType();
            if (!t.isVoidType()) {
                return ImportUtils.addImport(node, t);
            }
        }
        return gn;
    }

    /**
     * Evaluates a chain of scopes (e.g., `obj.method().field`) to resolve the final
     * target.
     * Iterates through the chain and resolves each part sequentially.
     *
     * @param node  the starting graph node context
     * @param chain the chain of scopes to evaluate
     * @return the resolved GraphNode at the end of the chain or null
     * @throws AntikytheraException if resolution fails
     */
    static GraphNode evaluateScopeChain(GraphNode node, ScopeChain chain) throws AntikytheraException {
        GraphNode gn = node;
        Iterator<Scope> iterator = chain.getChain().reversed().iterator();
        while (iterator.hasNext() && gn != null) {
            Expression expr = iterator.next().getExpression();
            if (expr.isFieldAccessExpr()) {
                gn = evaluateFIeldAccess(expr, gn);
            } else if (expr.isMethodCallExpr()) {
                gn = copyMethod(resolveArgumentTypes(gn, expr.asMethodCallExpr()), gn);
            } else if (expr.isNameExpr()) {
                gn = evaluateNameExpr(expr.asNameExpr(), gn);
            } else if (expr.isObjectCreationExpr()) {
                ObjectCreationExpr oce = expr.asObjectCreationExpr();
                gn = ImportUtils.addImport(gn, oce.getType());
            } else if (expr.isArrayAccessExpr()) {
                ArrayAccessExpr aae = expr.asArrayAccessExpr();
                if (aae.getName().isNameExpr()) {
                    gn = evaluateNameExpr(aae.getName().asNameExpr(), gn);
                }
            } else if (expr.isClassExpr()) {
                ClassExpr ce = expr.asClassExpr();
                gn = ImportUtils.addImport(gn, ce.getType());
            }
        }

        return gn;
    }

    /**
     * Evaluates a field access expression within a scope chain.
     *
     * @param expr the field access expression
     * @param gn   the current graph node context from the chain
     * @return the resolved graph node for the field access, or the original node if
     *         not resolved/relevant
     */
    private static GraphNode evaluateFIeldAccess(Expression expr, GraphNode gn) {
        FieldAccessExpr fieldAccessExpr = expr.asFieldAccessExpr();
        GraphNode tmp = Resolver.resolveField(gn, fieldAccessExpr);
        if (tmp != null || !gn.getEnclosingType().isEnumDeclaration()) {
            gn = tmp;
        }
        return gn;
    }

    /**
     * Evaluates a name expression to find its corresponding type or field.
     * Checks if the name refers to a known type, a field in the current class, or
     * needs to be imported.
     *
     * @param nameExpr the name expression to evaluate
     * @param gn       the current graph node context
     * @return the resolved graph node or the original one if side effects (imports)
     *         were processed
     * @throws AntikytheraException if resolution fails
     */
    static GraphNode evaluateNameExpr(NameExpr nameExpr, GraphNode gn) throws AntikytheraException {
        TypeDeclaration<?> cdecl = gn.getEnclosingType();
        Type t = DepSolver.getNames().get(nameExpr.toString());
        if (t == null) {
            Optional<FieldDeclaration> fd = cdecl.getFieldByName(nameExpr.getNameAsString());

            if (fd.isPresent()) {
                return findFieldNode(gn, fd.get());
            }

            gn = ImportUtils.addImport(gn, nameExpr);
        } else {
            return gn.processTypeArgument(t);
        }
        return gn;
    }

    /**
     * Helper method to process a found field declaration.
     * Adds the field to the graph node and processes its type arguments and
     * annotations.
     *
     * @param gn the current graph node
     * @param fd the field declaration found
     * @return the processed graph node
     */
    private static GraphNode findFieldNode(GraphNode gn, FieldDeclaration fd) {
        Type field = fd.getElementType();
        gn.addField(fd);

        if (field != null) {
            for (AnnotationExpr ann : field.getAnnotations()) {
                ImportUtils.addImport(gn, ann.getNameAsString());
            }
            return gn.processTypeArgument(field.getElementType());
        }
        return gn;
    }

    /**
     * Given a method call expression or new object creation expression, resolve the
     * types of the arguments.
     *
     * @param node a graph node representing the current context
     * @param mce  method call expression or object creation expression
     * @return a Method Call Wrapper instance that contains the original method call
     *         as well as
     *         resolved argument types.
     *         If the arguments cannot be resolved correctly, the corresponding
     *         field
     *         in the MCEWrapper will be null.
     */
    public static MCEWrapper resolveArgumentTypes(GraphNode node, NodeWithArguments<?> mce) {
        MCEWrapper mw = new MCEWrapper(mce);
        NodeList<Type> types = new NodeList<>();

        NodeList<Expression> arguments = mce.getArguments();

        for (Expression arg : arguments) {
            processExpression(node, arg, types);
        }
        if (types.size() == arguments.size()) {
            mw.setArgumentTypes(types);
        }

        return mw;
    }

    /**
     * Processes a generic expression to resolve types or update the graph node.
     * Dispatches to specific resolution methods based on the expression type.
     *
     * @param node  the graph node representing the current context
     * @param expr  the expression to process
     * @param types a list to collect resolved types
     */
    static void processExpression(GraphNode node, Expression expr, NodeList<Type> types) {
        if (expr.isNameExpr()) {
            resolveNameExpr(node, expr.asNameExpr(), types);
        } else if (expr.isLiteralExpr()) {
            types.add(AbstractCompiler.convertLiteralToType(expr.asLiteralExpr()));
        } else if (expr.isFieldAccessExpr()) {
            Resolver.resolveFieldAccess(node, expr, types);
        } else if (expr.isMethodCallExpr()) {
            wrapCallable(node, expr.asMethodCallExpr(), types);
        } else if (expr.isObjectCreationExpr()) {
            wrapCallable(node, expr.asObjectCreationExpr(), types);
        } else if (expr.isMethodReferenceExpr()) {
            resolveMethodReference(node, expr);
        } else if (expr.isConditionalExpr()) {
            ConditionalExpr ce = expr.asConditionalExpr();
            if (ce.getThenExpr().isNameExpr()) {
                resolveNameExpr(node, ce.getThenExpr().asNameExpr(), types);
            }
            if (ce.getElseExpr().isNameExpr()) {
                resolveNameExpr(node, ce.getElseExpr().asNameExpr(), types);
            }
        } else if (expr.isArrayAccessExpr()) {
            resolveArrayAccessExpr(node, expr, types);
        } else if (expr.isClassExpr()) {
            ClassExpr ce = expr.asClassExpr();
            ImportUtils.addImport(node, ce.getType());
        }
    }

    /**
     * Resolves an array access expression.
     * If the array is accessed via a name, resolves the name and extracts the
     * component type.
     *
     * @param node  the graph node context
     * @param expr  the array access expression
     * @param types list to collect resolved types
     */
    static void resolveArrayAccessExpr(GraphNode node, Expression expr, NodeList<Type> types) {
        ArrayAccessExpr aae = expr.asArrayAccessExpr();
        if (aae.getName().isNameExpr()) {
            resolveNameExpr(node, aae.getName().asNameExpr(), types);
            types.getLast().ifPresent(t -> {
                if (t.isArrayType()) {
                    Type at = types.removeLast();
                    types.add(at.asArrayType().getComponentType());
                }
            });
        }
    }

    /**
     * Resolves a method reference expression (e.g., `Class::method`).
     * Handles static references, instance references, and constructor references.
     *
     * @param node the graph node context
     * @param arg  the expression (expected to be a MethodReferenceExpr)
     */
    static void resolveMethodReference(GraphNode node, Expression arg) {

        MethodReferenceExpr mre = arg.asMethodReferenceExpr();
        Expression scope = mre.getScope();
        if (scope.isNameExpr()) {
            ImportUtils.addImport(node, scope.asNameExpr());
        } else if (scope.isThisExpr()) {
            for (MethodDeclaration m : node.getEnclosingType().getMethodsByName(mre.getIdentifier())) {
                Graph.createGraphNode(m);
            }
        } else if (scope.isTypeExpr()) {
            // Resolve the type expression to its corresponding class
            String typeName = scope.asTypeExpr().getType().asString();
            String fullQulifiedName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), typeName);
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullQulifiedName);
            if (cu != null) {
                TypeDeclaration<?> typeDecl = AbstractCompiler.getPublicType(cu);
                if (typeDecl != null) {
                    // Find the method in the resolved class
                    for (MethodDeclaration m : typeDecl.getMethodsByName(mre.getIdentifier())) {
                        Graph.createGraphNode(m);
                    }
                }
            }
            ImportUtils.addImport(node, typeName);
        }
    }

    /**
     * Wraps and resolves a callable expression (MethodCallExpr or
     * ObjectCreationExpr).
     * Resolves argument types and handles the method invocation chain.
     *
     * @param node           the graph node context
     * @param callExpression the callable expression
     * @param types          list to collect resolved types representing the return
     *                       type or result
     */
    static void wrapCallable(GraphNode node, NodeWithArguments<?> callExpression, NodeList<Type> types) {

        MCEWrapper wrap = resolveArgumentTypes(node, callExpression);
        GraphNode gn = Resolver.chainedMethodCall(node, wrap);
        if (gn == null) {
            return;
        }

        if (gn.getNode() instanceof MethodDeclaration md) {
            Type t = md.getType();
            ImportUtils.addImport(node, t);
            types.add(t);
        } else if (gn.getNode() instanceof ClassOrInterfaceDeclaration cid) {
            wrappCallableUsingClassDeclaration(node, callExpression, types, cid, wrap, gn);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * Helper to wrap a callable when a ClassOrInterfaceDeclaration is involved
     * (e.g. inner methods).
     * Tries to find the method declaration within the class or handles
     * Lombok-generated methods.
     *
     * @param node           the graph node context
     * @param callExpression the callable expression
     * @param types          list to collect resolved types
     * @param cid            the class or interface declaration context
     * @param wrap           the MCE wrapper with resolved arguments
     * @param gn             the graph node from the chain resolution
     */
    private static void wrappCallableUsingClassDeclaration(GraphNode node, NodeWithArguments<?> callExpression,
            NodeList<Type> types, ClassOrInterfaceDeclaration cid, MCEWrapper wrap, GraphNode gn) {
        Optional<Callable> omd = AbstractCompiler.findCallableDeclaration(wrap, cid);
        if (omd.isPresent()) {
            Callable cd = omd.get();
            if (cd.isCallableDeclaration()) {
                if (cd.isMethodDeclaration()) {
                    Type t = cd.asMethodDeclaration().getType();
                    types.add(t);
                    ImportUtils.addImport(node, t);
                }
                cd.getCallableDeclaration().findAncestor(ClassOrInterfaceDeclaration.class)
                        .ifPresent(c -> ImportUtils.addImport(node, c.getNameAsString()));
            }
            return;
        }
        if (callExpression instanceof MethodCallExpr methodCallExpr) {
            Type t = lombokSolver(methodCallExpr, cid, gn);
            if (t != null) {
                types.add(t);
            }
        }
    }

    /**
     * Attempts to resolve methods generated by Lombok annotations (Getter, Setter,
     * Data).
     * Infers the field type based on the accessor/mutator name.
     *
     * @param argMethodCall the method call expression (e.g., `getFoo()`)
     * @param cid           the class declaration to check for Lombok annotations
     *                      and fields
     * @param gn            the graph node context
     * @return the resolved Type of the field if successful, otherwise null
     */
    static Type lombokSolver(MethodCallExpr argMethodCall, ClassOrInterfaceDeclaration cid, GraphNode gn) {
        Optional<Type> type = SymbolResolver.resolveLombokFieldType(argMethodCall, cid);
        if (type.isPresent()) {
            ImportUtils.addImport(gn, type.get());
            return type.get();
        }
        return null;
    }

    /**
     * 'Copies' a method by resolving it to a graph node in the dependency graph.
     * Finds the matching method declaration, handles overrides, thrown exceptions,
     * and Lombok-generated methods.
     *
     * @param mceWrapper the wrapper containing the method call and resolved
     *                   arguments
     * @param node       the current graph node context
     * @return a GraphNode representing the resolved method or null
     */
    static GraphNode copyMethod(MCEWrapper mceWrapper, GraphNode node) {
        TypeDeclaration<?> cdecl = node.getEnclosingType();
        if (cdecl == null) {
            return null;
        }

        Optional<Callable> md = AbstractCompiler.findCallableDeclaration(
                mceWrapper, cdecl);

        if (md.isPresent() && md.get().isMethodDeclaration()) {
            return copyMethodDeclaration(mceWrapper, node,
                    md.get().asMethodDeclaration(), cdecl);
        } else if (mceWrapper.getMethodCallExpr() instanceof MethodCallExpr mce
                && cdecl instanceof ClassOrInterfaceDeclaration decl) {
            Type t = lombokSolver(mce, decl, node);
            if (t != null && t.isClassOrInterfaceType()) {
                return ImportUtils.addImport(node, t);
            }
            ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), mce.getNameAsString());
            if (imp != null) {
                node.getDestination().addImport(imp.getImport());
                if (imp.getMethodDeclaration() != null) {
                    return Graph.createGraphNode(imp.getMethodDeclaration());
                }
            }
        }
        return null;
    }

    private static GraphNode copyMethodDeclaration(MCEWrapper mceWrapper, GraphNode node, MethodDeclaration method,
            TypeDeclaration<?> cdecl) {
        for (Type ex : method.getThrownExceptions()) {
            ImportUtils.addImport(node, ex);
        }

        if (method.isAbstract()) {
            Optional<ClassOrInterfaceDeclaration> parent = method.findAncestor(ClassOrInterfaceDeclaration.class);

            if (parent.isPresent() && !parent.get().isInterface()) {
                AbstractCompiler.findMethodDeclaration(mceWrapper, cdecl, false)
                        .ifPresent(overRides -> Graph.createGraphNode(overRides.getCallableDeclaration()));
            }
        }
        return Graph.createGraphNode(method);
    }

    /**
     * Resolves the given name expression.
     * Operates via side effects.
     *
     * @param node           the node within which the expression is being resolved
     * @param nameExpression the name expression to resolve
     * @param types          the resolved type will be added to this list.
     */
    static void resolveNameExpr(GraphNode node, NameExpr nameExpression, NodeList<Type> types) {
        Type t = DepSolver.getNames().get(nameExpression.getNameAsString());
        if (t != null) {
            types.add(t);
        } else {
            Optional<FieldDeclaration> fd = node.getEnclosingType().getFieldByName(nameExpression.getNameAsString());
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
            } else {
                ImportUtils.addImport(node, nameExpression.getNameAsString());
            }
        }
    }
}
