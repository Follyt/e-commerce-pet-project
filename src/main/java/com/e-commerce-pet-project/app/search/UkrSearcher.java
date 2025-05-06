package com.e_commerce_pet_project.search;

import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class UkrainianSynonymSearcher extends Searcher {

    private final Map<String, List<String>> synonymMap = new HashMap<>();

    public UkrainianSynonymSearcher() {
        loadSynonyms();
    }

    private void loadSynonyms() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("dictionary.json")) {
            if (is == null) throw new RuntimeException("dictionary.json not found");
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);
            for (String key : obj.keySet()) {
                List<String> synonyms = new ArrayList<>();
                for (Object syn : obj.getJSONArray(key)) {
                    synonyms.add(syn.toString().toLowerCase());
                }
                synonymMap.put(key.toLowerCase(), synonyms);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error loading synonyms: " + e.getMessage(), e);
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        String queryStr = query.getModel().getQueryString().toLowerCase();

        Set<String> expandedTerms = new HashSet<>();
        expandedTerms.add(queryStr);

        for (Map.Entry<String, List<String>> entry : synonymMap.entrySet()) {
            for (String syn : entry.getValue()) {
                if (queryStr.contains(syn)) {
                    expandedTerms.add(entry.getKey());
                    expandedTerms.addAll(entry.getValue());
                }
            }
        }

        if (expandedTerms.size() > 1) {
            String orQuery = String.join(" OR ", expandedTerms);
            query.getModel().getQueryTree().setRoot(new WordItem(orQuery));
        }

        return execution.search(query);
    }
}

