package io.github.sukimiya.agileagent.memory;

/**
 * Provenance of a stored message or material. M1 only tags it; M2b adds semantic judgement. In particular
 * {@link #USER_INPUT} only means "arrived in a user-role message": pasted articles, quotes or hypotheticals in
 * it are not the user's own stated preferences ("用户亲口说" requires explicit personal expression or
 * confirmation, see spec 7.4). Content of any source never gains instruction or tool permissions.
 */
public enum SourceType {
    USER_INPUT,
    ASSISTANT,
    TOOL_RESULT,
    KNOWLEDGE_DOC,
    WEB,
    MODEL_SUMMARY
}
