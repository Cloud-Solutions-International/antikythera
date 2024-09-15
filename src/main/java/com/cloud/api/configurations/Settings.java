package com.cloud.api.configurations;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Manages the configuration properties from the generator.cfg file.
 */
public class Settings {
    /**
     * Hashmpa to store the configurations.
     */
    protected static HashMap<String, Object> props;

    /**
     * Private constructor to prevent class being initialized.
     */
    private Settings() {}

    public static void loadConfigMap() throws IOException {
        if (props == null) {
            props = new HashMap<>();
            File yamlFile = new File(Settings.class.getClassLoader().getResource("generator.yml").getFile());
            if (yamlFile.exists()) {
                loadYamlConfig(yamlFile);
            } else {
                loadCfgConfig();
            }
        }
    }

    /**
     * Load configuration from a yaml file
     *
     * Using yaml gives us the advantage of being able to have nested properties and also properties
     * tha can have multiple entries without getting into ugly comma seperated values.
     * @param yamlFile
     * @throws IOException
     */
    private static void loadYamlConfig(File yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(Map.class, new LinkedHashMapDeserializer()));


        Map<String, Object> yamlProps = mapper.readValue(yamlFile, Map.class);
        Map<String, Object> variables = (Map<String, Object>) yamlProps.getOrDefault("variables", new HashMap<>());
        props.put("variables", variables);

        replaceVariables(yamlProps);
        for(var entry : props.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue());
        }
    }

    /**
     * Replace variables from the yaml file with enviorenment or internal variables
     *
     * @param yamlProps
     */
    private static void replaceVariables(Map<String, Object> yamlProps) {
        String userDir = System.getProperty("user.home");

        for (Map.Entry<String, Object> entry : yamlProps.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null && !key.equals("variables")) {
                if (value instanceof String) {
                    String v = (String) value;
                    entry.setValue(v.replace("${USERDIR}", userDir));
                    v = replaceYamlVariables(entry);
                    props.put(key, replaceEnvVariables(v));
                }
                else if (value instanceof Map) {
                    replaceVariables((Map<String, Object>) value);
                }
                else {
                    props.put(key, value);
                }
            }
        }
    }

    /**
     * Replace variables in the given property.
     * @param entry
     * @return
     */
    private static String replaceYamlVariables(Map.Entry<String, Object> entry) {

        Map<String, Object> variablesMap = (Map<String, Object>) props.get("variables");
        for(Map.Entry<String, Object> variable : variablesMap.entrySet()) {
            String key = "${" + variable.getKey() + "}";
            String value = (String) variable.getValue();
            entry.setValue(((String) entry.getValue()).replace(key, value));
        }
        return entry.getValue().toString();
    }

    /**
     * Load configurations from props files
     * @throws IOException
     */
    private static void loadCfgConfig() throws IOException {
        try (InputStream fis = Settings.class.getClassLoader().getResourceAsStream("generator.cfg")) {
            Properties props = new Properties();
            props.load(fis);
            String userDir = System.getProperty("user.home");
            for (Map.Entry<Object, Object> prop : props.entrySet()) {
                String key = (String) prop.getKey();
                String value = (String) prop.getValue();
                if (value != null) {
                    value = value.replace("${USERDIR}", userDir);
                    value = replaceEnvVariables(value);
                    Settings.props.put(key, value);
                }
            }
        }
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

    public static Object getProperty(String key) {
        return props.get(key);
    }

    public static class LinkedHashMapDeserializer extends JsonDeserializer<Map<String, Object>> {
        @Override
        public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            return p.readValueAs(LinkedHashMap.class);
        }
    }
}
