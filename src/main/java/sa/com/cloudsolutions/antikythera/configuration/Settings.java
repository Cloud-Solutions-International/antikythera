package sa.com.cloudsolutions.antikythera.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/**
 * Manages the configuration properties from generator.yml file or generator.cfg file.
 */
public class Settings {
    public static final String APPLICATION_HOST = "application.host";
    /**
     * HashMap to store the configurations.
     */
    protected static HashMap<String, Object> props;

    /**
     * Private constructor to prevent class being initialized.
     */
    private Settings() {}

    /**
     * Load the configuration from the default generator.yml file.
     * @throws IOException if the file could not be read.
     */
    public static void loadConfigMap() throws IOException {
        if (props == null) {
            props = new HashMap<>();
            File yamlFile = new File(Settings.class.getClassLoader().getResource("generator.yml").getFile());
            if (yamlFile.exists()) {
                loadYamlConfig(yamlFile);
            } else {
                throw new FileNotFoundException(yamlFile.getPath());
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
     * Using yaml gives us the advantage of being able to have nested properties and also properties
     * that can have multiple entries without getting into ugly comma separated values.
     * @param yamlFile the location of the file containing the configuration data
     * @throws IOException if the file could not be read
     */
    private static void loadYamlConfig(File yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(Map.class, new LinkedHashMapDeserializer()));

        Map<String, Object> yamlProps = mapper.readValue(yamlFile, new TypeReference<Map<String, Object>>() {});

        Map<String, Object> variables = (Map<String, Object>) yamlProps.getOrDefault("variables", new HashMap<>());
        props.put("variables", variables);

        if (variables != null) {
            variables.replaceAll((k, value) -> replaceEnvVariables((String) value));
        }

        replaceVariables(yamlProps, props);

        hostInfo(yamlProps);
    }

    private static void hostInfo(Map<String, Object> yamlProps) throws IOException {
        if(yamlProps.get(APPLICATION_HOST) != null || yamlProps.get("application.version") != null) {
            Path path = Paths.get("src", "test", "resources", "testdata", "qa").resolve("Url.properties");
            File urlFile = path.toFile();
            if(urlFile.exists()) {
                StringBuilder sb = new StringBuilder();
                Scanner sc = new Scanner(urlFile);
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().strip();
                    if(line.startsWith(APPLICATION_HOST)) {
                        sb.append(APPLICATION_HOST).append("=")
                          .append(yamlProps.get(APPLICATION_HOST) == null ? "" : yamlProps.get(APPLICATION_HOST))
                          .append("\n");
                    }
                    else if(line.startsWith("application.version")) {
                        sb.append("application.version=")
                                .append(yamlProps.get("application.version") == null ? "" : yamlProps.get(APPLICATION_HOST))
                                .append("\n");
                    }
                    else {
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
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null && !key.equals("variables")) {
                if (value instanceof Map) {
                    Map<String, Object> nestedMap = new HashMap<>();
                    replaceVariables((Map<String, Object>) value, nestedMap);
                    target.put(key, nestedMap);
                } else if (value instanceof List) {
                    List<String> result = new ArrayList<>();
                    for(String s : (List<String>) value) {
                        s = s.replace("${USERDIR}", userDir);
                        s = replaceYamlVariables(s);
                        result.add(s);
                    }
                    target.put(key, result);
                }
                else if (value instanceof String v) {
                    v = v.replace("${USERDIR}", userDir);
                    v = replaceYamlVariables(v);
                    target.put(key, replaceEnvVariables(v));
                }
                else {
                    target.put(key, value);
                }
            }
        }
    }

    /**
     * Replace variables in the given property.
     * @param value the replacement
     * @return the updated value
     */
    private static String replaceYamlVariables(String value) {
        Map<String, Object> variablesMap = (Map<String, Object>) props.get("variables");
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
     * The cls parameter is used to cast the result to the given class so that the callers
     * need not clutter their call with casts
     *
     * @param key the key to search for
     * @param cls try to map the result to this class
     * @return an optional with the result if it's found
     */
    public static <T> Optional<T> getProperty(String key, Class<T> cls) {
        Object property = getProperty(key);
        if(property != null) {
            return Optional.of(cls.cast(property));
        }

        return Optional.empty();
    }

    public static Object getProperty(String key) {

        Object property = props.get(key);
        if(property != null) {
            return property;
        }
        String[] parts = key.split("\\.");
        if(parts.length > 1) {
            Object result = props.get(parts[0]);
            if (result instanceof Map<?,?> map) {
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
        return (String) props.get(Constants.BASE_PACKAGE);
    }


    /**
     * the top level folder for the AUT source code.
     * If there is a java class without a package it should be in this folder.
     */
    public static String getBasePath() {
        return (String) props.get(Constants.BASE_PATH);
    }

    public static String[] getArtifacts() {
        return getDependencies("artifact_ids");
    }

    private static String[] getDependencies(String artifactIds) {
        Object deps = props.getOrDefault(Constants.DEPENDENCIES, new HashMap<>());
        if (deps instanceof String s) {
            return s.split(",");
        }
        Map<String, Object> dependencies = (Map<String, Object>) deps;
        if (dependencies == null) {
            return new String[]{ };
        }

        Object artifacts = dependencies.get(artifactIds);
        if(artifacts == null) {
            return new String[] {};
        }
        return ((List<String>) artifacts).toArray(new String[0]);
    }

    public static String[] getJarFiles() {
        return getDependencies("jar_files");
    }

    public static class LinkedHashMapDeserializer extends JsonDeserializer<Map<String, Object>> {
        @Override
        public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException {
            return p.readValueAs(LinkedHashMap.class);
        }
    }
}
