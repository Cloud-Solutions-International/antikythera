package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.depsolver.GraphNode;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import java.util.Optional;

public class ImportUtils {
    private ImportUtils() {}

    public static GraphNode addImport(GraphNode node, Expression expr) {
        if (expr.isNameExpr()) {
            return ImportUtils.addImport(node, expr.asNameExpr().getNameAsString());
        }
        else if (expr.isObjectCreationExpr()) {
            return ImportUtils.addImport(node, expr.asObjectCreationExpr().getType());
        }
        return null;
    }

    public static GraphNode addImport(GraphNode node, Type type) {
        CompilationUnit compilationUnit = node.getCompilationUnit();
        TypeWrapper wrapper = AbstractCompiler.findType(compilationUnit, type);
        if (wrapper != null) {
            String packageName = compilationUnit.getPackageDeclaration().isPresent()
                    ? compilationUnit.getPackageDeclaration().orElseThrow().getNameAsString() : "";

            if (wrapper.getType() != null) {
                GraphNode n = Graph.createGraphNode(wrapper.getType());
                if (!packageName.equals(
                        findPackage(wrapper.getType())) && !packageName.isEmpty()) {
                    // Check if typeDeclaration is null before accessing it
                    TypeDeclaration<?> typeDecl = n.getTypeDeclaration();
                    if (typeDecl != null) {
                        node.getDestination().addImport(typeDecl.getFullyQualifiedName().orElseThrow());
                    } else {
                        // Fallback: use the wrapper's fully qualified name or find it from the type
                        String fqn = wrapper.getFullyQualifiedName();
                        if (fqn == null) {
                            fqn = AbstractCompiler.findFullyQualifiedName(compilationUnit, wrapper.getType().getNameAsString());
                        }
                        if (fqn != null && !fqn.equals(packageName) && !packageName.isEmpty()) {
                            node.getDestination().addImport(fqn);
                        }
                    }
                }
                return n;
            }
            else {
                String importFrom = findPackage(wrapper.getClazz());
                if (!importFrom.equals(packageName)
                        && !importFrom.equals("java.lang")
                        && !packageName.isEmpty()) {
                    node.getDestination().addImport(wrapper.getClazz().getName());
                }
            }
        }

        return null;
    }

    public static GraphNode addImport(GraphNode node, String name) {
        GraphNode returnValue = null;
        ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), name);
        if (imp != null) {
            node.getDestination().addImport(imp.getImport());
            if (imp.getType() != null) {
                returnValue = Graph.createGraphNode(imp.getType());
            }
            if (imp.getField() != null) {
                Graph.createGraphNode(imp.getField());
            } else if (imp.getImport().isAsterisk() && !imp.isExternal()) {
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp.getImport().getNameAsString());
                if (cu != null) {
                    AbstractCompiler.getMatchingType(cu, name).ifPresent(Graph::createGraphNode);
                }
            }
        } else {
            String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), name);
            Optional<TypeDeclaration<?>> matching = AntikytheraRunTime.getTypeDeclaration(fullyQualifiedName);
            if (matching.isPresent()) {
                return (Graph.createGraphNode(matching.get()));
            }
        }
        return returnValue;
    }

    public static String findPackage(Class<?> clazz) {
        if (clazz.getPackage() != null) {
            return clazz.getPackage().getName();
        }
        return "";
    }

    public static String findPackage(TypeDeclaration<?> t) {
        return t.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(NodeWithName::getNameAsString)
                .orElse("");
    }
}
