package com.xauusdpro99.notification;
import java.util.*;import java.util.Calendar;
/** Pure policy layer for cooldown and quiet-hours testing. */
public final class NotificationPolicy{
 private final Map<String,Long> lastDelivered=new HashMap<>();
 public synchronized boolean accept(String type,String timeframe,long now,boolean fresh,boolean enabled,int minutes){if(!fresh||!enabled||type==null||timeframe==null||now<=0)return false;long cd=Math.max(0,Math.min(1440,minutes))*60000L;String key=type+":"+timeframe;Long p=lastDelivered.get(key);if(p!=null&&(now<p||now-p<cd))return false;lastDelivered.put(key,now);return true;}
 public static boolean isQuietHours(int minute,int start,int end){int n=Math.floorMod(minute,1440),s=Math.floorMod(start,1440),e=Math.floorMod(end,1440);if(s==e)return false;return s<e?n>=s&&n<e:n>=s||n<e;}
 public static int currentMinuteOfDay(){Calendar c=Calendar.getInstance();return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}
}