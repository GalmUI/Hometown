package dev.conner.hometown.component;

import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record SettlementIdComponent(UUID settlementId) {
    public static final Codec<SettlementIdComponent> CODEC = UUIDUtil.CODEC.xmap(SettlementIdComponent::new, SettlementIdComponent::settlementId);
    public static final StreamCodec<FriendlyByteBuf, SettlementIdComponent> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> buffer.writeUUID(value.settlementId()),
            buffer -> new SettlementIdComponent(buffer.readUUID()));
}
