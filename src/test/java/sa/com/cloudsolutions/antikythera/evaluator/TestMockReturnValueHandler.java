package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TestMockReturnValueHandler {

    private MockReturnValueHandler handler;
    private InvocationOnMock invocation;

    @BeforeEach
    void setUp() {
        handler = spy(new MockReturnValueHandler());
        invocation = mock(InvocationOnMock.class);
        // Stub the whenThen method to do nothing since it's not being tested
        doNothing().when(handler).whenThen(any(), any(), anyString());
    }

    @Test
    void testAnswerWithPrimitiveTypes() throws Throwable {
        // Test with integer
        testPrimitiveType(TestService.class.getMethod("getInteger"), Integer.class);

        // Test with boolean
        testPrimitiveType(TestService.class.getMethod("getBoolean"), Boolean.class);

        // Test with string
        testPrimitiveType(TestService.class.getMethod("getString"), String.class);
    }

    private void testPrimitiveType(Method method, Class<?> type) throws Throwable {
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getArguments()).thenReturn(new Object[]{});
        when(invocation.getMock()).thenReturn(mock(TestService.class));

        Object result = handler.answer(invocation);

        assertNotNull(result);
        assertEquals(type, result.getClass());
    }

    @Test
    void testAnswerWithVoidReturnType() throws Throwable {
        Method method = TestService.class.getMethod("doVoid");
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getArguments()).thenReturn(new Object[]{});

        Object result = handler.answer(invocation);

        assertNull(result);
    }

    @Test
    void testAnswerWithCollectionTypes() throws Throwable {
        // Test with List
        Method listMethod = TestService.class.getMethod("getList");
        when(invocation.getMethod()).thenReturn(listMethod);
        when(invocation.getArguments()).thenReturn(new Object[]{});
        when(invocation.getMock()).thenReturn(mock(TestService.class));

        Object listResult = handler.answer(invocation);

        assertNotNull(listResult);
        assertInstanceOf(List.class, listResult);

        // Test with Map
        Method mapMethod = TestService.class.getMethod("getMap");
        when(invocation.getMethod()).thenReturn(mapMethod);

        Object mapResult = handler.answer(invocation);

        assertNotNull(mapResult);
        assertInstanceOf(Map.class, mapResult);
    }

    @Test
    void testAnswerWithGenericMethodAndClassParameter() throws Throwable {
        Method method = TestService.class.getMethod("convertValue", Object.class, Class.class);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getArguments()).thenReturn(new Object[]{"inputValue", ArrayList.class});
        when(invocation.getMock()).thenReturn(mock(TestService.class));

        Object result = handler.answer(invocation);

        // The exact result will depend on implementation, but we should at least verify it's not null
        assertNotNull(result);
    }

    @Test
    void testFindBetterReturnTypeWithClassArgument() throws Throwable {
        // This test will test the findBetterReturnType method with a Class argument
        Method method = TestService.class.getMethod("convertValue", Object.class, Class.class);
        when(invocation.getMethod()).thenReturn(method);

        // Setup with a Class argument
        when(invocation.getArguments()).thenReturn(new Object[]{"input", ArrayList.class});

        // Reset the spy to allow the real method to execute
        reset(handler);

        Object result = handler.answer(invocation);

        // The result should be an ArrayList since findBetterReturnType should find ArrayList.class
        assertInstanceOf(ArrayList.class, result);
    }

    @Test
    void testFindBetterReturnTypeWithParameterizedType() throws Throwable {
        // Create a mock ParameterizedType
        java.lang.reflect.ParameterizedType pType = mock(java.lang.reflect.ParameterizedType.class);
        when(pType.getRawType()).thenReturn(ArrayList.class);

        Method method = TestService.class.getMethod("convertValue", Object.class, Class.class);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getArguments()).thenReturn(new Object[]{"input", pType});

        // Reset the spy to allow the real method to execute
        reset(handler);

        Object result = handler.answer(invocation);

        // The result should be an ArrayList since findBetterReturnType should find ArrayList.class from the ParameterizedType
        assertInstanceOf(ArrayList.class, result);
    }

    // Test interfaces/classes used in the tests
    interface TestService {
        void doVoid();

        String getString();

        Integer getInteger();

        Boolean getBoolean();

        List<String> getList();

        Map<String, String> getMap();

        <T> T convertValue(Object fromValue, Class<T> toValueType);
    }

    static class ComplexObject {
    }
}
