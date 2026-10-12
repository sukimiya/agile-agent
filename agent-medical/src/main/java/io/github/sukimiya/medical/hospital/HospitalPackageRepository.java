package io.github.sukimiya.medical.hospital;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC access to hospital_capability_packages (V8). Mirrors the knowledge repository's
 * style: text-block SQL, bindNull for nullable scope columns, Readable-based mapping.
 * Registered as a @Bean by MedicalAutoConfiguration — not component-scanned.
 */
public class HospitalPackageRepository {

    private static final String COLUMNS = """
            id, hospital_code, hospital_name, routing_description, status, schema_version,
            content, source, valid_from, valid_until, scope_type, tenant_id, tenant_name,
            org_id, org_name, created_by, created_at
            """;

    private final DatabaseClient db;

    public HospitalPackageRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> insert(HospitalPackageRow row) {
        var query = db.sql("""
                INSERT INTO hospital_capability_packages
                    (id, hospital_code, hospital_name, routing_description, status, schema_version,
                     content, source, valid_from, valid_until, scope_type, tenant_id, tenant_name,
                     org_id, org_name, created_by)
                VALUES
                    (:id, :hospitalCode, :hospitalName, :routingDescription, :status, :schemaVersion,
                     :content, :source, :validFrom, :validUntil, :scopeType, :tenantId, :tenantName,
                     :orgId, :orgName, :createdBy)
                """)
                .bind("id", row.id())
                .bind("hospitalCode", row.hospitalCode())
                .bind("hospitalName", row.hospitalName())
                .bind("routingDescription", row.routingDescription())
                .bind("status", row.status().name())
                .bind("schemaVersion", row.schemaVersion())
                .bind("content", row.content())
                .bind("source", row.source())
                .bind("validFrom", row.validFrom())
                .bind("validUntil", row.validUntil())
                .bind("scopeType", row.scopeType().name())
                .bind("createdBy", row.createdBy());
        query = bindNullable(query, "tenantId", row.tenantId());
        query = bindNullable(query, "tenantName", row.tenantName());
        query = bindNullable(query, "orgId", row.orgId());
        query = bindNullable(query, "orgName", row.orgName());
        return query.fetch().rowsUpdated().then();
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec query, String name, String value) {
        return value == null ? query.bindNull(name, String.class) : query.bind(name, value);
    }

    public Mono<HospitalPackageRow> findById(String id) {
        return db.sql("SELECT " + COLUMNS + " FROM hospital_capability_packages WHERE id = :id")
                .bind("id", id)
                .map(HospitalPackageRepository::mapRow)
                .one();
    }

    public Mono<HospitalPackageRow> findByHospitalCode(String hospitalCode) {
        return db.sql("SELECT " + COLUMNS
                        + " FROM hospital_capability_packages WHERE hospital_code = :hospitalCode")
                .bind("hospitalCode", hospitalCode)
                .map(HospitalPackageRepository::mapRow)
                .one();
    }

    public Flux<HospitalPackageRow> findApproved() {
        return db.sql("SELECT " + COLUMNS + " FROM hospital_capability_packages "
                        + "WHERE status = 'APPROVED' ORDER BY hospital_code")
                .map(HospitalPackageRepository::mapRow)
                .all();
    }

    public Mono<Long> updateStatus(String id, HospitalPackageRow.Status status) {
        return db.sql("UPDATE hospital_capability_packages SET status = :status WHERE id = :id")
                .bind("status", status.name())
                .bind("id", id)
                .fetch().rowsUpdated();
    }

    private static HospitalPackageRow mapRow(Readable row) {
        return new HospitalPackageRow(
                row.get("id", String.class),
                row.get("hospital_code", String.class),
                row.get("hospital_name", String.class),
                row.get("routing_description", String.class),
                HospitalPackageRow.Status.valueOf(row.get("status", String.class)),
                row.get("schema_version", Integer.class),
                row.get("content", String.class),
                row.get("source", String.class),
                row.get("valid_from", LocalDateTime.class),
                row.get("valid_until", LocalDateTime.class),
                HospitalPackageRow.ScopeType.valueOf(row.get("scope_type", String.class)),
                row.get("tenant_id", String.class),
                row.get("tenant_name", String.class),
                row.get("org_id", String.class),
                row.get("org_name", String.class),
                row.get("created_by", String.class),
                row.get("created_at", LocalDateTime.class));
    }
}
