package com.cloud.api.configurations;

import java.io.FileInputStream;
import java.io.IOException;
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

            try (FileInputStream fis = new FileInputStream("src/main/resources/generator.cfg")) {
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
}
