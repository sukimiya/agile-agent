package io.github.sukimiya.medical;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.medical.hospital.HospitalCapabilityProvider;
import io.github.sukimiya.medical.hospital.HospitalPackageAccessPolicy;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageValidator;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.lifecycle.HospitalPackageLifecycleService;
import io.github.sukimiya.medical.transport.HumanTaskTransportExecutor;
import io.github.sukimiya.medical.transport.HttpApiTransportExecutor;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.github.sukimiya.medical.validation.CapabilityGate;
import io.github.sukimiya.medical.validation.EndpointScreeningPolicy;
import io.github.sukimiya.medical.validation.GatePipeline;
import io.github.sukimiya.medical.validation.ProvenanceGate;
import io.github.sukimiya.medical.validation.SafetyGate;
import io.github.sukimiya.medical.validation.SchemaGate;
import io.github.sukimiya.medical.validation.SemanticGate;
import io.github.sukimiya.medical.validation.SimulationGate;
import io.github.sukimiya.medical.validation.TextSafetyScreen;
import io.r2dbc.spi.ConnectionFactory;

/**
 * Wires the medical HOSPITAL capability into any Boot application that provides an R2DBC
 * {@link ConnectionFactory}. Packaged per docs/capability-adapters.md: this module depends on
 * agile-agent-spi only, never on agent-server packages. The bean condition keeps applications
 * that exclude R2DBC auto-configuration (such as the mock OAuth2 end-to-end app) booting
 * without any medical beans. No Clock bean is exposed — the provider and the provenance gate
 * get system clocks inline so host applications stay free to define their own. The six-gate
 * pipeline order is fixed here explicitly (spec §14 resolution 5): SCHEMA, SEMANTIC,
 * PROVENANCE, CAPABILITY, SAFETY, SIMULATION.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration"})
@ConditionalOnBean(ConnectionFactory.class)
@EnableConfigurationProperties(MedicalHospitalProperties.class)
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
    public HospitalPackageValidator hospitalPackageValidator(HospitalPackageCodec codec) {
        return new HospitalPackageValidator(codec);
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
    public EndpointScreeningPolicy endpointScreeningPolicy(MedicalHospitalProperties properties) {
        return new EndpointScreeningPolicy(properties.getEndpointAllowlist());
    }

    @Bean
    public TextSafetyScreen textSafetyScreen() {
        return new TextSafetyScreen();
    }

    @Bean
    public HttpApiTransportExecutor httpApiTransportExecutor(WebClient.Builder webClientBuilder,
            EndpointScreeningPolicy endpointScreeningPolicy) {
        return new HttpApiTransportExecutor(webClientBuilder, endpointScreeningPolicy);
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

    @Bean
    public SchemaGate schemaGate(HospitalPackageValidator validator) {
        return new SchemaGate(validator);
    }

    @Bean
    public SemanticGate semanticGate() {
        return new SemanticGate();
    }

    @Bean
    public ProvenanceGate provenanceGate() {
        return new ProvenanceGate(Clock.systemDefaultZone());
    }

    @Bean
    public CapabilityGate capabilityGate(EndpointScreeningPolicy endpointScreeningPolicy) {
        return new CapabilityGate(endpointScreeningPolicy);
    }

    @Bean
    public SafetyGate safetyGate(TextSafetyScreen screen) {
        return new SafetyGate(screen);
    }

    @Bean
    public SimulationGate simulationGate(HospitalPackageAccessPolicy accessPolicy,
            HospitalPackageCodec codec, List<TransportExecutor> transportExecutors,
            MedicalHumanTaskRepository tasks) {
        return new SimulationGate(accessPolicy, codec, transportExecutors, tasks);
    }

    @Bean
    public GatePipeline gatePipeline(SchemaGate schemaGate, SemanticGate semanticGate,
            ProvenanceGate provenanceGate, CapabilityGate capabilityGate, SafetyGate safetyGate,
            SimulationGate simulationGate) {
        return new GatePipeline(List.of(schemaGate, semanticGate, provenanceGate, capabilityGate,
                safetyGate, simulationGate));
    }

    @Bean
    public HospitalPackageLifecycleService hospitalPackageLifecycleService(
            HospitalPackageRepository packages, GatePipeline gatePipeline,
            HospitalPackageCodec codec) {
        return new HospitalPackageLifecycleService(packages, gatePipeline, codec);
    }
}
