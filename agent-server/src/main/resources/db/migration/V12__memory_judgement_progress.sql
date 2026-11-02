-- Judgement progress (M2b Task 3): how far JUDGE_MEMORY has processed each conversation.
ALTER TABLE memory_index_progress ADD judged_through_sequence BIGINT NOT NULL DEFAULT 0;
