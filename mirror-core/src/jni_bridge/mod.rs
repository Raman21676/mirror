//! JNI bridge for Android integration

use jni::objects::{JByteArray, JClass, JObject};
use jni::sys::{jbyteArray, jint, jobjectArray};
use jni::JNIEnv;
use log::info;
use std::sync::LazyLock;

use crate::crypto::{generate_nonce, CryptoSession};
use crate::network::{StreamDemux, StreamMux, StreamType};

static GLOBAL_DEMUX: LazyLock<std::sync::Mutex<StreamDemux>> = LazyLock::new(|| std::sync::Mutex::new(StreamDemux::new()));

/// Initialize the Rust library
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeInit(mut _env: JNIEnv, _class: JClass) {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug),
    );
    info!("Mirror Core library initialized");
}

/// Get library version
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeGetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jni::sys::jstring {
    let version = env
        .new_string(crate::VERSION)
        .expect("Failed to create version string");
    version.into_raw()
}

/// Encrypt a packet with AES-256-GCM.
/// Returns: [nonce (12 bytes)][ciphertext][tag (16 bytes)] or null on error.
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeEncryptPacket(
    mut env: JNIEnv,
    _class: JClass,
    data: jbyteArray,
    key: jbyteArray,
) -> jbyteArray {
    let data = convert_byte_array(&mut env, data);
    let key = convert_byte_array(&mut env, key);

    if key.len() != 32 {
        return std::ptr::null_mut();
    }

    let key_array: [u8; 32] = key.try_into().unwrap();
    let session = CryptoSession::from_raw_key(&key_array);
    let nonce = generate_nonce();

    match session.encrypt(&data, nonce) {
        Ok(packet) => {
            let mut result = Vec::with_capacity(12 + packet.ciphertext.len() + 16);
            result.extend_from_slice(&packet.nonce);
            result.extend_from_slice(&packet.ciphertext);
            result.extend_from_slice(&packet.tag);
            create_byte_array(&mut env, &result)
        }
        Err(_) => std::ptr::null_mut(),
    }
}

/// Decrypt a packet with AES-256-GCM.
/// Input: [nonce (12 bytes)][ciphertext][tag (16 bytes)]
/// Returns plaintext or null on error.
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeDecryptPacket(
    mut env: JNIEnv,
    _class: JClass,
    data: jbyteArray,
    key: jbyteArray,
) -> jbyteArray {
    let data = convert_byte_array(&mut env, data);
    let key = convert_byte_array(&mut env, key);

    if key.len() != 32 || data.len() < 28 {
        return std::ptr::null_mut();
    }

    let key_array: [u8; 32] = key.try_into().unwrap();
    let session = CryptoSession::from_raw_key(&key_array);

    let mut nonce = [0u8; 12];
    nonce.copy_from_slice(&data[0..12]);
    let tag_offset = data.len() - 16;
    let ciphertext = data[12..tag_offset].to_vec();
    let mut tag = [0u8; 16];
    tag.copy_from_slice(&data[tag_offset..]);

    let packet = crate::crypto::EncryptedPacket {
        nonce,
        ciphertext,
        tag,
    };

    match session.decrypt(&packet) {
        Ok(plaintext) => create_byte_array(&mut env, &plaintext),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Mux a payload into a framed packet.
/// stream_type: 0x01=Video, 0x02=Audio, 0x04=Screen, 0xFF=Control
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeMuxPacket(
    mut env: JNIEnv,
    _class: JClass,
    stream_type: jint,
    payload: jbyteArray,
) -> jbyteArray {
    let payload = convert_byte_array(&mut env, payload);
    let stream_type = match stream_type as u8 {
        0x01 => StreamType::Video,
        0x02 => StreamType::Audio,
        0x04 => StreamType::Screen,
        0xFF => StreamType::Control,
        _ => StreamType::Video,
    };

    let packet = crate::network::MuxPacket {
        stream_type,
        timestamp_ms: 0,
        payload,
    };

    let mut mux = StreamMux::new();
    match mux.mux(&packet) {
        Ok(bytes) => create_byte_array(&mut env, &bytes),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Demux raw bytes into individual payloads.
/// Returns an array of ByteArray payloads. May return empty array if no full packet is available.
#[no_mangle]
pub extern "C" fn Java_com_mirror_core_RustBridge_nativeDemuxPacket(
    mut env: JNIEnv,
    _class: JClass,
    data: jbyteArray,
) -> jobjectArray {
    let data = convert_byte_array(&mut env, data);

    let mut demux = GLOBAL_DEMUX.lock().unwrap();
    demux.feed(&data);

    let mut packets = Vec::new();
    while let Ok(Some(packet)) = demux.demux() {
        packets.push(packet.payload);
    }

    let byte_array_class = env.find_class("[B").expect("Failed to find byte[] class");
    let array = env
        .new_object_array(
            packets.len() as jni::sys::jsize,
            &byte_array_class,
            JObject::null(),
        )
        .expect("Failed to create object array");

    for (i, packet) in packets.iter().enumerate() {
        let byte_array = create_byte_array(&mut env, packet);
        let obj = unsafe { JObject::from_raw(byte_array) };
        env.set_object_array_element(&array, i as jni::sys::jsize, &obj)
            .expect("Failed to set array element");
    }

    array.into_raw()
}

fn convert_byte_array(env: &mut JNIEnv, array: jbyteArray) -> Vec<u8> {
    if array.is_null() {
        return Vec::new();
    }
    let jarray = unsafe { JByteArray::from_raw(array) };
    env.convert_byte_array(&jarray).expect("Failed to read byte array")
}

fn create_byte_array(env: &mut JNIEnv, data: &[u8]) -> jbyteArray {
    let jarray = env.byte_array_from_slice(data).expect("Failed to create byte array");
    jarray.into_raw()
}
