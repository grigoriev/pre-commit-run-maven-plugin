package io.github.grigoriev.precommit;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Parses .pre-commit-config.yaml files.
 */
public class PreCommitConfigParser {

    /**
     * Checks if a hook with the given ID is configured in the pre-commit config file.
     *
     * @param configFile the .pre-commit-config.yaml file
     * @param hookId     the hook ID to look for
     * @return true if the hook is found
     */
    public boolean isHookConfigured(File configFile, String hookId) {
        if (configFile == null || !configFile.exists()) {
            return false;
        }

        try (InputStream is = new FileInputStream(configFile)) {
            return isHookConfigured(is, hookId);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks if a hook with the given ID is configured in the pre-commit config.
     *
     * @param inputStream the input stream of the YAML content
     * @param hookId      the hook ID to look for
     * @return true if the hook is found
     */
    @SuppressWarnings("unchecked")
    public boolean isHookConfigured(InputStream inputStream, String hookId) {
        if (inputStream == null || hookId == null || hookId.isEmpty()) {
            return false;
        }

        try {
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(options));
            Map<String, Object> config = yaml.load(inputStream);

            if (config == null) {
                return false;
            }

            Object repos = config.get("repos");
            if (!(repos instanceof List)) {
                return false;
            }

            for (Object repo : (List<?>) repos) {
                if (!(repo instanceof Map)) {
                    continue;
                }

                Object hooks = ((Map<String, Object>) repo).get("hooks");
                if (!(hooks instanceof List)) {
                    continue;
                }

                for (Object hook : (List<?>) hooks) {
                    if (!(hook instanceof Map)) {
                        continue;
                    }

                    Object id = ((Map<String, Object>) hook).get("id");
                    if (hookId.equals(id)) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
