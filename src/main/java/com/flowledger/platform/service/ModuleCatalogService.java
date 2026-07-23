package com.flowledger.platform.service;

import com.flowledger.platform.dto.PlatformDtos.ModuleFeatureResponse;
import com.flowledger.platform.dto.PlatformDtos.ModuleResponse;
import com.flowledger.platform.entity.ModuleDependency;
import com.flowledger.platform.entity.ModuleFeature;
import com.flowledger.platform.entity.PlatformModule;
import com.flowledger.platform.repository.ModuleDependencyRepository;
import com.flowledger.platform.repository.ModuleFeatureRepository;
import com.flowledger.platform.repository.PlatformModuleRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModuleCatalogService {
    private final PlatformModuleRepository modules;
    private final ModuleDependencyRepository dependencies;
    private final ModuleFeatureRepository features;

    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public ModuleCatalogService(
            PlatformModuleRepository modules,
            ModuleDependencyRepository dependencies,
            ModuleFeatureRepository features) {
        this.modules = modules;
        this.dependencies = dependencies;
        this.features = features;
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> listModules() {
        return cached("modules", () -> {
            Map<String, List<String>> deps = dependencies.findAll().stream()
                    .collect(Collectors.groupingBy(
                            ModuleDependency::getModuleCode,
                            Collectors.mapping(ModuleDependency::getDependsOnCode, Collectors.toList())));
            return modules.findAllByOrderByCategoryAscDisplayNameAsc().stream()
                    .map(m -> toResponse(m, deps.getOrDefault(m.getCode(), List.of())))
                    .toList();
        });
    }

    @Transactional(readOnly = true)
    public List<ModuleFeatureResponse> listFeatures(String moduleCode) {
        String key = "features:" + moduleCode;
        return cached(key, () -> features.findByModuleCodeOrderByFeatureCodeAsc(moduleCode).stream()
                .map(this::toFeature)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ModuleFeatureResponse> listAllFeatures() {
        return cached("features:all", () -> features.findAllByOrderByModuleCodeAscFeatureCodeAsc().stream()
                .map(this::toFeature)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<String> dependenciesOf(String moduleCode) {
        return dependencies.findByModuleCode(moduleCode).stream()
                .map(ModuleDependency::getDependsOnCode)
                .toList();
    }

    public void evictCatalogCache() {
        cache.clear();
    }

    private ModuleResponse toResponse(PlatformModule m, List<String> deps) {
        return new ModuleResponse(
                m.getCode(),
                m.getDisplayName(),
                m.getDescription(),
                m.getIcon(),
                m.getCategory(),
                m.getVersion(),
                m.isCore(),
                m.isEnabledByDefault(),
                m.getStatus(),
                deps);
    }

    private ModuleFeatureResponse toFeature(ModuleFeature f) {
        return new ModuleFeatureResponse(
                f.getModuleCode(),
                f.getFeatureCode(),
                f.getDisplayName(),
                f.getDescription(),
                f.isEnabledByDefault(),
                f.getStatus());
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, java.util.function.Supplier<T> loader) {
        return (T) cache.computeIfAbsent(key, k -> loader.get());
    }
}
