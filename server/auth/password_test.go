package auth

import (
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
