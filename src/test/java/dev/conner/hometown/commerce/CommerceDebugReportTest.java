package dev.conner.hometown.commerce;

import dev.conner.hometown.observation.ObservationMetadata;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommerceDebugReportTest {
    @Test void detailRowsAreBoundedToThirtyTwoAndTotalsRemainVisible() {
        var professions=new LinkedHashMap<ResourceLocation,Integer>();
        for(int i=0;i<33;i++)professions.put(ResourceLocation.fromNamespaceAndPath("example",String.format(Locale.ROOT,"profession_%02d",i)),1);
        var snapshot=new CommerceSnapshot(new ObservationMetadata(UUID.randomUUID(),"minecraft:overworld",1,200,1,2,3),true,
                CommerceSnapshot.Status.COMPLETE,Map.of(),33,33,33,0,0,0,OptionalDouble.of(100),OptionalDouble.empty(),
                CommerceSnapshot.EmploymentState.FULLY_EMPLOYED,33,professions,33,0);
        String first=CommerceDebugReport.format(snapshot,0),second=CommerceDebugReport.format(snapshot,1);
        assertTrue(first.contains("Profession diversity: 33"));assertTrue(first.contains("Resident inspections: 33"));
        assertTrue(second.contains("Profession diversity: 33"));assertTrue(second.contains("Resident inspections: 33"));
        assertTrue(first.contains("example:profession_00 = 1"));assertTrue(first.contains("example:profession_31 = 1"));assertFalse(first.contains("example:profession_32 = 1"));
        assertFalse(second.contains("example:profession_31 = 1"));assertTrue(second.contains("example:profession_32 = 1"));
        assertTrue(first.contains("Profession detail page: 0 / 1"));assertTrue(second.contains("Profession detail page: 1 / 1"));
    }
}
