package io.github.sukimiya.medical;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.medical.hospital.HospitalCapabilityProvider;
import io.github.sukimiya.medical.hospital.HospitalPackageAccessPolicy;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.transport.HumanTaskTransportExecutor;
import io.github.sukimiya.medical.transport.HttpApiTransportExecutor;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.github.sukimiya.medical.validation.EndpointScreeningPolicy;
import io.r2dbc.spi.ConnectionFactory;

/**
 * Wires the medical HOSPITAL capability into any Boot application that provides an R2DBC
 * {@link ConnectionFactory}. Packaged per docs/capability-adapters.md: this module depends on
 * agile-agent-spi only, never on agent-server packages. The bean condition keeps applications
 * that exclude R2DBC auto-configuration (such as the mock OAuth2 end-to-end app) booting
 * without any medical beans. No Clock bean is exposed — the provider gets a system clock
 * inline so host applications stay free to define their own.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration"})
@ConditionalOnBean(ConnectionFactory.class)
public class MedicalAutoConfiguration {

    @Bean
    public HospitalPackageCodec hospitalPackageCodec() {
        return new HospitalPackageCodec();
    }

    @Bean
    public HospitalPackageAccessPolicy hospitalPackageAccessPolicy() {
        return new HospitalPackageAccessPolicy();
    }

    @Bean
    public HospitalPackageRepository hospitalPackageRepository(DatabaseClient databaseClient) {
        return new HospitalPackageRepository(databaseClient);
    }

    @Bean
    public MedicalHumanTaskRepository medicalHumanTaskRepository(DatabaseClient databaseClient) {
        return new MedicalHumanTaskRepository(databaseClient);
    }

    @Bean
    public HttpApiTransportExecutor httpApiTransportExecutor(WebClient.Builder webClientBuilder) {
        // Interim default-deny screening; Task 12 wires MedicalHospitalProperties into the policy.
        return new HttpApiTransportExecutor(webClientBuilder, new EndpointScreeningPolicy(Set.of()));
    }

    @Bean
    public HumanTaskTransportExecutor humanTaskTransportExecutor(MedicalHumanTaskRepository tasks) {
        return new HumanTaskTransportExecutor(tasks);
    }

    @Bean
    public HospitalCapabilityProvider hospitalCapabilityProvider(
            HospitalPackageRepository packages, HospitalPackageAccessPolicy accessPolicy,
            HospitalPackageCodec codec, List<TransportExecutor> transportExecutors) {
        return new HospitalCapabilityProvider(packages, accessPolicy, codec, transportExecutors,
                Clock.systemDefaultZone());
    }
}
