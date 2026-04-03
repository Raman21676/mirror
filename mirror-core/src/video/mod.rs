//! Video encoding and decoding module

use thiserror::Error;

mod encoder;
mod decoder;

pub use encoder::VideoEncoder;
pub use decoder::VideoDecoder;

#[derive(Error, Debug)]
pub enum VideoError {
    #[error("Encoder initialization failed: {0}")]
    InitFailed(String),
    #[error("Encoding failed: {0}")]
    EncodeFailed(String),
    #[error("Decoding failed: {0}")]
    DecodeFailed(String),
}

#[derive(Debug, Clone)]
pub struct VideoConfig {
    pub width: u32,
    pub height: u32,
    pub fps: u32,
    pub bitrate_bps: u32,
}

impl Default for VideoConfig {
    fn default() -> Self {
        Self {
            width: 1280,
            height: 720,
            fps: 30,
            bitrate_bps: 2_000_000,
        }
    }
}

#[derive(Debug, Clone)]
pub struct EncodedFrame {
    pub data: Vec<u8>,
    pub timestamp_ms: u64,
    pub is_keyframe: bool,
}

#[derive(Debug, Clone)]
pub struct RawFrame {
    pub data: Vec<u8>,
    pub width: u32,
    pub height: u32,
    pub timestamp_ms: u64,
}
