package dev.conner.hometown.client;

import com.google.gson.JsonParser;
import dev.conner.hometown.comfort.ComfortCategory;
import dev.conner.hometown.comfort.ComfortEvaluator;
import dev.conner.hometown.comfort.ComfortSettings;
import dev.conner.hometown.comfort.ComfortSnapshot;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import dev.conner.hometown.safety.SafetySnapshot;
import dev.conner.hometown.settlement.Settlement;
import dev.conner.hometown.settlement.SettlementStats;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
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
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression coverage for issues found during the 0.5.0-alpha.1 owner playtest. */
class TownLedgerPlaytestPolishTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    private TownLedgerScreen screen() throws Exception {
        var screen = mock(TownLedgerScreen.class, withSettings()
                .spiedInstance(new TownLedgerScreen(InteractionHand.MAIN_HAND))
                .defaultAnswer(call -> List.of("renderBlurredBackground", "renderMenuBackground", "renderPanorama")
                        .contains(call.getMethod().getName()) ? null : Answers.CALLS_REAL_METHODS.answer(call)));
        var font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> ((String) call.getArgument(0)).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> {
            String text = call.getArgument(0);
            int width = call.getArgument(1);
            return text.substring(0, Math.min(text.length(), Math.max(0, width / 6)));
        });
        var fontField = Screen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, font);
        var minecraftField = Screen.class.getDeclaredField("minecraft");
        minecraftField.setAccessible(true);
        minecraftField.set(screen, mock(Minecraft.class));
        screen.width = 480;
        screen.height = 270;
        var init = TownLedgerScreen.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(screen);
        return screen;
    }

    private List<String> draw(TownLedgerScreen screen) {
        var text = new ArrayList<String>();
        var graphics = mock(GuiGraphics.class, call -> {
            String method = call.getMethod().getName();
            if (method.equals("drawString") || method.equals("drawWordWrap") || method.equals("drawCenteredString")) {
                Object value = call.getArgument(1);
                text.add(value instanceof FormattedText formatted ? formatted.getString() : value.toString());
            }
            return Answers.RETURNS_DEFAULTS.answer(call);
        });
        screen.render(graphics, 0, 0, 0);
        return text;
    }

    private TownLedgerSnapshot snapshot() {
        var town = new Settlement(UUID.randomUUID(), "Playtest", Level.OVERWORLD, new BlockPos(0, 64, 0), 16,
                UUID.randomUUID(), "Founder", 0);
        var base = TownLedgerSnapshot.of(town,
                new SettlementStats(1, 1, 0, 0, SettlementStats.Availability.COMPLETE, List.of()),
                TownLedgerSnapshot.BellState.PRESENT, 0)
                .withHousing(new dev.conner.hometown.housing.HousingSnapshot(1, 1, 1, 0, 1, 1, 0, 0, 0, true));

        var safety = new SafetySnapshot(base.metadata(), true,
                SafetySnapshot.Status.COMPLETE, Map.of(),
                Map.of("minecraft:zombie", 5, "minecraft:skeleton", 4),
                Map.of("minecraft:iron_golem", 2), Map.of(),
                11, 0, 0, 4096, 1, 1,
                SafetySnapshot.Status.COMPLETE, Map.of(),
                1, 1, 0, OptionalDouble.of(100), OptionalDouble.of(100),
                SafetySnapshot.LightingCondition.OBSERVED, 1, 1, 262144, List.of());

        var categories = new EnumMap<ComfortCategory, ComfortSettings.Category>(ComfortCategory.class);
        var hits = new EnumMap<ComfortCategory, Integer>(ComfortCategory.class);
        for (var category : ComfortCategory.values()) {
            categories.put(category, new ComfortSettings.Category(true, category.defaultWeight));
            hits.put(category, 1);
        }
        var settings = new ComfortSettings(true, categories, 512, 65536);
        var room = ComfortEvaluator.room(new BlockPos(0, 64, 0), 1, 5, 4,
                ComfortSnapshot.Status.COMPLETE, Map.of(), hits, 0, 0, settings);
        var comfort = ComfortEvaluator.town(base.metadata(), settings, true, 1, List.of(room), Map.of(), 1, 9, 262144);
        return base.withSafety(safety).withComfort(comfort);
    }

    @Test void alpha1PlaytestFindingsAreFixed() throws Exception {
        Language original = Language.getInstance();
        var translations = JsonParser.parseReader(new InputStreamReader(
                getClass().getResourceAsStream("/assets/hometown/lang/en_us.json"))).getAsJsonObject();
        Language.inject(new Language() {
            public String getOrDefault(String key, String fallback) {
                return translations.has(key) ? translations.get(key).getAsString() : original.getOrDefault(key, fallback);
            }
            public boolean has(String key) { return translations.has(key) || original.has(key); }
            public boolean isDefaultRightToLeft() { return false; }
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
            }
        });
        try {
            var screen = screen();
            var snapshotField = TownLedgerScreen.class.getDeclaredField("snapshot");
            snapshotField.setAccessible(true);
            snapshotField.set(screen, snapshot());

            ((Button) screen.children().get(2)).onPress();
            var development = draw(screen);
            for (String label : List.of("Housing", "Food", "Safety", "Comfort", "Commerce", "Prosperity"))
                assertTrue(development.contains(label), "Development tab label should render in full: " + label);

            ((Button) screen.children().get(7 + DevelopmentSection.SAFETY.ordinal())).onPress();
            var safety = draw(screen);
            assertTrue(safety.contains("Threats observed: 9"));
            assertTrue(safety.contains("Zombie ×5, Skeleton ×4"), "Threat types should be visible with their observed counts");

            ((Button) screen.children().get(7 + DevelopmentSection.COMFORT.ordinal())).onPress();
            Button details = (Button) screen.children().get(13);
            assertTrue(details.visible);
            details.onPress();
            Button back = (Button) screen.children().get(14);
            assertTrue(back.visible);
            var topField = TownLedgerScreen.class.getDeclaredField("top");
            topField.setAccessible(true);
            int top = topField.getInt(screen);
            int firstCategoryY = top + 58 + 53;
            assertEquals(top + 58, back.getY(), "Back belongs beside the room heading");
            assertTrue(back.getY() + back.getHeight() < firstCategoryY,
                    "Back button must stay clear of the Comfort category list");
        } finally {
            Language.inject(original);
        }
    }
}
