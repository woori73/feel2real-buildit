package kr.co.feel2real.codi;

import android.content.res.Configuration;
import android.webkit.WebSettings;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onResume() {
        super.onResume();
        syncSystemTextSize(getResources().getConfiguration());
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        syncSystemTextSize(configuration);
    }

    private void syncSystemTextSize(Configuration configuration) {
        if (getBridge() == null) return;
        WebView view = getBridge().getWebView();
        if (view != null) applySystemTextSize(view.getSettings(), configuration.fontScale);
    }

    static void applySystemTextSize(WebSettings settings, float fontScale) {
        // Modern Android WebView already computes this absolute value from Configuration.
        // Update only a stale value (e.g. after resuming an older WebView).
        int systemPercent = (int) (fontScale * 100);
        if (systemPercent > 0 && settings.getTextZoom() != systemPercent) {
            settings.setTextZoom(systemPercent);
        }
    }
}
