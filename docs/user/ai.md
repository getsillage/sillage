# AI Usage and Privacy

Sillage's AI features are optional. Sillage does not provide a built-in model. After you configure an AI profile, the server or Android client calls the configured Anthropic-compatible or OpenAI-compatible endpoint directly. Once a request reaches the provider, that provider's policies govern logging, retention, and training.

## Configuration

An AI profile contains an API protocol, endpoint URL, model, API key, temperature, and maximum output token count. You can save multiple profiles, but only one default profile is used for summaries and Ask at a time. The protocol setting selects the request format; it is not a list of service providers.

- The Anthropic-compatible protocol uses `https://api.anthropic.com/v1` as its default endpoint.
- The OpenAI-compatible protocol uses `https://api.openai.com/v1` as its default endpoint.
- A custom endpoint receives the content described below. Sillage currently does not restrict the target host, protocol, or private network address. Only enter an endpoint you trust and that uses HTTPS; a plaintext endpoint on a local network is appropriate only in a controlled environment.
- Fetch Models (`获取模型`) sends the API key to request the configured endpoint's model list, but does not send any records.
- Test Connection (`测试连接`) sends a fixed, short test prompt, but does not send any records.

## Data Sent

| Operation | Content sent to the provider |
| --- | --- |
| Generate a summary | System prompt, the full Markdown content of the selected record, and model parameters |
| Route an Ask question | Routing system prompt, the current question, conversation history from the current branch, and model parameters; Sillage does not attach record bodies, summaries, or excerpts |
| Answer an Ask question | Answer system prompt, the current question, conversation history from the current branch, model parameters, and, only for a record or mixed question, relevant record excerpts or existing summaries selected from the chosen time range |
| Fetch Models | API key and a model-list request; no records |
| Test Connection | API key, model parameters, and a fixed test prompt; no records |

Each Ask turn first uses the same configured provider to classify the question as `general`, `records`, or `mixed` and produce retrieval phrases. This routing request receives the system prompt, current question, and current branch history, but Sillage does not add record bodies, stored summaries, or excerpts. Conversation history may itself contain sensitive text from earlier turns, so the routing request is still private data sent to the provider. If the routing response cannot be parsed, Sillage safely falls back to `records` instead of treating the question as general.

For `general`, Sillage makes the answer request without looking up or attaching records. For `records` and `mixed`, it uses the generated phrases to search locally within the selected time range before making the answer request. Server-side Ask currently sends at most 5 relevant sources; in record mode, it sends a raw excerpt around the matching content from each selected record, while summary mode sends the stored summary associated with each selected record. Android offline Ask selects at most 8 relevant source records and sends raw record excerpts. Selecting All Records (`全部记录`) only expands the local candidate range; it does not send every record in the database. If no relevant record is selected for a record-dependent question, the answer request still contains the question and branch history but no record excerpt. Attachment bytes are not sent as summary or Ask content, but file names, links, or descriptions in the Markdown body may be sent with that body.

The prompt requires claims about the user's records or personal history to use the supplied sources. Greetings and general-knowledge questions may be answered from the model's general knowledge without a source citation, and mixed questions should separate record-backed observations from general guidance. If a personal question cannot be answered from the supplied sources, the answer should say that the records do not provide enough information. Only valid source citations actually used in the answer are retained and displayed; a general answer has no source references. The model may still omit information or produce incorrect content, so verify important personal conclusions against the source records. Sillage does not treat AI output as a diagnosis or proof of fact.

## Automatic Summaries

When Automatically Summarize New Records (`新建记录后自动总结`) is enabled, the server sends a new record's content asynchronously after the record has been saved successfully. A generation failure does not roll back the record and is not retried indefinitely. Disabling the setting only prevents future automatic calls; it does not delete existing summaries or data already received by the provider.

Generating a summary manually makes one external request. An Ask turn normally makes two external requests to the same configured provider: one to route the question and generate retrieval phrases, and one to produce the answer. Both requests include the current question and current branch history. A general answer includes no attached record content in either request; record and mixed answers attach relevant source content only to the second request. Sillage does not call a provider when no usable default profile or API key is available. In Android offline mode, the device makes both requests directly instead of routing them through the Sillage server.

## Secrets and Local Data

- The server encrypts AI API keys with a key derived from `ENCRYPTION_SECRET`. The API reports only whether a key is configured and never returns the plaintext value.
- If `ENCRYPTION_SECRET` is lost or changed, existing API keys may no longer be decryptable and must be saved again. See [Data, Backup, and Recovery](data.md) for backup requirements.
- Android offline profiles and local data are protected with Android Keystore. Exported JSON removes API keys, but records, summaries, and Ask content remain plaintext sensitive data.
- These protections do not provide encryption at rest for the entire database, attachment directory, or backups.

When you stop using a provider, disable automatic summaries and delete the corresponding profile to prevent future calls. Deletion clears the encrypted API key envelope from the current server database, but historical backups may still contain the envelope and AI-derived data may remain. This is not permanent deletion from every historical copy. Data that has already been sent remains subject to the provider's policies.
