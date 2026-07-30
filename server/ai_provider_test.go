package server

import "testing"

func TestNormalizeAIBaseURL(t *testing.T) {
	for _, tt := range []struct {
		name     string
		raw      string
		provider string
		want     string
		wantErr  bool
	}{
		{name: "default openai", provider: "openai", want: "https://api.openai.com/v1"},
		{name: "normalizes", raw: "HTTPS://example.com/v1/?secret=1#fragment", want: "https://example.com/v1"},
		{name: "rejects file", raw: "file:///etc/passwd", wantErr: true},
		{name: "rejects relative", raw: "/v1", wantErr: true},
		{name: "rejects credentials", raw: "https://user:pass@example.com/v1", wantErr: true},
	} {
		t.Run(tt.name, func(t *testing.T) {
			got, err := normalizeAIBaseURL(tt.raw, tt.provider)
			if (err != nil) != tt.wantErr {
				t.Fatalf("normalizeAIBaseURL() error = %v, wantErr %v", err, tt.wantErr)
			}
			if err == nil && got != tt.want {
				t.Fatalf("normalizeAIBaseURL() = %q, want %q", got, tt.want)
			}
		})
	}
}
