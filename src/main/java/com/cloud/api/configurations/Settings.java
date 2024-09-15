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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Manages the configuration properties from the generator.cfg file.
 */
public class Settings {
    protected static Properties props;

    /**
     * Private constructor to prevent class being initialized.
     */
    private Settings() {}

    public static void loadConfigMap() throws IOException {
        if (props == null) {
            props = new Properties();

            File yamlFile = new File(Settings.class.getClassLoader().getResource("generator.yml").getFile());
            if (yamlFile.exists()) {
                loadYamlConfig(yamlFile);
            } else {
                loadCfgConfig();
            }
        }
    }

    private static void loadYamlConfig(File yamlFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addDeserializer(Map.class, new LinkedHashMapDeserializer()));


        Map<String, String> yamlProps = mapper.readValue(yamlFile, Map.class);
        String userDir = System.getProperty("user.home");

        for (String key : yamlProps.keySet()) {
            System.out.println(key);
            Object value = yamlProps.get(key);
            if (value != null) {
                if (value instanceof String) {
                    String v = (String) value;
                    v = v.replace("${USERDIR}", userDir);
                    v = replaceEnvVariables(v);
                    props.setProperty(key, v);
                }
            }
        }
    }

    private static void loadCfgConfig() throws IOException {
        try (InputStream fis = Settings.class.getClassLoader().getResourceAsStream("generator.cfg")) {
            props.load(fis);
            String userDir = System.getProperty("user.home");
            for (Map.Entry<Object, Object> prop : props.entrySet()) {
                String key = (String) prop.getKey();
                String value = (String) prop.getValue();
                if (value != null) {
                    value = value.replace("${USERDIR}", userDir);
                    value = replaceEnvVariables(value);
                    props.setProperty(key, value);
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

    public static String getProperty(String key) {
        return props.getProperty(key);
    }

    public static class LinkedHashMapDeserializer extends JsonDeserializer<Map<String, Object>> {
        @Override
        public Map<String, Object> deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            return p.readValueAs(LinkedHashMap.class);
        }
    }
}
