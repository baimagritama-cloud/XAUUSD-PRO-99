package com.xauusdpro99.data.alert;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
public class MarketAlertEngineTest {
 @Test public void alertCatalogContainsEveryRequiredEvent(){
  Set<MarketAlertEngine.Type> all=EnumSet.allOf(MarketAlertEngine.Type.class);
  assertEquals(23,all.size());
  for(MarketAlertEngine.Type t:MarketAlertEngine.Type.values())assertNotNull(t.name());
 }
 @Test public void staleInputCannotProduceAlerts(){
  MarketAlertEngine e=new MarketAlertEngine();
  MarketAlertEngine.Input in=new MarketAlertEngine.Input(Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),System.currentTimeMillis());
  assertTrue(e.evaluate(in).isEmpty());
 }
 @Test public void cooldownIsFiveMinutes(){assertEquals(300000L,MarketAlertEngine.COOLDOWN_MS);}
}
