package dev.conner.hometown.history;

import dev.conner.hometown.config.HometownServerConfig;

public record HistorySettings(boolean enabled,int maxEventsPerTown,int confirmationTicks,int coalesceTicks) {
    public HistorySettings {
        if(maxEventsPerTown<8||maxEventsPerTown>4096||confirmationTicks<200||confirmationTicks>24000||coalesceTicks<0||coalesceTicks>24000)
            throw new IllegalArgumentException("Invalid History settings");
    }
    public static HistorySettings defaults(){return new HistorySettings(true,256,200,1200);}
    public static HistorySettings current(){return new HistorySettings(HometownServerConfig.HISTORY_ENABLED.get(),HometownServerConfig.HISTORY_MAX_EVENTS.get(),HometownServerConfig.HISTORY_CONFIRMATION_TICKS.get(),HometownServerConfig.HISTORY_COALESCE_TICKS.get());}
}
