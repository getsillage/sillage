package auth

import (
	"crypto/tls"
	"net/http"
	"testing"
)

func TestClientIPTrustsForwardedForOnlyWithMarker(t *testing.T) {
	req := &http.Request{
		RemoteAddr: "198.51.100.8:4321",
		Header:     http.Header{"X-Forwarded-For": []string{"203.0.113.9"}},
	}
	if got := clientIP(req); got != "198.51.100.8" {
		t.Fatalf("clientIP(untrusted) = %q, want direct peer", got)
	}
	req.Header.Set(TrustedProxyMarkerHeader, "1")
	if got := clientIP(req); got != "203.0.113.9" {
		t.Fatalf("clientIP(trusted) = %q, want forwarded client", got)
	}
}

func TestSecureCookieRequiresTLSOrTrustedProxy(t *testing.T) {
	req := &http.Request{Header: http.Header{"X-Forwarded-Proto": []string{"https"}}}
	if shouldUseSecureCookie(req) {
		t.Fatal("untrusted X-Forwarded-Proto enabled Secure cookie")
	}
	req.Header.Set(TrustedProxyMarkerHeader, "1")
	if !shouldUseSecureCookie(req) {
		t.Fatal("trusted HTTPS proxy did not enable Secure cookie")
	}
	req.Header.Del(TrustedProxyMarkerHeader)
	req.TLS = &tls.ConnectionState{}
	if !shouldUseSecureCookie(req) {
		t.Fatal("direct TLS did not enable Secure cookie")
	}
}
