package com.flowledger.migration.parser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CsvParser {

    public ParsedSheet parse(InputStream in, String sheetName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return ParsedSheet.empty(sheetName);
            }
            // strip BOM
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            List<String> columns = splitCsvLine(headerLine);
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> values = splitCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    String col = columns.get(i).trim();
                    String val = i < values.size() ? values.get(i).trim() : "";
                    row.put(col, val);
                }
                rows.add(row);
            }
            return new ParsedSheet(sheetName, columns.stream().map(String::trim).toList(), rows);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    static List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
