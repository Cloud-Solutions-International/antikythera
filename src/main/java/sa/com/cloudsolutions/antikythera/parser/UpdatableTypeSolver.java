package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver;

public class UpdatableTypeSolver extends CombinedTypeSolver {
    MemoryTypeSolver memoryTypeSolver;
    static UpdatableTypeSolver instance = null;

    public MemoryTypeSolver getMemoryTypeSolver() {
        return memoryTypeSolver;
    }

    public void setMemoryTypeSolver(MemoryTypeSolver memoryTypeSolver) {
        this.memoryTypeSolver = memoryTypeSolver;
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(String name) {
        if (memoryTypeSolver != null) {
            SymbolReference<ResolvedReferenceTypeDeclaration> ref = memoryTypeSolver.tryToSolveType(name);
            if (ref.isSolved()) {
                return ref;
            }
        }
        return super.tryToSolveType(name);
    }

    public static UpdatableTypeSolver createTypeSolver() {
        if (instance == null) {
            instance = new UpdatableTypeSolver();
        }
        return instance;
    }
}
