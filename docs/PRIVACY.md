# Privacy Notes

JavaTavern is local-first by default.

## Stored on the device

- characters and world-book entries;
- conversations and Agent audit records;
- compressed message images;
- model endpoint and model name;
- an encrypted API key value.

App data is stored in Android private application storage. Android cloud backup and device-to-device transfer are disabled.

## Sent over the network

When a remote model is configured, the current character prompt, activated world-book entries, recent conversation context, and selected images are sent to the configured HTTPS endpoint. JavaTavern does not proxy these requests through its own server.

The configured provider may retain or process requests according to its own policy. Users should review that policy before adding an API key.

## Not collected by this repository

The open-source app currently contains no analytics SDK, advertising SDK, account system, or JavaTavern-operated backend.

## Deletion

The `/clear` command deletes messages and private image copies for the current character after explicit confirmation. Minimal action audit records remain so the app can explain that a destructive action occurred.
