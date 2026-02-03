package io.rong.callkit.util;

import android.text.TextUtils;
import cn.rongcloud.rtc.api.RCRTCASRContent;
import cn.rongcloud.rtc.api.RCRTCGenerateSummarizationConfig;
import cn.rongcloud.rtc.api.RCRTCGenerateSummarizationConfig.RCRTCGenerateSummarizationFormat;
import cn.rongcloud.rtc.api.RCRTCRealtimeTranslationContent;
import cn.rongcloud.rtc.api.callback.IRCRTCResultCallback;
import cn.rongcloud.rtc.api.callback.IRCRTCResultDataCallback;
import cn.rongcloud.rtc.api.callback.IRCRTCStreamDataCallback;
import cn.rongcloud.rtc.base.RTCErrorCode;
import io.rong.callkit.RongCallStreamDataCallback;
import io.rong.calllib.IRongCallASRListener;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallSession;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Created by RongCloud on 2025/12/15. */
public class RongAIManager {

    private static final String TAG = "RongAIManager";
    private RongCallSession currentSession;
    private SummarizationCache sumContent = new SummarizationCache();
    private RongCallStreamDataCallback streamDataCallback;
    private boolean isASRStarted = false;
    private boolean aiSumStarted = false;
    private String aiSumTaskId;
    private String sumTranslationLang;
    private Map<String, IRongCallASRListener> asrListenerMap =
            new ConcurrentHashMap<String, IRongCallASRListener>();

    private static class Holder {
        static RongAIManager instance = new RongAIManager();
    }

    private RongAIManager() {
        RongCallClient.getInstance()
                .setASRListener(
                        new IRongCallASRListener() {
                            @Override
                            public void onReceiveASRContent(RCRTCASRContent asrContent) {
                                Collection<IRongCallASRListener> listeners =
                                        asrListenerMap.values();
                                for (IRongCallASRListener listener : listeners) {
                                    listener.onReceiveASRContent(asrContent);
                                }
                            }

                            @Override
                            public void onReceiveStopASR() {
                                isASRStarted = false;
                                Collection<IRongCallASRListener> listeners =
                                        asrListenerMap.values();
                                for (IRongCallASRListener listener : listeners) {
                                    listener.onReceiveStopASR();
                                }
                            }

                            @Override
                            public void onReceiveStartASR() {
                                isASRStarted = true;
                                Collection<IRongCallASRListener> listeners =
                                        asrListenerMap.values();
                                for (IRongCallASRListener listener : listeners) {
                                    listener.onReceiveStartASR();
                                }
                            }

                            @Override
                            public void onReceiveRealtimeTranslationContent(
                                    RCRTCRealtimeTranslationContent content) {
                                Collection<IRongCallASRListener> listeners =
                                        asrListenerMap.values();
                                for (IRongCallASRListener listener : listeners) {
                                    listener.onReceiveRealtimeTranslationContent(content);
                                }
                            }

                            @Override
                            public void onASRError(int errorCode) {
                                Collection<IRongCallASRListener> listeners =
                                        asrListenerMap.values();
                                for (IRongCallASRListener listener : listeners) {
                                    listener.onASRError(errorCode);
                                }
                            }

                            @Override
                            public void onReceiveStopSummarization(String taskId) {
                                aiSumStarted = false;
                                Collection<IRongCallASRListener> listeners =
                                        asrListenerMap.values();
                                for (IRongCallASRListener listener : listeners) {
                                    listener.onReceiveStopSummarization(taskId);
                                }
                            }

                            @Override
                            public void onReceiveStartSummarization(String taskId) {
                                aiSumTaskId = taskId;
                                aiSumStarted = true;
                                Collection<IRongCallASRListener> listeners =
                                        asrListenerMap.values();
                                for (IRongCallASRListener listener : listeners) {
                                    listener.onReceiveStartSummarization(taskId);
                                }
                            }
                        });
    }

    public static RongAIManager getInstance() {
        return Holder.instance;
    }

    public String getCurrentSumTaskId() {
        return aiSumTaskId;
    }

    public String getCurrentCallId() {
        if (currentSession != null) {
            return currentSession.getCallId();
        }
        return null;
    }

    public boolean isAiSumStarted() {
        return aiSumStarted;
    }

    public void registerASRListener(String tag, IRongCallASRListener listener) {
        asrListenerMap.put(tag, listener);
    }

    public void unregisterASRListener(String tag) {
        asrListenerMap.remove(tag);
    }

    public void setCurrentSession(RongCallSession callSession) {
        this.currentSession = callSession;
    }

