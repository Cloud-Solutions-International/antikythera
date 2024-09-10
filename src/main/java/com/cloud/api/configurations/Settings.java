package com.cloud.api.configurations;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Settings {
    protected static Properties props;

    public static void loadConfigMap() throws IOException {
        if(props == null) {
            props = new Properties();

            try (FileInputStream fis = new FileInputStream("src/main/resources/generator.cfg")) {
                props.load(fis);
                String userDir = System.getProperty("user.home");
                for(var prop : props.entrySet()) {
                    String key = (String) prop.getKey();
                    String value = (String) prop.getValue();
                    if(value != null) {
                        value = value.replace("{$USERDIR}", userDir);
                        props.setProperty(key, value);
                    }
                }
            }
        }
    }

    public static String getProperty(String key) {
        return props.getProperty(key);
    }
}
