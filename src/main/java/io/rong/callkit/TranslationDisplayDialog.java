package io.rong.callkit;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import androidx.annotation.NonNull;
import io.rong.callkit.databinding.DialogTranslationDisplayBinding;
import io.rong.callkit.databinding.RcVoipItemTranslationLanguageBinding;
import java.util.Map;

public class TranslationDisplayDialog extends Dialog {

    private OnLanguageSelectedListener listener;
    private String currentLanguage;
    private DialogTranslationDisplayBinding binding;
    private Map<String, Integer> languageMap;

    public interface OnLanguageSelectedListener {
        void onLanguageSelected(String language);
    }

    public TranslationDisplayDialog(
            @NonNull Context context, String currentLanguage, Map<String, Integer> languageMap) {
        super(context, R.style.RC_VOIP_TranslationDisplayDialogStyle);
        this.currentLanguage = currentLanguage;
        this.languageMap = languageMap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DialogTranslationDisplayBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 设置弹窗从底部弹出
        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setWindowAnimations(R.style.RC_VOIP_TranslationDisplayDialogAnimation);
        }

        initView();
    }

    private void initView() {
        RcVoipItemTranslationLanguageBinding lastItem = null;
        for (Map.Entry<String, Integer> entry : this.languageMap.entrySet()) {
            RcVoipItemTranslationLanguageBinding item =
                    RcVoipItemTranslationLanguageBinding.inflate(getLayoutInflater());
            item.ivCheck.setVisibility(currentLanguage.equals(entry.getKey()) ? VISIBLE : GONE);
            item.tvLanguage.setText(entry.getValue());
            item.getRoot().setOnClickListener(v -> selectLanguage(entry.getKey()));
            binding.llOptions.addView(item.getRoot());
            lastItem = item;
        }
        lastItem.line.setVisibility(GONE);

        // 取消按钮
        binding.btnCancel.setOnClickListener(v -> dismiss());
    }

    private void selectLanguage(String language) {
        if (listener != null) {
            listener.onLanguageSelected(language);
        }
        dismiss();
    }

    public void setOnLanguageSelectedListener(OnLanguageSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (binding != null) {
            binding = null;
        }
    }
}
