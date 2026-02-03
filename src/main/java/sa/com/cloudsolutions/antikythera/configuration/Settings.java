package sa.com.cloudsolutions.antikythera.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Manages the configuration properties from a generator.yml file.
 */
public class Settings {
    public static final String ORACLE_ID = "oracle";
    public static final String POSTGRESQL_ID = "postgresql";

    public static final String APPLICATION_HOST = "application.host";
    public static final String LOG_APPENDER = "log_appender";
    private static final String VARIABLES = "variables";
    public static final String EXTRA_IMPORTS = "extra_imports";
    /**
     * In the generated tests, mocking will be through this framework.
     */
    public static final String MOCK_WITH = "mock_with";
    /**
     * While evaluating expressions, any mocking will be done through this
     * framework.
     */
    public static final String MOCK_WITH_INTERNAL = "mock_with_internal";
    public static final String BASE_TEST_CLASS = "base_test_class";
    public static final String BASE_PACKAGE = "base_package";
    public static final String BASE_PATH = "base_path";
    public static final String OUTPUT_PATH = "output_path";
    public static final String CONTROLLERS = "controllers";
    public static final String DEPENDENCIES = "dependencies";
    public static final String SERVICES = "services";
    private static final String ARTIFACT_IDS = "artifact_ids";
    private static final String JAR_FILES = "jar_files";
    public static final String DATABASE = "database";
    public static final String SQL_QUERY_CONVERSION = "query_conversion";
    public static final String SKIP_VOID_NO_SIDE_EFFECTS = "skip_void_no_side_effects";
    /**
     * HashMap to store the configurations.
     */
    protected static HashMap<String, Object> props;

    /**
     * Private constructor to prevent the class being initialized.
     */
    private Settings() {
    }

    /**
     * Load the configuration from the default generator.yml file.
     * 
     * @throws IOException if the file could not be read.
     */
    public static void loadConfigMap() throws IOException {
        if (props == null) {
            props = new HashMap<>();
            try (java.io.InputStream inputStream = Settings.class.getClassLoader()
                    .getResourceAsStream("generator.yml")) {
                if (inputStream == null) {
                    throw new IOException("generator.yml not found in classpath");
                }
                loadYamlConfig(inputStream);
            }
        }
    }

    public static void loadConfigMap(File f) throws IOException {
        props = new HashMap<>();
        loadYamlConfig(f);
    }

    /**
     * Load configuration from a yaml file
     *
     * Using yaml gives us the advantage of being able to have nested properties and
     * also properties
     * that can have multiple entries without getting into ugly comma separated
     * values.
     * 
     * @param yamlFile the location of the file containing the configuration data
     * @throws IOException if the file could not be read
     */
    private static void loadYamlConfig(File yamlFile) throws IOException {
        loadYamlConfig(new ObjectMapper(new YAMLFactory()), yamlFile);
    }

    private static void loadYamlConfig(java.io.InputStream inputStream) throws IOException {
        loadYamlConfig(new ObjectMapper(new YAMLFactory()), inputStream);
    }

    private static void loadYamlConfig(ObjectMapper mapper, Object source) throws IOException {
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(Map.class, new LinkedHashMapDeserializer()));

        Map<String, Object> yamlProps;
        if (source instanceof File file) {
            yamlProps = mapper.readValue(file, new TypeReference<>() {});
        } else {
            yamlProps = mapper.readValue((java.io.InputStream) source, new TypeReference<>() {});
        }

        Map<String, Object> variables = (Map<String, Object>) yamlProps.getOrDefault(VARIABLES, new HashMap<>());
        props.put(VARIABLES, variables);

        if (variables != null) {
            variables.replaceAll((k, value) -> replaceEnvVariables((String) value));
        }

        replaceVariables(yamlProps, props);

