# Shell-free container runtime

## Context

The old Alpine runtime depended on BusyBox for volume preparation, identity
switching, and the Compose probe. BusyBox remained in the final image despite
replacing `/bin/sh`, and its stable package did not resolve CVE-2025-60876. A
general-purpose Debian runtime removed BusyBox but did not pass the high-severity
image gate in the trial.

## Decision

Use a digest-pinned Chainguard static image for the final stage. Keep Alpine in
the existing build stages only. A Linux Go entrypoint reads existing `_FILE`
settings, prepares the data volume, drops to the configured numeric UID/GID, and
replaces itself with the server. The image runs the binary's local `/readyz`
check without a shell or proxy. Inspect notices using `docker cp` and scan the
final image without the old Alpine-specific VEX file.

## Consequences

The volume mount, ports, HTTP readiness response, default numeric ownership,
and backup format are unchanged. A customized shell or `wget` probe must be
removed or replaced with the documented binary health check. Maintainers must
rebuild and scan the pinned runtime on both supported architectures. The
data directory remains operator-controlled; startup does not follow symbolic
links or reassign multiply linked files to another owner.
