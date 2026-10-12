package io.github.sukimiya.medical;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import io.r2dbc.spi.ConnectionFactory;

/**
 * Wires the medical HOSPITAL capability into any Boot application that provides an R2DBC
 * {@link ConnectionFactory}. Packaged per docs/capability-adapters.md: this module depends on
 * agile-agent-spi only, never on agent-server packages. The bean condition keeps applications
 * that exclude R2DBC auto-configuration (such as the mock OAuth2 end-to-end app) booting
 * without any medical beans. Bean declarations are added in Task 7.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration"})
@ConditionalOnBean(ConnectionFactory.class)
public class MedicalAutoConfiguration {
}
