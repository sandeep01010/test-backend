// MongoDB Schema Definitions and Index Creation
// Run this in MongoDB shell or Compass

// ============================================================
// Database: exam_platform
// ============================================================

use('exam_platform');

// ============================================================
// COLLECTION: questions
// ============================================================
db.createCollection('questions', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['exam_type', 'subject', 'type', 'question_text', 'marks'],
      properties: {
        exam_type: {
          bsonType: 'string',
          enum: ['JEE_MAIN', 'JEE_ADVANCED', 'NEET', 'CUET', 'GATE', 'CAT', 'CUSTOM']
        },
        subject: { bsonType: 'string' },
        chapter: { bsonType: 'string' },
        topic: { bsonType: 'string' },
        difficulty: {
          bsonType: 'string',
          enum: ['EASY', 'MEDIUM', 'HARD', 'VERY_HARD']
        },
        type: {
          bsonType: 'string',
          enum: ['MCQ', 'NUMERICAL', 'SUBJECTIVE', 'MULTI_SELECT', 'MATCH_THE_FOLLOWING', 'ASSERTION_REASON']
        },
        question_text: { bsonType: 'string' },
        question_html: { bsonType: 'string' },
        question_image_urls: {
          bsonType: 'array',
          items: { bsonType: 'string' }
        },
        options: {
          bsonType: 'array',
          items: {
            bsonType: 'object',
            required: ['id', 'text'],
            properties: {
              id: { bsonType: 'string' },
              text: { bsonType: 'string' },
              image_url: { bsonType: ['string', 'null'] }
            }
          }
        },
        correct_answer: { bsonType: ['string', 'null'] },
        correct_answers: {
          bsonType: 'array',
          items: { bsonType: 'string' }
        },
        correct_range: {
          bsonType: ['object', 'null'],
          properties: {
            min: { bsonType: 'double' },
            max: { bsonType: 'double' }
          }
        },
        explanation: { bsonType: 'string' },
        marks: { bsonType: 'double' },
        negative_marks: { bsonType: 'double' },
        tags: {
          bsonType: 'array',
          items: { bsonType: 'string' }
        },
        is_active: { bsonType: 'bool' },
        created_by: { bsonType: 'string' },
        created_at: { bsonType: 'date' },
        updated_at: { bsonType: 'date' }
      }
    }
  }
});

// Indexes on questions
db.questions.createIndex({ exam_type: 1, subject: 1, difficulty: 1 }, { name: 'idx_type_subject_difficulty' });
db.questions.createIndex({ subject: 1, chapter: 1, topic: 1 }, { name: 'idx_subject_chapter_topic' });
db.questions.createIndex({ tags: 1 }, { name: 'idx_tags' });
db.questions.createIndex({ type: 1 }, { name: 'idx_question_type' });
db.questions.createIndex({ is_active: 1 }, { name: 'idx_active' });
db.questions.createIndex({ created_at: -1 }, { name: 'idx_created_at' });
// Text index for full-text search on question content
db.questions.createIndex(
  { question_text: 'text', explanation: 'text', tags: 'text' },
  { name: 'idx_text_search', weights: { question_text: 10, tags: 5, explanation: 1 } }
);

// ============================================================
// COLLECTION: exam_papers
// Paper generated per student: maps question_position → question_id
// ============================================================
db.createCollection('exam_papers', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['session_id', 'student_id', 'exam_id', 'questions'],
      properties: {
        session_id: { bsonType: 'string' },
        student_id: { bsonType: 'string' },
        exam_id: { bsonType: 'string' },
        questions: {
          bsonType: 'array',
          items: {
            bsonType: 'object',
            properties: {
              position: { bsonType: 'int' },
              question_id: { bsonType: 'string' },
              section_id: { bsonType: 'string' },
              section_name: { bsonType: 'string' }
            }
          }
        },
        generated_at: { bsonType: 'date' }
      }
    }
  }
});

db.exam_papers.createIndex({ session_id: 1 }, { unique: true, name: 'idx_session_unique' });
db.exam_papers.createIndex({ student_id: 1, exam_id: 1 }, { name: 'idx_student_exam' });
// TTL: auto-delete 30 days after exam
db.exam_papers.createIndex({ generated_at: 1 }, { expireAfterSeconds: 2592000, name: 'ttl_papers' });

// ============================================================
// COLLECTION: answer_snapshots
// Latest answer state per session (upserted every 15 seconds)
// ============================================================
db.createCollection('answer_snapshots');

db.answer_snapshots.createIndex(
  { session_id: 1 },
  { unique: true, name: 'idx_session_unique' }
);
db.answer_snapshots.createIndex({ student_id: 1, exam_id: 1 }, { name: 'idx_student_exam' });
db.answer_snapshots.createIndex({ exam_id: 1, is_submitted: 1 }, { name: 'idx_exam_submitted' });
// TTL: expire 60 days after last update
db.answer_snapshots.createIndex(
  { updated_at: 1 },
  { expireAfterSeconds: 5184000, name: 'ttl_snapshots' }
);

// ============================================================
// COLLECTION: anti_cheat_events
// ============================================================
db.createCollection('anti_cheat_events');

db.anti_cheat_events.createIndex({ session_id: 1 }, { name: 'idx_session' });
db.anti_cheat_events.createIndex({ student_id: 1, exam_id: 1 }, { name: 'idx_student_exam' });
db.anti_cheat_events.createIndex({ flagged: 1, exam_id: 1 }, { name: 'idx_flagged' });
db.anti_cheat_events.createIndex({ risk_score: -1 }, { name: 'idx_risk_score' });

// ============================================================
// COLLECTION: result_analytics
// Per-exam detailed analytics (question-level performance)
// ============================================================
db.createCollection('result_analytics');

db.result_analytics.createIndex({ exam_id: 1 }, { unique: true, name: 'idx_exam_unique' });
db.result_analytics.createIndex({ exam_id: 1, subject: 1 }, { name: 'idx_exam_subject' });

// ============================================================
// COLLECTION: student_performance
// Historical performance data per student across exams
// ============================================================
db.createCollection('student_performance');

db.student_performance.createIndex({ student_id: 1 }, { name: 'idx_student' });
db.student_performance.createIndex({ student_id: 1, exam_type: 1 }, { name: 'idx_student_type' });
db.student_performance.createIndex({ student_id: 1, subject: 1 }, { name: 'idx_student_subject' });

print('MongoDB schema setup complete.');
