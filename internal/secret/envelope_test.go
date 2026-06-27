package secret_test

import (
	"strings"
	"testing"

	"github.com/getsillage/sillage/internal/secret"
)

func TestEnvelopeEncryptDecrypt(t *testing.T) {
	raw, err := secret.EncryptEnvelope("secret-a", "api-key")
	if err != nil {
		t.Fatalf("EncryptEnvelope() error = %v", err)
	}
	if !strings.Contains(raw, secret.EnvelopeAlgorithm) {
		t.Fatalf("envelope missing algorithm: %s", raw)
	}

	plain, err := secret.DecryptEnvelope("secret-a", raw)
	if err != nil {
		t.Fatalf("DecryptEnvelope() error = %v", err)
	}
	if plain != "api-key" {
		t.Fatalf("plaintext = %q, want api-key", plain)
	}

	if _, err := secret.DecryptEnvelope("secret-b", raw); err == nil {
		t.Fatal("DecryptEnvelope() with wrong secret error = nil")
	}
}
