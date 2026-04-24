package sa.com.cloudsolutions.antikythera.evaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Encapsulates reflective method invocation logic extracted from {@link Evaluator}.
 *
 * <p>All methods are static utilities that return a {@link Variable} result rather
 * than mutating shared state, making the invocation pipeline easier to follow and
 * test in isolation.</p>
 */
public final class ReflectiveInvoker {

    private static final Logger logger = LoggerFactory.getLogger(ReflectiveInvoker.class);

    private ReflectiveInvoker() {}

    /**
     * Invoke a method reflectively, handling accessibility and argument-type mismatches.
     *
     * @param method   the resolved method to invoke
     * @param args     the final argument array
     * @param receiver the variable whose value is the invocation target (may hold null for static calls)
     * @return the result wrapped in a {@link Variable}
     */
    static Variable invoke(Method method, Object[] args, Variable receiver)
            throws InvocationTargetException, IllegalAccessException {
        Object target = Modifier.isStatic(method.getModifiers()) ? null : receiver.getValue();
        Object[] coerced = Reflect.coerceArgumentsForNumericParsing(method, args);
        Variable result = new Variable(method.invoke(target, coerced));
        if (result.getClazz() == null) {
            result.setClazz(method.getReturnType());
        }
        return result;
    }

    /**
     * Attempt to invoke a method, falling back to accessible-method resolution or
     * stream handling when the initial call fails.
     *
     * @param receiver            the target variable
     * @param reflectionArguments prepared reflection arguments (method + finalized args)
     * @param streamHandler       callback for stream-method delegation
     * @return the invocation result
     */
    static Variable invokeWithFallbacks(Variable receiver, ReflectionArguments reflectionArguments,
                                        StreamMethodHandler streamHandler)
            throws ReflectiveOperationException {
        Method method = reflectionArguments.getMethod();
        Object[] finalArgs = reflectionArguments.getFinalArgs();
        try {
            return invoke(method, finalArgs, receiver);
        } catch (IllegalAccessException e) {
            return handleInaccessible(receiver, reflectionArguments, streamHandler);
        } catch (IllegalArgumentException e) {
            return handleArgumentMismatch(receiver, reflectionArguments, finalArgs, method, streamHandler);
        }
    }

    /**
     * Handle an {@link IllegalAccessException} by making the method accessible,
     * or routing through public interface methods / stream handling.
     */
    @SuppressWarnings("java:S3011")
    private static Variable handleInaccessible(Variable receiver, ReflectionArguments reflectionArguments,
                                               StreamMethodHandler streamHandler)
            throws ReflectiveOperationException {
        Method method = reflectionArguments.getMethod();
        Object[] finalArgs = reflectionArguments.getFinalArgs();
        try {
            method.setAccessible(true);
            return invoke(method, finalArgs, receiver);
        } catch (InaccessibleObjectException ioe) {
            if (receiver.getClazz() != null
                    && receiver.getClazz().getName().startsWith(Evaluator.JAVA_UTIL_STREAM)) {
                return streamHandler.handle(receiver, reflectionArguments);
            }
            Method publicMethod = Reflect.findPublicMethod(receiver.getClazz(),
                    reflectionArguments.getMethodName(),
                    reflectionArguments.getArgumentTypes());
            if (publicMethod != null) {
                return invoke(publicMethod, finalArgs, receiver);
            }
            return null;
        } catch (IllegalArgumentException e) {
            if (receiver.getClazz() != null
                    && receiver.getClazz().getName().startsWith(Evaluator.JAVA_UTIL_STREAM)) {
                return streamHandler.handle(receiver, reflectionArguments);
            }
            throw e;
        }
    }

    /**
     * Handle an {@link IllegalArgumentException} by re-resolving the method based on
     * runtime argument types.
     */
    private static Variable handleArgumentMismatch(Variable receiver, ReflectionArguments reflectionArguments,
                                                    Object[] finalArgs, Method method,
                                                    StreamMethodHandler streamHandler)
            throws IllegalAccessException, InvocationTargetException {
        Class<?>[] runtimeTypes = new Class<?>[finalArgs.length];
        for (int i = 0; i < finalArgs.length; i++) {
            runtimeTypes[i] = finalArgs[i] == null ? Object.class : finalArgs[i].getClass();
        }

        ReflectionArguments retryArgs = new ReflectionArguments(
                reflectionArguments.getMethodName(), finalArgs, runtimeTypes);
        retryArgs.setScope(reflectionArguments.getScope());
        Method retry = Reflect.findMethod(method.getDeclaringClass(), retryArgs);
        if (retry != null) {
            Object target = Modifier.isStatic(retry.getModifiers()) ? null : receiver.getValue();
            retryArgs.setMethod(retry);
            retryArgs.finalizeArguments();
            Object[] retryFinal = retryArgs.getFinalArgs();
            Variable result = new Variable(retry.invoke(target, retryFinal));
            if (result.getValue() == null && result.getClazz() == null) {
                result.setClazz(retry.getReturnType());
            }
            return result;
        }
        return null;
    }

    /**
     * Callback interface for delegating stream-method handling.
     */
    @FunctionalInterface
    interface StreamMethodHandler {
        Variable handle(Variable receiver, ReflectionArguments args)
                throws ReflectiveOperationException;
    }
}
