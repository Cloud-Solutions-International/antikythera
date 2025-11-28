package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ReflectTest {

    @Test
    void testFindMethod() {
        ReflectionArguments args = new ReflectionArguments("substring", new Object[] { 1 },
                new Class<?>[] { int.class });
        Method m = Reflect.findMethod(String.class, args);
        assertNotNull(m);
        assertEquals("substring", m.getName());
    }

    @Test
    void testFindConstructor() {
        Class<?>[] argTypes = new Class<?>[] { String.class };
        Object[] args = new Object[] { "test" };
        Constructor<?> c = Reflect.findConstructor(String.class, argTypes, args);
        assertNotNull(c);
    }

    @Test
    void testFindMethodCaching() {
        ReflectionArguments args = new ReflectionArguments("substring", new Object[] { 1 },
                new Class<?>[] { int.class });

        long start = System.nanoTime();
        Method m1 = Reflect.findMethod(String.class, args);
        long firstCall = System.nanoTime() - start;

        start = System.nanoTime();
        Method m2 = Reflect.findMethod(String.class, args);
        long secondCall = System.nanoTime() - start;

        assertNotNull(m1);
        assertSame(m1, m2);

        // Note: Performance assertion is tricky in unit tests due to warmup,
        // but we can at least assert correctness and object identity.
        // System.out.println("First call: " + firstCall + "ns, Second call: " +
        // secondCall + "ns");
    }

    @Test
    void testFindConstructorCaching() {
        Class<?>[] argTypes = new Class<?>[] { String.class };
        Object[] args = new Object[] { "test" };

        Constructor<?> c1 = Reflect.findConstructor(String.class, argTypes, args);
        Constructor<?> c2 = Reflect.findConstructor(String.class, argTypes, args);

        assertNotNull(c1);
        assertSame(c1, c2); // Should be same instance if cached (though Constructor instances might not be
                            // singletons, our cache should return the same one)
    }
}
