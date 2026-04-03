//! Mirror Core Library
//! 
//! High-performance native library for the Mirror surveillance app.
//! Handles video encoding, audio encoding, encryption, and stream multiplexing.

use jni::JavaVM;
use std::sync::OnceLock;

// Module declarations
pub mod video;
pub mod audio;
pub mod crypto;
pub mod network;
pub mod jni_bridge;

// Re-export main types
pub use crypto::CryptoSession;
pub use network::{StreamMux, StreamDemux};

/// Global JVM reference for callback support
static GLOBAL_JVM: OnceLock<JavaVM> = OnceLock::new();

/// Initialize the global JVM reference
pub fn init_jvm(vm: JavaVM) {
    let _ = GLOBAL_JVM.set(vm);
}

/// Get the global JVM reference
pub fn get_jvm() -> Option<&'static JavaVM> {
    GLOBAL_JVM.get()
}

/// Library version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Initialize logging for Android
pub fn init_logging() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Debug)
            .with_tag("MirrorCore"),
    );
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version() {
        assert!(!VERSION.is_empty());
    }
}
