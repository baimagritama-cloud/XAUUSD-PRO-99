package com.xauusdpro99.data.signal;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class MarketJourneyEngineTest {
 @Test public void invalidQuoteProducesUnknownAndNoInventedLevels(){MarketJourneyEngine.Result r=new MarketJourneyEngine().analyze(new MarketJourneyEngine.Input(Double.NaN,Double.NaN,Double.NaN,Double.NaN,false,false,0,1800000000000L,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap()));
 assertFalse(r.dataValid);assertEquals(MarketJourneyEngine.PriceState.UNKNOWN,r.state);assertEquals(MarketJourneyEngine.Safety.UNKNOWN,r.safety);assertTrue(r.nearbyLevels.isEmpty());assertTrue(Double.isNaN(r.currentPrice));}
}