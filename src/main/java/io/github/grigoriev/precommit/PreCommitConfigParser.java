package io.github.grigoriev.precommit;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses .pre-commit-config.yaml files.
 */
public class PreCommitConfigParser {

    private static final Logger LOGGER = Logger.getLogger(PreCommitConfigParser.class.getName());

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
            LOGGER.log(Level.WARNING, "Failed to read pre-commit config file: " + configFile.getPath(), e);
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
    public boolean isHookConfigured(InputStream inputStream, String hookId) {
        if (inputStream == null || hookId == null || hookId.isEmpty()) {
            return false;
        }

        try {
            Map<String, Object> config = parseYaml(inputStream);
            List<Map<String, Object>> repos = getRepos(config);
            return repos.stream()
                    .flatMap(repo -> getHooks(repo).stream())
                    .anyMatch(hook -> hookId.equals(hook.get("id")));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse pre-commit config YAML", e);
            return false;
        }
    }

    private Map<String, Object> parseYaml(InputStream inputStream) {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Map<String, Object> config = yaml.load(inputStream);
        return config != null ? config : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getRepos(Map<String, Object> config) {
        Object repos = config.get("repos");
        if (repos instanceof List<?> repoList) {
            return repoList.stream()
                    .filter(Map.class::isInstance)
                    .map(repo -> (Map<String, Object>) repo)
                    .toList();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getHooks(Map<String, Object> repo) {
        Object hooks = repo.get("hooks");
        if (hooks instanceof List<?> hookList) {
            return hookList.stream()
                    .filter(Map.class::isInstance)
                    .map(hook -> (Map<String, Object>) hook)
                    .toList();
        }
        return Collections.emptyList();
    }
}
