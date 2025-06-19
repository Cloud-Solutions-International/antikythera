package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.expr.Expression;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class to read YAML mocks configuration and convert to a structured map.
 * All values in the resulting map are represented as Lists, even for single entries.
 */
public class MockConfigReader {

    private MockConfigReader() {}

    /**
     * Reads the specified YAML file and returns its content as a Map of parsed Java expressions.
     * All values are converted to Lists of parsed Expression objects.
     *
     * @param yamlPath The path to the YAML file
     * @return Map with class names as keys and list of parsed JavaParser expressions as values
     */
    public static Map<String, List<Expression>> readMockExpressions(String yamlPath) {
        Map<String, List<Expression>> result = new HashMap<>();
        Map<String, List<String>> stringMap = readMockConfig(yamlPath);
        JavaParser javaParser = new JavaParser();

        for (Map.Entry<String, List<String>> entry : stringMap.entrySet()) {
            String className = entry.getKey();
            List<String> expressionStrings = entry.getValue();
            List<Expression> expressions = new ArrayList<>();

            for (String expressionString : expressionStrings) {
                try {
                    ParseResult<Expression> parseResult = javaParser.parseExpression(expressionString);
                    if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                        expressions.add(parseResult.getResult().orElseThrow());
                    } else {
                        throw new AntikytheraException(
                                "Failed to parse expression: " + expressionString +
                                        " for class: " + className +
                                        " - " + parseResult.getProblems());
                    }
                } catch (Exception e) {
                    throw new AntikytheraException(
                            "Failed to parse expression: " + expressionString +
                                    " for class: " + className, e);
                }
            }

            result.put(className, expressions);
        }

        return result;
    }

    /**
     * Convenience method to read and parse expressions from the default mocks.yaml file
     *
     * @return Map with class names as keys and list of parsed expressions as values
     */
    public static Map<String, List<Expression>> readDefaultMockExpressions() {
        return readMockExpressions("mocks.yml");
    }

    /**
     * Reads the specified YAML file and returns its content as a Map.
     * All values are converted to Lists for consistency.
     *
     * @param yamlPath The path to the YAML file
     * @return Map with class names as keys and list of mock expressions as values
     */
    public static Map<String, List<String>> readMockConfig(String yamlPath) {
        Map<String, List<String>> result = new HashMap<>();

        try (InputStream inputStream = MockConfigReader.class.getClassLoader().getResourceAsStream(yamlPath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("YAML file not found: " + yamlPath);
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            Map<String, Object> loadedYaml = mapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {
            });

            for (Map.Entry<String, Object> entry : loadedYaml.entrySet()) {
                String className = entry.getKey();
                Object value = entry.getValue();

                List<String> mockValues = new ArrayList<>();

                if (value instanceof List<?> list) {
                    // Multiple values
                    for (Object item : list) {
                        mockValues.add(item.toString());
                    }
                } else {
                    // Single value
                    mockValues.add(value.toString());
                }

                result.put(className, mockValues);
            }
        } catch (IOException e) {
            throw new AntikytheraException("Failed to parse mock configuration: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * Convenience method to read the default mocks.yaml file
     *
     * @return Map with class names as keys and list of mock expressions as values
     */
    public static Map<String, List<String>> readDefaultMockConfig() {
        return readMockConfig("mocks.yml");
    }
}
