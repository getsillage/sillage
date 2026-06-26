package secret

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

const (
	sessionSecretName    = "SESSION_SECRET"
	encryptionSecretName = "ENCRYPTION_SECRET"
)

type Secrets struct {
	SessionSecret    string `json:"session_secret"`
	EncryptionSecret string `json:"encryption_secret"`
}

func Load(dataDir string) (*Secrets, error) {
	runtimeDir := filepath.Join(dataDir, "runtime")
	if err := os.MkdirAll(runtimeDir, 0o770); err != nil {
		return nil, fmt.Errorf("create runtime dir: %w", err)
	}
	path := filepath.Join(runtimeDir, "secrets.json")

	secrets := &Secrets{
		SessionSecret:    os.Getenv(sessionSecretName),
		EncryptionSecret: os.Getenv(encryptionSecretName),
	}

	existing, err := readFile(path)
	if err != nil {
		return nil, err
	}
	if secrets.SessionSecret == "" {
		secrets.SessionSecret = existing.SessionSecret
	}
	if secrets.EncryptionSecret == "" {
		secrets.EncryptionSecret = existing.EncryptionSecret
	}
	if secrets.SessionSecret == "" {
		secrets.SessionSecret = randomSecret()
	}
	if secrets.EncryptionSecret == "" {
		secrets.EncryptionSecret = randomSecret()
	}

	if existing.SessionSecret == "" || existing.EncryptionSecret == "" {
		if err := writeFile(path, secrets); err != nil {
			return nil, err
		}
	}
	return secrets, nil
}

func readFile(path string) (*Secrets, error) {
	b, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return &Secrets{}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read secrets file: %w", err)
	}
	var secrets Secrets
	if err := json.Unmarshal(b, &secrets); err != nil {
		return nil, fmt.Errorf("decode secrets file: %w", err)
	}
	return &secrets, nil
}

func writeFile(path string, secrets *Secrets) error {
	b, err := json.MarshalIndent(secrets, "", "  ")
	if err != nil {
		return fmt.Errorf("encode secrets file: %w", err)
	}
	b = append(b, '\n')
	if err := os.WriteFile(path, b, 0o600); err != nil {
		return fmt.Errorf("write secrets file: %w", err)
	}
	return nil
}

func randomSecret() string {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(fmt.Errorf("generate secret: %w", err))
	}
	return base64.RawURLEncoding.EncodeToString(b[:])
}
