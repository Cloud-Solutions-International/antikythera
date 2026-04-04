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

    /**
     * Sets the exception for this method response by wrapping it in an {@link ExceptionContext}.
     * <p>
     * This is a convenience method that automatically creates an ExceptionContext with the provided
     * exception. It delegates to {@link #setExceptionContext(ExceptionContext)}.
     *
     * @param eex the exception to wrap, or {@code null} to clear the exception context
     */
    public void setException(EvaluatorException eex) {
        if (eex == null) {
            this.exceptionContext = null;
        } else {
            ExceptionContext ctx = new ExceptionContext();
            ctx.setException(eex);
            this.exceptionContext = ctx;
        }
    }
    
    /**
     * Sets the exception context for this method response.
     * <p>
     * The exception context contains both the exception and additional metadata about where
     * and how the exception occurred during symbolic evaluation.
     *
     * @param ctx the exception context, or {@code null} to clear the exception
     */
    public void setExceptionContext(ExceptionContext ctx) {
        this.exceptionContext = ctx;
    }

    /**
     * Returns the exception from the exception context, if it is an {@link EvaluatorException}.
     * <p>
     * This is a convenience method that extracts the exception from the underlying
     * {@link ExceptionContext}. If no exception context exists, or if the exception is not
     * an EvaluatorException, this returns {@code null}.
     *
     * @return the EvaluatorException, or {@code null} if none exists or it's a different type
     */
    public EvaluatorException getException() {
        return (exceptionContext != null && exceptionContext.getException() instanceof EvaluatorException ee) 
            ? ee 
            : null;
    }
    
    /**
     * Returns the exception context containing the exception and related metadata.
     * <p>
     * The exception context provides additional information about where the exception occurred
     * in the symbolic evaluation process.
     *
     * @return the exception context, or {@code null} if no exception has been set
     */
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
