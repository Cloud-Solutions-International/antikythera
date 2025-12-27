package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * Query utilities for dependency graphs.
 * 
 * <p>
 * Provides convenience methods to query dependency information
 * after analysis has been performed using {@link DependencyAnalyzer}.
 * </p>
 */
public class DependencyQuery {

    private DependencyQuery() {
        // Utility class
    }

    /**
     * Get all methods that reference a specific type.
     * 
     * @param typeFqn Fully qualified name of the type
     * @param nodes   Set of graph nodes to search
     * @return Set of methods that use the specified type
     */
    public static Set<MethodDeclaration> getMethodsReferencingType(
            String typeFqn,
            Set<GraphNode> nodes) {
        Set<MethodDeclaration> result = new HashSet<>();

        for (GraphNode node : nodes) {
            if (node.getNode() instanceof MethodDeclaration md && referencesType(md, typeFqn)) {
                result.add(md);
            }
        }

        return result;
    }

    /**
     * Get all fields used by a method.
     * 
     * @param method Method to analyze
     * @param nodes  Set of graph nodes to search
     * @return Set of fields used by the method
     */
    public static Set<FieldDeclaration> getFieldsUsedBy(
            MethodDeclaration method,
            Set<GraphNode> nodes) {
        Set<FieldDeclaration> result = new HashSet<>();

        // Find the GraphNode for this method
        GraphNode methodNode = nodes.stream()
                .filter(n -> n.getNode().equals(method))
                .findFirst()
                .orElse(null);

        if (methodNode == null || methodNode.getEnclosingType() == null) {
            return result;
        }

        // Extract field references from method body
        method.findAll(com.github.javaparser.ast.expr.NameExpr.class).forEach(ne -> {
            String name = ne.getNameAsString();
            methodNode.getEnclosingType()
                    .getFieldByName(name)
                    .ifPresent(result::add);
        });

        return result;
    }

    /**
     * Get all types that a method depends on (from parameters and return type).
     * 
     * @param method Method to analyze
     * @return Set of type names that the method depends on
     */
    public static Set<String> getTypesDependendOn(MethodDeclaration method) {
        Set<String> result = new HashSet<>();

        // Parameter types
        method.getParameters().forEach(param -> {
            String typeName = extractTypeName(param.getType());
            if (typeName != null && !typeName.isEmpty()) {
                result.add(typeName);
            }
        });

        // Return type
        if (!method.getTypeAsString().equals("void")) {
            String typeName = extractTypeName(method.getType());
            if (typeName != null && !typeName.isEmpty()) {
                result.add(typeName);
            }
        }

        return result;
    }

    /**
     * Get all methods in a specific class from the discovered nodes.
     * 
     * @param classFqn Fully qualified class name
     * @param nodes    Set of graph nodes to search
     * @return Set of methods in the class
     */
    public static Set<MethodDeclaration> getMethodsInClass(
            String classFqn,
            Set<GraphNode> nodes) {
        Set<MethodDeclaration> result = new HashSet<>();

        for (GraphNode node : nodes) {
            TypeDeclaration<?> type = node.getEnclosingType();
            if (type != null && type.getFullyQualifiedName().isPresent()) {
                if (type.getFullyQualifiedName().get().equals(classFqn) &&
                    node.getNode() instanceof MethodDeclaration md) {
                        result.add(md);
                }
            }
        }

        return result;
    }

    /**
     * Get all field nodes from the discovered dependencies.
     * 
     * @param nodes Set of graph nodes to search
     * @return Set of field declarations
     */
    public static Set<FieldDeclaration> getFields(Set<GraphNode> nodes) {
        Set<FieldDeclaration> result = new HashSet<>();

        for (GraphNode node : nodes) {
            if (node.getNode() instanceof FieldDeclaration fd) {
                result.add(fd);
            }
        }

        return result;
    }

    /**
     * Get all method nodes from the discovered dependencies.
     * 
     * @param nodes Set of graph nodes to search
     * @return Set of method declarations
     */
    public static Set<MethodDeclaration> getMethods(Set<GraphNode> nodes) {
        Set<MethodDeclaration> result = new HashSet<>();

        for (GraphNode node : nodes) {
            if (node.getNode() instanceof MethodDeclaration md) {
                result.add(md);
            }
        }

        return result;
    }

    /**
     * Get all fields used by a collection of methods (batch operation).
     * More efficient than calling getFieldsUsedBy() in a loop.
     * 
     * @param methods Collection of methods to analyze
     * @param nodes   Set of graph nodes to search
     * @return Set of fields used by any of the methods
     */
    public static Set<FieldDeclaration> getFieldsUsedByMethods(
            Set<MethodDeclaration> methods,
            Set<GraphNode> nodes) {
        Set<FieldDeclaration> result = new HashSet<>();
        
        for (MethodDeclaration method : methods) {
            result.addAll(getFieldsUsedBy(method, nodes));
        }
        
        return result;
    }


    /**
     * Check if a method references a specific type.
     */
    private static boolean referencesType(MethodDeclaration method, String typeFqn) {
        // Check parameters
        for (var param : method.getParameters()) {
            String paramTypeName = extractTypeName(param.getType());
            if (typeFqn.equals(paramTypeName) || typeFqn.endsWith("." + paramTypeName)) {
                return true;
            }
        }

        // Check return type
        String returnTypeName = extractTypeName(method.getType());
        return typeFqn.equals(returnTypeName) || typeFqn.endsWith("." + returnTypeName);
    }

    /**
     * Extract type name from a Type.
     */
    private static String extractTypeName(Type type) {
        if (type.isClassOrInterfaceType()) {
            return type.asClassOrInterfaceType().getNameAsString();
        } else if (type.isPrimitiveType()) {
            return type.asPrimitiveType().asString();
        } else if (type.isArrayType()) {
            return extractTypeName(type.asArrayType().getComponentType());
        }
        return type.asString();
    }
}
