package com.xauusdpro99.monitoring;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
/** Periodic health check only; intentionally does not start a foreground service from background. */
public final class MonitoringMaintenanceWorker extends Worker {
 public MonitoringMaintenanceWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
 @NonNull @Override public Result doWork(){
  Context c=getApplicationContext();
  android.content.SharedPreferences settings=c.getSharedPreferences("monitoring_settings",Context.MODE_PRIVATE);
  android.content.SharedPreferences runtime=c.getSharedPreferences("monitoring_runtime",Context.MODE_PRIVATE);
  boolean enabled=settings.getBoolean("background_monitor_enabled",false);
  boolean running=runtime.getBoolean("service_running",false);
  runtime.edit().putLong("workmanager_check_at",System.currentTimeMillis())
    .putString("workmanager_status",!enabled?"MONITOR DISABLED":running?"FOREGROUND SERVICE REPORTED RUNNING":"SERVICE NOT RUNNING — user action required").apply();
  return Result.success();
 }
}
