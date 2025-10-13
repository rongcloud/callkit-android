package io.rong.callkit;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import io.rong.callkit.databinding.RcVoipAsrSettingBinding;
import java.util.LinkedHashMap;
import java.util.Map;

/** Created by RongCloud on 2025/9/8. */
public class RongASRSettingsDialog extends DialogFragment {

    public static String KEY_SETTINGS = "asr_settings";
    public static String KEY_OFF_TRANSLATION = "off";
    public static Map<String, Integer> LANGUAGE_MAP =
            new LinkedHashMap<String, Integer>() {
                {
                    put(KEY_OFF_TRANSLATION, R.string.rc_voip_asr_translation_off);
                    put("zh", R.string.rc_voip_asr_translation_zh);
                    put("en", R.string.rc_voip_asr_translation_en);
                    put("ar", R.string.rc_voip_asr_translation_ar);
                }
            };
    private RcVoipAsrSettingBinding binding;
    private ASRSettings settings;
    private OnDismissListener dismissListener;

    public static RongASRSettingsDialog newInstance(ASRSettings settings) {
        Bundle args = new Bundle();
        args.putParcelable(KEY_SETTINGS, settings.copy());
        RongASRSettingsDialog fragment = new RongASRSettingsDialog();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = RcVoipAsrSettingBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    public int getTheme() {
        return R.style.RC_VOIP_DialogFullScreen;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setWindowAnimations(R.style.RC_VOIP_DialogLikeActivity);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.RC_VOIP_DialogFullScreen);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (dismissListener != null) {
            dismissListener.onDismiss(dialog);
        }
    }

    private void initView() {
        settings = getArguments().getParcelable(KEY_SETTINGS);
        if (settings == null) {
            settings = new ASRSettings();
        }
        binding.switchDisplayModel.setChecked(settings.bothDisplay);
        binding.tvTranslationDest.setText(LANGUAGE_MAP.get(settings.destLanguage));
        binding.ivBack.setOnClickListener(v -> dismiss());
        binding.tvTranslationDest.setOnClickListener(v -> changeDestLanguage());
        binding.switchDisplayModel.setOnClickListener(
                v -> this.settings.bothDisplay = binding.switchDisplayModel.isChecked());
    }

    private void changeDestLanguage() {
        TranslationDisplayDialog dialog =
                new TranslationDisplayDialog(getContext(), settings.destLanguage);
        dialog.setOnLanguageSelectedListener(
                language -> {
                    settings.destLanguage = language;
                    binding.tvTranslationDest.setText(LANGUAGE_MAP.get(language));
                });
        dialog.show();
    }

    public ASRSettings getSettings() {
        return settings;
    }

    public void setOnDismissListener(OnDismissListener dismissListener) {
        this.dismissListener = dismissListener;
    }

    static class ASRSettings implements Parcelable {
        String destLanguage = KEY_OFF_TRANSLATION;
        boolean bothDisplay = false;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(destLanguage);
            dest.writeInt(bothDisplay ? 1 : 0);
        }

        public ASRSettings(Parcel in) {
            destLanguage = in.readString();
            bothDisplay = in.readInt() == 1;
        }

        public int getDisplayModel() {
            if (KEY_OFF_TRANSLATION.equals(destLanguage)) {
                return RongASRItemView.DISPLAY_MODEL_ASR;
            }
            if (bothDisplay) {
                return RongASRItemView.DISPLAY_MODEL_BOTH;
            }
            return RongASRItemView.DISPLAY_MODEL_TRANSLATION;
        }

        public ASRSettings() {}

        public static final Creator<ASRSettings> CREATOR =
                new Creator<ASRSettings>() {
                    @Override
                    public ASRSettings createFromParcel(Parcel in) {
                        return new ASRSettings(in);
                    }

                    @Override
                    public ASRSettings[] newArray(int size) {
                        return new ASRSettings[size];
                    }
                };

        public boolean isStopTranslation() {
            return KEY_OFF_TRANSLATION.equals(destLanguage);
        }

        public ASRSettings copy() {
            Parcel parcel = Parcel.obtain();
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            ASRSettings settings = CREATOR.createFromParcel(parcel);
            parcel.recycle();
            return settings;
        }
    }
}
