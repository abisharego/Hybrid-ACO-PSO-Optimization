package helpers;

import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the config.properties file.
 */
public class ConfigReader {
    private final Properties properties = new Properties();

    public ConfigReader(String fileName) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                System.err.println("Sorry, unable to find " + fileName);
                throw new RuntimeException("Configuration file not found: " + fileName);
            }
            properties.load(input);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("Error loading configuration file", ex);
        }
    }

    /**
     * Cleans a raw string value from the properties file.
     * It removes comments (anything after a '#') and trims whitespace.
     */
    private String cleanValue(String key) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null) {
            throw new RuntimeException("Missing configuration key: " + key);
        }

        // 1. Check for an inline comment
        if (rawValue.contains("#")) {
            // Get only the part *before* the comment
            rawValue = rawValue.substring(0, rawValue.indexOf("#"));
        }

        // 2. Remove any leading/trailing whitespace
        return rawValue.trim();
    }

    public double getDouble(String key) {
        // Use the cleaned value instead of the raw one
        return Double.parseDouble(cleanValue(key));
    }

    public int getInt(String key) {
        // Use the cleaned value instead of the raw one
        return Integer.parseInt(cleanValue(key));
    }

    public static void main(String[] args) {
        // Test the reader
        ConfigReader config = new ConfigReader("config.properties");

        double alpha = config.getDouble("aco.alpha");
        System.out.println("Successfully read aco.alpha: " + alpha);

        int hosts = config.getInt("simulation.hosts");
        System.out.println("Successfully read simulation.hosts: " + hosts);
    }
}