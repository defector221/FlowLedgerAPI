package com.flowledger.migration.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.entity.ImportSynonym;
import com.flowledger.migration.repository.ImportSynonymRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MappingEngine {
    private final ImportSynonymRepository synonyms;
    private final ObjectMapper objectMapper;
    private final ModuleFieldCatalog catalog;

    public MappingEngine(ImportSynonymRepository synonyms, ObjectMapper objectMapper, ModuleFieldCatalog catalog) {
        this.synonyms = synonyms;
        this.objectMapper = objectMapper;
        this.catalog = catalog;
    }

    public List<FieldMapping> autoMap(ImportModule module, List<String> columns) {
        Map<String, String> synonymMap = loadSynonyms(module);
        List<String> targets = catalog.fieldsFor(module);
        List<FieldMapping> mappings = new ArrayList<>();
        for (String column : columns) {
            if (column == null || column.isBlank()) continue;
            String key = column.trim().toLowerCase(Locale.ROOT);
            String target = synonymMap.get(key);
            if (target == null) {
                // exact target field name match
                target = targets.stream()
                        .filter(t -> t.equalsIgnoreCase(column.trim())
                                || t.toLowerCase(Locale.ROOT).equals(key.replace(" ", "")))
                        .findFirst()
                        .orElse(null);
            }
            if (target != null) {
                mappings.add(FieldMapping.of(column, target));
            }
        }
        return mappings;
    }

    public Map<String, String> applyMapping(Map<String, String> raw, List<FieldMapping> mappings) {
        Map<String, String> out = new LinkedHashMap<>();
        for (FieldMapping m : mappings) {
            String value = raw.getOrDefault(m.sourceColumn(), "");
            if ((value == null || value.isBlank()) && m.defaultValue() != null) {
                value = m.defaultValue();
            }
            if (m.transform() != null && !m.transform().isBlank() && value != null) {
                value = transform(value, m.transform());
            }
            out.put(m.targetField(), value == null ? "" : value.trim());
        }
        return out;
    }

    public String toJson(List<FieldMapping> mappings) {
        try {
            return objectMapper.writeValueAsString(mappings);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<FieldMapping> fromJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return List.of();
        try {
            if (json.trim().startsWith("{")) {
                // { source: target } map form
                Map<String, String> map = objectMapper.readValue(json, new TypeReference<>() {});
                List<FieldMapping> list = new ArrayList<>();
                map.forEach((k, v) -> list.add(FieldMapping.of(k, v)));
                return list;
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid mapping JSON", e);
        }
    }

    private Map<String, String> loadSynonyms(ImportModule module) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ImportSynonym s : synonyms.findForModule(module.name())) {
            map.put(s.getSynonym().trim().toLowerCase(Locale.ROOT), s.getTargetField());
        }
        return map;
    }

    private String transform(String value, String transform) {
        return switch (transform.toUpperCase(Locale.ROOT)) {
            case "UPPER" -> value.toUpperCase(Locale.ROOT);
            case "LOWER" -> value.toLowerCase(Locale.ROOT);
            case "TRIM" -> value.trim();
            default -> value;
        };
    }
}
