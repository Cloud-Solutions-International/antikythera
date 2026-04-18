package sa.com.cloudsolutions.antikythera.evaluator;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestReflect {

    @ParameterizedTest()
    @ValueSource(strings = {"int","double","float","short","byte","char","boolean"})
    void testGetComponentClass(String s) throws ClassNotFoundException {
        Class<?> clazz = Reflect.getComponentClass(s);
        assertEquals(clazz.getName(), s);
    }

    @ParameterizedTest()
    @ValueSource(strings = {"Integer","Double","Float","Short","Byte","Character","Boolean"})
    void testGetComponentClass2(String s) throws ClassNotFoundException {
        Class<?> clazz = Reflect.getComponentClass(s);
        assertEquals(clazz.getName(), "java.lang." + s);
    }

    @ParameterizedTest
    @CsvSource({
        "boolean,java.lang.Boolean",
        "int,java.lang.Integer",
        "long,java.lang.Long",
        "float,java.lang.Float",
        "double,java.lang.Double",
        "char,java.lang.Character",
    })
    void testPrimitiveToWrapper(String primitive, String expected) {
        String result = Reflect.primitiveToWrapper(primitive).getName();
        assertEquals(expected, result);
    }

    @Test
    void coerceArgumentsForNumericParsing_replacesNonNumericLongArg() throws Exception {
        Method m = Long.class.getMethod("parseLong", String.class);
        Object[] in = {Reflect.ANTIKYTHERA};
        Object[] out = Reflect.coerceArgumentsForNumericParsing(m, in);
        assertEquals("1", out[0]);
        assertNotSame(in, out);
    }

    @Test
    void coerceArgumentsForNumericParsing_leavesParsableStrings() throws Exception {
        Method m = Long.class.getMethod("parseLong", String.class);
        Object[] args = {"42"};
        Object[] out = Reflect.coerceArgumentsForNumericParsing(m, args);
        assertSame(args, out);
        assertEquals("42", out[0]);
    }

    @Test
    void coerceArgumentsForNumericParsing_parseInt() throws Exception {
        Method m = Integer.class.getMethod("parseInt", String.class);
        Object[] out = Reflect.coerceArgumentsForNumericParsing(m, new Object[] {"x"});
        assertEquals("1", out[0]);
    }

    @Test
    void coerceArgumentsForNumericParsing_parseLongWithRadix() throws Exception {
        Method m = Long.class.getMethod("parseLong", String.class, int.class);
        Object[] out = Reflect.coerceArgumentsForNumericParsing(m, new Object[] {Reflect.ANTIKYTHERA, 10});
        assertEquals("1", out[0]);
        assertEquals(10, out[1]);
    }

}
