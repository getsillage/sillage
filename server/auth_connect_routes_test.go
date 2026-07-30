package server_test

import (
	"context"
	"net/http/httptest"
	"testing"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
	"github.com/getsillage/sillage/proto/gen/api/v1/apiv1connect"
)

func TestConnectAuthServiceChangePassword(t *testing.T) {
	srv := newTestServer(t)
	oldToken := initializeAndToken(t, srv)
	httpServer := httptest.NewServer(srv)
	t.Cleanup(httpServer.Close)

	client := apiv1connect.NewAuthServiceClient(httpServer.Client(), httpServer.URL)
	req := connect.NewRequest(&apiv1.ChangePasswordRequest{
		CurrentPassword: "passw0rd!",
		NewPassword:     "new-passw0rd!",
	})
	req.Header().Set("Authorization", "Bearer "+oldToken)
	res, err := client.ChangePassword(context.Background(), req)
	if err != nil {
		t.Fatalf("ChangePassword() error = %v", err)
	}
	if res.Msg.GetAccessToken() == "" {
		t.Fatal("ChangePassword() returned no access token")
	}
	if res.Header().Get("Set-Cookie") == "" {
		t.Fatal("ChangePassword() returned no rotated refresh cookie")
	}

	me := connect.NewRequest(&apiv1.MeRequest{})
	me.Header().Set("Authorization", "Bearer "+res.Msg.GetAccessToken())
	if _, err := client.Me(context.Background(), me); err != nil {
		t.Fatalf("Me() after ChangePassword() error = %v", err)
	}

	oldMe := connect.NewRequest(&apiv1.MeRequest{})
	oldMe.Header().Set("Authorization", "Bearer "+oldToken)
	if _, err := client.Me(context.Background(), oldMe); err != nil {
		t.Fatalf("Me() with old access token error = %v", err)
	}
}
