package io.rong.callkit;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.rongcloud.rtc.api.RCRTCASRContent;
import cn.rongcloud.rtc.api.RCRTCRealtimeTranslationContent;
import cn.rongcloud.rtc.api.callback.IRCRTCResultDataCallback;
import cn.rongcloud.rtc.api.callback.IRCRTCStreamDataCallback;
import cn.rongcloud.rtc.base.RTCErrorCode;
import io.rong.callkit.databinding.RcVoipAiSumBinding;
import io.rong.callkit.util.RongAIManager;
import io.rong.calllib.IRongCallASRListener;
import java.util.LinkedHashMap;
import java.util.Map;

/** Created by RongCloud on 2025/12/15. */
public class RongAISummarizationDialog extends BaseDialogFragment {

    public static String KEY_DEFAULT_TRANSLATION = "zh";
    public static Map<String, Integer> LANGUAGE_MAP =
            new LinkedHashMap<String, Integer>() {
                {
                    put("zh", R.string.rc_voip_asr_translation_zh);
                    put("en", R.string.rc_voip_asr_translation_en);
                    put("ar", R.string.rc_voip_asr_translation_ar);
                }
            };
    private RcVoipAiSumBinding binding;
    private String callId;
    private String taskId;
    private boolean isOnCall = false;
    private boolean dismiss = false;
    private String destLang;
    private RongCallStreamDataCallback streamDataCallback;
    public static final String TAG = "RongAISummarizationDialog";

    public static RongAISummarizationDialog newInstance(
            String callId, String taskId, String destLang) {
        Bundle args = new Bundle();
        RongAISummarizationDialog fragment = new RongAISummarizationDialog();
        args.putString("callId", callId);
        args.putString("taskId", taskId);
        args.putString("destLang", destLang);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = RcVoipAiSumBinding.inflate(getLayoutInflater());
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        callId = getArguments().getString("callId");
        taskId = getArguments().getString("taskId");
        destLang = getArguments().getString("destLang");
        isOnCall = callId.equals(RongAIManager.getInstance().getCurrentCallId());
        initView();
        if (isOnCall) {
            RongAIManager.getInstance()
                    .registerASRListener(
                            TAG,
                            new IRongCallASRListener() {
                                @Override
                                public void onReceiveASRContent(RCRTCASRContent asrContent) {}

                                @Override
                                public void onReceiveStopASR() {}

                                @Override
                                public void onReceiveStartASR() {}

                                @Override
                                public void onReceiveRealtimeTranslationContent(
                                        RCRTCRealtimeTranslationContent content) {}

                                @Override
                                public void onASRError(int errorCode) {}

                                @Override
                                public void onReceiveStartSummarization(String taskId) {
                                    RongAISummarizationDialog.this.taskId = taskId;
                                    runOnUiThread(
                                            () -> {
                                                if (isDestroyed()) {
                                                    return;
                                                }
                                                binding.switchSummary.setChecked(true);
                                            });
                                }

                                @Override
                                public void onReceiveStopSummarization(String taskId) {
                                    runOnUiThread(
                                            () -> {
                                                if (isDestroyed()) {
                                                    return;
                                                }
                                                binding.switchSummary.setChecked(false);
                                            });
                                }
                            });
        } else {
            generateSummarization(destLang);
        }
    }

    private boolean isDestroyed() {
        return isDetached() || isRemoving() || getContext() == null || dismiss;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        this.dismiss = true;
        if (streamDataCallback != null) {
            streamDataCallback.release();
        }
        binding.markdownView.reset();
        RongAIManager.getInstance().unregisterASRListener(TAG);
    }

