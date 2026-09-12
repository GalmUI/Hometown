package dev.conner.hometown.client;

import com.google.gson.JsonParser;
import dev.conner.hometown.network.RequestTownLedgerPayload;
import dev.conner.hometown.network.TownLedgerSnapshotPayload;
import dev.conner.hometown.network.data.ResidentSummary;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import dev.conner.hometown.settlement.Settlement;
import dev.conner.hometown.settlement.SettlementStats;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the actual screen drawing methods with a recording graphics backend, without OpenGL. */
class TownLedgerScreenTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private TownLedgerScreen screen(int width, int height) throws Exception {
        var screen = mock(TownLedgerScreen.class, withSettings()
                .spiedInstance(new TownLedgerScreen(InteractionHand.MAIN_HAND))
                .defaultAnswer(call -> {
                    if (List.of("renderBlurredBackground", "renderMenuBackground", "renderPanorama")
                            .contains(call.getMethod().getName())) return null;
                    return Answers.CALLS_REAL_METHODS.answer(call);
                }));
        var font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String)call.getArgument(0)).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String text = call.getArgument(0); int length = call.getArgument(1);
            return text.substring(0, Math.min(text.length(), Math.max(0, length / 6)));
        });
        var fontField = Screen.class.getDeclaredField("font"); fontField.setAccessible(true); fontField.set(screen, font);
        var minecraftField = Screen.class.getDeclaredField("minecraft"); minecraftField.setAccessible(true);
        minecraftField.set(screen, mock(Minecraft.class));
        screen.width = width; screen.height = height;
        var init = TownLedgerScreen.class.getDeclaredMethod("init"); init.setAccessible(true); init.invoke(screen);
        return screen;
    }

    private TownLedgerSnapshot snapshot() {
        var town = new Settlement(UUID.randomUUID(), "Dured", Level.OVERWORLD, new BlockPos(115, 68, 7), 64,
                UUID.randomUUID(), "GalrUI", 432000);
        var residents = List.of(new ResidentSummary("Mira", "entity.minecraft.villager.librarian", false),
                new ResidentSummary("", "hometown.ledger.unemployed", true));
        return TownLedgerSnapshot.of(town, new SettlementStats(2, 3, 1, 1, SettlementStats.Availability.COMPLETE, residents),
                TownLedgerSnapshot.BellState.MISSING, 0).withHousing(new dev.conner.hometown.housing.HousingSnapshot(2,3,2,1,1,0,1,2,0,true))
                .withFood(dev.conner.hometown.food.FoodRules.DEFAULT.snapshot(10,4,17,8,1060));
    }

    private dev.conner.hometown.safety.SafetySnapshot safetyFixture(TownLedgerSnapshot base,int mode) {
        var complete=dev.conner.hometown.safety.SafetySnapshot.Status.COMPLETE;
        var partial=dev.conner.hometown.safety.SafetySnapshot.Status.PARTIAL;
        var unavailable=dev.conner.hometown.safety.SafetySnapshot.Status.UNAVAILABLE;
        var disabled=dev.conner.hometown.safety.SafetySnapshot.Status.DISABLED;
        var condition=dev.conner.hometown.safety.SafetySnapshot.LightingCondition.OBSERVED;
        var reasons=java.util.Map.of(dev.conner.hometown.safety.SafetySnapshot.Reason.ROOM_DATA_INCOMPLETE,1);
        var none=java.util.OptionalDouble.empty();
        var ratio=java.util.OptionalDouble.of(800.0/9);
        return new dev.conner.hometown.safety.SafetySnapshot(base.metadata(),mode!=3,
            mode==3?disabled:mode==4?unavailable:partial,mode==3?java.util.Map.of():reasons,
            java.util.Map.of(),java.util.Map.of(),java.util.Map.of(),0,0,0,4096,1,2,
            mode==3?disabled:mode==4?unavailable:mode==1?partial:complete,
            mode==1||mode==4?reasons:java.util.Map.of(),mode<2?9:0,mode<2?8:0,mode<2?1:0,
            mode<2?ratio:none,mode==0?ratio:none,
            mode==3?dev.conner.hometown.safety.SafetySnapshot.LightingCondition.DISABLED:
            mode==1||mode==4?dev.conner.hometown.safety.SafetySnapshot.LightingCondition.INCOMPLETE:
            mode==2?dev.conner.hometown.safety.SafetySnapshot.LightingCondition.NO_ENCLOSED_BEDS:condition,
            1,mode<2?9:0,262144,java.util.List.of());
    }

    private List<String> draw(TownLedgerScreen screen) {
        clearInvocations(screen);
        var text = new ArrayList<String>();
        GuiGraphics graphics = mock(GuiGraphics.class, call -> {
            String method = call.getMethod().getName();
            if (method.equals("drawString") || method.equals("drawWordWrap") || method.equals("drawCenteredString")) {
                Object value = call.getArgument(1);
                text.add(value instanceof FormattedText formatted ? formatted.getString() : value.toString());
            }
            if (method.equals("fill")) {
                int x1 = call.getArgument(0), y1 = call.getArgument(1), x2 = call.getArgument(2), y2 = call.getArgument(3);
                assertTrue(x1 >= 0 && y1 >= 0 && x2 <= screen.width && y2 <= screen.height, "Book or tabs escaped the GUI area");
            }
            return Answers.RETURNS_DEFAULTS.answer(call);
        });
        screen.render(graphics, 0, 0, 0);
        var blurCalls = mockingDetails(screen).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("renderBlurredBackground")).toList();
        assertEquals(1, blurCalls.size(), "World blur must execute exactly once per frame");
        int blurSequence = blurCalls.getFirst().getSequenceNumber();
        assertTrue(mockingDetails(graphics).getInvocations().stream()
                .filter(call -> List.of("fill", "drawString", "drawWordWrap", "drawCenteredString").contains(call.getMethod().getName()))
                .allMatch(call -> call.getSequenceNumber() > blurSequence), "No book, page, or tab may be drawn before blur");
        var draws = mockingDetails(graphics).getInvocations();
        var cover = draws.stream().filter(call -> call.getMethod().getName().equals("fill")
                && (int)call.getArgument(4) == 0xFF60412A).findFirst().orElseThrow();
        int left = cover.getArgument(0), top = cover.getArgument(1);
        int right = cover.getArgument(2), bottom = cover.getArgument(3);
        assertEquals((screen.width - (right - left)) / 2, left);
        assertEquals((screen.height - (bottom - top)) / 2, top);
        int firstTab = draws.stream().filter(call -> call.getMethod().getName().equals("fill")
                && (int)call.getArgument(1) < top).mapToInt(call -> call.getSequenceNumber()).min().orElseThrow();
        for (var call : draws) {
            if (call.getSequenceNumber() >= firstTab || !List.of("drawString", "drawWordWrap", "drawCenteredString").contains(call.getMethod().getName())) continue;
            Object value = call.getArgument(1);
            String line = value instanceof FormattedText formatted ? formatted.getString() : value.toString();
            int x = call.getArgument(2), y = call.getArgument(3);
            int pixels = line.length() * 6, lines = 1;
            if (call.getMethod().getName().equals("drawCenteredString")) x -= pixels / 2;
            if (call.getMethod().getName().equals("drawWordWrap")) {
                int wrap = call.getArgument(4), used = 0;
                for (String word : line.split(" ")) {
                    int length = word.length() * 6;
                    if (used > 0 && used + 6 + length > wrap) { lines++; used = 0; }
                    if (used > 0) used += 6;
                    while (length > wrap) { lines++; length -= wrap; }
                    used += length;
                }
                pixels = wrap;
            }
            assertTrue(x >= left && x + pixels <= right && y >= top && y + lines * 9 <= bottom,
                    "Page text escaped the ledger: " + line);
        }
        verify(graphics, never()).pose();
        return text;
    }

    @Test void allTabsRenderReadOnlyFactsAndFitNormalGuiScales() throws Exception {
        Language original = Language.getInstance();
        var translations = JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/assets/hometown/lang/en_us.json"))).getAsJsonObject();
        Language.inject(new Language() {
            public String getOrDefault(String key, String fallback) { return translations.has(key) ? translations.get(key).getAsString() : original.getOrDefault(key, fallback); }
            public boolean has(String key) { return translations.has(key) || original.has(key); }
            public boolean isDefaultRightToLeft() { return false; }
            public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.forward(text.getString(), Style.EMPTY); }
        });
        try (var packets = mockStatic(PacketDistributor.class)) {
            var requests = new ArrayList<RequestTownLedgerPayload>();
            packets.when(() -> PacketDistributor.sendToServer(any(CustomPacketPayload.class)))
                    .thenAnswer(call -> { requests.add(call.getArgument(0)); return null; });
            for (int[] size : List.of(new int[]{960, 540}, new int[]{640, 360}, new int[]{480, 270},
                    new int[]{959, 488}, new int[]{640, 326}, new int[]{480, 244}, new int[]{320, 240})) {
                var screen = screen(size[0], size[1]);
                screen.requestPage(0);
                screen.receive(new TownLedgerSnapshotPayload(requests.getLast().requestId(), snapshot(), TownLedgerSnapshotPayload.Error.NONE));
                var overview = draw(screen);
                assertTrue(overview.contains("Population: 2"));
                assertTrue(overview.contains("Beds: 3"));
                assertTrue(overview.stream().anyMatch(s -> s.contains("Founding bell missing")));
                ((Button)screen.children().get(1)).onPress();
                var residents = draw(screen);
                assertTrue(residents.contains("Mira")); assertTrue(residents.contains("Child"));
                ((Button)screen.children().get(2)).onPress();
                var development = draw(screen);
                assertEquals(0, development.stream().filter("Not yet tracked"::equals).count());
                assertTrue(development.contains("Capacity: 100%"));
                assertTrue(development.contains("Privacy: 80%"));
                assertTrue(development.contains("Housing Supply"));
                var subsectionField = TownLedgerScreen.class.getDeclaredField("activeDevelopment"); subsectionField.setAccessible(true);
                assertEquals(DevelopmentSection.HOUSING, subsectionField.get(screen));
                int sentBeforeNavigation = requests.size();
                for (var section : DevelopmentSection.values()) {
                    Button button = (Button)screen.children().get(7 + section.ordinal());
                    assertTrue(button.visible); assertTrue(button.getHeight() < ((Button)screen.children().getFirst()).getHeight());
                    button.onPress();
                    assertEquals(section, subsectionField.get(screen));
                    var subsection = draw(screen);
                    if (section != DevelopmentSection.HOUSING && section != DevelopmentSection.FOOD
                            && section != DevelopmentSection.SAFETY && section != DevelopmentSection.COMFORT
                            && section != DevelopmentSection.COMMERCE && section != DevelopmentSection.PROSPERITY) {
                        assertTrue(subsection.contains("This aspect of town development is not yet tracked."));
                        assertFalse(subsection.stream().anyMatch(t -> t.startsWith("Capacity:") || t.startsWith("Privacy:")));
                    }
                    if (section == DevelopmentSection.PROSPERITY) {
                        assertTrue(subsection.contains("Prosperity"));
                        assertFalse(subsection.stream().anyMatch(t -> t.startsWith("Capacity:") || t.startsWith("Privacy:")));
                    }
                    if (section == DevelopmentSection.COMFORT) {
                        assertTrue(subsection.contains("Comfort"));
                        assertTrue(subsection.contains("N/A"));
                        assertTrue(subsection.contains("Comfort Categories"));
                        assertFalse(subsection.contains("This aspect of town development is not yet tracked."));
                    }
                    if (section == DevelopmentSection.COMMERCE) {
                        assertTrue(subsection.contains("Commerce"));
                        assertTrue(subsection.contains("Commerce observation is still arriving."));
                        assertFalse(subsection.contains("This aspect of town development is not yet tracked."));
                    }
                    if (section == DevelopmentSection.FOOD) {
                        assertTrue(subsection.contains("Food Security"));assertTrue(subsection.contains("Town Stores"));
                        assertTrue(subsection.contains("5.3 Days"));assertTrue(subsection.contains("STABLE"));
                        assertTrue(subsection.contains("Food stacks: 17"));
                        var foodField=TownLedgerScreen.class.getDeclaredField("snapshot");foodField.setAccessible(true);
                        var twelve=dev.conner.hometown.food.FoodRules.DEFAULT.snapshot(10,1,1,1,2400);
                        foodField.set(screen,snapshot().withFood(twelve));assertTrue(draw(screen).contains("12.0 Days"));
                        var barGraphics=mock(GuiGraphics.class);screen.render(barGraphics,0,0,0);
                        var background=mockingDetails(barGraphics).getInvocations().stream().filter(call->call.getMethod().getName().equals("fill")
                                && (int)call.getArgument(4)==0xFFBCA27D && (int)call.getArgument(3)-(int)call.getArgument(1)==5).findFirst().orElseThrow();
                        var full=mockingDetails(barGraphics).getInvocations().stream().filter(call->call.getMethod().getName().equals("fill")
                                && (int)call.getArgument(4)==0xFF817644).findFirst().orElseThrow();
                        for(int coordinate=0;coordinate<4;coordinate++) assertEquals((Object)background.getArgument(coordinate),(Object)full.getArgument(coordinate));
                        for(var food:List.of(dev.conner.hometown.food.FoodRules.DEFAULT.snapshot(0,0,0,0,0),dev.conner.hometown.food.FoodSnapshot.unavailable(10))) {
                            foodField.set(screen,snapshot().withFood(food));var unavailableText=draw(screen);
                            var graphics=mock(GuiGraphics.class);screen.render(graphics,0,0,0);
                            assertFalse(mockingDetails(graphics).getInvocations().stream().anyMatch(call->call.getMethod().getName().equals("fill") && (int)call.getArgument(4)==0xFF817644));
                            if(food.scanComplete()) assertTrue(unavailableText.contains("N/A"));
                            else assertTrue(unavailableText.stream().anyMatch(t->t.contains("DATA UNAVAILABLE")));
                        }
                        var diagnostics=new dev.conner.hometown.food.FoodScanDiagnostics(java.util.Set.of(dev.conner.hometown.food.FoodScanReason.UNLOADED_CHUNKS),4,3,1,1,1,1,0,0,0,27,dev.conner.hometown.food.FoodScanner.Limits.DEFAULT,true);
                        for(long known:new long[]{1060,0}) {
                            var partial=dev.conner.hometown.food.FoodRules.DEFAULT.snapshot(10,known>0?1:0,known>0?1:0,known>0?1:0,known,dev.conner.hometown.food.FoodScanStatus.PARTIAL,diagnostics);
                            foodField.set(screen,snapshot().withFood(partial));var partialText=draw(screen);
                            assertTrue(partialText.contains("PARTIAL DATA"));assertTrue(partialText.contains("Known Town Stores"));
                            assertTrue(partialText.contains(known>0?"At least 5.3 Days":"At least 0.0 Days"));assertFalse(partialText.contains("EMPTY"));
                            assertTrue(partialText.contains("1 chunk unavailable"));
                        }
                        var incompletePopulation=dev.conner.hometown.food.FoodScanDiagnostics.empty(java.util.Set.of(dev.conner.hometown.food.FoodScanReason.POPULATION_INCOMPLETE),false);
                        foodField.set(screen,snapshot().withFood(dev.conner.hometown.food.FoodRules.DEFAULT.snapshot(10,1,1,1,1060,dev.conner.hometown.food.FoodScanStatus.PARTIAL,incompletePopulation)));
                        var denominatorText=draw(screen);assertFalse(denominatorText.stream().anyMatch(t->t.contains("At least")));
                        var noBar=mock(GuiGraphics.class);screen.render(noBar,0,0,0);assertFalse(mockingDetails(noBar).getInvocations().stream().anyMatch(call->call.getMethod().getName().equals("fill") && (int)call.getArgument(4)==0xFF817644));foodField.set(screen,snapshot());
                    }
                    if (section == DevelopmentSection.SAFETY) {
                        var field=TownLedgerScreen.class.getDeclaredField("snapshot");field.setAccessible(true);
                        for(int mode=0;mode<5;mode++) {var base=snapshot();field.set(screen,base.withSafety(safetyFixture(base,mode)));var safetyText=draw(screen);assertTrue(safetyText.contains("Safety"));assertTrue(safetyText.contains("Residential Lighting"));assertFalse(safetyText.contains("Safe"));if(mode==0)assertTrue(safetyText.contains("89%"));if(mode==1)assertTrue(safetyText.stream().anyMatch(t->t.startsWith("Observed Lighting:")));if(mode==2||mode==4)assertTrue(safetyText.contains("N/A"));if(mode==3)assertTrue(safetyText.contains("Disabled"));if(mode>=2) {var noBar=mock(GuiGraphics.class);screen.render(noBar,0,0,0);assertFalse(mockingDetails(noBar).getInvocations().stream().anyMatch(call->call.getMethod().getName().equals("fill") && (int)call.getArgument(4)==0xFF817644));}}
                        field.set(screen,snapshot());
                    }
                    var g = mock(GuiGraphics.class); screen.render(g,0,0,0);long highlights = mockingDetails(g).getInvocations().stream().filter(call -> call.getMethod().getName().equals("fill")&& (int)call.getArgument(4) == 0xFF99513B && (int)call.getArgument(1) == button.getY()+button.getHeight()-4).count();assertEquals(1,highlights);
                }
                assertEquals(sentBeforeNavigation,requests.size(),"Subnavigation must not request a new snapshot");
                ((Button)screen.children().get(0)).onPress();for(int index=7;index<13;index++) assertFalse(((Button)screen.children().get(index)).visible);
                ((Button)screen.children().get(2)).onPress();((Button)screen.children().get(7)).onPress();var snapshotField = TownLedgerScreen.class.getDeclaredField("snapshot"); snapshotField.setAccessible(true);var unknown = dev.conner.hometown.housing.HousingSnapshot.unavailable(10, 9);snapshotField.set(screen, snapshot().withHousing(unknown));var missing = draw(screen);assertTrue(missing.contains("SCAN INCOMPLETE"));assertFalse(missing.stream().anyMatch(t -> t.startsWith("Enclosed beds:")));assertTrue(missing.contains("Capacity: N/A")); assertTrue(missing.contains("Privacy: N/A"));
                var empty = new dev.conner.hometown.housing.HousingSnapshot(0,0,0,0,0,0,0,0,0,true);snapshotField.set(screen, snapshot().withHousing(empty));assertTrue(draw(screen).contains("Capacity: N/A"));var baseline = new dev.conner.hometown.housing.HousingSnapshot(10,9,9,0,8,7,1,2,0,true);snapshotField.set(screen, snapshot().withHousing(baseline));var crowded = draw(screen);assertTrue(crowded.contains("Capacity: 90%")); assertTrue(crowded.contains("SHORTAGE"));assertTrue(crowded.contains("Unhoused: 1"));snapshotField.set(screen, snapshot());
                ((Button)screen.children().get(3)).onPress();var history = draw(screen);assertTrue(history.contains("Day 18")); assertTrue(history.contains("Dured was founded by GalrUI."));assertFalse(screen.isPauseScreen());
            }
        } finally { Language.inject(original); }
    }

    @Test void residentPaginationSurvivesDevelopmentSubnavigation() throws Exception {
        try (var packets = mockStatic(PacketDistributor.class)) {
            var requests = new ArrayList<RequestTownLedgerPayload>();packets.when(() -> PacketDistributor.sendToServer(any(CustomPacketPayload.class))).thenAnswer(call -> { requests.add(call.getArgument(0)); return null; });
            var town = new Settlement(UUID.randomUUID(), "Dured", Level.OVERWORLD, new BlockPos(8,64,8), 16,UUID.randomUUID(), "Founder", 0);var stats = new SettlementStats(5,3,0,0,SettlementStats.Availability.COMPLETE,java.util.Collections.nCopies(5,new ResidentSummary("Resident","hometown.ledger.unemployed",false)));
            var screen = screen(480,270);screen.requestPage(0);screen.receive(new TownLedgerSnapshotPayload(requests.getLast().requestId(),TownLedgerSnapshot.of(town,stats,TownLedgerSnapshot.BellState.PRESENT,0),TownLedgerSnapshotPayload.Error.NONE));((Button)screen.children().get(1)).onPress();Button next = (Button)screen.children().get(5), previous = (Button)screen.children().get(4);assertTrue(next.visible && next.active); next.onPress();assertEquals(1,requests.getLast().page());screen.receive(new TownLedgerSnapshotPayload(requests.getLast().requestId(),TownLedgerSnapshot.of(town,stats,TownLedgerSnapshot.BellState.PRESENT,1),TownLedgerSnapshotPayload.Error.NONE));assertTrue(previous.active); assertFalse(next.active);int count = requests.size();((Button)screen.children().get(2)).onPress();((Button)screen.children().get(8)).onPress();assertFalse(previous.visible);((Button)screen.children().get(1)).onPress();assertEquals(count,requests.size()); assertTrue(previous.visible && previous.active);previous.onPress(); assertEquals(0,requests.getLast().page());
        }
    }

    @Test void staleResponsesAreIgnoredAndUnknownLedgerRendersAnError() throws Exception {
        try (var packets = mockStatic(PacketDistributor.class)) {
            var requests = new ArrayList<RequestTownLedgerPayload>();packets.when(() -> PacketDistributor.sendToServer(any(CustomPacketPayload.class))).thenAnswer(call -> { requests.add(call.getArgument(0)); return null; });var screen = screen(320, 240);screen.requestPage(0); int old = requests.getLast().requestId();screen.requestPage(1); int current = requests.getLast().requestId();screen.receive(new TownLedgerSnapshotPayload(old, snapshot(), TownLedgerSnapshotPayload.Error.NONE));assertFalse(draw(screen).contains("Dured"));screen.receive(new TownLedgerSnapshotPayload(current, null, TownLedgerSnapshotPayload.Error.UNKNOWN));assertTrue(draw(screen).stream().anyMatch(s -> s.contains("hometown.ledger.unknown")));assertTrue(screen.children().stream().anyMatch(widget -> widget instanceof Button button && button.visible && button.getMessage().getString().contains("hometown.ledger.retry")));
        }
    }
}
