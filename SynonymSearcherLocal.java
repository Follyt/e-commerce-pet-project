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
