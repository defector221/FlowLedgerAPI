package com.flowledger.migration.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.entity.ImportSynonym;
import com.flowledger.migration.repository.ImportSynonymRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MappingEngineTest {
    @Test
    void autoMapsSynonymsAndExactFieldNames() {
        ImportSynonym s = new ImportSynonym();
        s.setId(UUID.randomUUID());
        s.setSynonym("item code");
        s.setTargetField("productCode");
        MappingEngine engine =
                new MappingEngine(proxyRepo(List.of(s)), new ObjectMapper(), new ModuleFieldCatalog());

        List<FieldMapping> mappings =
                engine.autoMap(ImportModule.PRODUCT, List.of("Item Code", "productName", "unknown"));
        assertEquals(2, mappings.size());
        assertTrue(mappings.stream().anyMatch(m -> "productCode".equals(m.targetField())));
        assertTrue(mappings.stream().anyMatch(m -> "productName".equals(m.targetField())));
    }

    @Test
    void applyMappingUsesDefaultsAndTransforms() {
        MappingEngine engine =
                new MappingEngine(proxyRepo(List.of()), new ObjectMapper(), new ModuleFieldCatalog());
        List<FieldMapping> mappings = List.of(
                new FieldMapping("SKU", "productCode", "UPPER", null),
                new FieldMapping("Name", "productName", null, "Untitled"));
        var out = engine.applyMapping(java.util.Map.of("SKU", "abc", "Name", ""), mappings);
        assertEquals("ABC", out.get("productCode"));
        assertEquals("Untitled", out.get("productName"));
    }

    private static ImportSynonymRepository proxyRepo(List<ImportSynonym> synonyms) {
        return (ImportSynonymRepository) Proxy.newProxyInstance(
                ImportSynonymRepository.class.getClassLoader(),
                new Class<?>[] {ImportSynonymRepository.class},
                (proxy, method, args) -> {
                    if ("findForModule".equals(method.getName())) {
                        return synonyms;
                    }
                    Class<?> type = method.getReturnType();
                    if (type.equals(boolean.class)) return false;
                    if (type.equals(long.class) || type.equals(int.class)) return 0;
                    if (type.equals(Optional.class)) return Optional.empty();
                    if (List.class.isAssignableFrom(type)) return List.of();
                    return null;
                });
    }
}
