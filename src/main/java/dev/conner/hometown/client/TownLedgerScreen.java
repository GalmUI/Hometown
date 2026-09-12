package dev.conner.hometown.client;

import dev.conner.hometown.network.RequestTownLedgerPayload;
import dev.conner.hometown.network.TownLedgerSnapshotPayload;
import dev.conner.hometown.network.FoodM3SnapshotPayload;
import dev.conner.hometown.network.data.TownLedgerSnapshot;
import dev.conner.hometown.settlement.SettlementStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/** Presentation only. All town facts and resident pages arrive from the server. */
public final class TownLedgerScreen extends Screen {
    private static final int BOOK_WIDTH = 384, BOOK_HEIGHT = 224;
    private static final int INK = 0xFF493323, MUTED = 0xFF79654A, WARNING = 0xFF8B3D28;
    private static int nextRequest;
    private final InteractionHand hand;
    private TownLedgerSnapshot snapshot;
    private dev.conner.hometown.food.FoodVarietySnapshot foodVariety;
    private dev.conner.hometown.food.FoodGrowingSnapshot foodGrowing;
    private TownLedgerSnapshotPayload.Error error = TownLedgerSnapshotPayload.Error.NONE;
    private int requestId, requestedPage, activeTab, left, top, bookWidth, bookHeight, waitTicks;
    private boolean waiting;
    private Button previous, next, retry, comfortDetails, comfortBack, foodReserves, foodVarietyButton, foodGrowingButton;
    private DevelopmentSection activeDevelopment = DevelopmentSection.HOUSING;
    private final java.util.List<Button> developmentButtons = new java.util.ArrayList<>();
    private Component hoveredText;
    private int safetyPage;
    private int comfortRoomPage;
    private boolean comfortRoomDetails;
    private int foodSection, foodGrowingPage;

    public TownLedgerScreen(InteractionHand hand) {
        super(Component.translatable("item.hometown.town_ledger"));
        this.hand = hand;
    }

