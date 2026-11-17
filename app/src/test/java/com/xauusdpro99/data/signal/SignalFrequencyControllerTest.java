package com.xauusdpro99.data.signal;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SignalFrequencyControllerTest {
    private SignalScoringEngine.Result result(SignalScoringEngine.Direction d, int score,
            SignalScoringEngine.Status status, String key, long time) {
        return new SignalScoringEngine.Result(d,score,SignalScoringEngine.Quality.EARLY_LOWER_QUALITY,
                status,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
                "Bullish Engulfing",key,2000,1998,2005,2.5,.2,time);
    }

    @Test public void scoreBelow57NeverPublished() {
        SignalFrequencyController c=new SignalFrequencyController();
        SignalFrequencyController.Decision d=c.process(Collections.singletonList(
                result(SignalScoringEngine.Direction.BUY,56,SignalScoringEngine.Status.NO_TRADE,
                        "BUY|M1|Bullish Engulfing|1800000000000",1800000000000L)),1800000000000L).get(0);
        assertFalse(d.published);
        assertTrue(d.reason.contains("di bawah 57"));
    }

    @Test public void samePatternAcrossTimeframesIsDeduplicated() {
        SignalFrequencyController c=new SignalFrequencyController();
        long t=1800000000000L;
        c.process(Collections.singletonList(result(SignalScoringEngine.Direction.BUY,80,
                SignalScoringEngine.Status.BUY,"BUY|M1|Bullish Engulfing|"+t,t)),t);
        SignalFrequencyController.Decision second=c.process(Collections.singletonList(
                result(SignalScoringEngine.Direction.BUY,80,SignalScoringEngine.Status.BUY,
                        "BUY|M5|Bullish Engulfing|"+(t+60000),t+60000)),t+60000).get(0);
        assertFalse(second.published);
        assertTrue(second.reason.contains("Deduplikasi"));
    }

    @Test public void cooldownBlocksRepeatedDirection() {
        SignalFrequencyController c=new SignalFrequencyController();
        long t=1800000000000L;
        c.process(Collections.singletonList(result(SignalScoringEngine.Direction.BUY,80,
                SignalScoringEngine.Status.BUY,"BUY|M1|Hammer|"+t,t)),t);
        long later=t+6L*60L*60L*1000L;
        SignalFrequencyController.Decision second=c.process(Collections.singletonList(
                result(SignalScoringEngine.Direction.BUY,80,SignalScoringEngine.Status.BUY,
                        "BUY|M3|Shooting Star|"+later,later)),later).get(0);
        assertTrue(second.published);
        long shortly=later+5L*60L*1000L;
        SignalFrequencyController.Decision third=c.process(Collections.singletonList(
                result(SignalScoringEngine.Direction.BUY,80,SignalScoringEngine.Status.BUY,
                        "BUY|M2|Piercing Line|"+shortly,shortly)),shortly).get(0);
        assertFalse(third.published);
        assertTrue(third.reason.contains("Cooldown"));
    }

    @Test public void waitCannotBeUpgradedToEntry() {
        SignalFrequencyController c=new SignalFrequencyController();
        long t=1800000000000L;
        SignalFrequencyController.Decision d=c.process(Collections.singletonList(
                result(SignalScoringEngine.Direction.SELL,75,SignalScoringEngine.Status.WAIT,
                        "SELL|M1|Bearish Engulfing|"+t,t)),t).get(0);
        assertFalse(d.published);
    }
}
