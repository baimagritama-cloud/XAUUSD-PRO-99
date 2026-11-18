package com.xauusdpro99.monitoring;
/** Pure heartbeat policy: a stale process flag must never be presented as a live service. */
public final class MonitoringStatePolicy {
 private MonitoringStatePolicy(){}
 public static boolean isAlive(boolean enabled,boolean reportedRunning,long heartbeatMs,long nowMs){
  return enabled&&reportedRunning&&heartbeatMs>0&&nowMs>=heartbeatMs&&nowMs-heartbeatMs<=180_000L;
 }
}
