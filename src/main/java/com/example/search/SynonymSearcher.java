package com.example.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;

import com.yahoo.search.Searcher;
import com.yahoo.search.Result;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Searcher implementation that expands query terms via a synonym dictionary.
 * Synonyms are loaded from /synonymsCopy.json on startup.
 */
public class SynonymSearcher extends Searcher {

    private static final Logger log = LoggerFactory.getLogger(SynonymSearcher.class);

    // Maps each term (synonym or canonical) to its canonical form.
    private static final Map<String, String> termToCanonical = new HashMap<>();
    // Maps each canonical term to the full list of synonyms (including itself).
    private static final Map<String, List<String>> canonicalToGroup = new HashMap<>();

    static {
        log.info("Initializing SynonymSearcher and loading synonymsCopy.json");

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Module to convert all JSON keys and string values to lower-case
        SimpleModule lowercaseModule = new SimpleModule();

        lowercaseModule.addKeyDeserializer(String.class, new KeyDeserializer() {
            @Override
            public String deserializeKey(String key, DeserializationContext ct) {
                String lower = (key == null) ? null : key.toLowerCase();
                log.debug("Key deser: '{}' -> '{}'", key, lower);
                return lower;
            }
        });

        lowercaseModule.addDeserializer(String.class, new StdScalarDeserializer<String>(String.class) {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ct) throws IOException {
                String orig = p.getValueAsString();
                if (orig == null) {
                    log.debug("String-deserialize: null");
                    return null;
                }
                String lower = orig.toLowerCase();
                log.debug("String-deserialize: '{}' -> '{}'", orig, lower);
                return lower;
            }
        });

        mapper.registerModule(lowercaseModule);

