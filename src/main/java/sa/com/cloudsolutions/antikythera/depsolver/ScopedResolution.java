package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

/**
 * Represents the result of a scoped name resolution.
 * Holds information about what a name resolved to: a Type, an Import, or a
 * TypeDeclaration.
 */
public record ScopedResolution(Type type, ImportWrapper importWrapper, TypeDeclaration<?> resolvedTypeDecl) {

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
