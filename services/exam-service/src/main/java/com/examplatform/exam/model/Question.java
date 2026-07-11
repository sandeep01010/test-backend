package com.examplatform.exam.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Document(collection = "questions")
@CompoundIndexes({
    @CompoundIndex(name = "idx_type_subject_difficulty",
        def = "{'exam_type': 1, 'subject': 1, 'difficulty': 1}"),
    @CompoundIndex(name = "idx_subject_chapter_topic",
        def = "{'subject': 1, 'chapter': 1, 'topic': 1}")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Question {

    @Id
    private String id;

    @Field("exam_type")
    @Indexed
    private String examType;

    @Indexed
    private String subject;

    private String chapter;
    private String topic;

    @Field("difficulty")
    @Indexed
    private String difficulty;  // EASY, MEDIUM, HARD, VERY_HARD

    @Indexed
    private String type;  // MCQ, NUMERICAL, SUBJECTIVE, MULTI_SELECT, MATCH_THE_FOLLOWING

    @Field("question_text")
    private String questionText;

    @Field("question_html")
    private String questionHtml;

    @Field("question_image_urls")
    private List<String> questionImageUrls;

    private List<Option> options;

    @Field("correct_answer")
    private String correctAnswer;

    @Field("correct_answers")
    private List<String> correctAnswers;   // for MULTI_SELECT

    @Field("correct_range")
    private NumericalRange correctRange;   // for NUMERICAL

    @Field("match_list_left")
    private List<MatchItem> matchListLeft;   // for MATCH_THE_FOLLOWING — List-I

    @Field("match_list_right")
    private List<MatchItem> matchListRight;  // for MATCH_THE_FOLLOWING — List-II

    private String explanation;

    private double marks;

    @Field("negative_marks")
    private double negativeMarks;

    @Indexed
    private List<String> tags;

    /** Links this question to the specific uploaded exam (set on bulk upload). */
    @Field("exam_id")
    @Indexed
    private String examId;

    @Field("is_active")
    private boolean active = true;

    @Field("created_by")
    private String createdBy;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Option {
        @Field("id")          // force "id" not "_id" in MongoDB embedded doc
        private String id;
        private String text;
        @Field("image_url")
        private String imageUrl;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class NumericalRange {
        private double min;
        private double max;
    }

    /** One row of a MATCH_THE_FOLLOWING List-I/List-II table, e.g. label="P", text="|v|^2 is equal to". */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MatchItem {
        private String label;
        private String text;
    }
}
