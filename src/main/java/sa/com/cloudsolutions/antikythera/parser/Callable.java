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
        if(isCallableDeclaration()) {
            return callableDeclaration.asMethodDeclaration();
        }
        return null;
    }
}
