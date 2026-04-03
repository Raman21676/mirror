use super::{EncodedFrame, RawFrame, VideoConfig, VideoError};
use log::debug;

pub struct VideoDecoder {
    _config: VideoConfig,
}

impl VideoDecoder {
    pub fn new(config: VideoConfig) -> Result<Self, VideoError> {
        debug!("Creating video decoder");
        Ok(Self { _config: config })
    }
    
    pub fn decode(&mut self, frame: &EncodedFrame) -> Result<RawFrame, VideoError> {
        Ok(RawFrame {
            data: vec![0u8; 1000],
            width: 1280,
            height: 720,
            timestamp_ms: frame.timestamp_ms,
        })
    }
}
