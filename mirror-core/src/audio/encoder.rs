use super::{AudioConfig, AudioError, EncodedAudio, RawAudio};
use log::debug;

pub struct AudioEncoder {
    _config: AudioConfig,
}

impl AudioEncoder {
    pub fn new(config: AudioConfig) -> Result<Self, AudioError> {
        debug!("Creating audio encoder: {:?}", config);
        Ok(Self { _config: config })
    }
    
    pub fn encode(&mut self, audio: &RawAudio) -> Result<EncodedAudio, AudioError> {
        Ok(EncodedAudio {
            data: vec![0u8; 100], // Placeholder
            timestamp_ms: audio.timestamp_ms,
        })
    }
}
