# AI Usage and Privacy

Sillage's AI features are optional. Sillage does not provide a built-in model. After you configure an AI profile, the server or Android client calls the selected Anthropic or OpenAI-compatible endpoint directly. Once a request reaches the provider, that provider's policies govern logging, retention, and training.

## Configuration

An AI profile contains a provider, endpoint URL, model, API key, temperature, and maximum output token count. You can save multiple profiles, but only one default profile is used for summaries and Ask at a time.

- Anthropic uses `https://api.anthropic.com/v1` as its default endpoint.
- Other providers use `https://api.openai.com/v1` by default, or you can enter a compatible endpoint.
- A custom endpoint receives the content described below. Sillage currently does not restrict the target host, protocol, or private network address. Only enter an endpoint you trust and that uses HTTPS; a plaintext endpoint on a local network is appropriate only in a controlled environment.
- Fetch Models (`获取模型`) sends the API key to request the provider's model list, but does not send any records.
- Test Connection (`测试连接`) sends a fixed, short test prompt, but does not send any records.

## Data Sent

| Operation | Content sent to the provider |
| --- | --- |
| Generate a summary | System prompt, the full Markdown content of the selected record, and model parameters |
| Ask about records | System prompt, the current question, conversation history from the current branch, and record excerpts or existing summaries selected from the chosen time range |
| Fetch Models | API key and a model-list request; no records |
| Test Connection | API key, model parameters, and a fixed test prompt; no records |

Server-side Ask currently selects at most 5 source records. In record mode, it sends a raw excerpt from each selected record; in summary mode, it sends the stored summary associated with each selected record. Android offline Ask selects at most 8 source records and sends raw record excerpts. Selecting All Records (`全部记录`) only expands the candidate range; it does not send every record in the database. Attachment bytes are not sent as summary or Ask content, but file names, links, or descriptions in the Markdown body may be sent with that body.

The prompt instructs the model to answer only from the supplied sources, but the model may still omit information or produce incorrect content. Verify important conclusions against the source records. Sillage does not treat AI output as a diagnosis or proof of fact.

## Automatic Summaries

When Automatically Summarize New Records (`新建记录后自动总结`) is enabled, the server sends a new record's content asynchronously after the record has been saved successfully. A generation failure does not roll back the record and is not retried indefinitely. Disabling the setting only prevents future automatic calls; it does not delete existing summaries or data already received by the provider.

Generating a summary manually makes one external request. Ask calls the provider only after it finds citable sources within the current scope. Sillage does not call a provider when no usable default profile or API key is available. In Android offline mode, the device calls the provider directly for summaries and Ask instead of routing requests through the Sillage server.

## Secrets and Local Data

- The server encrypts AI API keys with a key derived from `ENCRYPTION_SECRET`. The API reports only whether a key is configured and never returns the plaintext value.
- If `ENCRYPTION_SECRET` is lost or changed, existing API keys may no longer be decryptable and must be saved again. See [Data, Backup, and Recovery](data.md) for backup requirements.
- Android offline profiles and local data are protected with Android Keystore. Exported JSON removes API keys, but records, summaries, and Ask content remain plaintext sensitive data.
- These protections do not provide encryption at rest for the entire database, attachment directory, or backups.

When you stop using a provider, disable automatic summaries and delete the corresponding profile to prevent future calls. Deletion clears the encrypted API key envelope from the current server database, but historical backups may still contain the envelope and AI-derived data may remain. This is not permanent deletion from every historical copy. Data that has already been sent remains subject to the provider's policies.
