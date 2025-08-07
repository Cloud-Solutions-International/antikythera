package sa.com.cloudsolutions.antikythera.evaluator;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConvertValueTest {
    
    public static class Source {
        private String name;
        @JsonIgnore
        private int age;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public int getAge() {
            return age;
        }
        public void setAge(int age) {
            this.age = age;
        }
    }

    public static class Target {
        private String name;
        private int yearsOld;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public int getYearsOld() {
            return yearsOld;
        }
        public void setYearsOld(int yearsOld) {
            this.yearsOld = yearsOld;
        }
        
        @Override
        public String toString() {
            return "Target{name='" + name + "', yearsOld=" + yearsOld + "}";
        }
    }

    @Test
    public void testConvert() {
        ObjectMapper objectMapper = new ObjectMapper();

        Source source = new Source();
        source.setName("Alice");
        source.setAge(30);

        // Convert Source to Target
        Target target = objectMapper.convertValue(source, Target.class);

        System.out.println("ObjectMapper result:");
        System.out.println("Name: " + target.getName());
        System.out.println("Years Old: " + target.getYearsOld());
        System.out.println(target);
        
        // Add assertions to see actual values in test results
        org.junit.jupiter.api.Assertions.assertEquals("Alice", target.getName());
        org.junit.jupiter.api.Assertions.assertEquals(0, target.getYearsOld());
    }

    @Test
    public void testMap() {
        ModelMapper modelMapper = new ModelMapper();
        
        // Add custom mapping configuration to explicitly map age to yearsOld
        modelMapper.typeMap(Source.class, Target.class)
            .addMapping(Source::getAge, Target::setYearsOld);

        Source source = new Source();
        source.setName("Alice");
        source.setAge(30);

        // Convert Source to Target
        Target target = modelMapper.map(source, Target.class);

        System.out.println("ModelMapper result with custom mapping:");
        System.out.println("Name: " + target.getName());
        System.out.println("Years Old: " + target.getYearsOld());
        System.out.println(target);
        
        // Add assertions to see actual values in test results
        org.junit.jupiter.api.Assertions.assertEquals("Alice", target.getName());
        org.junit.jupiter.api.Assertions.assertEquals(30, target.getYearsOld(), "ModelMapper should map age to yearsOld with custom mapping");
    }
    
    @Test
    public void testMapWithoutCustomMapping() {
        ModelMapper modelMapper = new ModelMapper();
        
        // No custom mapping configuration - default behavior

        Source source = new Source();
        source.setName("Alice");
        source.setAge(30);

        // Convert Source to Target
        Target target = modelMapper.map(source, Target.class);

        System.out.println("ModelMapper result without custom mapping:");
        System.out.println("Name: " + target.getName());
        System.out.println("Years Old: " + target.getYearsOld());
        System.out.println(target);
        
        // Add assertions to see actual values in test results
        org.junit.jupiter.api.Assertions.assertEquals("Alice", target.getName());
        org.junit.jupiter.api.Assertions.assertEquals(0, target.getYearsOld(), "ModelMapper respects @JsonIgnore by default");
    }
}
