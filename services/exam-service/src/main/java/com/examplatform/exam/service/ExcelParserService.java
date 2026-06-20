package com.examplatform.exam.service;

import com.examplatform.exam.dto.ExcelUploadRequest;
import com.examplatform.exam.model.Question;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Parses the standard ExamForge Excel template into Question + ExcelUploadRequest.
 *
 * Sheet 1 (EXAM_INFO): key-value metadata rows
 * Sheet 2 (QUESTIONS): one question per row, header on row 0
 */
@Slf4j
@Service
public class ExcelParserService {

    // ── Column names expected in the QUESTIONS sheet ──────────────────────
    private static final String COL_Q_NO            = "Q_NO";
    private static final String COL_SECTION         = "SECTION";
    private static final String COL_TYPE            = "TYPE";
    private static final String COL_QUESTION_TEXT   = "QUESTION_TEXT";
    private static final String COL_QUESTION_IMAGE  = "QUESTION_IMAGE_URL";
    private static final String COL_OPTION_A        = "OPTION_A";
    private static final String COL_OPTION_A_IMG    = "OPTION_A_IMAGE";
    private static final String COL_OPTION_B        = "OPTION_B";
    private static final String COL_OPTION_B_IMG    = "OPTION_B_IMAGE";
    private static final String COL_OPTION_C        = "OPTION_C";
    private static final String COL_OPTION_C_IMG    = "OPTION_C_IMAGE";
    private static final String COL_OPTION_D        = "OPTION_D";
    private static final String COL_OPTION_D_IMG    = "OPTION_D_IMAGE";
    private static final String COL_CORRECT_ANSWER  = "CORRECT_ANSWER";
    private static final String COL_MARKS_CORRECT   = "MARKS_CORRECT";
    private static final String COL_MARKS_NEGATIVE  = "MARKS_NEGATIVE";
    private static final String COL_DIFFICULTY      = "DIFFICULTY";
    private static final String COL_TOPIC           = "TOPIC";
    private static final String COL_SUBTOPIC        = "SUBTOPIC";
    private static final String COL_SOLUTION_TEXT   = "SOLUTION_TEXT";
    private static final String COL_SOLUTION_IMAGE  = "SOLUTION_IMAGE_URL";
    private static final String COL_TAGS            = "TAGS";

    public ParseResult parse(MultipartFile file) throws IOException {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {

            // ── Sheet 1: EXAM_INFO ─────────────────────────────────────────
            Sheet infoSheet = wb.getSheet("EXAM_INFO");
            if (infoSheet == null) infoSheet = wb.getSheetAt(0);
            ExcelUploadRequest meta = parseExamInfo(infoSheet);

            // ── Sheet 2: QUESTIONS ─────────────────────────────────────────
            Sheet qSheet = wb.getSheet("QUESTIONS");
            if (qSheet == null) qSheet = wb.getSheetAt(1);
            List<Question> questions = parseQuestions(qSheet, meta);

            log.info("Parsed {} questions from Excel for exam: {}", questions.size(), meta.getTitle());
            return new ParseResult(meta, questions);
        }
    }

    // ── EXAM_INFO sheet: rows are key | value ──────────────────────────────
    private ExcelUploadRequest parseExamInfo(Sheet sheet) {
        Map<String, String> info = new HashMap<>();
        for (Row row : sheet) {
            if (row == null) continue;
            Cell keyCell = row.getCell(0);
            Cell valCell = row.getCell(1);
            if (keyCell == null || valCell == null) continue;
            String key = cellStr(keyCell).trim().toUpperCase();
            String val = cellStr(valCell).trim();
            if (!key.isEmpty()) info.put(key, val);
        }
        return ExcelUploadRequest.builder()
                .categoryCode(info.getOrDefault("CATEGORY", ""))
                .testType(info.getOrDefault("TEST_TYPE", "FULL_MOCK"))
                .title(info.getOrDefault("TITLE", "Untitled Exam"))
                .description(info.getOrDefault("DESCRIPTION", ""))
                .durationMins(parseInt(info.getOrDefault("DURATION_MINS", "180")))
                .totalMarks(parseInt(info.getOrDefault("TOTAL_MARKS", "300")))
                .instructions(info.getOrDefault("INSTRUCTIONS", ""))
                .build();
    }

    // ── QUESTIONS sheet: row 0 = header, rows 1..N = questions ────────────
    private List<Question> parseQuestions(Sheet sheet, ExcelUploadRequest meta) {
        Iterator<Row> rows = sheet.rowIterator();
        if (!rows.hasNext()) return Collections.emptyList();

        // Build column index map from header row
        Row header = rows.next();
        Map<String, Integer> colIdx = new HashMap<>();
        for (Cell cell : header) {
            colIdx.put(cellStr(cell).trim().toUpperCase(), cell.getColumnIndex());
        }

        List<Question> questions = new ArrayList<>();
        while (rows.hasNext()) {
            Row row = rows.next();
            if (isRowEmpty(row)) continue;
            try {
                questions.add(rowToQuestion(row, colIdx, meta));
            } catch (Exception e) {
                log.warn("Skipping row {}: {}", row.getRowNum() + 1, e.getMessage());
            }
        }
        return questions;
    }

