package com.examplatform.result.service;

import java.util.Arrays;
import java.util.List;

/**
 * Shared helper for comparing MCQ/MULTI_SELECT answers — used by both EvaluationService
 * (scoring/ranking) and DetailedResultService (the per-question review page) so they can't
 * silently drift into two different interpretations of the same correct_answer string.
 */
public final class AnswerMatching {

    private AnswerMatching() {}

    /**
     * MULTI_SELECT correct answers can be written either comma/pipe-separated ("A,C") or as
     * concatenated letters with no separator ("AC" — the format the Excel template and LLM
     * extraction pipeline both use). Handle both, so a no-separator answer doesn't silently
     * fail to match a student's individually-selected options, and option letters can be
     * looked up individually (e.g. for "show the correct answer" tags on a review page).
     */
    public static List<String> splitLetters(String correctAnswer) {
        if (correctAnswer == null) return List.of();
        if (correctAnswer.contains(",") || correctAnswer.contains("|")) {
            return Arrays.stream(correctAnswer.split("[,|]"))
                    .map(String::trim).map(String::toUpperCase).filter(s -> !s.isEmpty())
                    .sorted().toList();
        }
        String trimmed = correctAnswer.trim().toUpperCase();
        if (trimmed.length() > 1 && trimmed.chars().allMatch(c -> c >= 'A' && c <= 'Z')) {
            // Concatenated single-letter options, e.g. "AC" -> ["A", "C"]
            return trimmed.chars().mapToObj(c -> String.valueOf((char) c)).sorted().toList();
        }
        return trimmed.isEmpty() ? List.of() : List.of(trimmed);
    }
}
