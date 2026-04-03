//! Audio encoding and decoding module

use thiserror::Error;

mod encoder;
mod decoder;

pub use encoder::AudioEncoder;
pub use decoder::AudioDecoder;

#[derive(Error, Debug)]
pub enum AudioError {
    #[error("Encoder error: {0}")]
    EncodeError(String),
    #[error("Decoder error: {0}")]
    DecodeError(String),
}

#[derive(Debug, Clone)]
pub struct AudioConfig {
    pub sample_rate: u32,
    pub channels: u16,
    pub bitrate: u32,
}

impl Default for AudioConfig {
    fn default() -> Self {
        Self {
            sample_rate: 48000,
            channels: 1,
            bitrate: 24000,
        }
    }
}

#[derive(Debug, Clone)]
pub struct EncodedAudio {
    pub data: Vec<u8>,
    pub timestamp_ms: u64,
}

#[derive(Debug, Clone)]
pub struct RawAudio {
    pub samples: Vec<i16>,
    pub timestamp_ms: u64,
}
