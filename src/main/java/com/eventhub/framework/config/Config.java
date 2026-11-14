package com.eventhub.framework.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Layered configuration. Later layers win:
 * <ol>
 *   <li>classpath {@code config/default.properties}</li>
 *   <li>classpath {@code config/<env>.properties} (env from {@code -Denv} or {@code EVENTHUB_ENV}, default {@code public})</li>
 *   <li>{@code ./local.properties} (git-ignored; for credentials on a dev machine)</li>
 *   <li>environment variable {@code EVENTHUB_<KEY>} (dots become underscores, upper-cased) - used by CI</li>
 *   <li>JVM system property {@code -D<key>=...}</li>
 * </ol>
 * Tests never read configuration directly; framework components do.
 */
public final class Config {

    private static final String DEFAULT_ENV = "public";
    private static final Config INSTANCE = load();

    private final String env;
    private final Properties fileProperties;

    private Config(String env, Properties fileProperties) {
        this.env = env;
        this.fileProperties = fileProperties;
    }

    public static Config get() {
        return INSTANCE;
    }

    public String env() {
        return env;
    }

    public Optional<String> find(String key) {
        String sys = System.getProperty(key);
        if (notBlank(sys)) {
            return Optional.of(sys.trim());
        }
        String envVar = System.getenv(toEnvVar(key));
        if (notBlank(envVar)) {
            return Optional.of(envVar.trim());
        }
        String file = fileProperties.getProperty(key);
        return notBlank(file) ? Optional.of(file.trim()) : Optional.empty();
    }

    public String require(String key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "Missing configuration '" + key + "'. Set it in local.properties, as -D" + key
                        + "=..., or as environment variable " + toEnvVar(key)));
    }

    public int getInt(String key) {
        return Integer.parseInt(require(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(require(key));
    }

    public List<String> getList(String key) {
        return Arrays.stream(require(key).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static String toEnvVar(String key) {
        return "EVENTHUB_" + key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private static Config load() {
        String env = Optional.ofNullable(System.getProperty("env"))
                .or(() -> Optional.ofNullable(System.getenv("EVENTHUB_ENV")))
                .filter(Config::notBlank)
                .orElse(DEFAULT_ENV);

        Properties props = new Properties();
        loadClasspath(props, "config/default.properties", true);
        loadClasspath(props, "config/" + env + ".properties", true);
        loadFile(props, Path.of("local.properties"));
        return new Config(env, props);
    }

    private static void loadClasspath(Properties target, String resource, boolean required) {
        try (InputStream in = Config.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                if (required) {
                    throw new IllegalStateException("Config resource not found on classpath: " + resource);
                }
                return;
            }
            target.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }

    private static void loadFile(Properties target, Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            target.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
