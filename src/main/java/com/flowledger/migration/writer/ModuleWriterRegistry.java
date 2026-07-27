package com.flowledger.migration.writer;

import com.flowledger.migration.domain.ImportModule;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ModuleWriterRegistry {
    private final Map<ImportModule, ModuleWriter> writers = new EnumMap<>(ImportModule.class);

    public ModuleWriterRegistry(List<ModuleWriter> writerList) {
        for (ModuleWriter w : writerList) {
            writers.put(w.module(), w);
        }
    }

    public ModuleWriter require(ImportModule module) {
        ModuleWriter w = writers.get(module);
        if (w == null) {
            throw new IllegalArgumentException("No writer registered for module " + module);
        }
        return w;
    }

    public boolean supports(ImportModule module) {
        return writers.containsKey(module);
    }
}
