package auth

import (
	"fmt"
	"strings"
	"testing"
)

func TestPasswordHashVerify(t *testing.T) {
	hash, err := HashPassword("correct horse battery staple")
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	ok, err := VerifyPassword(hash, "correct horse battery staple")
	if err != nil {
		t.Fatalf("VerifyPassword() error = %v", err)
	}
	if !ok {
		t.Fatal("password should verify")
	}

	ok, err = VerifyPassword(hash, "wrong")
	if err != nil {
		t.Fatalf("VerifyPassword(wrong) error = %v", err)
	}
	if ok {
		t.Fatal("wrong password should not verify")
	}
}

func TestValidateNewPasswordLimits(t *testing.T) {
	for _, tt := range []struct {
		name     string
		password string
		wantErr  bool
	}{
		{name: "minimum", password: "12345678"},
		{name: "unicode", password: "密码管理器生成值一二三"},
		{name: "too short", password: "1234567", wantErr: true},
		{name: "too large", password: strings.Repeat("x", MaxPasswordBytes+1), wantErr: true},
	} {
		t.Run(tt.name, func(t *testing.T) {
			err := ValidateNewPassword(tt.password)
			if (err != nil) != tt.wantErr {
				t.Fatalf("ValidateNewPassword() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}

func TestVerifyPasswordRejectsMalformedParameters(t *testing.T) {
	hash, err := HashPassword("correct horse battery staple")
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}

	parts := strings.Split(hash, "$")
	if len(parts) != 5 {
		t.Fatalf("HashPassword() parts = %d, want 5", len(parts))
	}
	for _, tt := range []struct {
		name   string
		params string
	}{
		{name: "missing", params: fmt.Sprintf("m=%d,t=%d", argonMemory, argonIterations)},
		{name: "unknown", params: fmt.Sprintf("m=%d,t=%d,p=%d,x=1", argonMemory, argonIterations, argonParallelism)},
		{name: "duplicate", params: fmt.Sprintf("m=%d,t=%d,p=%d,p=%d", argonMemory, argonIterations, argonParallelism, argonParallelism)},
		{name: "memory", params: fmt.Sprintf("m=%d,t=%d,p=%d", argonMemory+1, argonIterations, argonParallelism)},
		{name: "iterations", params: fmt.Sprintf("m=%d,t=%d,p=%d", argonMemory, argonIterations+1, argonParallelism)},
		{name: "parallelism overflow", params: fmt.Sprintf("m=%d,t=%d,p=258", argonMemory, argonIterations)},
	} {
		t.Run(tt.name, func(t *testing.T) {
			malformed := strings.Join([]string{parts[0], parts[1], tt.params, parts[3], parts[4]}, "$")
			if ok, err := VerifyPassword(malformed, "correct horse battery staple"); err == nil || ok {
				t.Fatalf("VerifyPassword() = (%v, %v), want rejected hash", ok, err)
			}
		})
	}
}

func TestVerifyPasswordRejectsMalformedLengths(t *testing.T) {
	hash, err := HashPassword("correct horse battery staple")
	if err != nil {
		t.Fatalf("HashPassword() error = %v", err)
	}
	parts := strings.Split(hash, "$")
	if len(parts) != 5 {
		t.Fatalf("HashPassword() parts = %d, want 5", len(parts))
	}

	for _, tt := range []struct {
		name string
		part int
		data string
	}{
		{name: "salt", part: 3, data: "c2hvcnQ"},
		{name: "hash", part: 4, data: "c2hvcnQ"},
	} {
		t.Run(tt.name, func(t *testing.T) {
			malformedParts := append([]string(nil), parts...)
			malformedParts[tt.part] = tt.data
			if ok, err := VerifyPassword(strings.Join(malformedParts, "$"), "correct horse battery staple"); err == nil || ok {
				t.Fatalf("VerifyPassword() = (%v, %v), want rejected hash", ok, err)
			}
		})
	}
}
