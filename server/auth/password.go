package auth

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"strconv"
	"strings"

	"golang.org/x/crypto/argon2"
)

const (
	passwordAlgorithm = "argon2id"
	argonMemory       = 64 * 1024
	argonIterations   = 3
	argonParallelism  = 2
	argonKeyLength    = 32
)

func HashPassword(password string) (string, error) {
	if password == "" {
		return "", fmt.Errorf("password is required")
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
