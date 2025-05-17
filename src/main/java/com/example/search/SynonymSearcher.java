package com.example.search;  // укажите нужный пакет

import com.yahoo.search.Searcher;
import com.yahoo.search.Result;
import com.yahoo.search.Query;
import com.yahoo.search.searchchain.Execution;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SynonymSearcher extends Searcher {
    // Карта синонимов: ключ – слово/фраза, значение – полный список синонимов
    private static final Map<String, List<String>> synonymsMap = new HashMap<>();

    static {
        try (InputStream is = SynonymSearcher.class.getResourceAsStream("/synonyms.json")) {
            if (is != null) {
                // Создём ридер, далее InputStreamReader (переводит байты в символы с использованием кодировки) оборачиваем в BufferedReader, а InputStream в InputStreamReader
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                // Парсим JSON-массив групп синонимов
                // Этот блок в ите проходит по всем синонимическим группам, в йоте он вытаскивает каждый термин из группы и складывает их в синонимГруп
                JSONArray groups = new JSONArray(sb.toString());
                for (int i = 0; i < groups.length(); i++) {
                    JSONArray group = groups.getJSONArray(i);
                    List<String> synonymGroup = new ArrayList<>();
                    for (int j = 0; j < group.length(); j++) {
                        String term = group.getString(j);
                        synonymGroup.add(term);
                    }
                    // Заполняем карту: каждое слово/фраза указывает на весь список синонимов
                    for (String term : synonymGroup) {
                        synonymsMap.put(term, synonymGroup);
                    }
                }
            } else {
                System.err.println("Не удалось найти synonyms.json в classpath");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        String originalQuery = query.getModel().getQueryString();
        if (originalQuery == null || originalQuery.isEmpty()) {
            // Если пользовательский запрос пустой или отсутствует (например, YQL), ничего не делаем
            return execution.search(query);
        }
        String rewritten = rewriteQuery(originalQuery);
        if (!rewritten.equals(originalQuery)) {
            // Заменяем строку запроса на переписанную с синонимами
            query.getModel().setQueryString(rewritten);
        }
        // Продолжаем цепочку поисковых компонентов
        return execution.search(query);
    }

    /** Переписывает строку запроса, подставляя синонимы вместо отдельных токенов */
    private String rewriteQuery(String queryStr) {
        List<String> tokens = new ArrayList<>();
        List<Boolean> wasQuoted = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder token = new StringBuilder();
        // Разбиваем запрос на токены, учитывая кавычки для фраз
        for (int i = 0; i < queryStr.length(); i++) {
            char c = queryStr.charAt(i);
            if (c == '\"') {
                inQuote = !inQuote;
                if (!inQuote) {
                    // Закрывающая кавычка – заканчиваем формирование токена-фразы
                    if (token.length() > 0) {
                        tokens.add(token.toString());
                        wasQuoted.add(true);
                        token.setLength(0);
                    }
                }
                continue;
            }
            if (!inQuote && Character.isWhitespace(c)) {
                // Разрыв токена вне кавычек
                if (token.length() > 0) {
                    tokens.add(token.toString());
                    wasQuoted.add(false);
                    token.setLength(0);
                }
            } else {
                token.append(c);
            }
        }
        // Добавляем последний токен, если есть
        if (token.length() > 0) {
            tokens.add(token.toString());
            wasQuoted.add(inQuote);
        }

        StringBuilder newQuery = new StringBuilder();
        boolean synonymFound = false;
        for (int i = 0; i < tokens.size();) {
            String term = tokens.get(i);
            List<String> synGroup = null;
            // Проверяем двухсловные фразы на синонимы (например, "ай фон")
            if (i < tokens.size() - 1) {
                String twoWordPhrase = term + " " + tokens.get(i + 1);
                if (synonymsMap.containsKey(twoWordPhrase)) {
                    synGroup = synonymsMap.get(twoWordPhrase);
                    i += 2;  // пропускаем два токена, объединённых в фразу
                }
            }
            // Если двухсловного синонима не найдено, проверяем одиночный токен
            if (synGroup == null && synonymsMap.containsKey(term)) {
                synGroup = synonymsMap.get(term);
                i += 1;
            }
            if (synGroup != null) {
                synonymFound = true;
                // Строим конструкцию (syn1 OR syn2 OR ... OR synN)
                newQuery.append("(");
                for (int j = 0; j < synGroup.size(); j++) {
                    String syn = synGroup.get(j);
                    // Если синоним – фраза из нескольких слов, заключаем её в кавычки
                    if (syn.contains(" ")) {
                        newQuery.append("\"").append(syn).append("\"");
                    } else {
                        newQuery.append(syn);
                    }
                    if (j < synGroup.size() - 1) {
                        newQuery.append(" OR ");
                    }
                }
                newQuery.append(")");
            } else {
                // Синонима нет – возвращаем исходный токен (добавляя кавычки, если это была фраза)
                if (wasQuoted.get(i)) {
                    newQuery.append("\"").append(term).append("\"");
                } else {
                    newQuery.append(term);
                }
                i += 1;
            }
            if (i < tokens.size()) {
                newQuery.append(" ");
            }
        }
        String newQueryStr = newQuery.toString();
        // Если были подставлены синонимы, оборачиваем весь запрос в скобки для сохранения структуры
        return synonymFound ? "(" + newQueryStr + ")" : queryStr;
    }
}
