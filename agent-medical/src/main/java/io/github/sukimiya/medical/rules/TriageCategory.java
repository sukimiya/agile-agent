package io.github.sukimiya.medical.rules;

/**
 * The code-layer curated triage vocabulary (Plan 4 spec §5): submitTriage's
 * triageCategory enum. The LLM picks a value; the rule engine — never the LLM — decides
 * what it means (main spec §6).
 */
public enum TriageCategory { EMERGENCY, CRITICAL, ROUTINE }