    public void release() {
        this.currentSession = null;
        this.aiSumTaskId = null;
        sumContent.clear();
        if (streamDataCallback != null) {
            streamDataCallback.release();
            streamDataCallback = null;
        }
        asrListenerMap.clear();
        aiSumStarted = false;
        isASRStarted = false;
        sumTranslationLang = "";
    }

    /**
     * 获取上次总结内容
     *
     * @param callback 有可能上次还未完成数据接收，通过回调获取最新数据
     * @return
     */
    public String getPreSummarizationData(
            String taskId, IRCRTCStreamDataCallback<String> callback) {
        if (streamDataCallback != null && streamDataCallback.taskId.equals(taskId)) {
            streamDataCallback.setCallback(callback);
        }
        if (TextUtils.equals(taskId, sumContent.getTaskId())) {
            return sumContent.getData();
        }
        return "";
    }

    public String getSumTranslationLang() {
        return sumTranslationLang;
    }

    /**
     * 开启语音识别
     *
     * @param callback
     */
    public void startASR(IRCRTCResultCallback callback) {
        RongCallClient.getInstance()
                .startASR(
                        new IRCRTCResultCallback() {
                            @Override
                            public void onSuccess() {
                                isASRStarted = true;
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            }

                            @Override
                            public void onFailed(RTCErrorCode errorCode) {
                                if (callback != null) {
                                    callback.onFailed(errorCode);
                                }
                            }
                        });
    }

    /**
     * 停止 AI 智能总结
     *
     * @param callback
     */
    public void stopSummarization(IRCRTCResultCallback callback) {
        RongCallClient.getInstance().stopSummarization(callback);
    }

    /**
     * 开启 AI 智能总结
     *
     * @param callback
     */
    public void startSummarization(IRCRTCResultDataCallback<String> callback) {
        // 必须先开启 ASR 才可以使用 AI 智能总结
        if (!isASRStarted) {
            startASR(
                    new IRCRTCResultCallback() {
                        @Override
                        public void onSuccess() {
                            startSummarization(callback);
                        }

                        @Override
                        public void onFailed(RTCErrorCode errorCode) {
                            if (callback != null) {
                                callback.onFailed(errorCode);
                            }
                        }
                    });
            return;
        }
        RongCallClient.getInstance()
                .startSummarization(
                        new IRCRTCResultDataCallback<String>() {
                            @Override
                            public void onSuccess(String data) {
                                aiSumTaskId = data;
                                if (callback != null) {
                                    callback.onSuccess(data);
                                }
                            }

                            @Override
                            public void onFailed(RTCErrorCode errorCode) {
                                if (callback != null) {
                                    callback.onFailed(errorCode);
                                }
                            }
                        });
    }

    /**
     * 生成 AI 智能总结
     *
     * @param callId
     * @param taskId
     * @param destLang
     * @param callback
     */
    public void generateSummarization(
            String callId,
            String taskId,
            String destLang,
            boolean cache,
            IRCRTCStreamDataCallback<String> callback) {
        if (cache) {
            if (streamDataCallback != null) {
                streamDataCallback.release();
            }
            streamDataCallback =
                    new RongCallStreamDataCallback(taskId, callback) {

                        @Override
                        protected void onDataComplete() {}

                        @Override
                        protected void onData(String data) {
                            sumContent.append(taskId, data);
                        }

                        @Override
                        protected void onError(RTCErrorCode errorCode) {}
                    };
            sumContent.reset(taskId);
            this.sumTranslationLang = destLang;
        }
        RCRTCGenerateSummarizationConfig config =
                RCRTCGenerateSummarizationConfig.Builder.create()
                        .enableChapterSummary(true)
                        .enableTodoList(true)
                        .enableHashtag(true)
                        .enableSummarization(true)
                        .enableSummarizationDetails(true)
                        .format(RCRTCGenerateSummarizationFormat.MARK_DOWN)
                        .setDestLang(destLang)
                        .build();
        RongCallClient.getInstance()
                .generateSummarization(
                        callId, taskId, 0, 0, config, cache ? streamDataCallback : callback);
    }

    private static class SummarizationCache {
        private String taskId;
        private final StringBuilder content;

        public SummarizationCache() {
            this.content = new StringBuilder();
        }

        public void reset(String taskId) {
            this.taskId = taskId;
            synchronized (this) {
                this.content.setLength(0);
            }
        }

        public void clear() {
            this.taskId = null;
            synchronized (this) {
                this.content.setLength(0);
            }
        }

        public void append(String taskId, String data) {
            if (!TextUtils.equals(taskId, this.taskId)) {
                return;
            }
            synchronized (this) {
                this.content.append(data);
            }
        }

        public String getTaskId() {
            return taskId;
        }

        public String getData() {
            synchronized (this) {
                return content.toString();
            }
        }
    }
}
