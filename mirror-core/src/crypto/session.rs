use super::{CryptoError, EncryptedPacket, Nonce, Tag};
use ring::aead::{self, Aes256Gcm, Nonce as RingNonce, UnboundKey, AES_256_GCM};
use ring::hkdf;
use log::debug;

/// Encryption session for secure communication
pub struct CryptoSession {
    encryption_key: [u8; 32],
    decryption_key: [u8; 32],
}

impl CryptoSession {
    /// Create a new crypto session from a shared secret
    pub fn from_shared_secret(secret: &[u8]) -> Result<Self, CryptoError> {
        debug!("Creating crypto session from shared secret");
        
        // Use HKDF to derive encryption and decryption keys
        let salt = hkdf::Salt::new(hkdf::HKDF_SHA256, &[]);
        let prk = salt.extract(secret);
        
        let mut encryption_key = [0u8; 32];
        let mut decryption_key = [0u8; 32];
        
        prk.expand(&[b"encryption"], &AES_256_GCM)
            .map_err(|e| CryptoError::KeyDerivationError(e.to_string()))?
            .fill(&mut encryption_key)
            .map_err(|e| CryptoError::KeyDerivationError(e.to_string()))?;
            
        prk.expand(&[b"decryption"], &AES_256_GCM)
            .map_err(|e| CryptoError::KeyDerivationError(e.to_string()))?
            .fill(&mut decryption_key)
            .map_err(|e| CryptoError::KeyDerivationError(e.to_string()))?;
        
        Ok(Self {
            encryption_key,
            decryption_key,
        })
    }
    
    /// Encrypt data
    pub fn encrypt(&self, plaintext: &[u8], nonce: Nonce) -> Result<EncryptedPacket, CryptoError> {
        let unbound_key = UnboundKey::new(&AES_256_GCM, &self.encryption_key)
            .map_err(|e| CryptoError::EncryptError(e.to_string()))?;
        let sealing_key = aead::LessSafeKey::new(unbound_key);
        
        let ring_nonce = RingNonce::assume_unique_for_key(nonce);
        let mut ciphertext = plaintext.to_vec();
        let tag = sealing_key.seal_in_place_separate_tag(
            ring_nonce,
            aead::Aad::empty(),
            &mut ciphertext,
        ).map_err(|e| CryptoError::EncryptError(e.to_string()))?;
        
        let mut tag_bytes = [0u8; 16];
        tag_bytes.copy_from_slice(tag.as_ref());
        
        Ok(EncryptedPacket {
            nonce,
            ciphertext,
            tag: tag_bytes,
        })
    }
    
    /// Decrypt data
    pub fn decrypt(&self, packet: &EncryptedPacket) -> Result<Vec<u8>, CryptoError> {
        let unbound_key = UnboundKey::new(&AES_256_GCM, &self.decryption_key)
            .map_err(|e| CryptoError::DecryptError(e.to_string()))?;
        let opening_key = aead::LessSafeKey::new(unbound_key);
        
        let ring_nonce = RingNonce::assume_unique_for_key(packet.nonce);
        let mut ciphertext = packet.ciphertext.clone();
        ciphertext.extend_from_slice(&packet.tag);
        
        let plaintext = opening_key.open_in_place(
            ring_nonce,
            aead::Aad::empty(),
            &mut ciphertext,
        ).map_err(|e| CryptoError::DecryptError(e.to_string()))?;
        
        Ok(plaintext.to_vec())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_encrypt_decrypt() {
        let secret = b"test_secret_key_32bytes_long!!!";
        let session = CryptoSession::from_shared_secret(secret).unwrap();
        
        let plaintext = b"Hello, Mirror!";
        let nonce = [0u8; 12];
        
        let encrypted = session.encrypt(plaintext, nonce).unwrap();
        let decrypted = session.decrypt(&encrypted).unwrap();
        
        assert_eq!(plaintext.to_vec(), decrypted);
    }
}
