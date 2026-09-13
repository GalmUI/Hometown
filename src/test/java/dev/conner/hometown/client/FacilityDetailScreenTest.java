package dev.conner.hometown.client;

import dev.conner.hometown.network.FacilityDetailSnapshotPayload;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FacilityDetailScreenTest {
    @Test void longFacilityViewsRemainScrollableInsteadOfDroppingLines() {
        var lines = new ArrayList<FacilityDetailSnapshotPayload.Line>();
        lines.add(FacilityDetailSnapshotPayload.Line.section("Status"));
        lines.add(FacilityDetailSnapshotPayload.Line.row("State", "Established — Active", FacilityDetailSnapshotPayload.Tone.GOOD));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Registered sign", "153, -57, 178", FacilityDetailSnapshotPayload.Tone.MUTED));
        lines.add(FacilityDetailSnapshotPayload.Line.section("Facility"));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Farm building", "Qualified", FacilityDetailSnapshotPayload.Tone.GOOD));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Recognized storage", "1 / 1", FacilityDetailSnapshotPayload.Tone.GOOD));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Looms", "1 / 1", FacilityDetailSnapshotPayload.Tone.GOOD));
        lines.add(FacilityDetailSnapshotPayload.Line.section("Paddock"));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Building connections", "2 / 2", FacilityDetailSnapshotPayload.Tone.GOOD));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Connected fences/gates", "24 / 16", FacilityDetailSnapshotPayload.Tone.GOOD));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Enclosure", "Closed", FacilityDetailSnapshotPayload.Tone.GOOD));
        lines.add(FacilityDetailSnapshotPayload.Line.section("Livestock"));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Production", "Not yet active", FacilityDetailSnapshotPayload.Tone.MUTED));
        lines.add(FacilityDetailSnapshotPayload.Line.row("Animals", "Not yet tracked", FacilityDetailSnapshotPayload.Tone.MUTED));
        lines.add(FacilityDetailSnapshotPayload.Line.section("Role"));
        lines.add(FacilityDetailSnapshotPayload.Line.note("Livestock production and animal-based town supplies.", FacilityDetailSnapshotPayload.Tone.NORMAL));

        int content = FacilityDetailScreen.contentHeight(lines);
        assertTrue(content > 157, "Animal Farm detail should exceed the smallest current viewport");
        assertEquals(content - 157, FacilityDetailScreen.scrollLimit(content, 157));
        assertEquals(0, FacilityDetailScreen.scrollLimit(100, 157));
    }
}
