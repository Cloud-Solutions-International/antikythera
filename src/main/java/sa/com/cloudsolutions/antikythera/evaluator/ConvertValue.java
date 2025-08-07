package sa.com.cloudsolutions.antikythera.evaluator;

import org.modelmapper.ModelMapper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings({"java:S106","unused"})
public class ConvertValue {
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
        // Added getter and setter for yearsOld to enable proper mapping
        public int getYearsOld() {
            return yearsOld;
        }
        public void setYearsOld(int yearsOld) {
            this.yearsOld = yearsOld;
        }
    }

    public static void main(String[] args) {
        convert();
        map();
    }

    private static void map() {
        ModelMapper mapper = new ModelMapper();
        
        // Add custom mapping configuration to explicitly map age to yearsOld
        // This is needed because ModelMapper respects @JsonIgnore by default
        mapper.typeMap(Source.class, Target.class)
            .addMapping(Source::getAge, Target::setYearsOld);
            
        Source source = new Source();
        source.name = "Alice";
        source.age = 30;

        // Convert SourceObject to TargetObject
        Target target = mapper.map(source, Target.class);

        System.out.println("Name: " + target.name);
        System.out.println("Years Old: " + target.yearsOld);
    }
    
    private static void convert() {
        ObjectMapper objectMapper = new ObjectMapper();

        Source source = new Source();
        source.name = "Alice";
        source.age = 30;

        // Convert SourceObject to TargetObject
        Target target = objectMapper.convertValue(source, Target.class);

        System.out.println("Name: " + target.name);
        System.out.println("Years Old: " + target.yearsOld);
    }
}
