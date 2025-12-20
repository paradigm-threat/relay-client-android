use client_core::{
    transpile_jsx,
    get_themed_styler_version,
    get_transpiler_version,
    render_css,
    get_rn_styles,
    get_android_styles,
    get_default_state,
    check_port,
};
use jni::JNIEnv;
use jni::objects::{JObject, JString};
use jni::sys::jstring;

fn new_jstring(env: &mut JNIEnv, value: &str) -> jstring {
    match env.new_string(value) {
        Ok(jstr) => jstr.into_raw(),
        Err(_) => env.new_string("").unwrap().into_raw(),
    }
}

fn jstring_to_string(env: &mut JNIEnv, input: JString) -> Option<String> {
    match env.get_string(&input) {
        Ok(jni_str) => jni_str.to_str().ok().map(|s| s.to_string()),
        Err(_) => None,
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_ThemedStylerModule_nativeRenderCss(
    mut env: JNIEnv,
    _this: JObject,
    usage_json: JString,
    themes_json: JString,
) -> jstring {
    let usage = jstring_to_string(&mut env, usage_json).unwrap_or_else(|| "{}".to_string());
    let themes = jstring_to_string(&mut env, themes_json).unwrap_or_else(|| "{}".to_string());
    let css = render_css(&usage, &themes);
    new_jstring(&mut env, &css)
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_ThemedStylerModule_nativeGetRnStyles(
    mut env: JNIEnv,
    _this: JObject,
    selector: JString,
    classes_json: JString,
    themes_json: JString,
) -> jstring {
    let sel = jstring_to_string(&mut env, selector).unwrap_or_else(|| "".to_string());
    let classes = jstring_to_string(&mut env, classes_json).unwrap_or_else(|| "[]".to_string());
    let themes = jstring_to_string(&mut env, themes_json).unwrap_or_else(|| "{}".to_string());
    let styles = get_rn_styles(&sel, &classes, &themes);
    new_jstring(&mut env, &styles)
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_ThemedStylerModule_nativeGetAndroidStyles(
    mut env: JNIEnv,
    _this: JObject,
    selector: JString,
    classes_json: JString,
    themes_json: JString,
) -> jstring {
    let sel = jstring_to_string(&mut env, selector).unwrap_or_else(|| "".to_string());
    let classes = jstring_to_string(&mut env, classes_json).unwrap_or_else(|| "[]".to_string());
    let themes = jstring_to_string(&mut env, themes_json).unwrap_or_else(|| "{}".to_string());
    let styles = get_android_styles(&sel, &classes, &themes);
    new_jstring(&mut env, &styles)
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_ThemedStylerModule_nativeGetDefaultState(
    mut env: JNIEnv,
    _this: JObject,
) -> jstring {
    new_jstring(&mut env, &get_default_state())
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_ThemedStylerModule_nativeGetVersion(
    mut env: JNIEnv,
    _this: JObject,
) -> jstring {
    new_jstring(&mut env, &get_themed_styler_version())
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_RustTranspilerModule_nativeTranspile(
    mut env: JNIEnv,
    _this: JObject,
    code: JString,
    filename: JString,
    include_runtime: jni::sys::jboolean,
) -> jstring {
    let source = match jstring_to_string(&mut env, code) {
        Some(val) => val,
        None => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", "code was null");
            return new_jstring(&mut env, "");
        }
    };
    let file = jstring_to_string(&mut env, filename).unwrap_or_else(|| "module.tsx".to_string());
    let with_runtime = include_runtime == jni::sys::JNI_TRUE;

    match transpile_jsx(&source, &file, with_runtime) {
        Ok(output) => new_jstring(&mut env, &output),
        Err(err) => {
            let _ = env.throw_new("java/lang/RuntimeException", err.to_string());
            new_jstring(&mut env, "")
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_RustTranspilerModule_nativeGetVersion(
    mut env: JNIEnv,
    _this: JObject,
) -> jstring {
    new_jstring(&mut env, &get_transpiler_version())
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_com_relay_pure_NetworkModule_nativeCheckPort(
    mut env: JNIEnv,
    _this: JObject,
    host: JString,
    port: jni::sys::jint,
) -> jni::sys::jboolean {
    let host_str = jstring_to_string(&mut env, host).unwrap_or_else(|| "127.0.0.1".to_string());
    if check_port(&host_str, port as u16) {
        jni::sys::JNI_TRUE
    } else {
        jni::sys::JNI_FALSE
    }
}
