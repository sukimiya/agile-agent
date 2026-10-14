package io.github.sukimiya.medical.validation;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;

/**
 * Everything a gate may look at: the candidate row, its parsed content (null when the JSON is
 * unparsable — short-circuit rule 1 of the pipeline, spec §14 resolution 5), and the submitting
 * identity for authority checks. Gates must not reach for anything else.
 */
public record GateContext(
        HospitalPackageRow candidate,
        HospitalPackageContent parsedContent,
        IdentityContext submitter) {
}
