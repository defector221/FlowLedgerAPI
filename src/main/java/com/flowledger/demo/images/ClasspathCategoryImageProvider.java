package com.flowledger.demo.images;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class ClasspathCategoryImageProvider implements CategoryImageProvider {
    private static final Logger log = LoggerFactory.getLogger(ClasspathCategoryImageProvider.class);
    private static final String ROOT = "demo-images/";

    private final Map<String, List<DemoImageResource>> byFolder = new ConcurrentHashMap<>();
    private DemoImageResource fallback;

    @PostConstruct
    void index() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:" + ROOT + "**/*.{png,jpg,jpeg,PNG,JPG,JPEG}");
            for (Resource resource : resources) {
                String path = toRelativePath(resource);
                if (path == null) {
                    continue;
                }
                String folder = folderOf(path);
                String filename = path.substring(path.lastIndexOf('/') + 1);
                String ct = filename.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
                DemoImageResource img = new DemoImageResource(ROOT + path, filename, ct);
                byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(img);
                if ("defaults".equals(folder)
                        && filename.toLowerCase(Locale.ROOT).contains("no-image")) {
                    fallback = img;
                }
            }
            if (fallback == null) {
                List<DemoImageResource> defaults = byFolder.getOrDefault("defaults", List.of());
                if (!defaults.isEmpty()) {
                    fallback = defaults.get(0);
                }
            }
            log.info(
                    "Indexed {} demo image folders ({} files)",
                    byFolder.size(),
                    byFolder.values().stream().mapToInt(List::size).sum());
        } catch (IOException e) {
            log.warn("Failed to index demo-images: {}", e.getMessage());
        }
    }

    @Override
    public List<DemoImageResource> imagesFor(String categoryPath) {
        if (categoryPath == null || categoryPath.isBlank()) {
            return List.of();
        }
        String key = categoryPath.replace('\\', '/').replaceAll("^/+|/+$", "");
        List<DemoImageResource> exact = byFolder.get(key);
        if (exact != null && !exact.isEmpty()) {
            return List.copyOf(exact);
        }
        // parent folder fallback e.g. grocery
        int slash = key.indexOf('/');
        if (slash > 0) {
            List<DemoImageResource> parent = byFolder.get(key.substring(0, slash));
            if (parent != null && !parent.isEmpty()) {
                return List.copyOf(parent);
            }
        }
        return List.of();
    }

    @Override
    public DemoImageResource defaultImage() {
        return fallback;
    }

    private static String folderOf(String relativePath) {
        int idx = relativePath.lastIndexOf('/');
        return idx < 0 ? "" : relativePath.substring(0, idx);
    }

    private static String toRelativePath(Resource resource) throws IOException {
        URL url = resource.getURL();
        String full = url.toString();
        int idx = full.indexOf(ROOT);
        if (idx < 0) {
            return null;
        }
        String rel = full.substring(idx + ROOT.length());
        // strip jar ! noise already handled; remove query
        int q = rel.indexOf('?');
        if (q >= 0) {
            rel = rel.substring(0, q);
        }
        return rel;
    }
}
