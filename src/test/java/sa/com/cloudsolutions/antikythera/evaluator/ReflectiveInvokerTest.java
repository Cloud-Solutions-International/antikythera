package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReflectiveInvoker}.
 */
class ReflectiveInvokerTest {

    // ── invoke (static method) ──

    @Test
    void invokeStaticMethod() throws Exception {
        Method method = String.class.getMethod("valueOf", int.class);
        Variable receiver = new Variable(null);
        Variable result = ReflectiveInvoker.invoke(method, new Object[]{42}, receiver);
        assertNotNull(result);
        assertEquals("42", result.getValue());
    }

    @Test
    void invokeInstanceMethod() throws Exception {
        Method method = String.class.getMethod("length");
        Variable receiver = new Variable("hello");
        Variable result = ReflectiveInvoker.invoke(method, new Object[0], receiver);
        assertNotNull(result);
        assertEquals(5, result.getValue());
    }

    @Test
    void invokeMethodSetsReturnTypeWhenClazzIsNull() throws Exception {
        Method method = String.class.getMethod("length");
        Variable receiver = new Variable("test");
        Variable result = ReflectiveInvoker.invoke(method, new Object[0], receiver);
        assertNotNull(result);
        assertNotNull(result.getClazz());
        // Method.invoke auto-boxes int to Integer
        assertEquals(Integer.class, result.getClazz());
    }

    @Test
    void invokeInstanceMethodReturnsCorrectValue() throws Exception {
        Method method = String.class.getMethod("toUpperCase");
        Variable receiver = new Variable("hello");
        Variable result = ReflectiveInvoker.invoke(method, new Object[0], receiver);
        assertEquals("HELLO", result.getValue());
    }

    @Test
    void invokeMethodWithArguments() throws Exception {
        Method method = String.class.getMethod("substring", int.class, int.class);
        Variable receiver = new Variable("hello world");
        Variable result = ReflectiveInvoker.invoke(method, new Object[]{0, 5}, receiver);
        assertEquals("hello", result.getValue());
    }

    // ── invokeWithFallbacks ──

    @Test
    void invokeWithFallbacksSuccessPath() throws Exception {
        Method method = String.class.getMethod("length");
        ReflectionArguments args = new ReflectionArguments("length", new Object[0], new Class<?>[0]);
        args.setMethod(method);
        args.finalizeArguments();

        Variable receiver = new Variable("test");
        receiver.setClazz(String.class);

        Variable result = ReflectiveInvoker.invokeWithFallbacks(receiver, args, (r, a) -> null);
        assertNotNull(result);
        assertEquals(4, result.getValue());
    }

    // ── handleArgumentMismatch ──

    @Test
    void invokeWithFallbacksHandlesArgumentMismatch() throws Exception {
        Method method = List.class.getMethod("get", int.class);
        List<String> list = new ArrayList<>();
        list.add("item");

        ReflectionArguments args = new ReflectionArguments("get", new Object[]{0}, new Class<?>[]{int.class});
        args.setMethod(method);
        args.finalizeArguments();

        Variable receiver = new Variable(list);
        receiver.setClazz(ArrayList.class);

        Variable result = ReflectiveInvoker.invokeWithFallbacks(receiver, args, (r, a) -> null);
        assertNotNull(result);
        assertEquals("item", result.getValue());
    }
}
