package auth

import "testing"

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
