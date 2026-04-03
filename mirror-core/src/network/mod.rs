//! Network streaming module

use thiserror::Error;

mod mux;
mod demux;

pub use mux::StreamMux;
pub use demux::StreamDemux;

#[derive(Error, Debug)]
pub enum NetworkError {
    #[error("Mux error: {0}")]
    MuxError(String),
    #[error("Demux error: {0}")]
    DemuxError(String),
}

/// Stream type identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum StreamType {
    Video = 0x01,
    Audio = 0x02,
    Location = 0x03,
    Screen = 0x04,
    Control = 0xFF,
}

/// Multiplexed packet structure
#[derive(Debug, Clone)]
pub struct MuxPacket {
    pub stream_type: StreamType,
    pub timestamp_ms: u64,
    pub payload: Vec<u8>,
}
