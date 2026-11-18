package com.xauusdpro99.monitoring;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public final class MonitoringWorkScheduler {
 private MonitoringWorkScheduler(){}
 public static void schedule(Context c){
  Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
  PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(MonitoringMaintenanceWorker.class,15,TimeUnit.MINUTES)
    .setConstraints(constraints).addTag("xauusd-monitor-maintenance").build();
  WorkManager.getInstance(c).enqueueUniquePeriodicWork("xauusd-monitor-maintenance",ExistingPeriodicWorkPolicy.KEEP,request);
 }
}
