package io.github.sukimiya.agileagent.memory.items;

/**
 * A checked relation to write with a memory item (relation write path T2): {@code from —relation→ to}. An endpoint of
 * type {@link EntityGraphRepository#SELF_TYPE} is the user-self entity (its name is ignored).
 */
public record RelationSpec(Endpoint from, String relation, Endpoint to) {

    public record Endpoint(String name, String type) {
        public boolean self() {
            return EntityGraphRepository.SELF_TYPE.equals(type);
        }

        /** Identity of the endpoint within one owner's graph. */
        public String key() {
            return self() ? EntityGraphRepository.SELF_KEY : EntityGraphRepository.normalize(name);
        }
    }

    public static RelationSpec of(String fromName, String fromType, String relation, String toName, String toType) {
        return new RelationSpec(new Endpoint(fromName, fromType), relation, new Endpoint(toName, toType));
    }

    /** Identity of the edge (same as the unique edge of an item). */
    public String edgeKey() {
        return from.key() + '\u0000' + relation + '\u0000' + to.key();
    }
}
