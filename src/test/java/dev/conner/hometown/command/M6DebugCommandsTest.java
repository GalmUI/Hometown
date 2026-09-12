package dev.conner.hometown.command;

import dev.conner.hometown.settlement.LedgerPerformanceProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class M6DebugCommandsTest {
    @Test void cachedProfileFormatIsBoundedReadableAndUsesMilliseconds(){
        var p=new LedgerPerformanceProfile(UUID.fromString("00000000-0000-0000-0000-000000000006"),"Profileton","minecraft:overworld",
                new BlockPos(-12,70,34),64,9,1000,12_345_678,10,12,3,3,5,5,17,21,40,4096,900,262144,2,16,512);
        String out=M6DebugCommands.format(p,1040);
        assertTrue(out.contains("12.346 ms"));assertTrue(out.contains("ageTicks=40"));
        assertTrue(out.contains("Loaded town chunks: 17 / 21"));assertTrue(out.contains("Shared new-block work: 900 / 262144"));
        assertTrue(out.contains("this command ran no scan"));
    }
}
