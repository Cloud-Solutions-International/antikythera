package com.cloud.api.generator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

public class PomParser {
    public static void main(String[] args) {
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            JsonNode rootNode = xmlMapper.readTree(new File("pom.xml"));
            System.out.println("GroupId: " + rootNode.path("groupId").asText());
            System.out.println("ArtifactId: " + rootNode.path("artifactId").asText());
            System.out.println("Version: " + rootNode.path("version").asText());

            JsonNode dependenciesNode = rootNode.path("dependencies").path("dependency");
            if (dependenciesNode.isArray()) {
                for (JsonNode dependencyNode : dependenciesNode) {
                    System.out.println("Dependency GroupId: " + dependencyNode.path("groupId").asText());
                    System.out.println("Dependency ArtifactId: " + dependencyNode.path("artifactId").asText());
                    System.out.println("Dependency Version: " + dependencyNode.path("version").asText());
                }
            }

            JsonNode propertiesNode = rootNode.path("properties");
            Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                System.out.println("Property Key: " + field.getKey() + " : Property Value: " + field.getValue().asText());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
