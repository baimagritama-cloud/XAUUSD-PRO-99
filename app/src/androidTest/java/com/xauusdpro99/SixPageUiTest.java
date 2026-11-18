package com.xauusdpro99;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public final class SixPageUiTest {
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

        device.setOrientationPortrait();
        device.waitForIdle();

        // Exercise the live-chart controls before collecting the page evidence.
        click(device, "REFRESH");
        click(device, "REFRESH OHLC");
        click(device, "M1");
        click(device, "EMA: OFF");
        click(device, "INDICATOR: RSI14");
        device.waitForIdle();

        captureAllPages(device, output, "portrait");

        device.setOrientationLandscape();
        device.waitForIdle();
        captureAllPages(device, output, "landscape");

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
            assertTrue("Missing or empty screenshot: " + screenshot, screenshot.isFile() && screenshot.length() > 0);
        }
    }

    private static void captureAllPages(UiDevice device, File output, String orientation) throws Exception {
        for (String page : PAGES) {
            UiObject2 nav = device.findObject(By.text(page));
            if (nav == null) {
                // Reveal later tabs; the strip may still be scrolled to the end after rotation.
                int y = Math.max(80, device.getDisplayHeight() / 12);
                device.swipe(device.getDisplayWidth() - 40, y, 40, y, 25);
                device.waitForIdle();
                nav = device.findObject(By.text(page));
                if (nav == null) {
                    device.swipe(40, y, device.getDisplayWidth() - 40, y, 25);
                    device.waitForIdle();
                    nav = device.findObject(By.text(page));
                }
            }
            if (nav == null) throw new AssertionError("Navigation tab not found: " + page);
            nav.click();
            device.waitForIdle();
            Thread.sleep(350);
            File screenshot = new File(output,
                orientation + "_" + page.toLowerCase().replace(' ', '_') + ".png");
            assertTrue("Screenshot failed for " + page + " (" + orientation + ")",
                device.takeScreenshot(screenshot));
        }
    }

    private static void click(UiDevice device, String text) {
        UiObject2 control = device.findObject(By.text(text));
        if (control == null) throw new AssertionError("Expected clickable control not found: " + text);
        control.click();
        device.waitForIdle();
    }
}
