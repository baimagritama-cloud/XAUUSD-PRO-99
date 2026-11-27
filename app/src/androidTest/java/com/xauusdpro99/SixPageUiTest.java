package com.xauusdpro99;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.xauusdpro99.monitoring.MarketMonitoringService;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public final class SixPageUiTest {
    private static final String PACKAGE = "com.xauusdpro99";
    private static final String[] PAGES = {
        "LIVE CHART", "MARKET JOURNEY", "INDICATOR CENTER",
        "SIGNAL CENTER", "NEWS", "NOTIFICATIONS"
    };

    @Test
    public void allSixPagesRenderInPortrait() throws Exception {
        UiDevice device = device();
        File output = outputDirectory();
        launchApp(device, true);
        exerciseChartControls(device);
        captureAllPages(device, output, "portrait");
        assertScreenshots(output, "portrait");
    }

    @Test
    public void allSixPagesRenderInLandscape() throws Exception {
        UiDevice device = device();
        File output = outputDirectory();
        launchApp(device, false);
        exerciseChartControls(device);
        captureAllPages(device, output, "landscape");
        assertScreenshots(output, "landscape");
    }

    @Test
    public void liveMarketAndBackgroundMonitoringRemainActiveAfterLeavingApp() throws Exception {
        UiDevice device = device();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Keep the complete integration test in the same landscape-only CI orientation.
        launchApp(device, false);

        // Exercise both orientations within the same running task.
        awaitOrientation(device, true);
        assertTrue("Portrait rotation did not complete", device.wait(Until.hasObject(By.text("LIVE CHART")), 30000L));
        assertTrue("Portrait dimensions incorrect", device.getDisplayHeight() > device.getDisplayWidth());
        awaitOrientation(device, false);
        assertTrue("Landscape rotation did not complete", device.wait(Until.hasObject(By.text("LIVE CHART")), 30000L));
        assertTrue("Landscape dimensions incorrect", device.getDisplayWidth() > device.getDisplayHeight());

        // Start the real production foreground service; no fake feed or disabled feature flags.
        MarketMonitoringService.start(context);
        Context app = context.getApplicationContext();
        long deadline = System.currentTimeMillis() + 45_000L;
        boolean running = false;
        String connection = "";
        while (System.currentTimeMillis() < deadline) {
            android.content.SharedPreferences runtime =
                app.getSharedPreferences("monitoring_runtime", Context.MODE_PRIVATE);
            running = runtime.getBoolean("service_running", false);
            connection = runtime.getString("connection_status", "");
            if (running && !connection.isEmpty()) break;
            Thread.sleep(500);
        }

        assertTrue("Foreground Background Monitoring service did not report RUNNING",
            app.getSharedPreferences("monitoring_runtime", Context.MODE_PRIVATE)
                .getBoolean("service_running", false));
        assertTrue("SignalR connection lifecycle did not publish a status (LIVE/STALE/RECONNECTING/ERROR)",
            !connection.isEmpty());

        // Verify the foreground service survives moving the UI task to the background.
        device.pressHome();
        Thread.sleep(3_000L);
        assertTrue("Background Monitoring stopped after leaving the app",
            app.getSharedPreferences("monitoring_runtime", Context.MODE_PRIVATE)
                .getBoolean("service_running", false));
        assertTrue("SignalR status was lost while the app was backgrounded",
            !app.getSharedPreferences("monitoring_runtime", Context.MODE_PRIVATE)
                .getString("connection_status", "").isEmpty());
    }

    private static UiDevice device() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    private static File outputDirectory() {
        // Use the instrumentation package's private cache. ExternalFilesDir may be null on
        // headless API-34 TCG emulators; private cache is always writable by the test UID.
        // The CI script exports this directory with run-as after instrumentation completes.
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File cache = context.getCacheDir();
        if (cache == null || (!cache.isDirectory() && !cache.mkdirs())) {
            throw new AssertionError("Instrumentation cache directory is unavailable: " + cache);
        }
        File output = new File(cache, "ui-screenshots");
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new AssertionError("Cannot create writable screenshot directory: " + output
                + "; cache=" + cache + "; exists=" + cache.exists()
                + "; writable=" + cache.canWrite());
        }
        if (!output.isDirectory() || !output.canWrite()) {
            throw new AssertionError("Screenshot directory is not writable: " + output);
        }
        return output;
    }

    private static void launchApp(UiDevice device, boolean portrait) throws Exception {
        device.pressHome();
        if (portrait) device.setOrientationPortrait();
        else device.setOrientationLandscape();
        device.executeShellCommand("pm grant " + PACKAGE + " android.permission.POST_NOTIFICATIONS");
        device.executeShellCommand("am start -W -n " + PACKAGE + "/.MainActivity");

        awaitOrientation(device, portrait);

        // Wait for a stable app-owned navigation element, not the REFRESH button.
        // The latter can be absent from the accessibility tree during first-frame/layout work.
        boolean packageVisible = device.wait(Until.hasObject(By.pkg(PACKAGE)), 60_000L);
        boolean mainUiReady = device.wait(Until.hasObject(By.text("LIVE CHART")), 60_000L);
        assertTrue("MainActivity did not reach its first usable UI; packageVisible="
            + packageVisible + "; liveChartTab=" + mainUiReady
            + "; foreground=" + device.getCurrentPackageName(),
            packageVisible && mainUiReady);
    }

    // UiDevice rotation requests are asynchronous; verify the actual display and
    // screenshot, not merely the presence of a navigation tab from the old layout.
    private static void awaitOrientation(UiDevice device, boolean portrait) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 30000L;
        File probe = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "rotation-probe.png");
        int stable = 0;
        try {
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                if (portrait) device.setOrientationPortrait();
                else device.setOrientationLandscape();
                device.waitForIdle(1000L);
                android.graphics.BitmapFactory.Options b = new android.graphics.BitmapFactory.Options();
                b.inJustDecodeBounds = true;
                boolean captured = device.takeScreenshot(probe);
                if (captured) android.graphics.BitmapFactory.decodeFile(probe.getAbsolutePath(), b);
                boolean displayMatches = portrait ? device.getDisplayHeight() > device.getDisplayWidth()
                    : device.getDisplayWidth() > device.getDisplayHeight();
                boolean screenshotMatches = captured && b.outWidth > 0 && b.outHeight > 0
                    && (portrait ? b.outHeight > b.outWidth : b.outWidth > b.outHeight);
                stable = displayMatches && screenshotMatches ? stable + 1 : 0;
                if (stable >= 2) return;
                Thread.sleep(500L);
            }
            throw new AssertionError("Rotation did not stabilize: portrait=" + portrait
                + "; display=" + device.getDisplayWidth() + "x" + device.getDisplayHeight());
        } finally { probe.delete(); }
    }

    private static void exerciseChartControls(UiDevice device) {
        click(device, "REFRESH");
        click(device, "REFRESH OHLC");
        exerciseTimeframes(device);
        clickTimeframe(device, "M1");
        click(device, "EMA: OFF");
        click(device, "INDICATOR: RSI14");
        assertTrue("Indicator selection did not change", device.wait(Until.hasObject(By.text("INDICATOR: MOM14")), 10000L));
        assertTrue("EMA selection did not change", device.wait(Until.hasObject(By.text("EMA: 10")), 10000L));
        UiObject2 chart=device.wait(Until.findObject(By.desc("LIVE_CANDLE_CHART")),10000L);
        assertTrue("Chart missing or too short", chart!=null && chart.getVisibleBounds().height() >= device.getDisplayHeight()/6);
        exerciseChartDrag(device, chart);
        exerciseChartPinch(device);
        click(device, "DETAIL OHLC");
        assertTrue("OHLC detail dialog missing", device.wait(Until.hasObject(By.textContains("OHLC — seluruh timeframe")), 10000L));
        click(device, "TUTUP");
        assertTrue("Chart controls unavailable after closing details", device.wait(Until.hasObject(By.text("DETAIL OHLC")), 10000L));
        device.waitForIdle();
    }

    private static android.view.View findChart(android.view.View view) {
        if(view instanceof LiveCandleChartView)return view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.view.View found=findChart(group.getChildAt(i));
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static int[] chartState(){
        int[] state={-1,-1,-1};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(android.app.Activity activity:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)){
                android.view.View view=findChart(activity.getWindow().getDecorView());
                if(view instanceof LiveCandleChartView){
                    LiveCandleChartView chart=(LiveCandleChartView)view;
                    state[0]=chart.chartClosedCount();state[1]=chart.chartPanBars();state[2]=chart.chartVisibleBars();break;
                }
            }
        });
        return state;
    }

    private static void exerciseChartDrag(UiDevice device, UiObject2 chart){
        long deadline=android.os.SystemClock.elapsedRealtime()+45000L;
        int[] before=chartState();
        while(before[0]<2 && android.os.SystemClock.elapsedRealtime()<deadline){
            android.os.SystemClock.sleep(500);before=chartState();
        }
        assertTrue("Drag requires real closed candles; count="+before[0],before[0]>=2);
        android.graphics.Rect b=chart.getVisibleBounds();
        int left=b.left+b.width()/10,right=b.left+b.width()/2,y=b.centerY();
        assertTrue("Chart drag injection failed",device.swipe(left,y,right,y,30));
        device.waitForIdle();
        int[] after=chartState();
        assertTrue("Drag did not move toward older candles: "+before[1]+" -> "+after[1],after[1]>before[1]);
        assertTrue("Reverse drag injection failed",device.swipe(right,y,left,y,30));
        device.waitForIdle();
        int[] restored=chartState();
        assertTrue("Reverse drag did not move toward newer candles",restored[1]<after[1]);
        android.util.Log.i("ChartUiTest","Real chart drag verified: "+before[1]+" -> "+after[1]+" -> "+restored[1]+"; closed="+after[0]);
    }

    private static void exerciseChartPinch(UiDevice device){
        UiObject2 chart=device.wait(Until.findObject(By.desc("LIVE_CANDLE_CHART")),10000L);
        assertTrue("Chart missing before pinch",chart!=null);
        int[] before=chartState();
        assertTrue("Pinch requires real closed candles",before[0]>=2);
        chart.setGestureMargin(Math.max(1,chart.getVisibleBounds().width()/10));
        chart.pinchOpen(0.75f,400);
        device.waitForIdle();
        int[] enlarged=chartState();
        assertTrue("Pinch open did not zoom in: "+before[2]+" -> "+enlarged[2],enlarged[2]<before[2]);
        assertTrue("Zoom below minimum",enlarged[2]>=10);
        chart=device.wait(Until.findObject(By.desc("LIVE_CANDLE_CHART")),10000L);
        assertTrue("Chart missing after pinch open",chart!=null);
        chart.setGestureMargin(Math.max(1,chart.getVisibleBounds().width()/10));
        chart.pinchClose(0.75f,400);
        device.waitForIdle();
        int[] reduced=chartState();
        assertTrue("Pinch close did not zoom out: "+enlarged[2]+" -> "+reduced[2],reduced[2]>enlarged[2]);
        assertTrue("Zoom above maximum",reduced[2]<=300);
        android.util.Log.i("ChartUiTest","Pinch viewport verified: "+before[2]+" -> "+enlarged[2]+" -> "+reduced[2]);
    }

    private static void exerciseTimeframes(UiDevice device) {
        for (com.xauusdpro99.data.candle.CandleTimeframe tf : com.xauusdpro99.data.candle.CandleTimeframe.values()) {
            clickTimeframe(device, tf.name());
            assertTrue("Chart missing after selecting "+tf.name(),
                device.wait(Until.hasObject(By.desc("LIVE_CANDLE_CHART")),10000L));
            android.util.Log.i("ChartUiTest","Timeframe button exercised: "+tf.name());
        }
    }

    private static void clickTimeframe(UiDevice device,String name) {
        for(int attempt=0;attempt<20;attempt++){
            UiObject2 strip=device.wait(Until.findObject(By.desc("CHART_TIMEFRAME_NAVIGATION")),5000L);
            assertTrue("Timeframe navigation missing",strip!=null);
            UiObject2 button=strip.findObject(By.text(name));
            if(button!=null && button.getVisibleBounds().width()>0){
                button.click();device.waitForIdle();return;
            }
            android.graphics.Rect b=strip.getVisibleBounds();
            int left=b.left+b.width()/8,right=b.right-b.width()/8;
            if(attempt<10)device.swipe(right,b.centerY(),left,b.centerY(),20);
            else device.swipe(left,b.centerY(),right,b.centerY(),20);
            device.waitForIdle();
        }
        throw new AssertionError("Timeframe button not reachable: "+name);
    }

    private static void captureAllPages(UiDevice device, File output, String orientation) throws Exception {
        File debug = new File(output, "debug");
        if (!debug.exists()) debug.mkdirs();
        for (String page : PAGES) {
            awaitOrientation(device, orientation.equals("portrait"));
            UiObject2 nav = waitForText(device, page, 3_000L);
            for (int attempt = 0; nav == null && attempt < 12; attempt++) {
                UiObject2 strip = device.wait(Until.findObject(By.desc("MAIN_PAGE_NAVIGATION")), 5_000L);
                assertTrue("Main navigation strip missing", strip != null);
                android.graphics.Rect bounds = strip.getVisibleBounds();
                int left = bounds.left + bounds.width()/8;
                int right = bounds.right - bounds.width()/8;
                int y = bounds.centerY();
                if (attempt < 6) device.swipe(right, y, left, y, 25);
                else device.swipe(left, y, right, y, 25);
                device.waitForIdle(2_000L);
                nav = waitForText(device, page, 1_500L);
            }
            if (nav == null) {
                device.takeScreenshot(new File(debug, orientation + "_missing_" + page.replace(' ', '_') + ".png"));
                device.dumpWindowHierarchy(new File(debug, orientation + "_missing_" + page.replace(' ', '_') + ".xml"));
                throw new AssertionError("Navigation tab not found: " + page);
            }
            nav.click();
            device.waitForWindowUpdate(PACKAGE, 5_000L);
            device.waitForIdle(5_000L);
            // Anchors are matched against both initial and live-rendered TextViews
            // in MainActivity. Indicator and signal summaries change their full heading
            // after data arrives, so use stable shared keywords for those two pages.
            String anchor = page.equals("LIVE CHART") ? "XAUUSD PRO 99"
                : page.equals("MARKET JOURNEY") ? "MARKET JOURNEY"
                : page.equals("INDICATOR CENTER") ? "INDICATOR"
                : page.equals("SIGNAL CENTER") ? "SIGNAL"
                : page.equals("NEWS") ? "ECONOMIC CALENDAR"
                : page.equals("NOTIFICATIONS") ? "VOICE ENGINE & NOTIFICATION CENTER"
                : null;
            assertTrue("No audited content anchor configured for page: " + page, anchor != null);
            BySelector contentSelector = By.pkg(PACKAGE).textContains(anchor);
            boolean contentVisible = device.wait(Until.hasObject(contentSelector), 15_000L);
            // A horizontal navigation tab can receive a tap while the scroll container is
            // still settling on slow TCG emulators. Retry the same tab once before failing.
            if (!contentVisible) {
                UiObject2 retryNav = waitForText(device, page, 3_000L);
                if (retryNav != null) {
                    retryNav.click();
                    device.waitForWindowUpdate(PACKAGE, 5_000L);
                    device.waitForIdle(5_000L);
                    contentVisible = device.wait(Until.hasObject(contentSelector), 10_000L);
                }
            }
            String pageKey = page.toLowerCase().replace(' ', '_');
            // Keep a hierarchy snapshot for every page, not only failed pages.
            device.dumpWindowHierarchy(new File(debug, orientation + "_" + pageKey + ".xml"));
            if (!contentVisible) {
                device.takeScreenshot(new File(debug, orientation + "_missing_content_" + pageKey + ".png"));
                device.dumpWindowHierarchy(new File(debug, orientation + "_missing_content_" + pageKey + ".xml"));
                throw new AssertionError("Page content not visible after navigation: " + page
                    + "; foreground=" + device.getCurrentPackageName()
                    + "; expected text contains=" + anchor);
            }
            if (page.equals("NEWS")) click(device, "REFRESH ECONOMIC CALENDAR");
            else if (page.equals("NOTIFICATIONS")) clickOneOf(device, "SUARA: AKTIF", "SUARA: NONAKTIF");
            Thread.sleep(350L);
            File screenshot = new File(output,
                orientation + "_" + page.toLowerCase().replace(' ', '_') + ".png");
            assertTrue("Screenshot failed for " + page + " (" + orientation + ")",
                device.takeScreenshot(screenshot));
        }
    }

    private static void assertScreenshots(File output, String orientation) {
        for (String page : PAGES) {
            File screenshot = new File(output,
                orientation + "_" + page.toLowerCase().replace(' ', '_') + ".png");
            assertTrue("Missing or empty screenshot: " + screenshot,
                screenshot.isFile() && screenshot.length() > 0);
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(screenshot.getAbsolutePath(), bounds);
            assertTrue("Incorrect screenshot orientation: " + screenshot,
                orientation.equals("portrait") ? bounds.outHeight > bounds.outWidth : bounds.outWidth > bounds.outHeight);
        }
    }

    private static UiObject2 waitForText(UiDevice device, String text, long timeoutMs) {
        BySelector selector = By.text(text);
        if (!device.wait(Until.hasObject(selector), timeoutMs)) return null;
        return device.findObject(selector);
    }

    private static void click(UiDevice device, String text) {
        UiObject2 control = waitForText(device, text, 15_000L);
        if (control == null) throw new AssertionError("Expected control not found: " + text);
        control.click();
        device.waitForIdle();
    }

    private static void clickOneOf(UiDevice device, String first, String second) {
        UiObject2 control = waitForText(device, first, 5_000L);
        if (control == null) control = waitForText(device, second, 5_000L);
        if (control == null) throw new AssertionError("Neither toggle state was found: " + first + " / " + second);
        control.click();
        device.waitForIdle();
    }

    @Test public void calendarNotificationDeliveryAndVoiceSafety() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.app.NotificationManager nm=c.getSystemService(android.app.NotificationManager.class);
        if(android.os.Build.VERSION.SDK_INT>=33){
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(c.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
        }
        com.xauusdpro99.data.news.EconomicNewsEngine engine=new com.xauusdpro99.data.news.EconomicNewsEngine();
        long now=System.currentTimeMillis(), release=now+7200000L;
        String payload="{\"events\":[{\"currency\":\"USD\",\"title\":\"CPI instrumentation fixture\",\"ts\":"+release+",\"impact\":\"high\"}]}";
        assertTrue(engine.updateJson(payload,com.xauusdpro99.data.news.EconomicCalendarClient.PRIMARY,now));
        java.util.List<com.xauusdpro99.data.news.EconomicNewsEngine.Alert> alerts=engine.evaluateAlerts(now);
        org.junit.Assert.assertEquals(1,alerts.size());
        com.xauusdpro99.notification.NotificationCenter center=new com.xauusdpro99.notification.NotificationCenter(c);
        String title="TEST ONLY — "+alerts.get(0).title()+" "+now;
        long id=center.recordAlert(title,"Instrumentation fixture; not live market data",now,now,0);
        assertTrue(id>0);
        try{
            center.show(c,id,title,"Instrumentation fixture; not live market data");
            boolean found=false;
            for(int i=0;i<20&&!found;i++){
                for(android.service.notification.StatusBarNotification n:nm.getActiveNotifications())
                    if(n.getId()==(int)(id&0x7fffffff))found=true;
                if(!found)Thread.sleep(250);
            }
            assertTrue("Android notification not posted",found);
            assertTrue("Duplicate alert was recorded",center.recordAlert(title,"duplicate",now,now,0)<0);
            long releaseTime=now+7200000L;
            String cpi=title+" — CPI — "+releaseTime;
            String nfp=title+" — NFP — "+releaseTime;
            assertTrue(center.recordAlert(cpi,"TEST ONLY",releaseTime,now,300000L)>0);
            assertTrue("Different event suppressed by cooldown",center.recordAlert(nfp,"TEST ONLY",releaseTime,now,300000L)>0);
            assertTrue("Same event repeated",center.recordAlert(cpi,"TEST ONLY",releaseTime,now+1000L,300000L)<0);
            assertTrue("Next milestone suppressed",center.recordAlert(cpi+" — 45 minutes","TEST ONLY",releaseTime,now+4500000L,300000L)>0);
            // Exercise the same delivery method called by the running foreground service.
            com.xauusdpro99.data.news.EconomicNewsEngine production=new com.xauusdpro99.data.news.EconomicNewsEngine();
            String unique="CPI CI fixture "+now;
            assertTrue("Production fixture must retain a supported economic event name",production.updateJson(payload.replace("CPI instrumentation fixture",unique),com.xauusdpro99.data.news.EconomicCalendarClient.PRIMARY,now));
            android.content.SharedPreferences prefs=c.getSharedPreferences("notification_settings",Context.MODE_PRIVATE);
            java.util.Map<String,?> saved=prefs.getAll();
            try{
                prefs.edit().remove("alert_types").putBoolean("notifications_enabled",true).putBoolean("voice_enabled",false).commit();
                MarketMonitoringService.deliverCalendarAlerts(c,production,center,null,now);
                int matches=0;
                for(com.xauusdpro99.notification.NotificationCenter.Item item:center.recent(200)){
                    if(item.title.contains(unique)){matches++;nm.cancel((int)(item.id&0x7fffffff));}
                }
                org.junit.Assert.assertEquals("Production delivery history",1,matches);
                MarketMonitoringService.deliverCalendarAlerts(c,production,center,null,now);
                int repeated=0;
                for(com.xauusdpro99.notification.NotificationCenter.Item item:center.recent(200))if(item.title.contains(unique))repeated++;
                org.junit.Assert.assertEquals(1,repeated);
            }finally{
                android.content.SharedPreferences.Editor edit=prefs.edit().clear();
                for(java.util.Map.Entry<String,?> entry:saved.entrySet()){
                    Object v=entry.getValue();String k=entry.getKey();
                    if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
                    else if(v instanceof Integer)edit.putInt(k,(Integer)v);
                    else if(v instanceof Long)edit.putLong(k,(Long)v);
                    else if(v instanceof Float)edit.putFloat(k,(Float)v);
                    else if(v instanceof String)edit.putString(k,(String)v);
                    else if(v instanceof java.util.Set)edit.putStringSet(k,(java.util.Set<String>)v);
                }
                edit.commit();
            }
            java.util.concurrent.atomic.AtomicReference<com.xauusdpro99.voice.VoiceEngine> voice=new java.util.concurrent.atomic.AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.set(new com.xauusdpro99.voice.VoiceEngine(c,status->android.util.Log.i("NewsVoiceTest",status))));
            try{
                long deadline=android.os.SystemClock.elapsedRealtime()+15000L;
                while(!voice.get().isReady()&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(250);
                String before=c.getSharedPreferences("voice_history",Context.MODE_PRIVATE).getString("entries","");
                voice.get().speakAlert("NEWS_ALERT","NEWS",Double.NaN,"STALE_TEST_ONLY","INFO",false);
                Thread.sleep(1000);
                org.junit.Assert.assertEquals(before,c.getSharedPreferences("voice_history",Context.MODE_PRIVATE).getString("entries",""));
                android.util.Log.i("NewsVoiceTest","Stale voice suppression checked; TTS status="+voice.get().getStatus());
                if(voice.get().isReady()){
                    String marker="FRESH_CPI_TEST_"+now;
                    voice.get().speakAlert("NEWS_ALERT","NEWS",Double.NaN,marker,"INFO",true);
                    long until=android.os.SystemClock.elapsedRealtime()+10000L;
                    boolean accepted=false;
                    while(android.os.SystemClock.elapsedRealtime()<until){
                        if(c.getSharedPreferences("voice_history",Context.MODE_PRIVATE).getString("entries","").contains(marker)){accepted=true;break;}
                        Thread.sleep(200);
                    }
                    assertTrue("Fresh news TTS request not accepted",accepted);
                    android.util.Log.i("NewsVoiceTest","Fresh news TTS accepted; audible output not asserted");
                }else{
                    android.util.Log.w("NewsVoiceTest","FRESH TTS NOT VERIFIED: emulator TTS unavailable");
                }
            }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->voice.get().shutdown());}
        }finally{nm.cancel((int)(id&0x7fffffff));center.close();}
    }
}
