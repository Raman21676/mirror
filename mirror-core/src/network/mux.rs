use super::{MuxPacket, NetworkError, StreamType};
use log::debug;

/// Stream multiplexer - combines multiple streams into one
pub struct StreamMux {
    sequence_number: u32,
}

impl StreamMux {
    pub fn new() -> Self {
        debug!("Creating stream multiplexer");
        Self {
            sequence_number: 0,
        }
    }
    
    /// Mux a packet into bytes
    /// Format: [1: type][4: timestamp][2: length][payload][16: auth_tag]
    pub fn mux(&mut self, packet: &MuxPacket) -> Result<Vec<u8>, NetworkError> {
        self.sequence_number = self.sequence_number.wrapping_add(1);
        
        let mut output = Vec::new();
        
        // Stream type (1 byte)
        output.push(packet.stream_type as u8);
        
        // Timestamp (4 bytes, big-endian)
        output.extend_from_slice(&(packet.timestamp_ms as u32).to_be_bytes());
        
        // Payload length (2 bytes, big-endian)
        let len = packet.payload.len() as u16;
        output.extend_from_slice(&len.to_be_bytes());
        
        // Payload
        output.extend_from_slice(&packet.payload);
        
        // Placeholder for auth tag (16 bytes)
        output.extend_from_slice(&[0u8; 16]);
        
        Ok(output)
    }
}

impl Default for StreamMux {
    fn default() -> Self {
        Self::new()
    }
}
