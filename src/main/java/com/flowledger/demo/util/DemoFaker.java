package com.flowledger.demo.util;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

@Component
public final class DemoFaker {
    private final Faker faker = new Faker(new Locale("en", "IN"));

    public Faker faker() {
        return faker;
    }

    public String phone() {
        return "9" + faker.number().digits(9);
    }

    public String email(String localPart) {
        return localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".") + "@demo.flowledger.local";
    }

    /** Valid-looking GSTIN (format only). */
    public String gstin() {
        String state = String.format("%02d", ThreadLocalRandom.current().nextInt(1, 38));
        String pan = faker.regexify("[A-Z]{5}[0-9]{4}[A-Z]");
        return state + pan + "1Z" + faker.regexify("[0-9A-Z]");
    }

    public String ean13() {
        StringBuilder sb = new StringBuilder("890");
        for (int i = 0; i < 9; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = sb.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        int check = (10 - (sum % 10)) % 10;
        sb.append(check);
        return sb.toString();
    }

    public String sku(String prefix, int seq) {
        return prefix + "-" + String.format("%06d", seq);
    }

    public String batchNumber() {
        return "LOT-" + faker.number().digits(8);
    }

    public String serialNumber() {
        return "SN" + faker.number().digits(12);
    }
}
