-- MySQL only (D7/D8): ngram FULLTEXT indexes for recall over Chinese and mixed text. The token size is the
-- server setting ngram_token_size (default 2, the chosen value); changing it requires rebuilding these indexes.
ALTER TABLE memory_chunks ADD FULLTEXT INDEX ft_memory_chunks_content (content) WITH PARSER ngram;
ALTER TABLE memory_summaries ADD FULLTEXT INDEX ft_memory_summaries_content (content) WITH PARSER ngram;
