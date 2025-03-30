package sa.com.cloudsolutions.antikythera.evaluator;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import static org.mockito.Mockito.withSettings;

class MockReturnValueHandler implements Answer<Object> {
    @Override
    public Object answer(InvocationOnMock invocation) throws Throwable {
        Class<?> returnType = invocation.getMethod().getReturnType();
        String clsName = returnType.getName();
        if (AntikytheraRunTime.getCompilationUnit(clsName) != null) {
            return EvaluatorFactory.create(clsName, Evaluator.class);
        } else {
            Object obj = Reflect.getDefault(returnType);
            if (obj == null) {
                Class<?> cls = AbstractCompiler.loadClass(clsName);
                return Mockito.mock(cls, withSettings().defaultAnswer(new MockReturnValueHandler()).strictness(Strictness.LENIENT));
            }
            return obj;
        }
    }
}
