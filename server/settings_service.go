package server

import (
	"context"

	"connectrpc.com/connect"

	apiv1 "github.com/miofelix/sillage/proto/gen/api/v1"
)

type settingsService struct {
	server *Server
}

func (s *settingsService) GetAISettings(ctx context.Context, req *connect.Request[apiv1.GetAISettingsRequest]) (*connect.Response[apiv1.AISettingsResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	profiles, err := s.server.getAISettings(ctx, account.ID)
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(aiSettingsResponsePB(profiles)), nil
}

func (s *settingsService) PatchAISettings(ctx context.Context, req *connect.Request[apiv1.PatchAISettingsRequest]) (*connect.Response[apiv1.AISettingsResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	input := aiSettingsInput{Profiles: make([]aiProfileInput, 0, len(req.Msg.GetProfiles()))}
	for _, profile := range req.Msg.GetProfiles() {
		if profile == nil {
			continue
		}
		input.Profiles = append(input.Profiles, aiProfileInput{
			ID:          profile.GetId(),
			Name:        profile.GetName(),
			Provider:    profile.GetProvider(),
			BaseURL:     profile.GetBaseUrl(),
			Model:       profile.GetModel(),
			Temperature: profile.GetTemperature(),
			MaxTokens:   profile.GetMaxTokens(),
			Enabled:     profile.GetEnabled(),
			Active:      profile.GetActive(),
			AutoSummary: profile.GetAutoSummary(),
			APIKey:      profile.ApiKey,
		})
	}
	profiles, err := s.server.patchAISettings(ctx, account.ID, input)
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(aiSettingsResponsePB(profiles)), nil
}
