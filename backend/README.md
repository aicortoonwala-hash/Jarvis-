# JARVIS AI Backend

Minimal server contract for the Android app.

## API

- `GET /health` → `{"status":"ok"}`
- `POST /chat` with `{ "prompt": "...", "memories": { ... } }`
- Response: `{ "reply": "..." }`

Keep `GEMINI_API_KEY` only in the server environment. Never put it in the Android APK or commit it to Git.

The Android app currently points at `http://10.0.2.2:8080/chat` for local emulator development. A deployed HTTPS endpoint should be used for a real phone.
