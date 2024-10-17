package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.HashMap;
import java.util.Map;

public class ControllerResponse {
    Type type;
    Object response;
    int statusCode;
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

    public ControllerResponse() {

    }

    public ControllerResponse(Variable v) {
        this.response = v.getValue();
        this.type = v.getType();
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setResponse(Object response) {
        this.response = response;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public void setStatusCode(String code) {
        this.statusCode = statusCodes.get(code);
    }

    public Type getType() {
        return type;
    }

    public Object getResponse() {
        return response;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public static Map<String, Integer> getStatusCodes() {
        return statusCodes;
    }

    public static void setStatusCodes(Map<String, Integer> statusCodes) {
        ControllerResponse.statusCodes = statusCodes;
    }
}
