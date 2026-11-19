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
    public void navigateAllPagesAndCapturePortraitLandscapeScreenshots() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File output = new File(context.getExternalFilesDir(null), "ui-screenshots");
        if (!output.exists() && !output.mkdirs()) {
            throw new AssertionError("Cannot create screenshot directory: " + output);
        }
        File debug = new File(output, "debug");
        if (!debug.exists() && !debug.mkdirs()) {
            throw new AssertionError("Cannot create UI debug directory: " + debug);
        }

        // Start from the launcher and grant the runtime permission before app startup.
        // Do not force-stop the target package here: Android may host the instrumentation
        // runner in that package process, so force-stop can kill the test runner itself.
        // Launch the real Activity directly without restarting the instrumentation process.
        device.pressHome();
        device.setOrientationPortrait();
        device.executeShellCommand("pm grant " + PACKAGE + " android.permission.POST_NOTIFICATIONS");
        device.executeShellCommand("am start -W -n " + PACKAGE + "/.MainActivity");

        boolean appVisible = device.wait(Until.hasObject(By.pkg(PACKAGE)), 60_000);
        UiObject2 refresh = waitForText(device, "REFRESH", 60_000);
        if (!appVisible || refresh == null) {
            device.takeScreenshot(new File(debug, "initial-screen.png"));
            device.dumpWindowHierarchy(new File(debug, "initial-hierarchy.xml"));
            throw new AssertionError("App/control not visible. foreground="
                + device.getCurrentPackageName() + ", appVisible=" + appVisible
                + ", REFRESH found=" + (refresh != null));
        }

        // Exercise live-chart controls before collecting page evidence.
        click(device, "REFRESH");
        click(device, "REFRESH OHLC");
        click(device, "M1");
        click(device, "EMA: OFF");
        click(device, "INDICATOR: RSI14");
        device.waitForIdle();

        captureAllPages(device, output, "portrait", debug);

        device.setOrientationLandscape();
        device.waitForIdle();
        captureAllPages(device, output, "landscape", debug);

        device.setOrientationNatural();
        device.waitForIdle();

        String[] expected = new String[12];
        int index = 0;
        for (String orientation : new String[]{"portrait", "landscape"}) {
            for (String page : PAGES) {
                expected[index++] = orientation + "_" + page.toLowerCase().replace(' ', '_') + ".png";
            }
        }
        for (String name : expected) {
            File screenshot = new File(output, name);
            assertTrue("Missing or empty screenshot: " + screenshot,
                screenshot.isFile() && screenshot.length() > 0);
        }
    }

    private static UiObject2 waitForText(UiDevice device, String text, long timeoutMs) {
        BySelector selector = By.text(text);
        if (!device.wait(Until.hasObject(selector), timeoutMs)) return null;
        return device.findObject(selector);
    }

    private static void captureAllPages(UiDevice device, File output, String orientation, File debug) throws Exception {
        for (String page : PAGES) {
            UiObject2 nav = waitForText(device, page, 10_000);
            if (nav == null) {
                int y = Math.max(80, device.getDisplayHeight() / 12);
                device.swipe(device.getDisplayWidth() - 40, y, 40, y, 25);
                device.waitForIdle();
                nav = waitForText(device, page, 3_000);
                if (nav == null) {
                    device.swipe(40, y, device.getDisplayWidth() - 40, y, 25);
                    device.waitForIdle();
                    nav = waitForText(device, page, 3_000);
                }
            }
            if (nav == null) {
                device.takeScreenshot(new File(debug, orientation + "_missing_" + page.replace(' ', '_') + ".png"));
                device.dumpWindowHierarchy(new File(debug, orientation + "_missing_" + page.replace(' ', '_') + ".xml"));
                throw new AssertionError("Navigation tab not found: " + page
                    + "; foreground=" + device.getCurrentPackageName());
            }
            nav.click();
            device.waitForIdle();
            String anchor = page.equals("MARKET JOURNEY") ? "MARKET JOURNEY"
                : page.equals("INDICATOR CENTER") ? "INDICATORS (closed candles only)"
                : page.equals("SIGNAL CENTER") ? "SIGNAL CENTER"
                : page.equals("NEWS") ? "ECONOMIC CALENDAR"
                : page.equals("NOTIFICATIONS") ? "VOICE ENGINE & NOTIFICATION CENTER"
                : "XAUUSD PRO 99";
            if (!device.wait(Until.hasObject(By.textContains(anchor)), 10_000)) {
                device.takeScreenshot(new File(debug, orientation + "_missing_content_" + page.replace(' ', '_') + ".png"));
                device.dumpWindowHierarchy(new File(debug, orientation + "_missing_content_" + page.replace(' ', '_') + ".xml"));
                throw new AssertionError("Page content not visible after navigation: " + page);
            }
            if (page.equals("NEWS")) {
                click(device, "REFRESH ECONOMIC CALENDAR");
            } else if (page.equals("NOTIFICATIONS")) {
                clickOneOf(device, "SUARA: AKTIF", "SUARA: NONAKTIF");
            }
            Thread.sleep(350);
            File screenshot = new File(output,
                orientation + "_" + page.toLowerCase().replace(' ', '_') + ".png");
            assertTrue("Screenshot failed for " + page + " (" + orientation + ")",
                device.takeScreenshot(screenshot));
        }
    }

    private static void click(UiDevice device, String text) {
        UiObject2 control = waitForText(device, text, 15_000);
        if (control == null) throw new AssertionError("Expected control not found: " + text
            + "; foreground=" + device.getCurrentPackageName());
        control.click();
        device.waitForIdle();
    }

    private static void clickOneOf(UiDevice device, String first, String second) {
        UiObject2 control = waitForText(device, first, 5_000);
        if (control == null) control = waitForText(device, second, 5_000);
        if (control == null) throw new AssertionError("Neither toggle state was found: " + first + " / " + second);
        control.click();
        device.waitForIdle();
    }
}
