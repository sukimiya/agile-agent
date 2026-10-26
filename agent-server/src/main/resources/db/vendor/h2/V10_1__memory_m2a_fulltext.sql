-- H2 counterpart of db/vendor/mysql/V10_1__memory_m2a_fulltext.sql. H2 has no ngram FULLTEXT and cannot index
-- MEDIUMTEXT (CLOB) columns, so recall uses LIKE behind the full-text search interface and this file only keeps
-- the version history identical on both databases. The scope indexes it would need are in V10.
SELECT 1;
