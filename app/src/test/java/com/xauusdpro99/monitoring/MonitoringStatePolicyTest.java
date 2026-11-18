package com.xauusdpro99.monitoring;
import org.junit.Test;
import static org.junit.Assert.*;
public class MonitoringStatePolicyTest {
 @Test public void freshHeartbeatMeansAlive(){assertTrue(MonitoringStatePolicy.isAlive(true,true,90_000,100_000));}
 @Test public void staleHeartbeatMeansNotAlive(){assertFalse(MonitoringStatePolicy.isAlive(true,true,1,200_000));}
 @Test public void disabledOrFutureHeartbeatMeansNotAlive(){
  assertFalse(MonitoringStatePolicy.isAlive(false,true,90_000,100_000));
  assertFalse(MonitoringStatePolicy.isAlive(true,true,101_000,100_000));
 }
}
