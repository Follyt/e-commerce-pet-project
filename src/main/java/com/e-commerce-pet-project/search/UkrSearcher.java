package com.e_commerce_pet_project.search;

import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import org.json.JSONObject;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.Item;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class UkrSearcher extends Searcher {

    private final Map<String, List<String>> synonymMap = new HashMap<>();

    public UkrSearcher() {
        System.out.println("🔍 UkrSearcher initialized");
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

    // Разбиваем запрос на слова
    String[] tokens = queryStr.split("\\s+");
    List<Item> allTerms = new ArrayList<>();

for (String token : tokens) {
    Set<String> allSynonyms = new HashSet<>();
    allSynonyms.add(token);

    synonymMap.forEach((key, values) -> {
        if (token.equals(key) || values.contains(token)) {
            allSynonyms.add(key);
            allSynonyms.addAll(values);
        }
    });

    System.out.println("Token: " + token + " -> Synonyms: " + allSynonyms);

    if (allSynonyms.size() > 1) {
        OrItem orItem = new OrItem();
        for (String term : allSynonyms) {
            orItem.addItem(new WordItem(term));
        }
        allTerms.add(orItem);
    } else {
        allTerms.add(new WordItem(token));
    }
}


    // Склеим всё в AND-запрос
    if (!allTerms.isEmpty()) {
        AndItem andItem = new AndItem();
        for (Item item : allTerms) {
            andItem.addItem(item);
        }
        query.getModel().getQueryTree().setRoot(andItem);
    }

    return execution.search(query);
  }
}
