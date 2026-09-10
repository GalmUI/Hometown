package dev.conner.hometown.safety;

import java.util.OptionalDouble;
import static dev.conner.hometown.safety.SafetySnapshot.*;

/** Pure classification and arithmetic over copied observations. */
public final class SafetyEvaluator {
    private SafetyEvaluator() {}
    public enum Classification { THREAT, PROTECTOR, NEITHER }
    public static Classification classify(boolean player, boolean threat, boolean excludedThreat, boolean protector, boolean excludedProtector) {
        if(player) return Classification.NEITHER;
        if(threat&&!excludedThreat) return Classification.THREAT;
        return protector&&!excludedProtector?Classification.PROTECTOR:Classification.NEITHER;
    }
    public static Status status(boolean complete,boolean meaningful) {
        return complete?Status.COMPLETE:meaningful?Status.PARTIAL:Status.UNAVAILABLE;
    }
    public static OptionalDouble coverage(int lit,int assessed) {
        return assessed==0?OptionalDouble.empty():OptionalDouble.of(100.0*lit/assessed);
    }
}
