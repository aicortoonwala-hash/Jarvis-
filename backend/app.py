import os
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="JARVIS AI Backend")

class ChatRequest(BaseModel):
    prompt: str
    memories: dict[str, str] = {}

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/chat")
def chat(request: ChatRequest):
    if not os.getenv("GEMINI_API_KEY"):
        return {"reply": "JARVIS backend is online, but GEMINI_API_KEY is not configured."}
    # Provider integration is intentionally kept behind this endpoint.
    # Add the current Google GenAI SDK call here on the server; never expose the key to Android.
    return {"reply": "JARVIS AI backend is connected. Gemini provider integration is ready to be enabled."}
