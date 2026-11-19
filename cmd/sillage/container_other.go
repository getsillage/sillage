//go:build !linux

package main

import "github.com/spf13/cobra"

func registerContainerCommand(_ *cobra.Command) {}
