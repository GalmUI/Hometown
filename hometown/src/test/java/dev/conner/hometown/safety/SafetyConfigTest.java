package dev.conner.hometown.safety;

import dev.conner.hometown.TestServerConfig;
import dev.conner.hometown.config.HometownServerConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafetyConfigTest {
    @Test void exactDefaultsAndAcceptedRanges() {
        try(var config=new TestServerConfig()) {
            assertTrue(HometownServerConfig.SAFETY_ENABLED.get());
            assertEquals(new SafetyCollector.Rules(true,1,4096,4096,262144),SafetyCollector.Rules.current());
            assertEquals(40,HometownServerConfig.REQUEST_COOLDOWN.get());
            check("safety.minimumResidentialBlockLight",0,15);
            check("safety.maxEntityInspections",128,65536);
            check("scan.maxNewEntityInspections",128,65536);
            check("scan.maxNewBlockInspections",1024,1048576);
            check("ledger.requestCooldownTicks",1,1200);
        }
    }
    private void check(String path,int min,int max) {
        net.neoforged.neoforge.common.ModConfigSpec.ValueSpec rule=HometownServerConfig.SPEC.getSpec().get(path);
        assertTrue(rule.test(min));assertTrue(rule.test(max));
        assertFalse(rule.test(min-1));assertFalse(rule.test(max+1));assertFalse(rule.test("invalid"));
    }
}