        // Load synonymsCopy.json from classpath
        try (InputStream is = SynonymSearcher.class.getResourceAsStream("/synonymsCopy.json")) {
            if (is == null) {
                log.error("synonymsCopy.json not found");
                throw new IllegalStateException("synonymsCopy.json not found");
            }

            Map<String, List<String>> root;
            try {
                root = mapper.readValue(is, new TypeReference<Map<String, List<String>>>() {});
                log.info("Loaded synonymsCopy.json, {} entries", root.size());
            } catch (IOException e) {
                log.error("Failed to parse synonymsCopy.json", e);
                throw new ExceptionInInitializerError(e);
            }

            // Build bidirectional maps: canonical -> group, and each term -> canonical
            for (Map.Entry<String, List<String>> entry : root.entrySet()) {
                String canonical = entry.getKey();
                List<String> synonyms = entry.getValue();

                Set<String> groupSet = new LinkedHashSet<>();
                groupSet.add(canonical);
                if (synonyms != null) {
                    groupSet.addAll(synonyms);
                }

                List<String> group = new ArrayList<>(groupSet);
                canonicalToGroup.put(canonical, group);

                for (String term : group) {
                    termToCanonical.put(term, canonical);
                    log.trace("Mapped term '{}' -> canonical '{}'", term, canonical);
                }
            }

            log.info("Dictionary initialized: {} canonicals, {} term mappings",
                    canonicalToGroup.size(), termToCanonical.size());

        } catch (IOException e) {
            log.error("Error reading synonymsCopy.json", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        String originalQuery = query.getModel().getQueryString();
        log.debug("search(): original query='{}'", originalQuery);

        if (originalQuery == null || originalQuery.isEmpty()) {
            return execution.search(query);
        }

        String rewritten = rewriteQuery(originalQuery);
        if (!rewritten.equals(originalQuery)) {
            log.info("search(): rewriting '{}' -> '{}'", originalQuery, rewritten);
            query.getModel().setQueryString(rewritten);
        } else {
            log.debug("search(): no synonyms found, query unchanged");
        }

        return execution.search(query);
    }

    /**
     * Splits queryStr on whitespace and replaces each term with "(syn1 OR syn2 ...)" if synonyms exist.
     */
    private String rewriteQuery(String queryStr) {
        log.debug("rewriteQuery(): tokenizing '{}'", queryStr);

        List<String> tokens = new ArrayList<>();
        String s = queryStr;
        int len = s.length();
        int start = 0;

        // Skip leading whitespace
        while (start < len && Character.isWhitespace(s.charAt(start))) {
            start++;
        }

        // Extract tokens separated by spaces
        while (start < len) {
            int spaceIndex = s.indexOf(' ', start);
            if (spaceIndex == -1) {
                tokens.add(s.substring(start));
                break;
            }
            tokens.add(s.substring(start, spaceIndex));
            start = spaceIndex + 1;
            while (start < len && Character.isWhitespace(s.charAt(start))) {
                start++;
            }
        }

        log.debug("rewriteQuery(): tokens = {}", tokens);

        StringBuilder newQuery = new StringBuilder();
        boolean synonymFound = false;

        for (int i = 0; i < tokens.size(); i++) {
            String term = tokens.get(i);
            String canonical = termToCanonical.get(term.toLowerCase());
            log.trace("rewriteQuery(): term='{}', canonical='{}'", term, canonical);

            if (canonical != null) {
                List<String> group = canonicalToGroup.get(canonical);
                if (group != null && !group.isEmpty()) {
                    synonymFound = true;
                    StringJoiner joiner = new StringJoiner(" OR ");
                    for (String syn : group) {
                        joiner.add(syn);
                    }
                    String component = "(" + joiner.toString() + ")";
                    newQuery.append(component);
                    log.trace("rewriteQuery(): replaced '{}' with '{}'", term, component);
                } else {
                    newQuery.append(term);
                }
            } else {
                newQuery.append(term);
            }

            if (i < tokens.size() - 1) {
                newQuery.append(" ");
            }
        }

        String result = synonymFound ? "(" + newQuery.toString() + ")" : queryStr;
        log.debug("rewriteQuery(): result = '{}'", result);
        return result;
    }

    /*
    Вариант реализации двунаправленной хеш-мапы через одну мапу с помощью библиотеки GoogleGuava:

    import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

// Maps each term (synonym or canonical) to its canonical form (bidirectional).
private static final BiMap<String, String> termToCanonical = HashBiMap.create();
// Maps each canonical term to the full list of synonyms (including itself).
private static final Map<String, List<String>> canonicalToGroup = new HashMap<>();

static {
    log.info("Initializing SynonymSearcher and loading synonymsCopy.json");

    ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Module to convert all JSON keys and string values to lower-case
    SimpleModule lowercaseModule = new SimpleModule();

    lowercaseModule.addKeyDeserializer(String.class, new KeyDeserializer() {
        @Override
        public String deserializeKey(String key, DeserializationContext ct) {
            String lower = (key == null) ? null : key.toLowerCase();
            log.debug("Key deser: '{}' -> '{}'", key, lower);
            return lower;
        }
    });

    lowercaseModule.addDeserializer(String.class, new StdScalarDeserializer<String>(String.class) {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ct) throws IOException {
            String orig = p.getValueAsString();
            if (orig == null) {
                log.debug("String-deserialize: null");
                return null;
            }
            String lower = orig.toLowerCase();
            log.debug("String-deserialize: '{}' -> '{}'", orig, lower);
            return lower;
        }
    });

    mapper.registerModule(lowercaseModule);

    // Load synonymsCopy.json from classpath
    try (InputStream is = SynonymSearcher.class.getResourceAsStream("/synonymsCopy.json")) {
        if (is == null) {
            log.error("synonymsCopy.json not found");
            throw new IllegalStateException("synonymsCopy.json not found");
        }

        Map<String, List<String>> root;
        try {
            root = mapper.readValue(is, new TypeReference<Map<String, List<String>>>() {});
            log.info("Loaded synonymsCopy.json, {} entries", root.size());
        } catch (IOException e) {
            log.error("Failed to parse synonymsCopy.json", e);
            throw new ExceptionInInitializerError(e);
        }

        // Build bidirectional maps: canonical -> group, and each term -> canonical
        for (Map.Entry<String, List<String>> entry : root.entrySet()) {
            String canonical = entry.getKey();
            List<String> synonyms = entry.getValue();

            Set<String> groupSet = new LinkedHashSet<>();
            groupSet.add(canonical);
            if (synonyms != null) {
                groupSet.addAll(synonyms);
            }

            List<String> group = new ArrayList<>(groupSet);
            canonicalToGroup.put(canonical, group);

            for (String term : group) {
                try {
                    termToCanonical.put(term, canonical);
                    log.trace("Mapped term '{}' -> canonical '{}'", term, canonical);
                } catch (IllegalArgumentException e) {
                    log.warn("Conflict: term '{}' already mapped to a different canonical", term);
                }
            }
        }

        log.info("Dictionary initialized: {} canonicals, {} term mappings",
                canonicalToGroup.size(), termToCanonical.size());

    } catch (IOException e) {
        log.error("Error reading synonymsCopy.json", e);
        throw new ExceptionInInitializerError(e);
    }
}
     */
    /*
    Строки со 143 по 165 можно заменить одной строкой:
    List<String> tokens = new ArrayList<>(Arrays.asList(queryStr.trim().split("\\s+")));

    Разница между моим вариантом и сплиттером заключается в том, что мой вариант будет работать быстрее в примитивных случаях и лишь с обычными пробелами " ",
    в то время как сплиттер более читаем и покрывает больше кейсов, но в моём случае такая гибкость не шибко нужна.
     */

    public static void main(String[] args) {
        // Example usage / local test
        termToCanonical.clear();
        canonicalToGroup.clear();

        String canonical1 = "телефон";
        List<String> group1 = Arrays.asList("телефон", "samsung");
        canonicalToGroup.put(canonical1, group1);
        for (String term : group1) {
            termToCanonical.put(term, canonical1);
        }
        log.info("main(): added group: {} -> {}", canonical1, group1);

        String canonical2 = "самсунг";
        List<String> group2 = Arrays.asList("самсунг", "samsung", "galaxy");
        canonicalToGroup.put(canonical2, group2);
        for (String term : group2) {
            termToCanonical.put(term, canonical2);
        }
        log.info("main(): added group: {} -> {}", canonical2, group2);

        SynonymSearcher searcher = new SynonymSearcher();
        String result = searcher.rewriteQuery("айфон galaxy");
        log.info("main(): rewrite result = '{}'", result);
    }
}
