package com.xauusdpro99.monitoring;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Schedules a best-effort health check after boot; does not illegally auto-start a market FGS. */
public final class MonitoringBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent==null)return;
  String action=intent.getAction();
  if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){
   MonitoringWorkScheduler.schedule(context);
   context.getSharedPreferences("monitoring_runtime",Context.MODE_PRIVATE).edit()
    .putString("boot_status","DEVICE RESTARTED — background market service not auto-started; open app and start monitoring")
    .putBoolean("service_running",false).apply();
  }
 }
}
