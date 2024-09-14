package com.cloud.api.generator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.File;
import java.io.IOException;

public class PomParser {
    public static void main(String[] args) {
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            PomModel pomModel = xmlMapper.readValue(new File("pom.xml"), PomModel.class);
            System.out.println("GroupId: " + pomModel.getGroupId());
            System.out.println("ArtifactId: " + pomModel.getArtifactId());
            System.out.println("Version: " + pomModel.getVersion());

            for (PomModel.Dependency dependency : pomModel.getDependencies()) {
                System.out.println("Dependency GroupId: " + dependency.getGroupId());
                System.out.println("Dependency ArtifactId: " + dependency.getArtifactId());
                System.out.println("Dependency Version: " + dependency.getVersion());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
