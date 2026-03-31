package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.Type;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.evaluator.ExceptionContext;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.HashMap;
import java.util.Map;

public class MethodResponse {
    Type type;
    Variable response;
    Variable body;
    String capturedOutput;

    private static Map<String, Integer> statusCodes = new HashMap<>();
    static {
        statusCodes.put("OK", 200);
        statusCodes.put("CREATED", 201);
        statusCodes.put("ACCEPTED", 202);
        statusCodes.put("NO_CONTENT", 204);
        statusCodes.put("BAD_REQUEST", 400);
        statusCodes.put("UNAUTHORIZED", 401);
        statusCodes.put("FORBIDDEN", 403);
        statusCodes.put("NOT_FOUND", 404);
        statusCodes.put("NOT_ACCEPTABLE", 406);
        statusCodes.put("CONFLICT", 409);
        statusCodes.put("INTERNAL_SERVER_ERROR", 500);
    }

    private ExceptionContext exceptionContext;
    private AssertionConfidence returnAssertionConfidence = AssertionConfidence.HIGH;

    public MethodResponse() {

    }

    public MethodResponse(Variable v) {
        this.response = v;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public Object getResponse() {
        return response;
    }

    public void setResponse(Variable response) {
        this.response = response;
    }

    public int getStatusCode() {
        if (response != null && response.getValue() instanceof ResponseEntity<?> re) {
            return re.getStatusCodeValue();
        }
        return 0;
    }

    public Variable getBody() {
        return body;
    }

    public void setBody(Variable body) {
        this.body = body;
    }

    public void setException(EvaluatorException eex) {
        if (eex == null) {
            this.exceptionContext = null;
        } else {
            ExceptionContext ctx = new ExceptionContext();
            ctx.setException(eex);
            this.exceptionContext = ctx;
        }
    }
    
    public void setExceptionContext(ExceptionContext ctx) {
        this.exceptionContext = ctx;
    }

    public EvaluatorException getException() {
        if (exceptionContext != null && exceptionContext.getException() instanceof EvaluatorException ee) {
            return ee;
        }
        return null;
    }
    
    public ExceptionContext getExceptionContext() {
        return exceptionContext;
    }

    public String getCapturedOutput() {
        return capturedOutput;
    }

    public void setCapturedOutput(String capturedOutput) {
        this.capturedOutput = capturedOutput;
    }

    public AssertionConfidence getReturnAssertionConfidence() {
        return returnAssertionConfidence;
    }

    public void inferReturnAssertionConfidence(CompilationUnit cu, Type type) {
        this.returnAssertionConfidence = confidenceForDeclaredReturnType(cu, type);
    }

    public static AssertionConfidence confidenceForDeclaredReturnType(CompilationUnit cu, Type type) {
        if (type.isVoidType()) {
            return AssertionConfidence.HIGH;
        }
        if (type.isClassOrInterfaceType()) {
            String typeName = type.asClassOrInterfaceType().getNameAsString();
            String fqn = AbstractCompiler.findFullyQualifiedName(cu, typeName);
            if (fqn != null) {
                try {
                    Class<?> clazz = Class.forName(fqn);
                    return clazz.isInterface() ? AssertionConfidence.LOW : AssertionConfidence.HIGH;
                } catch (ClassNotFoundException e) {
                    // Can't load the class; assume HIGH
                }
            }
        }
        return AssertionConfidence.HIGH;
    }
}
