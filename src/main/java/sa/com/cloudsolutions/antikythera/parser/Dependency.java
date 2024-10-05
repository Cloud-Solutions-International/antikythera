package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.Type;

public class Dependency {
    TypeDeclaration<?> from;
    String to;
    boolean returnType;
    boolean parameter;
    boolean controller;
    boolean external;
    boolean extension;

    public Dependency(TypeDeclaration<?> from, String to, boolean returnType, boolean isParameter) {
        this.from = from;
        this.to = to;
        this.returnType = returnType;
        this.parameter = isParameter;
    }

    public Dependency(TypeDeclaration<?> from, String to) {
        this(from, to, false, false);
    }

    public TypeDeclaration<?> getFrom() {
        return from;
    }

    public void setFrom(TypeDeclaration<?> from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public boolean isReturnType() {
        return returnType;
    }

    public void setReturnType(boolean returnType) {
        this.returnType = returnType;
    }

    public boolean isParameter() {
        return parameter;
    }

    public void setParameter(boolean parameter) {
        this.parameter = parameter;
    }

    public boolean isController() {
        return controller;
    }

    public void setController(boolean controller) {
        this.controller = controller;
    }

    public boolean isExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public boolean isExtension() {
        return extension;
    }

    public void setExtension(boolean extension) {
        this.extension = extension;
    }

    @Override
    public int hashCode() {
        return from.getFullyQualifiedName().hashCode() + to.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Dependency other) {
            return from.getFullyQualifiedName().equals(other.from.getFullyQualifiedName()) && to.equals(other.to);
        }

        return false;
    }
}
