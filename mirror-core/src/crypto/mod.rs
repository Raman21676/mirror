//! Encryption module

use thiserror::Error;
use rand::Rng;

mod session;

pub use session::CryptoSession;

#[derive(Error, Debug)]
pub enum CryptoError {
    #[error("Encryption failed: {0}")]
    EncryptError(String),
    #[error("Decryption failed: {0}")]
    DecryptError(String),
    #[error("Key derivation failed: {0}")]
    KeyDerivationError(String),
}

/// Nonce type for AES-GCM
pub type Nonce = [u8; 12];

/// Tag type for AES-GCM authentication
pub type Tag = [u8; 16];

/// Generate a random nonce
pub fn generate_nonce() -> Nonce {
    let mut nonce = [0u8; 12];
    rand::thread_rng().fill(&mut nonce);
    nonce
}

/// Encrypted packet structure
#[derive(Debug, Clone)]
pub struct EncryptedPacket {
    pub nonce: Nonce,
    pub ciphertext: Vec<u8>,
    pub tag: Tag,
}
