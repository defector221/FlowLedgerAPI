package com.flowledger.migration.parser;

import com.flowledger.migration.domain.ImportSourceType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ParserEngine {
    private final CsvParser csvParser;
    private final ExcelParser excelParser;

    public ParserEngine(CsvParser csvParser, ExcelParser excelParser) {
        this.csvParser = csvParser;
        this.excelParser = excelParser;
    }

    public ImportSourceType detectSource(String fileName) {
        if (fileName == null) return ImportSourceType.GENERIC;
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return ImportSourceType.CSV;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return ImportSourceType.XLSX;
        if (lower.endsWith(".json")) return ImportSourceType.JSON;
        if (lower.endsWith(".xml")) return ImportSourceType.XML;
        return ImportSourceType.GENERIC;
    }

    public ParsedSheet parsePrimary(byte[] bytes, String fileName) {
        ImportSourceType type = detectSource(fileName);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return switch (type) {
                case CSV, GENERIC -> csvParser.parse(in, "data");
                case XLSX -> excelParser.parsePrimary(in);
                default -> throw new IllegalArgumentException("Unsupported file type for Phase 1: " + type);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse file: " + e.getMessage(), e);
        }
    }

    public List<ParsedSheet> parseAll(byte[] bytes, String fileName) {
        ImportSourceType type = detectSource(fileName);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return switch (type) {
                case CSV, GENERIC -> List.of(csvParser.parse(in, "data"));
                case XLSX -> excelParser.parseAll(in);
                default -> throw new IllegalArgumentException("Unsupported file type for Phase 1: " + type);
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse file: " + e.getMessage(), e);
        }
    }
}