    private Question rowToQuestion(Row row, Map<String, Integer> col, ExcelUploadRequest meta) {
        String type    = str(row, col, COL_TYPE, "MCQ").toUpperCase();
        String section = str(row, col, COL_SECTION, "GENERAL").toUpperCase();
        String correctRaw = str(row, col, COL_CORRECT_ANSWER, "");
        double marksCorrect  = dbl(row, col, COL_MARKS_CORRECT, 4.0);
        double marksNegative = dbl(row, col, COL_MARKS_NEGATIVE, type.equals("NUMERICAL") ? 0.0 : -1.0);

        // Build options for MCQ
        List<Question.Option> options = null;
        if ("MCQ".equals(type) || "MULTI_SELECT".equals(type)) {
            options = buildOptions(row, col);
        }

        // Numerical range: correct answer can be "42" or "40.5-43.5"
        Question.NumericalRange numericalRange = null;
        if ("NUMERICAL".equals(type) && !correctRaw.isBlank()) {
            if (correctRaw.contains("-")) {
                String[] parts = correctRaw.split("-");
                numericalRange = new Question.NumericalRange(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()));
            } else {
                double v = Double.parseDouble(correctRaw.trim());
                numericalRange = new Question.NumericalRange(v, v);
            }
        }

        // Tags from TAGS column (comma-separated) + auto-tag from section/topic
        String tagsRaw = str(row, col, COL_TAGS, "");
        List<String> tags = new ArrayList<>();
        if (!tagsRaw.isBlank()) {
            Arrays.stream(tagsRaw.split(",")).map(String::trim).filter(t -> !t.isEmpty()).forEach(tags::add);
        }
        tags.add(section);
        tags.add(meta.getCategoryCode());

        // Question image
        String qImg = str(row, col, COL_QUESTION_IMAGE, "");
        List<String> qImgs = qImg.isBlank() ? null : List.of(qImg);

        return Question.builder()
                .examType(meta.getCategoryCode())
                .subject(section)
                .chapter(str(row, col, COL_TOPIC, ""))
                .topic(str(row, col, COL_SUBTOPIC, ""))
                .difficulty(str(row, col, COL_DIFFICULTY, "MEDIUM").toUpperCase())
                .type(type)
                .questionText(str(row, col, COL_QUESTION_TEXT, ""))
                .questionImageUrls(qImgs)
                .options(options)
                .correctAnswer(correctRaw.isBlank() ? null :
                        "MCQ".equals(type) ? correctRaw.toUpperCase() : correctRaw.trim())
                .correctRange(numericalRange)
                .explanation(str(row, col, COL_SOLUTION_TEXT, ""))
                .marks(marksCorrect)
                .negativeMarks(marksNegative)
                .tags(tags)
                .active(true)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
    }

    private List<Question.Option> buildOptions(Row row, Map<String, Integer> col) {
        List<Question.Option> opts = new ArrayList<>();
        String[][] defs = {
            {"A", COL_OPTION_A, COL_OPTION_A_IMG},
            {"B", COL_OPTION_B, COL_OPTION_B_IMG},
            {"C", COL_OPTION_C, COL_OPTION_C_IMG},
            {"D", COL_OPTION_D, COL_OPTION_D_IMG},
        };
        for (String[] d : defs) {
            String text = str(row, col, d[1], "");
            String img  = str(row, col, d[2], "");
            if (!text.isBlank() || !img.isBlank()) {
                opts.add(Question.Option.builder()
                        .id(d[0])
                        .text(text)
                        .imageUrl(img.isBlank() ? null : img)
                        .build());
            }
        }
        return opts.isEmpty() ? null : opts;
    }

    // ── helpers ────────────────────────────────────────────────────────────
    private String str(Row row, Map<String, Integer> col, String name, String def) {
        Integer idx = col.get(name);
        if (idx == null) return def;
        Cell c = row.getCell(idx);
        if (c == null) return def;
        String v = cellStr(c).trim();
        return v.isEmpty() ? def : v;
    }

    private double dbl(Row row, Map<String, Integer> col, String name, double def) {
        String v = str(row, col, name, "");
        if (v.isEmpty()) return def;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return def; }
    }

    private String cellStr(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (Cell c : row) {
            if (c != null && c.getCellType() != CellType.BLANK && !cellStr(c).isBlank()) return false;
        }
        return true;
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    public record ParseResult(ExcelUploadRequest meta, List<Question> questions) {}
}
