package com.flowledger.demo.catalog;

import java.util.List;

public interface CatalogStrategy {
    CatalogStrategyId id();

    List<String> categoryNames();

    List<String> brandNames();

    /** Product name prefixes cycled while generating. */
    List<String> productNameSeeds();

    boolean preferBatchExpiry();

    boolean preferSerial();

    boolean preferVariants();
}
