---
title: Android CallKit 开发指南 - 融云 RongCloud
description: 提供融云 Android 端 VoIP 开发的相关帮助和指南。
template: doc.jade
---

# Android CallKit 开发指南

## 简介

`CallKit` 是融云音视频通话功能的 UI 界面 SDK。包含了单人、多人音视频通话的界面的各种场景和功能。您可以快速的集成 `CallKit` 来实现丰富的音视频通话界面，并进行自己的 UI 定制开发。同时我们开源了 `CallKit`，您可以根据您的需要去使用。

GitHub 项目：[CallKit 开源代码](https://github.com/rongcloud/callkit-android)

## 开通方式

音视频服务开通，请参考[音视频开通方式](call.html#开通方式)说明。

## 使用说明

由于底层引擎技术不同，`2.6.0` 之后的音视频 SDK 与 `2.6.0` 之前的 SDK 中的 VoIP 不能互通。

音视频 SDK 为商用收费功能，之前的 SDK 中的 VoIP 为免费测试功能，如果您还想使用之前的 VoIP，可以使用 `2.5.2` 版本，详细开通及使用说明如下：

## 集成说明

1、 首先需要集成融云 IM SDK，融云 IM SDK 是 Call 的基础，关于 IM SDK 的集成请参考[官网文档](http://www.rongcloud.cn/docs/android.html)。

2、 `src/main/java/io/rong/imkit` 下面是 Call 界面的源码，可以自行修改以满足自己的需求。

3、 打开 `src/main/AndroidManifest.xml`，下面是 Call 相关的代码。

```xml
<uses-permission android:name="android.permission.PROCESS_OUTGOING_CALLS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

Call 用到的权限。

```xml
<activity
    android:name="io.rong.imkit.MultiVideoCallActivity"
    android:launchMode="singleTop"
    android:screenOrientation="portrait"
    android:windowSoftInputMode="stateHidden|adjustResize">
    <intent-filter>
        <action android:name="io.rong.intent.action.voip.MULTIVIDEO" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>

<activity
    android:name="io.rong.imkit.SingleCallActivity"
    android:launchMode="singleTop"
    android:screenOrientation="portrait"
    android:windowSoftInputMode="stateHidden|adjustResize">
    <intent-filter>
        <action android:name="io.rong.intent.action.voip.SINGLEVIDEO" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <intent-filter>
        <action android:name="io.rong.intent.action.voip.SINGLEAUDIO" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
<activity
    android:name="io.rong.imkit.MultiAudioCallActivity"
    android:launchMode="singleTop"
    android:screenOrientation="portrait"
    android:windowSoftInputMode="stateHidden|adjustResize">
    <intent-filter>
        <action android:name="io.rong.intent.action.voip.MULTIAUDIO" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>

<activity android:name="io.rong.imkit.CallSelectMemberActivity" />

<receiver android:name="io.rong.imkit.RongCallReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="io.rong.intent.action.UI_READY" />
        <action android:name="io.rong.intent.action.SDK_INIT" />
        <action android:name="android.intent.action.PHONE_STATE" />
        <action android:name="android.intent.action.NEW_OUTGOING_CALL" />
    </intent-filter>
</receiver>
```
Call 功能中使用到的 `activity` 和 `receiver` 。

4、 用户使用 Call 期间融云保持 connected 状态。

## 发起通话

在私聊或讨论组的会话界面，点击输入框右侧的＋号，就可以选择`语音聊天`和`视频聊天`了。

<div style="border:solid 0px #999;width:380px;margin:auto">
![image](/docs/assets/img/guide/android_call_send.png)
</div>

或者你可以根据自己的代码逻辑，在需要的地方调用下面接口发起呼叫。

```java
/**
 * 发起单人通话。
 *
 * @param context   上下文
 * @param targetId  会话 id
 * @param mediaType 会话媒体类型
 */
RongCallKit.startSingleCall(Context context, String targetId, CallMediaType mediaType);

/**
 * 发起多人通话
 *
 * @param context          上下文
 * @param conversationType 会话类型
 * @param targetId         会话 id
 * @param mediaType        会话媒体类型
 * @param userIds          参与者 id 列表
 */
RongCallKit.startMultiCall(Context context, Conversation.ConversationType conversationType, String targetId, CallMediaType mediaType, ArrayList<String> userIds);
```

## 收到呼入的通话

如果 App 在前台或者在后台且并没有被系统回收时，当收到呼叫，会自动弹出通话界面。如果 App 已经被回收，`2.6.0` 以上的版本需要集成远程推送。这样即使被回收了也能收到 Call 的推送消息，点击推送消息启动 App 会自动弹出通话界面。详细请参考[远程推送集成文档](http://www.rongcloud.cn/docs/android_push.html#如何使用远程推送)。

## 通话回调接口

`IRongCallListener` 接口类用于 SDK 向 App 发送回调事件通知。App 通过继承该接口类的方法获取 SDK 的事件通知。

```java
public interface IRongCallListener {
    /**
     * 电话已拨出。
     * 主叫端拨出电话后，通过回调 onCallOutgoing 通知当前 call 的详细信息。
     *
     * @param callSession call 会话信息。
     * @param localVideo  本地 camera 信息。
     */
    void onCallOutgoing(RongCallSession callSession, SurfaceView localVideo);

    /**
     * 已建立通话。
     * 通话接通时，通过回调 onCallConnected 通知当前 call 的详细信息。
     *
     * @param callSession call 会话信息。
     * @param localVideo  本地 camera 信息。
     */
    void onCallConnected(RongCallSession callSession, SurfaceView localVideo);

    /**
     * 通话结束。
     * 通话中，对方挂断，己方挂断，或者通话过程网络异常造成的通话中断，都会回调 onCallDisconnected。
     *
     * @param callSession call 会话信息。
     * @param reason      通话中断原因。
     */
    void onCallDisconnected(RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason);

    /**
     * 被叫端正在振铃。
     * 主叫端拨出电话，被叫端收到请求，发出振铃响应时，回调 onRemoteUserRinging。
     *
     * @param userId 振铃端用户 id。
     */
    void onRemoteUserRinging(String userId);

    /**
     * 被叫端加入通话。
     * 主叫端拨出电话，被叫端收到请求后，加入通话，回调 onRemoteUserJoined。
     *
     * @param userId      加入用户的 id。
     * @param mediaType   加入用户的媒体类型，audio or video。
     * @param remoteVideo 加入用户者的 camera 信息。
     */
    void onRemoteUserJoined(String userId, RongCallCommon.CallMediaType mediaType, SurfaceView remoteVideo);

    /**
     * 会话中的某一个参与者，邀请好友加入会话，发出邀请请求后，回调 onRemoteUserInvited。
     *
     * @param userId    被邀请者的 id。
     * @param mediaType 被邀请者的 id。
     */
    void onRemoteUserInvited(String userId, RongCallCommon.CallMediaType mediaType);

    /**
     * 会话中的远端参与者离开。
     * 回调 onRemoteUserLeft 通知状态更新。
     *
     * @param userId 远端参与者的 id。
     * @param reason 远端参与者离开原因。
     */
    void onRemoteUserLeft(String userId, RongCallCommon.CallDisconnectedReason reason);

    /**
     * 当通话中的某一个参与者切换通话类型，例如由 audio 切换至 video，回调 onMediaTypeChanged。
     *
     * @param userId    切换者的 userId。
     * @param mediaType 切换者，切换后的媒体类型。
     * @param video     切换着，切换后的 camera 信息，如果由 video 切换至 audio，则为 null。
     */
    void onMediaTypeChanged(String userId, RongCallCommon.CallMediaType mediaType, SurfaceView video);

    /**
     * 通话过程中，发生异常。
     *
     * @param errorCode 异常原因。
     */
    void onError(RongCallCommon.CallErrorCode errorCode);

    /**
     * 远端参与者 camera 状态发生变化时，回调 onRemoteCameraDisabled 通知状态变化。
     *
     * @param userId   远端参与者 id。
     * @param disabled 远端参与者 camera 是否可用。
     */
    void onRemoteCameraDisabled(String userId, boolean disabled);
}
```

## 通话界面的控制

由于通话界面的需求是多种多样的，融云设计了一套通用的 UI 界面，开源供用户使用。对于普通用户来说，`CallKit` 应该满足需求；而对于有特殊需要的用户，可以自己来修改 `CallKit` 的源代码来满足需求。

### IMLib 用户注意事项

`RongCallService` 类里的成员变量 `uiReady`，是跟 `IMKit` 包配合使用的。它的作用是在登陆的时候，先显示主界面，然后再显示来电界面。`IMKit` 包会在主界面显示之后发送 `intent` 把 `uiReady` 置为 `true`。集成 `IMLib` 的用户会发现这个值始终为 `false` 。用户视自己的业务逻辑，可以仿照 `voipkit` 的源代码使用同样的机制，也可以忽视这个变量，在 `onReceivedCall` 里直接调用 `startVoIPActivity`。
