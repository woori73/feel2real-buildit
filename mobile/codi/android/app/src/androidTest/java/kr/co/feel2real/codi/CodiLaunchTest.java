package kr.co.feel2real.codi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.content.Intent;
import android.webkit.WebView;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CodiLaunchTest {
    @Test
    public void phoneLinkOpensSystemDialerWithNumberAndNoForcedAccount() {
        Intent dial = SimDialer.dialIntent(Uri.parse("tel:010-0000-0000"));
        assertNotNull(dial);
        assertEquals(Intent.ACTION_DIAL, dial.getAction());
        assertEquals("tel:01000000000", dial.getDataString());
        assertNull(dial.getExtras());
        assertNull(SimDialer.dialIntent(Uri.parse("tel:*123%23")));
    }

    @Test
    public void installedPackageOpensCodiActivity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("kr.co.feel2real.codi", context.getPackageName());
        Intent launcher = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launcher);
        assertEquals(MainActivity.class.getName(), launcher.getComponent().getClassName());
    }

    @Test
    public void systemTextSizeIsAbsoluteAcrossRepeatedChanges() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            WebView view = new WebView(context);
            try {
                for (float scale : new float[]{1.0f, 1.3f, 1.3f, 1.0f}) {
                    MainActivity.applySystemTextSize(view.getSettings(), scale);
                    assertEquals((int) (scale * 100), view.getSettings().getTextZoom());
                }
            } finally {
                view.destroy();
            }
        });
    }
}
