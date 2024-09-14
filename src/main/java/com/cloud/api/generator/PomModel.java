package com.cloud.api.generator;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import lombok.Getter;

import java.util.List;

@Getter
public class PomModel {
    @JacksonXmlProperty(localName = "groupId")
    private String groupId;

    @JacksonXmlProperty(localName = "artifactId")
    private String artifactId;

    @JacksonXmlProperty(localName = "version")
    private String version;

    @JacksonXmlElementWrapper(localName = "dependencies")
    @JacksonXmlProperty(localName = "dependency")
    private List<Dependency> dependencies;

    @Getter
    public static class Dependency {
        @JacksonXmlProperty(localName = "groupId")
        private String groupId;

        @JacksonXmlProperty(localName = "artifactId")
        private String artifactId;

        @JacksonXmlProperty(localName = "version")
        private String version;

        // Getters and Setters
    }
}
