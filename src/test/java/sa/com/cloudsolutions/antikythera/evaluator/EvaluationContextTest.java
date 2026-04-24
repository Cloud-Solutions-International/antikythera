package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationContextTest {

    private EvaluationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new EvaluationContext();
    }

    @Test
    void testDefaultSingletonIsNotNull() {
        assertNotNull(EvaluationContext.getDefault());
    }

    @Test
    void testPushPop() {
        Variable v = new Variable("hello");
        ctx.push(v);
        assertSame(v, ctx.pop());
    }

    @Test
    void testResetStackClearsStack() {
        ctx.push(new Variable("a"));
        ctx.push(new Variable("b"));
        ctx.resetStack();
        assertThrows(java.util.NoSuchElementException.class, () -> ctx.pop());
    }

    @Test
    void testSubClasses() {
        ctx.addSubClass("Parent", "Child1");
        ctx.addSubClass("Parent", "Child2");
        assertEquals(2, ctx.findSubClasses("Parent").size());
        assertTrue(ctx.findSubClasses("Parent").contains("Child1"));
        assertTrue(ctx.findSubClasses("Parent").contains("Child2"));
        assertTrue(ctx.findSubClasses("Unknown").isEmpty());
    }

    @Test
    void testImplementations() {
        ctx.addImplementation("Iface", "Impl1");
        ctx.addImplementation("Iface", "Impl2");
        assertEquals(2, ctx.findImplementations("Iface").size());
        assertTrue(ctx.findImplementations("Unknown").isEmpty());
    }

    @Test
    void testAutoWire() {
        Variable v = new Variable("bean");
        ctx.autoWire("com.example.MyService", v);
        assertSame(v, ctx.getAutoWire("com.example.MyService"));
        assertNull(ctx.getAutoWire("com.example.Unknown"));
    }

    @Test
    void testResetAutowires() {
        ctx.autoWire("com.example.MyService", new Variable("bean"));
        ctx.resetAutowires();
        assertNull(ctx.getAutoWire("com.example.MyService"));
    }

    @Test
    void testStaticVariables() {
        Variable v = new Variable(42);
        ctx.setStaticVariable("com.Foo", "COUNT", v);
        assertSame(v, ctx.getStaticVariable("com.Foo", "COUNT"));
        assertNull(ctx.getStaticVariable("com.Foo", "MISSING"));
        assertNull(ctx.getStaticVariable("com.Bar", "COUNT"));
    }

    @Test
    void testResetStatics() {
        ctx.setStaticVariable("com.Foo", "COUNT", new Variable(1));
        ctx.resetStatics();
        assertNull(ctx.getStaticVariable("com.Foo", "COUNT"));
    }

    @Test
    void testResetAll() {
        ctx.push(new Variable("a"));
        ctx.addSubClass("P", "C");
        ctx.addImplementation("I", "Impl");
        ctx.autoWire("Svc", new Variable("b"));
        ctx.setStaticVariable("Cls", "f", new Variable(1));

        ctx.resetAll();

        assertThrows(java.util.NoSuchElementException.class, () -> ctx.pop());
        assertTrue(ctx.findSubClasses("P").isEmpty());
        assertTrue(ctx.findImplementations("I").isEmpty());
        assertNull(ctx.getAutoWire("Svc"));
        assertNull(ctx.getStaticVariable("Cls", "f"));
        assertTrue(ctx.getResolvedTypes().isEmpty());
        assertTrue(ctx.getCompilationUnits().isEmpty());
    }
}
