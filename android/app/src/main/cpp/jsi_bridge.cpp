#include <jni.h>
#include <android/log.h>

#define LOG_TAG "RelayJSIBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT void JNICALL
Java_com_relay_pure_HermesNativeBridge_setupJSIBridge(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong /*jsiRuntimePtr*/) {
    // Stub implementation to keep native library loading working.
    LOGI("setupJSIBridge stub invoked");
}

}
