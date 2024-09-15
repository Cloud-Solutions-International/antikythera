package com.cloud.api.generator;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class PomParser {
    public static void main(String[] args) {
        try {
            // Read the pom.xml file into a Model object
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader("pom.xml"));

            // Modify the Model object as needed (example: print values)
            System.out.println("GroupId: " + model.getGroupId());
            System.out.println("ArtifactId: " + model.getArtifactId());
            System.out.println("Version: " + model.getVersion());

            model.getDependencies().forEach(dependency -> {
                System.out.println("Dependency GroupId: " + dependency.getGroupId());
                System.out.println("Dependency ArtifactId: " + dependency.getArtifactId());
                System.out.println("Dependency Version: " + dependency.getVersion());
            });

            // Write the Model object back to an XML file
            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(new FileWriter("output-pom.xml"), model);

        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        }
    }
}
