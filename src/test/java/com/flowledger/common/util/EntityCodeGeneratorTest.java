package com.flowledger.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EntityCodeGeneratorTest {
    @Test
    void buildsCodeFromNameWithSalt() {
        String code = EntityCodeGenerator.fromName("YRV Solutions");
        assertTrue(code.startsWith("YRV-SOLUT-") || code.startsWith("YRV-SOLUTIONS-") || code.contains("YRV"));
        assertTrue(code.matches("^[A-Z0-9]+(-[A-Z0-9]+)+$"));
    }

    @Test
    void uniqueFromNameAvoidsCollisions() {
        Set<String> taken = new HashSet<>();
        String first = EntityCodeGenerator.uniqueFromName("Main Warehouse", "WH", taken::contains);
        taken.add(first);
        String second = EntityCodeGenerator.uniqueFromName("Main Warehouse", "WH", taken::contains);
        assertFalse(first.equals(second));
        assertTrue(second.startsWith("WH-"));
    }
}
