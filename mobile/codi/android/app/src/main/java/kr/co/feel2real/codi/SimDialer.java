package kr.co.feel2real.codi;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "SimDialer")
public class SimDialer extends Plugin {
    @Override
    public Boolean shouldOverrideLoad(Uri url) {
        if (!"tel".equalsIgnoreCase(url.getScheme())) return null;
        Intent intent = dialIntent(url);
        if (intent != null) {
            try {
                getActivity().startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(), "전화 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    static Intent dialIntent(Uri url) {
        String number = url.getSchemeSpecificPart().replaceAll("[ ()-]", "");
        if (!url.isOpaque() || url.getFragment() != null || !number.matches("\\+?[0-9]{2,15}")) return null;
        // Android selects the user's default calling SIM or presents its SIM chooser.
        return new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
    }
}
