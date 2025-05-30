package com.example.search;

import com.yahoo.search.Searcher;
import com.yahoo.search.Result;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SynSOtladka extends Searcher {

    private static final Map<String, String> termToCanonical = new HashMap<>();
    private static final Map<String, List<String>> canonicalToGroup = new HashMap<>();

    static {
        try (InputStream is = SynonymSearcher.class.getResourceAsStream("/synonymsCopy.json")) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JSONObject root = new JSONObject(sb.toString());
                for (String canonical : root.keySet()) {
                    Set<String> groupSet = new LinkedHashSet<>();
                    groupSet.add(canonical);
                    JSONArray synonyms = root.getJSONArray(canonical);
                    for (int i = 0; i < synonyms.length(); i++) {
                        groupSet.add(synonyms.getString(i));
                    }
                    List<String> group = new ArrayList<>(groupSet);
                    canonicalToGroup.put(canonical.toLowerCase(), group);
                    for (String term : group) {
                        termToCanonical.put(term.toLowerCase(), canonical.toLowerCase());
                    }
                }
            } else {
                System.err.println("Не удалось найти synonymsCopy.json в classpath");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        String originalQuery = query.getModel().getQueryString();
        if (originalQuery == null || originalQuery.isEmpty()) {
            return execution.search(query);
        }
        String rewritten = rewriteQuery(originalQuery);
        if (!rewritten.equals(originalQuery)) {
            query.getModel().setQueryString(rewritten);
        }
        return execution.search(query);
    }

    private String rewriteQuery(String queryStr) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < queryStr.length(); i++) {
            char c = queryStr.charAt(i);
            if (Character.isWhitespace(c)) {
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }

        System.out.println("▶️ Токены из запроса: " + tokens);

        StringBuilder newQuery = new StringBuilder();
        boolean synonymFound = false;

        for (int i = 0; i < tokens.size(); i++) {
            String term = tokens.get(i);
            String lowerTerm = term.toLowerCase();
            String canonical = termToCanonical.get(lowerTerm);

            System.out.println("\n🔹 Обрабатываем токен: \"" + term + "\"");
            if (canonical != null) {
                System.out.println("   ⤷ Найдена каноническая форма: " + canonical);
                List<String> group = canonicalToGroup.get(canonical);
                if (group != null) {
                    System.out.println("   ⤷ Группа синонимов: " + group);
                    synonymFound = true;
                    newQuery.append("(");
                    for (int j = 0; j < group.size(); j++) {
                        String syn = group.get(j);
                        newQuery.append(syn);
                        if (j < group.size() - 1) {
                            newQuery.append(" OR ");
                        }
                    }
                    newQuery.append(")");
                } else {
                    System.out.println("   ⤷ ⚠️ Группа не найдена, вставляем оригинальное слово");
                    newQuery.append(term);
                }
            } else {
                System.out.println("   ⤷ ❌ Каноническая форма не найдена, вставляем как есть");
                newQuery.append(term);
            }

            if (i < tokens.size() - 1) {
                newQuery.append(" ");
            }
        }

        String finalQuery = synonymFound ? "(" + newQuery.toString() + ")" : queryStr;
        System.out.println("\n✅ Финальный запрос: " + finalQuery);
        return finalQuery;
    }

    public static void main(String[] args) {
        // Пример запроса
        String query = "купить айфон galaxy";

        // Запускаем вручную метод rewriteQuery
        SynSOtladka searcher = new SynSOtladka();
        searcher.rewriteQuery(query);
    }


}
