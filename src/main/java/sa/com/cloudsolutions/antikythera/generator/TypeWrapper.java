package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;

public class TypeWrapper {
    TypeDeclaration<?> type;
    Class<?> cls;

    public TypeWrapper(TypeDeclaration<?> type) {
        this.type = type;
    }

    public TypeWrapper(Class<?> cls) {
        this.cls = cls;
    }

    public TypeWrapper() {

    }

    public TypeDeclaration<?> getType() {
        return type;
    }

    public void setCu(TypeDeclaration<?> type) {
        this.type = type;
    }

    public Class<?> getCls() {
        return cls;
    }

    public void setCls(Class<?> cls) {
        this.cls = cls;
    }
}
