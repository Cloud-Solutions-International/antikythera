package sa.com.cloudsolutions.antikythera.evaluator;


import org.junit.jupiter.params.ParameterizedTest;

import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestReflect {

    @ParameterizedTest()
    @ValueSource(strings = {"int","double","float","short","byte","char","boolean"})
    void testGetComponentClass(String s) {
        Optional<Class<?>> clazz = Reflect.getComponentClass(s);
        assertTrue(clazz.isPresent());
        assertEquals(s, clazz.get().getName());
    }

    @ParameterizedTest()
    @ValueSource(strings = {"Integer","Double","Float","Short","Byte","Character","Boolean"})
    void testGetComponentClass2(String s)  {
        Optional<Class<?>> clazz = Reflect.getComponentClass(s);
        assertTrue(clazz.isPresent());
        assertEquals(clazz.get().getName(), "java.lang." + s);
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

}
