package auth

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"strconv"
	"strings"
	"unicode/utf8"

	"golang.org/x/crypto/argon2"
)

const (
	MinPasswordRunes  = 8
	MaxPasswordBytes  = 256
	passwordAlgorithm = "argon2id"
	argonMemory       = 64 * 1024
	argonIterations   = 3
	argonParallelism  = 2
	argonKeyLength    = 32
)

func HashPassword(password string) (string, error) {
	if err := ValidateNewPassword(password); err != nil {
		return "", err
	}
	var salt [16]byte
	if _, err := rand.Read(salt[:]); err != nil {
		return "", fmt.Errorf("generate password salt: %w", err)
	}
	hash := argon2.IDKey([]byte(password), salt[:], argonIterations, argonMemory, argonParallelism, argonKeyLength)
	return fmt.Sprintf(
		"%s$v=19$m=%d,t=%d,p=%d$%s$%s",
		passwordAlgorithm,
		argonMemory,
		argonIterations,
		argonParallelism,
		base64.RawStdEncoding.EncodeToString(salt[:]),
		base64.RawStdEncoding.EncodeToString(hash),
	), nil
}

func VerifyPassword(encoded, password string) (bool, error) {
	if !utf8.ValidString(password) || len(password) > MaxPasswordBytes {
		return false, nil
	}
	parts := strings.Split(encoded, "$")
	if len(parts) != 5 || parts[0] != passwordAlgorithm || parts[1] != "v=19" {
		return false, fmt.Errorf("unsupported password hash")
	}

	params := map[string]uint32{}
	for _, item := range strings.Split(parts[2], ",") {
		keyValue := strings.SplitN(item, "=", 2)
		if len(keyValue) != 2 {
			return false, fmt.Errorf("invalid password hash params")
		}
		value, err := strconv.ParseUint(keyValue[1], 10, 32)
		if err != nil {
			return false, fmt.Errorf("invalid password hash param %s: %w", keyValue[0], err)
		}
		params[keyValue[0]] = uint32(value)
	}

	salt, err := base64.RawStdEncoding.DecodeString(parts[3])
	if err != nil {
		return false, fmt.Errorf("decode password salt: %w", err)
	}
	expected, err := base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil {
		return false, fmt.Errorf("decode password hash: %w", err)
	}
	actual := argon2.IDKey([]byte(password), salt, params["t"], params["m"], uint8(params["p"]), uint32(len(expected)))
	return subtle.ConstantTimeCompare(actual, expected) == 1, nil
}

func ValidateNewPassword(password string) error {
	if !utf8.ValidString(password) {
		return fmt.Errorf("password must be valid UTF-8")
	}
	if utf8.RuneCountInString(password) < MinPasswordRunes {
		return fmt.Errorf("password must contain at least %d characters", MinPasswordRunes)
	}
	if len(password) > MaxPasswordBytes {
		return fmt.Errorf("password must not exceed %d bytes", MaxPasswordBytes)
	}
	return nil
}
