package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.lang.reflect.Method;

/**
 * A wrapper that unifies CallableDeclaration from java parser with reflection
 */
public class Callable {
    CallableDeclaration<?> callableDeclaration;
    Method method;
    Class<?> foundInClass;

    public Callable(CallableDeclaration<?> callableDeclaration) {
        this.callableDeclaration = callableDeclaration;
    }

    public Callable(Method method) {
        this.method = method;
    }

    public Callable() {

    }

    public CallableDeclaration<?> getCallableDeclaration() {
        return callableDeclaration;
    }

    public void setCallableDeclaration(CallableDeclaration<?> callableDeclaration) {
        this.callableDeclaration = callableDeclaration;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public boolean isMethod() {
        return method != null;
    }

    public boolean isMethodDeclaration() {
        return callableDeclaration != null && callableDeclaration.isMethodDeclaration();
    }

    public boolean isCallableDeclaration() {
        return callableDeclaration != null;
    }

    public MethodDeclaration asMethodDeclaration() {
        if (isCallableDeclaration()) {
            return callableDeclaration.asMethodDeclaration();
        }
        return null;
    }

    public String getNameAsString() {
        if (isMethod()) {
            return method.getName();
        }
        if (callableDeclaration != null) {
            return callableDeclaration.getNameAsString();
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Callable other) {
            if (method != null) {
                return method.equals(other.method);
            }
            return callableDeclaration.equals(other.callableDeclaration);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (method != null) {
            return method.hashCode() + 141;
        }
        return callableDeclaration.hashCode() + 431;
    }

    @Override
    public String toString() {
        return getNameAsString();
   }

    public Class<?> getFoundInClass() {
        return foundInClass;
    }

    public void setFoundInClass(Class<?> foundInClass) {
        this.foundInClass = foundInClass;
    }
}
