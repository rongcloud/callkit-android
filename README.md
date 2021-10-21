## callkit-android

Open-source code of RongCloud VoIP Audio/Video UI. 融云音视频通话功能 UI 界面 SDK 开源代码。

## 适用场景

融云提供 CallKit 源码，是为方便开发者根据 App 风格对呼叫 UI 做个性化的修改，比如色调搭配，按钮位置等，都可以自由定制。

## 集成步骤

1. 先按照 Maven 导入或本地手动导入的方式，集成 CallLib、IMKit、IMLib 三个 CallKit 依赖库，并确保都是当时官网的最新版本，如下：

    ```groovy
    dependencies {
        implementation 'cn.rongcloud.sdk:call_lib:x.y.z'
        implementation 'cn.rongcloud.sdk:im_kit:x.y.z'
        implementation 'cn.rongcloud.sdk:im_lib:x.y.z'
    }
    ```

    > CallKit 源码因为是开源的，融云不提供老版本的下载。用户配合 CallKit 所使用的 CallLib、IMKit、IMLib 版本应该也是官网此时的最新版本。

2. 进入工程目录，克隆 CallKit 源码：

    ```shell
    cd <ProjectFolder>
    git clone https://github.com/rongcloud/callkit-android.git
    ```

3. 在 `settings.gradle` 文件中，添加引用：

    ```groovy
    include ':callkit-android'
    ```

4. 在应用的 `build.gradle` 中，添加依赖：

    ```groovy
    dependencies {
        ...
        implementation project(':callkit-android')
    }
    ```