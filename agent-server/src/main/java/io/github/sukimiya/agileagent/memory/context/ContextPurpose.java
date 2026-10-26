package io.github.sukimiya.agileagent.memory.context;

/** Which model call a context is assembled for; each call is budgeted on its own. */
public enum ContextPurpose {
    /** Choosing capabilities for the question. */
    ROUTING,
    /** A domain agent's tool loop. */
    CAPABILITY,
    /** Combining capability results into the answer. */
    AGGREGATE,
    /** Answering without capabilities. */
    DIRECT_ANSWER
}
