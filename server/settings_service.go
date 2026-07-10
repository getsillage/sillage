package server

import (
	"context"

	"connectrpc.com/connect"

	apiv1 "github.com/getsillage/sillage/proto/gen/api/v1"
)

type settingsService struct {
	server *Server
}

func (s *settingsService) GetAISettings(ctx context.Context, req *connect.Request[apiv1.GetAISettingsRequest]) (*connect.Response[apiv1.AISettingsResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	settings, err := s.server.getAISettings(ctx, account.ID)
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(aiSettingsResponsePB(settings)), nil
}

func (s *settingsService) PatchAISettings(ctx context.Context, req *connect.Request[apiv1.PatchAISettingsRequest]) (*connect.Response[apiv1.AISettingsResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	input := aiSettingsInput{
		Profiles:    make([]aiProfileInput, 0, len(req.Msg.GetProfiles())),
		AutoSummary: req.Msg.AutoSummary,
	}
	for _, profile := range req.Msg.GetProfiles() {
		if profile == nil {
			continue
		}
		input.Profiles = append(input.Profiles, aiProfileInput{
			ID:       profile.GetId(),
			Name:     profile.GetName(),
			Provider: profile.GetProvider(),
			BaseURL:  profile.GetBaseUrl(),
			Model:    profile.GetModel(),
			// proto3 scalars can't distinguish 0 from unset; keep the prior
			// Connect behaviour where 0 falls back to the server default.
			Temperature: connectOptionalFloat(profile.GetTemperature()),
			MaxTokens:   connectOptionalInt(profile.GetMaxTokens()),
			Enabled:     profile.GetEnabled(),
			Active:      profile.GetActive(),
			AutoSummary: profile.GetAutoSummary(),
			APIKey:      profile.ApiKey,
		})
	}
	settings, err := s.server.patchAISettings(ctx, account.ID, input)
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(aiSettingsResponsePB(settings)), nil
}

func (s *settingsService) SetAIAutoSummary(ctx context.Context, req *connect.Request[apiv1.SetAIAutoSummaryRequest]) (*connect.Response[apiv1.SetAIAutoSummaryResponse], error) {
	account, err := s.server.accountFromConnect(ctx, req.Header())
	if err != nil {
		return nil, err
	}
	autoSummary, err := s.server.setAIAutoSummary(ctx, account.ID, req.Msg.GetAutoSummary())
	if err != nil {
		return nil, connectError(err)
	}
	return connect.NewResponse(&apiv1.SetAIAutoSummaryResponse{AutoSummary: autoSummary}), nil
}

func connectOptionalFloat(value float64) *float64 {
	if value == 0 {
		return nil
	}
	return &value
}

func connectOptionalInt(value int64) *int64 {
	if value == 0 {
		return nil
	}
	return &value
}
