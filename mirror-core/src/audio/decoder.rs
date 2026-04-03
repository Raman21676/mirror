use super::{AudioConfig, AudioError, EncodedAudio, RawAudio};
use log::debug;

pub struct AudioDecoder {
    _config: AudioConfig,
}

impl AudioDecoder {
    pub fn new(config: AudioConfig) -> Result<Self, AudioError> {
        debug!("Creating audio decoder");
        Ok(Self { _config: config })
    }
    
    pub fn decode(&mut self, audio: &EncodedAudio) -> Result<RawAudio, AudioError> {
        Ok(RawAudio {
            samples: vec![0i16; 960],
            timestamp_ms: audio.timestamp_ms,
        })
    }
}