        hostInfo(yamlProps);
    }

    private static void hostInfo(Map<String, Object> yamlProps) throws IOException {
        if (yamlProps.get(APPLICATION_HOST) != null || yamlProps.get("application.version") != null) {
            Path path = Paths.get("src", "test", "resources", "testdata", "qa").resolve("Url.properties");
            File urlFile = path.toFile();
            if (urlFile.exists()) {
                StringBuilder sb = new StringBuilder();
                Scanner sc = new Scanner(urlFile);
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().strip();
                    if (line.startsWith(APPLICATION_HOST)) {
                        sb.append(APPLICATION_HOST).append("=")
                                .append(yamlProps.get(APPLICATION_HOST) == null ? "" : yamlProps.get(APPLICATION_HOST))
                                .append("\n");
                    } else if (line.startsWith("application.version")) {
                        sb.append("application.version=")
                                .append(yamlProps.get("application.version") == null ? ""
                                        : yamlProps.get(APPLICATION_HOST))
                                .append("\n");
                    } else {
                        sb.append(line).append("\n");
                    }
                }
                sc.close();
                Files.write(path, sb.toString().getBytes());
            }
        }
    }

    /**
     * Replace variables from the yaml file with environment or internal variables
     *
     * @param source the source from whcih we will copy the data
     * @param target the destination where we will put the data
     */
    private static void replaceVariables(Map<String, Object> source, Map<String, Object> target) {
        String userDir = System.getProperty("user.home");

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            replaceVariable(target, entry, userDir);
        }
    }

    private static void replaceVariable(Map<String, Object> target, Map.Entry<String, Object> entry, String userDir) {
        String key = entry.getKey();
        Object value = entry.getValue();
        if (value != null && !key.equals(VARIABLES)) {
            if (value instanceof Map) {
                Map<String, Object> nestedMap = new HashMap<>();
                replaceVariables((Map<String, Object>) value, nestedMap);
                target.put(key, nestedMap);
            } else if (value instanceof List) {
                List<String> result = new ArrayList<>();
                for (String s : (List<String>) value) {
                    if (s != null) {
                        s = s.replace("${USERDIR}", userDir);
                        s = replaceYamlVariables(s);
                        result.add(s);
                    }
                }
                target.put(key, result);
            } else if (value instanceof String v) {
                v = v.replace("${USERDIR}", userDir);
                v = replaceYamlVariables(v);
                target.put(key, replaceEnvVariables(v));
            } else {
                target.put(key, value);
            }
        }
    }

    /**
     * Replace variables in the given property.
     * 
     * @param value the replacement
     * @return the updated value
     */
    private static String replaceYamlVariables(String value) {
        Map<String, Object> variablesMap = (Map<String, Object>) props.get(VARIABLES);
        for (Map.Entry<String, Object> variable : variablesMap.entrySet()) {
            String key = "${" + variable.getKey() + "}";
            String varValue = (String) variable.getValue();
            value = value.replace(key, varValue);
        }
        return value;
    }

    public static Map<String, String> loadCustomMethodNames(String className, String fieldName) {
        Map<String, String> methodNames = new HashMap<>();
        Map<String, Object> dtoConfig = (Map<String, Object>) Settings.getProperty("DTO");
        if (dtoConfig != null) {
            Map<String, Object> classConfig = (Map<String, Object>) dtoConfig.get(className);
            if (classConfig != null) {
                Map<String, String> fieldConfig = (Map<String, String>) classConfig.get(fieldName);
                if (fieldConfig != null) {
                    methodNames.put("getter", fieldConfig.get("getter"));
                    methodNames.put("setter", fieldConfig.get("setter"));
                }
            }
        }
        return methodNames;
    }

    /**
     * The value is checked for an environment variable and replaced if found.
     * The format is ${ENV_VAR_NAME}.
     *
     * @param value the configuration that needs to be searched for env variables
     * @return the value with the env variables replaced
     */
    private static String replaceEnvVariables(String value) {
        int startIndex;
        while ((startIndex = value.indexOf("${")) != -1) {
            int endIndex = value.indexOf("}", startIndex);
            if (endIndex == -1) {
                break;
            }
            String envVar = value.substring(startIndex + 2, endIndex);
            String envValue = System.getenv(envVar);
            if (envValue != null) {
                value = value.substring(0, startIndex) + envValue + value.substring(endIndex + 1);
            } else {
                value = value.substring(0, startIndex) + value.substring(endIndex + 1);
            }
        }
        return value;
    }

    /**
     * Get the property value for the given key.
     * The cls parameter is used to cast the result to the given class so that the
     * callers
     * need not clutter their call with casts
     *
     * @param key the key to search for
     * @param cls try to map the result to this class
     * @return an optional with the result if it's found
     */
    public static <T> Optional<T> getProperty(String key, Class<T> cls) {
        Object property = getProperty(key);
        if (property != null) {
            return Optional.of(cls.cast(property));
        }

        return Optional.empty();
    }

    public static <T> Collection<T> getPropertyList(String key, Class<T> cls) {
        Object property = getProperty(key);
        Collection<T> result = new ArrayList<>();

        if (property instanceof Collection<?> c) {
            for (Object o : c) {
                result.add(cls.cast(o));
            }
        } else if (property != null) {
            result.add(cls.cast(property));
        }
        return result;
    }

    public static Object getProperty(String key) {

        Object property = props.get(key);
        if (property != null) {
            return property;
        }
        String[] parts = key.split("\\.");
        if (parts.length > 1) {
            Object result = props.get(parts[0]);
            if (result instanceof Map<?, ?> map) {
                return map.get(parts[1]);
            }
        }
        return null;
    }

    /**
     * The base package for the AUT.
     * It helps to identify if a class we are looking at is something we should
     * try to compile or not.
     */
    public static String getBasePackage() {
        return (String) props.get(Settings.BASE_PACKAGE);
    }

    /**
     * the top level folder for the AUT source code.
     * If there is a java class without a package it should be in this folder.
     */
    public static String getBasePath() {
        return (String) props.get(Settings.BASE_PATH);
    }

    public static String getOutputPath() {
        return (String) props.get(Settings.OUTPUT_PATH);
    }

    public static String[] getArtifacts() {
        return getDependencies(ARTIFACT_IDS);
    }

    private static String[] getDependencies(String artifactIds) {
        Object deps = props.getOrDefault(Settings.DEPENDENCIES, new HashMap<>());
        if (deps instanceof String s) {
            return s.split(",");
        }
        Map<String, Object> dependencies = (Map<String, Object>) deps;
        if (dependencies == null) {
            return new String[] {};
        }

        Object artifacts = dependencies.get(artifactIds);
        if (artifacts == null) {
            return new String[] {};
        }
        return ((List<String>) artifacts).toArray(new String[0]);
    }

    public static String[] getJarFiles() {
        return getDependencies(JAR_FILES);
    }

    public static class LinkedHashMapDeserializer extends JsonDeserializer<Map<String, Object>> {
        @Override
        public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            return p.readValueAs(LinkedHashMap.class);
        }
    }

    /**
     * Use with caution - this will overwrite the existing value.
     * only intended for use in tests.
     * 
     * @param key   the key to replace or create
     * @param value the new value to assign.
     */
    public static void setProperty(String key, Object value) {
        props.put(key, value);
    }

}
