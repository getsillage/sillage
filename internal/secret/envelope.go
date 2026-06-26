package secret

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"time"

	"golang.org/x/crypto/hkdf"
)

const EnvelopeAlgorithm = "AES-256-GCM+HKDF-SHA256"

type Envelope struct {
	Algorithm  string `json:"algorithm"`
	KeyID      string `json:"key_id"`
	Nonce      string `json:"nonce"`
	Ciphertext string `json:"ciphertext"`
	CreatedAt  string `json:"created_at"`
}

func EncryptEnvelope(secretValue, plaintext string) (string, error) {
	if plaintext == "" {
		return "", nil
	}
	key, keyID, err := deriveEncryptionKey(secretValue)
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", fmt.Errorf("create aes cipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("create gcm: %w", err)
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", fmt.Errorf("generate nonce: %w", err)
	}
	envelope := Envelope{
		Algorithm:  EnvelopeAlgorithm,
		KeyID:      keyID,
		Nonce:      base64.RawURLEncoding.EncodeToString(nonce),
		Ciphertext: base64.RawURLEncoding.EncodeToString(gcm.Seal(nil, nonce, []byte(plaintext), nil)),
		CreatedAt:  time.Now().UTC().Format(time.RFC3339),
	}
	payload, err := json.Marshal(envelope)
	if err != nil {
		return "", fmt.Errorf("encode envelope: %w", err)
	}
	return string(payload), nil
}

func DecryptEnvelope(secretValue, raw string) (string, error) {
	if raw == "" {
		return "", nil
	}
	var envelope Envelope
	if err := json.Unmarshal([]byte(raw), &envelope); err != nil {
		return "", fmt.Errorf("decode envelope: %w", err)
	}
	if envelope.Algorithm != EnvelopeAlgorithm {
		return "", fmt.Errorf("unsupported envelope algorithm")
	}
	key, keyID, err := deriveEncryptionKey(secretValue)
	if err != nil {
		return "", err
	}
	if envelope.KeyID != keyID {
		return "", fmt.Errorf("envelope key unavailable")
	}
	nonce, err := base64.RawURLEncoding.DecodeString(envelope.Nonce)
	if err != nil {
		return "", fmt.Errorf("decode nonce: %w", err)
	}
	ciphertext, err := base64.RawURLEncoding.DecodeString(envelope.Ciphertext)
	if err != nil {
		return "", fmt.Errorf("decode ciphertext: %w", err)
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", fmt.Errorf("create aes cipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", fmt.Errorf("create gcm: %w", err)
	}
	plaintext, err := gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return "", fmt.Errorf("decrypt envelope: %w", err)
	}
	return string(plaintext), nil
}

func deriveEncryptionKey(secretValue string) ([]byte, string, error) {
	if secretValue == "" {
		return nil, "", fmt.Errorf("encryption secret is empty")
	}
	hash := sha256.Sum256([]byte(secretValue))
	keyID := base64.RawURLEncoding.EncodeToString(hash[:8])
	reader := hkdf.New(sha256.New, []byte(secretValue), nil, []byte("sillage ai api key envelope"))
	key := make([]byte, 32)
	if _, err := io.ReadFull(reader, key); err != nil {
		return nil, "", fmt.Errorf("derive encryption key: %w", err)
	}
	return key, keyID, nil
}
