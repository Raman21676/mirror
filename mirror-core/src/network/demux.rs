use super::{MuxPacket, NetworkError, StreamType};
use log::debug;

/// Stream demultiplexer - separates combined stream into individual streams
pub struct StreamDemux {
    buffer: Vec<u8>,
}

impl StreamDemux {
    pub fn new() -> Self {
        debug!("Creating stream demultiplexer");
        Self {
            buffer: Vec::new(),
        }
    }
    
    /// Feed data into the demux buffer
    pub fn feed(&mut self, data: &[u8]) {
        self.buffer.extend_from_slice(data);
    }
    
    /// Try to extract a packet from the buffer
    pub fn demux(&mut self) -> Result<Option<MuxPacket>, NetworkError> {
        // Need at least header: 1 + 4 + 2 = 7 bytes
        if self.buffer.len() < 7 {
            return Ok(None);
        }
        
        let stream_type = match self.buffer[0] {
            0x01 => StreamType::Video,
            0x02 => StreamType::Audio,
            0x03 => StreamType::Location,
            0x04 => StreamType::Screen,
            0xFF => StreamType::Control,
            _ => return Err(NetworkError::DemuxError("Invalid stream type".to_string())),
        };
        
        let timestamp = u32::from_be_bytes([self.buffer[1], self.buffer[2], 
                                             self.buffer[3], self.buffer[4]]) as u64;
        let payload_len = u16::from_be_bytes([self.buffer[5], self.buffer[6]]) as usize;
        
        // Check if we have the full packet (including auth tag)
        let total_len = 7 + payload_len + 16;
        if self.buffer.len() < total_len {
            return Ok(None);
        }
        
        let payload = self.buffer[7..7 + payload_len].to_vec();
        
        // Remove processed bytes from buffer
        self.buffer.drain(0..total_len);
        
        Ok(Some(MuxPacket {
            stream_type,
            timestamp_ms: timestamp,
            payload,
        }))
    }
}

impl Default for StreamDemux {
    fn default() -> Self {
        Self::new()
    }
}
