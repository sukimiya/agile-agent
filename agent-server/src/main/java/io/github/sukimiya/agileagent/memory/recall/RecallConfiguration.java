package io.github.sukimiya.agileagent.memory.recall;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

/** Chooses the full-text implementation for the configured database (MySQL ngram FULLTEXT, else LIKE). */
@Configuration(proxyBeanMethods = false)
public class RecallConfiguration {

    @Bean
    FullTextSearch fullTextSearch(DatabaseClient db) {
        return FullTextSearch.forDatabase(db);
    }
}
