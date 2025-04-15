package sa.com.cloudsolutions.antikythera.evaluator;


import org.junit.jupiter.params.ParameterizedTest;

import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

}
