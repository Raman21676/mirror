//! JNI bridge for Android integration

use jni::JNIEnv;
use jni::objects::JClass;
use jni::signature::JavaType;
use log::info;

/// Initialize the Rust library
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeInit(env: JNIEnv, _class: JClass) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug),
    );
    info!("Mirror Core library initialized");
}

/// Get library version
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeGetVersion(env: JNIEnv, _class: JClass) -> jni::sys::jstring {
    let version = env.new_string(crate::VERSION)
        .expect("Failed to create version string");
    version.into_raw()
}
