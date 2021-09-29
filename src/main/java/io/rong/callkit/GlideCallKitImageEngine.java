package io.rong.callkit;

import android.content.Context;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

public class GlideCallKitImageEngine {
    /**
     * 设置头像加载样式
     * @param context
     * @param url
     * @param replaceRes
     * @param imageView
     */
    public void loadPortrait(Context context, Uri url, @DrawableRes int replaceRes, ImageView imageView) {
        Glide.with(context)
                .load(url)
                .error(replaceRes)
                .placeholder(replaceRes)
                .apply(RequestOptions.circleCropTransform())
                .into(imageView);
    }

}
