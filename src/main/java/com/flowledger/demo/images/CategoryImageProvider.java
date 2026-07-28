package com.flowledger.demo.images;

import java.util.List;

/** Resolves classpath (or future ZIP) demo images by category slug. Drop files under demo-images/ without code changes. */
public interface CategoryImageProvider {
    /** Relative folder under demo-images/, e.g. grocery/milk */
    List<DemoImageResource> imagesFor(String categoryPath);

    DemoImageResource defaultImage();
}