    private void initView() {
        if (isOnCall) {
            binding.switchSummary.setVisibility(View.VISIBLE);
            binding.switchSummary.setChecked(RongAIManager.getInstance().isAiSumStarted());
            binding.switchSummary.setOnClickListener(
                    v -> {
                        if (binding.switchSummary.isChecked()) {
                            RongAIManager.getInstance()
                                    .startSummarization(
                                            new IRCRTCResultDataCallback<String>() {
                                                @Override
                                                public void onSuccess(String data) {
                                                    taskId = data;
                                                    runOnUiThread(
                                                            () -> {
                                                                if (isDestroyed()) {
                                                                    return;
                                                                }
                                                                generateSummarization(destLang);
                                                            });
                                                }

                                                @Override
                                                public void onFailed(RTCErrorCode errorCode) {
                                                    runOnUiThread(
                                                            () -> {
                                                                if (isDestroyed()) {
                                                                    return;
                                                                }
                                                                binding.switchSummary.setChecked(
                                                                        false);
                                                                Toast.makeText(
                                                                                getContext(),
                                                                                R.string
                                                                                        .rc_voip_start_sum_failed,
                                                                                Toast.LENGTH_SHORT)
                                                                        .show();
                                                            });
                                                }
                                            });
                        } else {
                            RongAIManager.getInstance().stopSummarization(null);
                        }
                    });
            binding.markdownView.start(true);
            binding.markdownView.setContent(
                    RongAIManager.getInstance()
                            .getPreSummarizationData(taskId, createStreamDataCallback()));
        } else {
            binding.switchSummary.setVisibility(View.GONE);
        }
        binding.ivClose.setOnClickListener(v -> dismiss());
        binding.tvRefresh.setOnClickListener(v -> generateSummarization(destLang));
        binding.tvDestLang.setText(
                RongASRSettingsDialog.LANGUAGE_MAP.get(getLanguageKey(destLang)));
        binding.tvDestLang.setOnClickListener(v -> changeDestLanguage());
    }

    private void runOnUiThread(Runnable runnable) {
        if (isDestroyed()) {
            return;
        }
        getActivity().runOnUiThread(runnable);
    }

    private void generateSummarization(String destLang) {
        if (TextUtils.isEmpty(taskId)) {
            Log.e(TAG, "generateSummarization: taskId is empty");
            Toast.makeText(getContext(), R.string.rc_voip_generate_sum_failed, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        binding.tvDestLang.setText(
                RongASRSettingsDialog.LANGUAGE_MAP.get(getLanguageKey(destLang)));
        this.destLang = destLang;
        createStreamDataCallback();
        binding.markdownView.reset();
        binding.markdownView.start(true);
        String languageCode = TextUtils.isEmpty(destLang) ? KEY_DEFAULT_TRANSLATION : destLang;
        RongAIManager.getInstance()
                .generateSummarization(callId, taskId, languageCode, isOnCall, streamDataCallback);
    }

    private IRCRTCStreamDataCallback createStreamDataCallback() {
        if (streamDataCallback != null) {
            streamDataCallback.release();
        }
        streamDataCallback =
                new RongCallStreamDataCallback(taskId) {
                    @Override
                    protected void onError(RTCErrorCode errorCode) {
                        runOnUiThread(
                                () -> {
                                    if (isDestroyed()) {
                                        return;
                                    }
                                    if (!isReleased) {
                                        int tipResId = R.string.rc_voip_generate_sum_failed;
                                        if (42215 == errorCode.getValue()) {
                                            tipResId = R.string.rc_voip_generate_sum_failed_empty;
                                        }
                                        Toast.makeText(getContext(), tipResId, Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                });
                    }

                    @Override
                    protected void onDataComplete() {
                        if (isDestroyed()) {
                            return;
                        }
                        binding.markdownView.setComplete();
                    }

                    @Override
                    protected void onData(String data) {
                        if (isDestroyed()) {
                            return;
                        }
                        binding.markdownView.appendContent(data);
                    }
                };
        return streamDataCallback;
    }

    private String getLanguageKey(String destLang) {
        return TextUtils.isEmpty(destLang) ? KEY_DEFAULT_TRANSLATION : destLang;
    }

    private void changeDestLanguage() {
        String currentLanguage = getLanguageKey(destLang);
        TranslationDisplayDialog dialog =
                new TranslationDisplayDialog(getContext(), currentLanguage, LANGUAGE_MAP);
        dialog.setOnLanguageSelectedListener(
                language -> {
                    generateSummarization(language);
                });
        dialog.show();
    }
}
