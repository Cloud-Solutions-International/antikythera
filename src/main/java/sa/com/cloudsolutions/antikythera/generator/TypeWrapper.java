package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;

public class TypeWrapper {
    CompilationUnit cu;
    Class<?> cls;

    public CompilationUnit getCu() {
        return cu;
    }

    public void setCu(CompilationUnit cu) {
        this.cu = cu;
    }

    public Class<?> getCls() {
        return cls;
    }

    public void setCls(Class<?> cls) {
        this.cls = cls;
    }
}
