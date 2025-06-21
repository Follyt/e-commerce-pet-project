import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class JsonlNameExtractor {
    private static final Logger log = LoggerFactory.getLogger(JsonlNameExtractor.class);

    public static void main(String[] args) {
        Set<String> names = new LinkedHashSet<>();
        ObjectMapper mapper = new ObjectMapper();

        try (BufferedReader reader = new BufferedReader(new FileReader("products.json"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode root = mapper.readTree(line);
                    JsonNode nameNode = root.path("fields").path("name_en");
                    if (!nameNode.isMissingNode()) {
                        names.add(nameNode.asText());
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при обработке строки: {}", line);
                    log.debug("Детали ошибки:", e);
                }
            }

            // Запись в файл
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"))) {
                for (String name : names) {
                    writer.write(name);
                    writer.newLine();
                }
            }

            log.info("Успешно: output.txt создан. Всего уникальных name_en: {}", names.size());

        } catch (IOException e) {
            log.error("Ошибка при чтении файла 'products.json'", e);
        }
    }
}
