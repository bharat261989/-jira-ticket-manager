package org.limits.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Custom Dropwizard ConfigurationFactory that loads HOCON configuration files.
 *
 * Load order:
 * 1. reference.conf (from classpath, if exists)
 * 2. application.conf / config.conf (main config)
 * 3. user.conf (local overrides, if exists)
 * 4. Environment variables
 * 5. System properties
 */
public class HoconConfigurationFactory<T> extends ConfigurationFactory<T> {

    private static final Logger LOG = LoggerFactory.getLogger(HoconConfigurationFactory.class);

    private final Class<T> klass;
    private final ObjectMapper mapper;
    private final Validator validator;

    public HoconConfigurationFactory(Class<T> klass, Validator validator, ObjectMapper mapper, String propertyPrefix) {
        super(klass, validator, mapper, propertyPrefix);
        this.klass = klass;
        this.validator = validator;
        this.mapper = mapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public T build(ConfigurationSourceProvider provider, String path) throws IOException, ConfigurationException {
        LOG.info("Loading HOCON configuration from: {}", path);

        Config config = loadConfig(path);

        // Log effective config (without sensitive data)
        LOG.debug("Effective configuration loaded");

        return buildFromConfig(config);
    }

    @Override
    public T build() throws IOException, ConfigurationException {
        Config config = loadConfig(null);
        return buildFromConfig(config);
    }

    private Config loadConfig(String path) {
        Config config;

        if (path != null && !path.isEmpty()) {
            File configFile = new File(path);
            if (configFile.exists()) {
                LOG.info("Loading config from file: {}", configFile.getAbsolutePath());
                config = ConfigFactory.parseFile(configFile);
            } else {
                // Try classpath
                LOG.info("Loading config from classpath: {}", path);
                config = ConfigFactory.parseResources(path);
            }
            // Apply fallbacks: user.conf -> loaded config -> application.conf -> reference.conf
            Config withFallbacks = ConfigFactory.load(config);
            config = withFallbacks;
        } else {
            // Default load order: application.conf, then reference.conf
            config = ConfigFactory.load();
        }

        // Resolve all substitutions
        config = config.resolve();

        LOG.info("Configuration loaded successfully");
        return config;
    }

    private T buildFromConfig(Config config) throws IOException, ConfigurationException {
        // Render HOCON to JSON
        String json = config.root().render(ConfigRenderOptions.concise());

        // Parse JSON to target class
        T instance = mapper.readValue(json, klass);

        // Validate
        validate(instance);

        return instance;
    }

    private void validate(T instance) throws ConfigurationException {
        var violations = validator.validate(instance);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Configuration validation failed:\n");
            for (var violation : violations) {
                sb.append("  - ").append(violation.getPropertyPath())
                  .append(": ").append(violation.getMessage()).append("\n");
            }
            throw new ConfigurationException(sb.toString());
        }
    }
}
