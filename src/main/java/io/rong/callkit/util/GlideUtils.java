package io.rong.callkit.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;
import cn.rongcloud.rtc.utils.FinLog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.request.RequestOptions;

/** Created by dengxudong on 2018/5/18. */
public class GlideUtils {

    private static final String TAG = GlideUtils.class.getSimpleName();

    public static void showBlurTransformation(Context context, ImageView imageView, Uri val) {
        if (val == null) {
            return;
        }
        try {
            Glide.with(context)
                    .load(val)
                    .apply(RequestOptions.bitmapTransform(new GlideBlurformation(context)))
                    .apply(new RequestOptions().centerCrop())
                    .into(imageView);
        } catch (Exception e) {
            e.printStackTrace();
            FinLog.e(TAG, "Glide Utils Error=" + e.getMessage());
        } catch (NoSuchMethodError noSuchMethodError) {
            noSuchMethodError.printStackTrace();
            FinLog.e(TAG, "Glide NoSuchMethodError = " + noSuchMethodError.getMessage());
        }
    }

    public static void showPortrait(Context context, ImageView imageView, Uri val) {
        RequestOptions requestOptions = new RequestOptions();
        requestOptions
                .transform(new GlideBlurformation(context))
                .priority(Priority.HIGH)
                .placeholder(io.rong.imkit.R.drawable.rc_default_portrait)
                .apply(new RequestOptions().centerCrop());
        if (val == null) {
            Log.d(TAG, "showPortrait: val is Null");
            Glide.with(context)
                    .load(io.rong.imkit.R.drawable.rc_default_portrait)
                    .apply(requestOptions)
                    .into(imageView);
        } else {
            Glide.with(context).load(val).apply(requestOptions).into(imageView);
        }
    }
}
