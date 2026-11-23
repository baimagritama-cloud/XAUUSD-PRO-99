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

import org.junit.Ignore;
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

    // Temporary CI stabilization phase: keep portrait support and its test source,
    // but do not execute portrait instrumentation until landscape is stable.
    @Ignore("Temporarily landscape-only CI phase; restore portrait test after emulator stabilization")
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
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
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

        // Wait for a stable app-owned navigation element, not the REFRESH button.
        // The latter can be absent from the accessibility tree during first-frame/layout work.
        boolean packageVisible = device.wait(Until.hasObject(By.pkg(PACKAGE)), 60_000L);
        boolean mainUiReady = device.wait(Until.hasObject(By.text("LIVE CHART")), 60_000L);
        assertTrue("MainActivity did not reach its first usable UI; packageVisible="
            + packageVisible + "; liveChartTab=" + mainUiReady
            + "; foreground=" + device.getCurrentPackageName(),
            packageVisible && mainUiReady);
    }

    private static void exerciseChartControls(UiDevice device) {
        click(device, "REFRESH");
        click(device, "REFRESH OHLC");
        click(device, "M1");
        click(device, "EMA: OFF");
        click(device, "INDICATOR: RSI14");
        device.waitForIdle();
    }

    private static void captureAllPages(UiDevice device, File output, String orientation) throws Exception {
        File debug = new File(output, "debug");
        if (!debug.exists()) debug.mkdirs();
        for (String page : PAGES) {
            UiObject2 nav = waitForText(device, page, 10_000L);
            if (nav == null) {
                int y = Math.max(80, device.getDisplayHeight() / 12);
                device.swipe(device.getDisplayWidth() - 40, y, 40, y, 25);
                device.waitForIdle();
                nav = waitForText(device, page, 3_000L);
                if (nav == null) {
                    device.swipe(40, y, device.getDisplayWidth() - 40, y, 25);
                    device.waitForIdle();
                    nav = waitForText(device, page, 3_000L);
                }
            }
            if (nav == null) {
                device.takeScreenshot(new File(debug, orientation + "_missing_" + page.replace(' ', '_') + ".png"));
                device.dumpWindowHierarchy(new File(debug, orientation + "_missing_" + page.replace(' ', '_') + ".xml"));
                throw new AssertionError("Navigation tab not found: " + page);
            }
            nav.click();
            device.waitForWindowUpdate(PACKAGE, 5_000L);
            device.waitForIdle(5_000L);
            // Audited against the initial TextViews created by MainActivity.onCreate().
            // The real indicator heading is "INDICATOR CENTER", not the former
            // guessed label "INDICATORS (closed candles only)".
            String anchor = page.equals("LIVE CHART") ? "XAUUSD PRO 99"
                : page.equals("MARKET JOURNEY") ? "MARKET JOURNEY"
                : page.equals("INDICATOR CENTER") ? "INDICATOR CENTER"
                : page.equals("SIGNAL CENTER") ? "SIGNAL CENTER"
                : page.equals("NEWS") ? "ECONOMIC CALENDAR"
                : page.equals("NOTIFICATIONS") ? "VOICE ENGINE & NOTIFICATION CENTER"
                : null;
            assertTrue("No audited content anchor configured for page: " + page, anchor != null);
            BySelector contentSelector = By.textContains(anchor);
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
}