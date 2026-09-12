package dev.conner.hometown.commerce;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable resident evidence copied during the existing settlement resident pass. */
public record CommerceResidentFact(UUID uuid, boolean baby, ResourceLocation professionId) {
    public CommerceResidentFact {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(professionId);
    }
}
