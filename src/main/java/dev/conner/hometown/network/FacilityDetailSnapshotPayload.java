package dev.conner.hometown.network;

import dev.conner.hometown.Hometown;
import dev.conner.hometown.civic.FacilityType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/** Immutable, server-authoritative detail snapshot for one registered civic facility sign. */
public record FacilityDetailSnapshotPayload(
        UUID settlementId,
        String townName,
        DyeColor primaryColor,
        DyeColor secondaryColor,
        FacilityType facilityType,
        boolean active,
        List<Line> lines) implements CustomPacketPayload {

    public static final int WIRE_TEXT_LIMIT = 256;
    public static final int MAX_LINES = 48;
    public static final Type<FacilityDetailSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Hometown.MOD_ID, "facility_detail_snapshot"));

    public enum LineKind { SECTION, ROW, NOTE }
    public enum Tone { NORMAL, GOOD, WARNING, MUTED }

    public record Line(LineKind kind, String label, String value, Tone tone) {
        public Line {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(label);
            Objects.requireNonNull(value);
            Objects.requireNonNull(tone);
            if (label.length() > WIRE_TEXT_LIMIT || value.length() > WIRE_TEXT_LIMIT) {
                throw new IllegalArgumentException("Facility detail line exceeds wire limit");
            }
        }
        public static Line section(String text) { return new Line(LineKind.SECTION, text, "", Tone.NORMAL); }
        public static Line row(String label, String value, Tone tone) { return new Line(LineKind.ROW, label, value, tone); }
        public static Line note(String text, Tone tone) { return new Line(LineKind.NOTE, "", text, tone); }
    }

    public static final StreamCodec<FriendlyByteBuf, FacilityDetailSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.settlementId());
                buffer.writeUtf(value.townName(), WIRE_TEXT_LIMIT);
                buffer.writeEnum(value.primaryColor());
                buffer.writeEnum(value.secondaryColor());
                buffer.writeEnum(value.facilityType());
                buffer.writeBoolean(value.active());
                buffer.writeVarInt(value.lines().size());
                for (Line line : value.lines()) {
                    buffer.writeEnum(line.kind());
                    buffer.writeUtf(line.label(), WIRE_TEXT_LIMIT);
                    buffer.writeUtf(line.value(), WIRE_TEXT_LIMIT);
                    buffer.writeEnum(line.tone());
                }
            },
            buffer -> {
                UUID settlementId = buffer.readUUID();
                String townName = buffer.readUtf(WIRE_TEXT_LIMIT);
                DyeColor primary = buffer.readEnum(DyeColor.class);
                DyeColor secondary = buffer.readEnum(DyeColor.class);
                FacilityType facilityType = buffer.readEnum(FacilityType.class);
                boolean active = buffer.readBoolean();
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_LINES) throw new IllegalArgumentException("Invalid facility detail line count");
                java.util.ArrayList<Line> lines = new java.util.ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    lines.add(new Line(buffer.readEnum(LineKind.class), buffer.readUtf(WIRE_TEXT_LIMIT),
                            buffer.readUtf(WIRE_TEXT_LIMIT), buffer.readEnum(Tone.class)));
                }
                return new FacilityDetailSnapshotPayload(settlementId, townName, primary, secondary,
                        facilityType, active, lines);
            });

    public FacilityDetailSnapshotPayload {
        Objects.requireNonNull(settlementId);
        Objects.requireNonNull(townName);
        Objects.requireNonNull(primaryColor);
        Objects.requireNonNull(secondaryColor);
        Objects.requireNonNull(facilityType);
        Objects.requireNonNull(lines);
        if (primaryColor == secondaryColor) throw new IllegalArgumentException("Facility detail colors must differ");
        if (townName.length() > WIRE_TEXT_LIMIT) throw new IllegalArgumentException("Facility detail town name exceeds wire limit");
        if (lines.size() > MAX_LINES) throw new IllegalArgumentException("Too many facility detail lines");
        lines = List.copyOf(lines);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
