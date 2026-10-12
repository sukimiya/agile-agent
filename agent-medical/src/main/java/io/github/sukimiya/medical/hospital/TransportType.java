package io.github.sukimiya.medical.hospital;

/**
 * Transport channels of schema v1 (spec §4). This plan ships API and HUMAN executors;
 * EMAIL and PORTAL executors arrive with Plan 5 — until then their tools are not advertised.
 */
public enum TransportType { API, EMAIL, PORTAL, HUMAN }
