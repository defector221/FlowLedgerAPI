package com.flowledger.migration.parser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class ExcelParser {
    private final DataFormatter formatter = new DataFormatter();

    public List<ParsedSheet> parseAll(InputStream in) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            List<ParsedSheet> sheets = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheets.add(parseSheet(workbook.getSheetAt(i)));
            }
            return sheets;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Excel: " + e.getMessage(), e);
        }
    }

    public ParsedSheet parsePrimary(InputStream in) {
        List<ParsedSheet> sheets = parseAll(in);
        return sheets.isEmpty() ? ParsedSheet.empty("Sheet1") : sheets.get(0);
    }

    private ParsedSheet parseSheet(Sheet sheet) {
        if (sheet.getPhysicalNumberOfRows() == 0) {
            return ParsedSheet.empty(sheet.getSheetName());
        }
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            return ParsedSheet.empty(sheet.getSheetName());
        }
        List<String> columns = new ArrayList<>();
        short lastCell = headerRow.getLastCellNum();
        for (int c = 0; c < lastCell; c++) {
            columns.add(cellValue(headerRow.getCell(c)).trim());
        }
        // drop trailing empty headers
        while (!columns.isEmpty() && columns.get(columns.size() - 1).isBlank()) {
            columns.remove(columns.size() - 1);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isEmptyRow(row, columns.size())) continue;
            Map<String, String> map = new LinkedHashMap<>();
            boolean any = false;
            for (int c = 0; c < columns.size(); c++) {
                String val = cellValue(row.getCell(c)).trim();
                if (!val.isEmpty()) any = true;
                map.put(columns.get(c), val);
            }
            if (any) rows.add(map);
        }
        return new ParsedSheet(sheet.getSheetName(), columns, rows);
    }

    private boolean isEmptyRow(Row row, int cols) {
        for (int c = 0; c < cols; c++) {
            if (!cellValue(row.getCell(c)).isBlank()) return false;
        }
        return true;
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        return formatter.formatCellValue(cell);
    }
}
