package io.rong.callkit;

import android.content.Context;
import androidx.fragment.app.FragmentActivity;
import io.rong.imkit.utils.language.RongConfigurationManager;

public class BaseNoActionBarActivity extends FragmentActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        Context newContext =
                RongConfigurationManager.getInstance().getConfigurationContext(newBase);
        super.attachBaseContext(newContext);
    }
}