    @Override protected void init() {
        // Native GUI pixels. Small viewports change layout bounds, never the pose/font scale.
        bookWidth = Math.min(BOOK_WIDTH, width - 12);
        bookHeight = Math.min(BOOK_HEIGHT, height - 44);
        left = (width - bookWidth) / 2;
        top = (height - bookHeight) / 2;
        int tabWidth = (bookWidth - 12) / 4;
        String[] tabs = {"overview", "residents", "development", "history"};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            addRenderableWidget(new Bookmark(left + 6 + tabWidth * i, top - 18, tabWidth - 2, 21,
                    Component.translatable("hometown.ledger.tab." + tabs[i]), button -> { activeTab = tab; updateButtons(); }, tab));
        }
        previous = addRenderableWidget(new Bookmark(left + 18, top + bookHeight - 25, 36, 17,
                Component.literal("←"), button -> previousPage(), -1));
        next = addRenderableWidget(new Bookmark(left + bookWidth - 54, top + bookHeight - 25, 36, 17,
                Component.literal("→"), button -> nextPage(), -1));
        retry = addRenderableWidget(new Bookmark(left + bookWidth / 2 - 42, top + bookHeight - 25, 84, 17,
                Component.translatable("hometown.ledger.retry"), button -> requestPage(requestedPage), -1));
        developmentButtons.clear();
        var sections = DevelopmentSection.values();
        var labels = new java.util.ArrayList<Component>(sections.length);
        int availableWidth = bookWidth - 24;
        int gap = 2;
        int naturalWidth = 0;
        for (var section : sections) {
            Component label = Component.translatable(section.translationKey());
            labels.add(label);
            naturalWidth += font.width(label.getString()) + 2;
        }
        int gapWidth = gap * Math.max(0, sections.length - 1);
        boolean labelsFit = naturalWidth + gapWidth <= availableWidth;
        int extraWidth = labelsFit ? availableWidth - gapWidth - naturalWidth : 0;
        int subsectionX = left + 12;
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            Component label = labels.get(i);
            int buttonWidth = labelsFit
                    ? font.width(label.getString()) + 2 + extraWidth / sections.length + (i < extraWidth % sections.length ? 1 : 0)
                    : Math.max(1, (availableWidth - gapWidth) / sections.length);
            var bookmark = new Bookmark(subsectionX, top + 33, buttonWidth, 16, label, button -> {
                        activeDevelopment = section;
                        if (section != DevelopmentSection.SAFETY) safetyPage = 0;
                        if (section != DevelopmentSection.COMFORT) { comfortRoomDetails = false; comfortRoomPage = 0; }
                        updateButtons();
                    }, () -> activeDevelopment == section);
            if (font.width(label.getString()) > buttonWidth - 2)
                bookmark.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
            developmentButtons.add(addRenderableWidget(bookmark));
            subsectionX += buttonWidth + gap;
        }
        comfortDetails = addRenderableWidget(new Bookmark(left + bookWidth / 2 + 18, top + bookHeight - 43, 86, 15,
                Component.translatable("hometown.comfort.room_details"), button -> {
                    comfortRoomDetails = true;
                    comfortRoomPage = 0;
                    updateButtons();
                }, -1));
        comfortBack = addRenderableWidget(new Bookmark(left + bookWidth - 72, top + 58, 54, 15,
                Component.translatable("hometown.comfort.back"), button -> {
                    comfortRoomDetails = false;
                    comfortRoomPage = 0;
                    updateButtons();
                }, -1));
        int foodGap=2, foodWidth=Math.max(1,(bookWidth-36-foodGap*2)/3), foodX=left+18;
        foodReserves=addRenderableWidget(new Bookmark(foodX,top+52,foodWidth,14,Component.translatable("hometown.food.nav.reserves"),button->{foodSection=0;foodGrowingPage=0;updateButtons();},()->foodSection==0));
        foodVarietyButton=addRenderableWidget(new Bookmark(foodX+foodWidth+foodGap,top+52,foodWidth,14,Component.translatable("hometown.food.nav.variety"),button->{foodSection=1;foodGrowingPage=0;updateButtons();},()->foodSection==1));
        foodGrowingButton=addRenderableWidget(new Bookmark(foodX+(foodWidth+foodGap)*2,top+52,foodWidth,14,Component.translatable("hometown.food.nav.growing"),button->{foodSection=2;foodGrowingPage=0;updateButtons();},()->foodSection==2));
        updateButtons();
    }

    private int foodGrowingPages(){return foodGrowing==null?1:Math.max(1,(foodGrowing.families().size()+3)/4);}
    private void previousPage() {
        if (activeTab != 2) { requestPage(snapshot.residentPage() - 1); return; }
        if (activeDevelopment == DevelopmentSection.SAFETY) safetyPage--;
        else if (activeDevelopment == DevelopmentSection.COMFORT && comfortRoomDetails) comfortRoomPage--;
        else if (activeDevelopment == DevelopmentSection.FOOD && foodSection==2) foodGrowingPage--;
        updateButtons();
    }

    private void nextPage() {
        if (activeTab != 2) { requestPage(snapshot.residentPage() + 1); return; }
        if (activeDevelopment == DevelopmentSection.SAFETY) safetyPage++;
        else if (activeDevelopment == DevelopmentSection.COMFORT && comfortRoomDetails) comfortRoomPage++;
        else if (activeDevelopment == DevelopmentSection.FOOD && foodSection==2) foodGrowingPage++;
        updateButtons();
    }

    public void requestPage(int page) {
        if(snapshot==null){foodVariety=null;foodGrowing=null;foodGrowingPage=0;}
        requestId = ++nextRequest;
        requestedPage = Math.max(0, page);
        waiting = true;
        waitTicks = 0;
        error = TownLedgerSnapshotPayload.Error.NONE;
        updateButtons();
        PacketDistributor.sendToServer(new RequestTownLedgerPayload(hand, requestedPage, requestId,
            snapshot == null ? -1 : snapshot.metadata().requestGeneration()));
    }

    public void receive(TownLedgerSnapshotPayload payload) {
        if (payload.requestId() != requestId || !waiting) return;
        waiting = false;
        error = payload.error();
        if (payload.snapshot() != null) snapshot = payload.snapshot();
        if (snapshot != null) {
            safetyPage = Math.min(safetyPage, safetyPages() - 1);
            comfortRoomPage = Math.min(comfortRoomPage, Math.max(0, snapshot.comfort().roomRecords().size() - 1));
            if(foodVariety!=null&&!foodVariety.metadata().equals(snapshot.metadata())){foodVariety=null;foodGrowing=null;foodGrowingPage=0;}
        }
        // Do not keep showing another town's data after an invalid/switched held item.
        if (error != TownLedgerSnapshotPayload.Error.NONE) { snapshot = null;foodVariety=null;foodGrowing=null;foodGrowingPage=0; }
        updateButtons();
    }
    public void receiveFoodM3(FoodM3SnapshotPayload payload) {
        if(payload.requestId()!=requestId)return;
        if(snapshot!=null&&!payload.variety().metadata().equals(snapshot.metadata()))return;
        foodVariety=payload.variety();foodGrowing=payload.growing();foodGrowingPage=Math.min(foodGrowingPage,foodGrowingPages()-1);updateButtons();
    }

    @Override public void tick() {
        if (waiting && ++waitTicks >= 200) {
            waiting = false;
            error = TownLedgerSnapshotPayload.Error.FAILED;
            snapshot = null;foodVariety=null;foodGrowing=null;foodGrowingPage=0;
            updateButtons();
        }
    }

    private void updateButtons() {
        if (previous == null) return;
        boolean paging = activeTab == 1 && snapshot != null && snapshot.residentPages() > 1;
        boolean safetyPaging = activeTab == 2 && activeDevelopment == DevelopmentSection.SAFETY && snapshot != null && safetyPages() > 1;
        boolean comfortPaging = activeTab == 2 && activeDevelopment == DevelopmentSection.COMFORT && comfortRoomDetails
                && snapshot != null && snapshot.comfort().roomRecords().size() > 1;
        boolean growingPaging=activeTab==2&&activeDevelopment==DevelopmentSection.FOOD&&foodSection==2&&foodGrowing!=null&&foodGrowingPages()>1;
        previous.visible = next.visible = paging || safetyPaging || comfortPaging || growingPaging;
        previous.active = !waiting && ((paging && snapshot.residentPage() > 0)
                || (safetyPaging && safetyPage > 0) || (comfortPaging && comfortRoomPage > 0) || (growingPaging&&foodGrowingPage>0));
        next.active = !waiting && ((paging && snapshot.residentPage() + 1 < snapshot.residentPages())
                || (safetyPaging && safetyPage + 1 < safetyPages())
                || (comfortPaging && comfortRoomPage + 1 < snapshot.comfort().roomRecords().size()) || (growingPaging&&foodGrowingPage+1<foodGrowingPages()));
        retry.visible = !waiting && error != TownLedgerSnapshotPayload.Error.NONE;
        developmentButtons.forEach(button -> button.visible = activeTab == 2 && snapshot != null);
        boolean comfort = activeTab == 2 && activeDevelopment == DevelopmentSection.COMFORT && snapshot != null;
        comfortDetails.visible = comfort && !comfortRoomDetails && !snapshot.comfort().roomRecords().isEmpty();
        comfortBack.visible = comfort && comfortRoomDetails;
        boolean food=activeTab==2&&activeDevelopment==DevelopmentSection.FOOD&&snapshot!=null;
        foodReserves.visible=food;foodVarietyButton.visible=food;foodGrowingButton.visible=food;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        if (snapshot != null) PacketDistributor.sendToServer(new RequestTownLedgerPayload(hand,-1,requestId,snapshot.metadata().requestGeneration()));
        super.removed();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoveredText = null;
        // Screen.render invokes renderBackground before rendering the bookmark widgets.
        // Calling it after drawing pages would blur the finished pages a second time.
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredText != null) graphics.renderTooltip(font, hoveredText, mouseX, mouseY);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        drawBook(graphics);
        int half = bookWidth / 2;
        int column = half - 34;
        if (snapshot == null) {
            graphics.drawCenteredString(font, title, left + half, top + 20, INK);
            Component message = waiting ? Component.translatable("hometown.ledger.loading") : errorText();
            graphics.drawWordWrap(font, message, left + 25, top + 58, bookWidth - 50, INK);
        } else {
            line(graphics, Component.literal(snapshot.townName()), left + 18, top + 14, bookWidth - 36, INK, mouseX, mouseY);
            graphics.fill(left + 18, top + 29, left + bookWidth - 18, top + 30, 0xFFBCA27D);
            switch (activeTab) {
                case 0 -> overview(graphics, column, mouseX, mouseY);
                case 1 -> residents(graphics, column, mouseX, mouseY);
                case 2 -> development(graphics, column, mouseX, mouseY);
                case 3 -> history(graphics, column);
                default -> throw new IllegalStateException("Unknown tab");
            }
            if (waiting) graphics.drawCenteredString(font, Component.translatable("hometown.ledger.loading"), left + half, top + bookHeight - 21, MUTED);
            else {
                Component age=minecraft.level==null?Component.translatable("hometown.ledger.observed_hint"):
                    Component.translatable("hometown.ledger.observation_age",Math.max(0,minecraft.level.getGameTime()-snapshot.metadata().observedGameTime())/20);
                line(graphics,age,left+58,top+bookHeight-21,bookWidth-116,MUTED,mouseX,mouseY);
            }
        }
    }

    private void drawBook(GuiGraphics g) {
        int right = left + bookWidth, bottom = top + bookHeight, middle = left + bookWidth / 2;
        g.fill(left + 3, top + 5, right + 3, bottom + 5, 0x88000000);
        g.fill(left, top, right, bottom, 0xFF60412A);
        g.fill(left + 3, top + 3, right - 3, bottom - 3, 0xFFBEA17B);
        g.fill(left + 5, top + 4, middle, bottom - 6, 0xFFF2E3BE);
        g.fill(middle, top + 4, right - 5, bottom - 6, 0xFFF6E9CB);
        g.fill(left + 7, bottom - 6, right - 7, bottom - 5, 0xFF907653);
        g.fill(left + 9, bottom - 4, right - 9, bottom - 3, 0xFF907653);
        g.fill(middle - 3, top + 6, middle - 1, bottom - 8, 0xFFDBC79E);
        g.fill(middle - 1, top + 6, middle + 1, bottom - 8, 0xFFB69C73);
        g.fill(middle + 1, top + 6, middle + 4, bottom - 8, 0xFFE6D3AE);
    }

    private void overview(GuiGraphics g, int column, int mx, int my) {
        int x = left + 18, y = top + 40, right = left + bookWidth / 2 + 16;
        line(g, Component.translatable("hometown.ledger.founder", snapshot.founderName()), x, y, column, INK, mx, my);
        line(g, Component.translatable("hometown.ledger.day", snapshot.foundedDay()), x, y + 14, column, MUTED, mx, my);
        line(g, Component.translatable("hometown.ledger.dimension", dimension()), x, y + 38, column, INK, mx, my);
        var bell = snapshot.bell();
        line(g, Component.translatable("hometown.ledger.bell", bell.getX(), bell.getY(), bell.getZ()), x, y + 52, column, INK, mx, my);
        Component warning = switch (snapshot.bellState()) {
            case PRESENT -> Component.empty();
            case MISSING -> Component.translatable("hometown.ledger.bell_missing", bell.getX(), bell.getY(), bell.getZ());
            case UNAVAILABLE -> Component.translatable("hometown.ledger.bell_unavailable");
        };
        g.drawWordWrap(font, warning, x, y + 70, column, WARNING);
        String[] metrics = {"population", "beds", "employed", "professions"};
        int[] values = {snapshot.population(), snapshot.beds(), snapshot.employed(), snapshot.professionDiversity()};
        for (int i = 0; i < metrics.length; i++) {
            Object value = snapshot.availability() == SettlementStats.Availability.UNAVAILABLE ? "—" : values[i];
            line(g, Component.translatable("hometown.ledger.stat." + metrics[i], value), right, y + i * 19, column, INK, mx, my);
        }
        g.drawWordWrap(font, availabilityText(), right, y + 84, column, MUTED);
    }

    private void residents(GuiGraphics g, int column, int mx, int my) {
        if (snapshot.residents().isEmpty()) {
            g.drawWordWrap(font, snapshot.availability() == SettlementStats.Availability.UNAVAILABLE ? availabilityText()
                    : Component.translatable("hometown.ledger.no_residents"), left + 18, top + 48, column, MUTED);
        }
        for (int i = 0; i < snapshot.residents().size(); i++) {
            var resident = snapshot.residents().get(i);
            int x = left + 18 + (i / 2) * (bookWidth / 2);
            int y = top + 43 + (i % 2) * 44;
            Component name = resident.name().isBlank() ? Component.translatable("entity.minecraft.villager") : Component.literal(resident.name());
            line(g, name, x, y, column, INK, mx, my);
            line(g, Component.translatable(resident.professionKey()), x, y + 12, column, MUTED, mx, my);
            line(g, Component.translatable(resident.child() ? "hometown.ledger.child" : "hometown.ledger.adult"), x, y + 24, column, MUTED, mx, my);
        }
        g.drawCenteredString(font, Component.translatable("hometown.ledger.page", snapshot.residentPage() + 1, snapshot.residentPages()),
                left + bookWidth / 2, top + bookHeight - 40, MUTED);
        if (snapshot.availability() == SettlementStats.Availability.PARTIAL)
            line(g, availabilityText(), left + 18, top + bookHeight - 56, bookWidth - 36, WARNING, mx, my);
    }

    private void development(GuiGraphics g, int column, int mx, int my) {
        if (activeDevelopment == DevelopmentSection.FOOD) { food(g,column,mx,my); return; }
        if (activeDevelopment == DevelopmentSection.SAFETY) { safety(g,column,mx,my); return; }
        if (activeDevelopment == DevelopmentSection.COMFORT) { comfort(g,column,mx,my); return; }
        if (activeDevelopment != DevelopmentSection.HOUSING) {
            line(g, Component.translatable(activeDevelopment.translationKey()), left + 18, top + 60, column, INK, mx, my);
            g.drawWordWrap(font, Component.translatable("hometown.development.placeholder"), left + 18, top + 82, column, MUTED);
            return;
        }
        housing(g, column, mx, my);
    }

    private int safetyPages() {
        var s=snapshot.safety();
        int reasons=Math.max(s.entityReasonCounts().size(),s.lightingReasonCounts().size());
        return 1+(reasons+3)/4;
    }
    private void safety(GuiGraphics g,int column,int mx,int my) {
        var s=snapshot.safety();
        int x=left+18,right=left+bookWidth/2+18,y=top+58;
        line(g,Component.translatable("hometown.safety.title"),x,y,column,INK,mx,my);
        line(g,Component.translatable("hometown.safety.lighting"),right,y,column,INK,mx,my);
        if(safetyPage>0) {
            safetyReasons(g,s.entityReasonCounts(),x,y+20,column,mx,my);
            safetyReasons(g,s.lightingReasonCounts(),right,y+20,column,mx,my);
            return;
        }
        line(g,Component.translatable("hometown.safety.threat_observation"),x,y+13,column,INK,mx,my);
        String condition=switch(s.entityScanStatus()) {
            case DISABLED -> "disabled";
            case UNAVAILABLE -> "unavailable";
            case PARTIAL -> s.threatsObserved()==0?"partial_zero":"partial";
            case COMPLETE -> s.threatsObserved()==0?"zero":"present";
        };
        line(g,Component.translatable("hometown.safety."+condition),x,y+27,column,MUTED,mx,my);
        int entityY=y+43;
        if(s.entityScanStatus()!=dev.conner.hometown.safety.SafetySnapshot.Status.UNAVAILABLE && s.enabled()) {
            line(g,Component.translatable("hometown.safety.threats",s.threatsObserved()),x,entityY,column,INK,mx,my);
            entityY+=14;
            if(!s.countsByThreatType().isEmpty()) {
                line(g,safetyThreatTypes(s.countsByThreatType()),x,entityY,column,MUTED,mx,my);
                entityY+=14;
            }
            line(g,Component.translatable("hometown.safety.protectors",s.protectorsObserved()),x,entityY,column,INK,mx,my);
            entityY+=18;
        } else entityY=y+75;
        line(g,Component.translatable("hometown.safety.observed"),x,entityY,column,MUTED,mx,my);
        if(!s.entityReasonCounts().isEmpty()) {
            var reason=new java.util.TreeMap<>(s.entityReasonCounts()).firstKey();
            line(g,Component.translatable("hometown.safety.reason."+reason.name().toLowerCase(java.util.Locale.ROOT)),
                x,entityY+15,column,WARNING,mx,my);
        }
        var percent=s.residentialLightingPercent().isPresent()?s.residentialLightingPercent():s.observedLightingPercent();
        String percentKey=s.residentialLightingPercent().isPresent()?"percent":"observed_percent";
        Component value=!s.enabled()?Component.translatable("hometown.safety.disabled"):percent.isPresent()
            ?Component.translatable("hometown.safety."+percentKey,Math.round(percent.getAsDouble())):Component.literal("N/A");
        line(g,value,right,y+14,column,INK,mx,my);
        if(percent.isPresent()) {
            g.fill(right,y+26,right+column,y+30,0xFFBCA27D);
            g.fill(right,y+26,right+(int)Math.round(column*percent.getAsDouble()/100),y+30,0xFF817644);
        }
        if(!s.enabled()) return;
        if(s.lightingCondition()==dev.conner.hometown.safety.SafetySnapshot.LightingCondition.NO_ENCLOSED_BEDS)
            line(g,Component.translatable("hometown.safety.no_beds"),right,y+42,column,MUTED,mx,my);
        else {
            line(g,Component.translatable("hometown.safety.lit",s.litBedSamples()),right,y+38,column,INK,mx,my);
            line(g,Component.translatable("hometown.safety.unlit",s.unlitBedSamples()),right,y+51,column,INK,mx,my);
            line(g,Component.translatable("hometown.safety.assessed",s.assessedBedSamples(),s.expectedBedSamples()),right,y+64,column,INK,mx,my);
            if(s.unassessedBedSamples()>0)line(g,Component.translatable("hometown.safety.unassessed",s.unassessedBedSamples()),right,y+90,column,WARNING,mx,my);
            else if(s.lightingScanStatus()!=dev.conner.hometown.safety.SafetySnapshot.Status.COMPLETE)
                line(g,Component.translatable("hometown.safety.incomplete"),right,y+90,column,WARNING,mx,my);
        }
        line(g,Component.translatable(s.minimumBlockLight()==0?"hometown.safety.threshold_zero":"hometown.safety.threshold",s.minimumBlockLight()),right,y+77,column,MUTED,mx,my);
        line(g,Component.translatable("hometown.safety.scope"),right,y+105,column,MUTED,mx,my);
    }
    private void safetyReasons(GuiGraphics g,java.util.Map<dev.conner.hometown.safety.SafetySnapshot.Reason,Integer> reasons,
            int x,int y,int column,int mx,int my) {
        var entries=new java.util.ArrayList<>(new java.util.TreeMap<>(reasons).entrySet());
        int start=(safetyPage-1)*4;
        for(int i=start;i<Math.min(start+4,entries.size());i++) {
            var e=entries.get(i);
            line(g,Component.translatable("hometown.safety.reason."+e.getKey().name().toLowerCase(java.util.Locale.ROOT))
                .append(" ("+e.getValue()+")"),x,y+(i-start)*20,column,WARNING,mx,my);
        }
    }
    private Component safetyThreatTypes(java.util.Map<String,Integer> counts) {
        var entries=new java.util.ArrayList<>(counts.entrySet());
        entries.sort(java.util.Comparator.<java.util.Map.Entry<String,Integer>>comparingInt(java.util.Map.Entry::getValue)
                .reversed().thenComparing(java.util.Map.Entry::getKey));
        var text=new StringBuilder();
        for(var entry:entries) {
            if(!text.isEmpty())text.append(", ");
            text.append(entityTypeLabel(entry.getKey())).append(" ×").append(entry.getValue());
        }
        return Component.literal(text.toString());
    }
    private static String entityTypeLabel(String id) {
        int colon=id.indexOf(':');
        String path=colon>=0?id.substring(colon+1):id;
        var words=path.replace('_',' ').split(" ");
        var text=new StringBuilder();
        for(String word:words) {
            if(word.isEmpty())continue;
            if(!text.isEmpty())text.append(' ');
            text.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return text.isEmpty()?id:text.toString();
    }

    private void comfort(GuiGraphics g,int column,int mx,int my) {
        var c=snapshot.comfort();
        int x=left+18,right=left+bookWidth/2+18,y=top+58;
        line(g,Component.translatable("hometown.comfort.title"),x,y,column,INK,mx,my);
        java.util.OptionalDouble shown=c.residentialComfortPercent().isPresent()?c.residentialComfortPercent():c.observedRoomComfortPercent();
        String scoreKey=c.residentialComfortPercent().isPresent()?"hometown.comfort.residential":"hometown.comfort.observed";
        line(g,Component.translatable(scoreKey),x,y+14,column,INK,mx,my);
        line(g,shown.isPresent()?Component.translatable("hometown.comfort.percent",roundPercent(shown.getAsDouble())):Component.literal("N/A"),
                x,y+28,column,c.scanStatus()==dev.conner.hometown.comfort.ComfortSnapshot.Status.COMPLETE?INK:WARNING,mx,my);
        if(c.residentialComfortPercent().isPresent()&&c.band().isPresent())
            line(g,Component.translatable("hometown.comfort.band."+c.band().get().name().toLowerCase(java.util.Locale.ROOT)),x,y+42,column,INK,mx,my);
        else if(c.condition()!=dev.conner.hometown.comfort.ComfortSnapshot.Condition.OBSERVED)
            line(g,Component.translatable("hometown.comfort.condition."+c.condition().name().toLowerCase(java.util.Locale.ROOT)),x,y+42,column,MUTED,mx,my);
        if(shown.isPresent()) {
            g.fill(x,y+55,x+column,y+60,0xFFBCA27D);
            int filled=(int)Math.round(column*Math.clamp(shown.getAsDouble(),0,100)/100.0);
            if(filled>0)g.fill(x,y+55,x+filled,y+60,0xFF817644);
        }
        Object beds=c.expectedEnclosedBeds().isPresent()?c.expectedEnclosedBeds().getAsInt():c.assessedEnclosedBeds();
        line(g,Component.translatable(c.expectedEnclosedBeds().isPresent()?"hometown.comfort.enclosed_beds":"hometown.comfort.known_enclosed_beds",beds),x,y+72,column,INK,mx,my);
        Component rooms=c.expectedRooms().isPresent()?Component.translatable("hometown.comfort.rooms_assessed",c.assessedRooms(),c.expectedRooms().getAsInt())
                :Component.translatable("hometown.comfort.rooms_assessed_unknown",c.assessedRooms());
        line(g,rooms,x,y+86,column,INK,mx,my);
        if(!c.reasonCounts().isEmpty()) {
            var reason=new java.util.TreeMap<>(c.reasonCounts()).firstKey();
            line(g,Component.translatable("hometown.comfort.reason."+reason.name().toLowerCase(java.util.Locale.ROOT)),x,y+102,column,WARNING,mx,my);
        }

        if(comfortRoomDetails) comfortRoom(g,c,right,y,column,mx,my);
        else comfortCategories(g,c,right,y,column,mx,my);
    }

    private void comfortCategories(GuiGraphics g,dev.conner.hometown.comfort.ComfortSnapshot c,int x,int y,int column,int mx,int my) {
        line(g,Component.translatable("hometown.comfort.categories"),x,y,column,INK,mx,my);
        int row=0;
        for(var category:dev.conner.hometown.comfort.ComfortCategory.values()) {
            var setting=c.categories().get(category);
            if(setting==null||!setting.enabled())continue;
            line(g,Component.translatable("hometown.comfort.category."+category.id(),c.categoryCoverage().getOrDefault(category,0),c.assessedRooms()),
                    x,y+15+row*12,column,INK,mx,my);
            row++;
        }
        if(c.enabledWeight()==0)line(g,Component.translatable("hometown.comfort.no_enabled_weight"),x,y+15,column,MUTED,mx,my);
    }

    private void comfortRoom(GuiGraphics g,dev.conner.hometown.comfort.ComfortSnapshot c,int x,int y,int column,int mx,int my) {
        if(c.roomRecords().isEmpty()) {
            line(g,Component.translatable("hometown.comfort.no_rooms"),x,y,column,MUTED,mx,my);
            return;
        }
        int page=Math.clamp(comfortRoomPage,0,c.roomRecords().size()-1);
        var room=c.roomRecords().get(page);
        line(g,Component.translatable("hometown.comfort.room",page+1,c.roomRecords().size()),x,y,column,INK,mx,my);
        line(g,Component.translatable("hometown.comfort.room_beds",room.enclosedBeds()),x,y+13,column,INK,mx,my);
        line(g,room.score().isPresent()?Component.translatable("hometown.comfort.room_score",roundPercent(room.score().getAsDouble()))
                :Component.translatable("hometown.comfort.room_score_na"),x,y+26,column,INK,mx,my);
        if(room.band().isPresent())line(g,Component.translatable("hometown.comfort.band."+room.band().get().name().toLowerCase(java.util.Locale.ROOT)),x,y+39,column,MUTED,mx,my);
        int row=0;
        for(var category:dev.conner.hometown.comfort.ComfortCategory.values()) {
            var setting=c.categories().get(category);
            if(setting==null||!setting.enabled())continue;
            var presence=room.categoryPresence().get(category);
            line(g,Component.translatable("hometown.comfort.room_category",Component.translatable("hometown.comfort.category_name."+category.id()),
                    Component.translatable("hometown.comfort.presence."+presence.name().toLowerCase(java.util.Locale.ROOT))),
                    x,y+53+row*10,column,presence==dev.conner.hometown.comfort.ComfortSnapshot.Presence.UNKNOWN?WARNING:INK,mx,my);
            row++;
        }
        if(!room.reasonCounts().isEmpty()) {
            var reason=new java.util.TreeMap<>(room.reasonCounts()).firstKey();
            line(g,Component.translatable("hometown.comfort.reason."+reason.name().toLowerCase(java.util.Locale.ROOT)),x,y+57+row*10,column,WARNING,mx,my);
        }
    }

    private static int roundPercent(double value) { return (int)Math.floor(value+0.5d); }

    private void food(GuiGraphics g, int column, int mx, int my) {
        if(foodSection==1){if(foodVariety==null)foodM3Waiting(g,column,mx,my,"hometown.food.variety.title");else FoodM3LedgerView.variety(g,font,foodVariety,left+18,left+bookWidth/2+18,top+72,column,INK,MUTED,WARNING);return;}
        if(foodSection==2){if(foodGrowing==null)foodM3Waiting(g,column,mx,my,"hometown.food.growing.title");else FoodM3LedgerView.growing(g,font,foodGrowing,foodGrowingPage,left+18,left+bookWidth/2+18,top+72,column,INK,MUTED,WARNING);return;}
        int x=left+18, right=left+bookWidth/2+18, y=top+72;
        var f=snapshot.food();
        boolean partial=f.scanStatus()==dev.conner.hometown.food.FoodScanStatus.PARTIAL;
        line(g,Component.translatable("hometown.food.security"),x,y,column,INK,mx,my);
        line(g,Component.translatable(partial?"hometown.food.known_stores":"hometown.food.stores"),right,y,column,INK,mx,my);
        Component status=f.scanComplete()?Component.translatable("hometown.food.state."+f.state().orElseThrow().name().toLowerCase(java.util.Locale.ROOT))
                :Component.translatable(partial?"hometown.food.partial":"hometown.food.data_unavailable");
        line(g,status,x,y+12,column,f.scanComplete()?INK:WARNING,mx,my);
        if(!f.scanComplete()) line(g,Component.translatable("hometown.food.reason."+(f.diagnostics().primaryReason()==dev.conner.hometown.food.FoodScanReason.UNLOADED_CHUNKS && f.diagnostics().unavailableChunks()==1
                        ?"unloaded_chunk":f.diagnostics().primaryReason().name().toLowerCase(java.util.Locale.ROOT)),
                f.diagnostics().unavailableChunks()),x,y+24,column,MUTED,mx,my);
        if(f.scanStatus()==dev.conner.hometown.food.FoodScanStatus.UNAVAILABLE) {
            line(g,Component.translatable("hometown.food.unavailable"),x,y+44,column,MUTED,mx,my);return;
        }
        line(g,Component.translatable(partial?"hometown.food.known_reserves":"hometown.food.reserves"),x,y+38,column,INK,mx,my);
        if(f.barPercent().isPresent()) {
            g.fill(x,y+49,x+column,y+54,0xFFBCA27D);
            int filled=column*f.barPercent().getAsInt()/100;
            if(filled>0)g.fill(x,y+49,x+filled,y+54,0xFF817644);
        }
        Component duration=Component.literal("N/A");
        if(f.reserveDays().isPresent()) {
            double days=f.reserveDays().getAsDouble();
            if(partial) days=Math.floor(days*10)/10;
            duration=Component.translatable(partial?"hometown.food.at_least_days":"hometown.food.days",String.format(java.util.Locale.ROOT,"%.1f",days));
        }
        line(g,duration,x,y+60,column,INK,mx,my);
        String[] supplyKeys={f.diagnostics().populationComplete()?"residents":"known_residents",f.diagnostics().populationComplete()?"daily":"known_daily",partial?"known_nutrition":"nutrition"};
        long[] supply={f.population(),f.dailyNutritionRequirement(),f.totalNutrition()};
        for(int i=0;i<supplyKeys.length;i++) line(g,Component.translatable("hometown.food."+supplyKeys[i],String.format(java.util.Locale.ROOT,"%,d",supply[i])),
                x,y+76+i*14,column,INK,mx,my);
        String[] storeKeys={"containers","stacks","types",partial?"known_nutrition":"nutrition"};
        long[] stores={f.foodContainers(),f.foodStacks(),f.uniqueFoodTypes(),f.totalNutrition()};
        for(int i=0;i<storeKeys.length;i++) line(g,Component.translatable("hometown.food."+storeKeys[i],String.format(java.util.Locale.ROOT,"%,d",stores[i])),
                right,y+28+i*18,column,INK,mx,my);
        if(partial) line(g,Component.translatable("hometown.food.known_only"),right,y+106,column,MUTED,mx,my);
    }
    private void foodM3Waiting(GuiGraphics g,int column,int mx,int my,String titleKey){
        line(g,Component.translatable(titleKey),left+18,top+72,column,INK,mx,my);line(g,Component.translatable("hometown.food.m3_waiting"),left+18,top+92,bookWidth-36,MUTED,mx,my);
    }

    private void housing(GuiGraphics g, int column, int mx, int my) {
        int x = left + 18, right = left + bookWidth / 2 + 18, y = top + 58;
        var h = snapshot.housing();
        line(g, Component.translatable("hometown.housing.supply"), x, y, column, INK, mx, my);
        line(g, Component.translatable("hometown.housing.privacy_title"), right, y, column, INK, mx, my);
        line(g, Component.translatable("hometown.housing.state." + h.state().name().toLowerCase(java.util.Locale.ROOT)),
                x, y + 12, column, h.scanComplete() ? INK : WARNING, mx, my);
        if (!h.scanComplete()) {
            g.drawWordWrap(font, Component.translatable("hometown.housing.unavailable_detail"), x, y + 42, column, MUTED);
            line(g, Component.translatable("hometown.housing.capacity", "N/A"), x, y + 24, column, MUTED, mx, my);
            line(g, Component.translatable("hometown.housing.privacy", "N/A"), right, y + 24, column, MUTED, mx, my);
            return;
        }
        percent(g, "hometown.housing.capacity", h.capacityPercent(), x, y + 24, column, mx, my);
        percent(g, "hometown.housing.privacy", h.privacyPercent(), right, y + 24, column, mx, my);
        String[] supplyKeys = {"residents", "enclosed", "unhoused", "spare", "total", "unsealed"};
        int[] supply = {h.population(),h.enclosedBeds(),h.unhousedResidents(),h.spareHousingCapacity(),h.totalBeds(),h.unsealedBeds()};
        String[] roomKeys = {"rooms", "private", "shared", "crowded", "high_density"};
        int[] rooms = {h.roomCount(),h.privateRooms(),h.sharedRooms(),h.crowdedRooms(),h.highDensityRooms()};
        for (int i=0; i<supplyKeys.length; i++) line(g, Component.translatable("hometown.housing." + supplyKeys[i], supply[i]),
                x, y + 48 + i * 12, column, INK, mx, my);
        for (int i=0; i<roomKeys.length; i++) line(g, Component.translatable("hometown.housing." + roomKeys[i], rooms[i]),
                right, y + 48 + i * 12, column, INK, mx, my);
    }

    private void percent(GuiGraphics g, String key, java.util.OptionalInt value, int x, int y, int width, int mx, int my) {
        line(g, Component.translatable(key, value.isPresent() ? value.getAsInt() + "%" : "N/A"), x, y, width, INK, mx, my);
        if (value.isEmpty()) return;
        g.fill(x, y + 11, x + width, y + 16, 0xFFBCA27D);
        int filled = width * Math.clamp(value.getAsInt(), 0, 100) / 100;
        if (filled > 0) g.fill(x, y + 11, x + filled, y + 16, 0xFF817644);
    }

    private void history(GuiGraphics g, int column) {
        g.drawString(font, Component.translatable("hometown.ledger.history_day", snapshot.foundedDay()), left + 18, top + 43, MUTED, false);
        g.drawWordWrap(font, Component.translatable("hometown.ledger.founding_event", snapshot.townName(), snapshot.founderName()),
                left + 18, top + 64, column, INK);
    }

    private Component dimension() {
        return switch (snapshot.dimension()) {
            case "minecraft:overworld" -> Component.translatable("hometown.dimension.overworld");
            case "minecraft:the_nether" -> Component.translatable("hometown.dimension.nether");
            case "minecraft:the_end" -> Component.translatable("hometown.dimension.end");
            default -> Component.literal(snapshot.dimension());
        };
    }
    private Component availabilityText() {
        return Component.translatable(switch (snapshot.availability()) {
            case COMPLETE -> "hometown.ledger.fresh";
            case PARTIAL -> "hometown.ledger.partial";
            case UNAVAILABLE -> "hometown.ledger.unavailable";
        });
    }
    private Component errorText() {
        return Component.translatable(switch (error) {
            case UNKNOWN -> "hometown.ledger.unknown";
            case HOLD_LEDGER -> "hometown.ledger.hold";
            case WAIT -> "hometown.ledger.wait";
            default -> "hometown.ledger.failed";
        });
    }

    private void line(GuiGraphics g, Component text, int x, int y, int maxWidth, int color, int mx, int my) {
        String value = text.getString();
        boolean shortened = font.width(value) > maxWidth;
        String shown = shortened ? font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…" : value;
        g.drawString(font, shown, x, y, color, false);
        if (shortened && mx >= x && mx < x + maxWidth && my >= y && my < y + 11) hoveredText = text;
    }

    private final class Bookmark extends Button {
        private final java.util.function.BooleanSupplier selection;
        private Bookmark(int x, int y, int w, int h, Component label, OnPress press, int tab) {
            this(x, y, w, h, label, press, () -> tab >= 0 && tab == activeTab);
        }
        private Bookmark(int x, int y, int w, int h, Component label, OnPress press, java.util.function.BooleanSupplier selection) {
            super(x, y, w, h, label, press, DEFAULT_NARRATION);
            this.selection = selection;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
            boolean selected = selection.getAsBoolean();
            int edge = isHoveredOrFocused() ? 0xFF8C4632 : 0xFF927652;
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), edge);
            g.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
                    selected ? 0xFFF6E9CB : 0xFFD8BF93);
            if (selected) g.fill(getX() + 5, getY() + getHeight() - 4, getX() + getWidth() - 5, getY() + getHeight() - 3, 0xFF99513B);
            String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, getWidth() - 2));
            g.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2, getY() + (getHeight() - 8) / 2,
                    active ? INK : MUTED, false);
        }
    }
}
