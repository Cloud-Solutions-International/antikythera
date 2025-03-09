package sa.com.cloudsolutions.antikythera.finch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finches are simple mechanism to replace classes in the source code with custom classes.
 *
 */
public class Finch {
    static Map<String, Object> finches;
    private static final Logger logger = LoggerFactory.getLogger(Finch.class);

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.err.println("Usage: java Finch <source-directory>");
            System.exit(1);
        }

        File sourceDir = new File(args[0]);

        Map<String, Object> classes = loadClasses(sourceDir);
        for (String cls : classes.keySet()) {
            System.out.println(cls);
        }
    }

    public static void loadFinches() {
        try {
            if (Finch.finches == null) {
                Finch.finches = new HashMap<>();
                Collection<String> scouts = Settings.getPropertyList("finch", String.class);
                for (String scout : scouts) {
                    Map<String, Object> finches = Finch.loadClasses(new File(scout));
                    Finch.finches.putAll(finches);
                }
            }
        } catch (Exception e) {
            logger.warn("Finches could not be loaded {}", e.getMessage());
        }
    }

    public static Map<String, Object> loadClasses(File sourceDir) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        // List to hold the paths of all Java source files
        List<String> sourceFiles = new ArrayList<>();
        Map<String, Object> classes = new HashMap<>();

        // Recursively find all Java source files in the directory
        findJavaFiles(sourceDir, sourceFiles);

        // Convert the list to an array
        String[] sourceFileArray = sourceFiles.toArray(new String[0]);

        // Compile the source files
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, sourceFileArray);
        if (result != 0) {
            throw new RuntimeException("Compilation failed.");
        }

        // Load and instantiate the compiled classes
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{sourceDir.toURI().toURL()});
        for (String sourceFile : sourceFiles) {
            String className = sourceFile
                    .replace(sourceDir.getPath() + File.separator, "")
                    .replace(File.separator, ".")
                    .replace(".java", "");
            Class<?> cls = Class.forName(className, true, classLoader);
            Object instance = cls.getDeclaredConstructor().newInstance();
            classes.put(cls.getName(), instance);
        }
        return classes;
    }

    // Helper method to find all Java files in a directory
    private static void findJavaFiles(File dir, List<String> fileList) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                findJavaFiles(file, fileList);
            } else if (file.getName().endsWith(".java")) {
                fileList.add(file.getPath());
            }
        }
    }

    public static Object getFinch(String resolvedClass) {
        return finches.get(resolvedClass);
    }

    public static void clear() {
        finches = null;
    }
}
