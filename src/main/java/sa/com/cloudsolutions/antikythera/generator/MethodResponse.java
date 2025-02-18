package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.type.Type;
import org.springframework.http.ResponseEntity;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.util.HashMap;
import java.util.Map;

public class MethodResponse {
    Type type;
    Variable response;
    Variable body;

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
}
