import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from google import genai

app = FastAPI(title="JARVIS AI Backend", version="0.3.0")

MODEL = os.getenv("GEMINI_MODEL", "gemini-3.7-flash")
SYSTEM_PROMPT = os.getenv(
    "JARVIS_SYSTEM_PROMPT",
    "You are JARVIS, a helpful Android voice assistant. Be concise, practical, and friendly. "
    "When the user asks for an action, explain the safe Android action they can take if the phone app must confirm it.",
)


class ChatRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=12000)
    memories: dict[str, str] = Field(default_factory=dict)


class ChatResponse(BaseModel):
    reply: str


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL, "gemini_configured": bool(os.getenv("GEMINI_API_KEY"))}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise HTTPException(status_code=503, detail="GEMINI_API_KEY is not configured")

    memory_context = ""
    if request.memories:
        items = [f"{key}: {value}" for key, value in request.memories.items()]
        memory_context = "\nRelevant user memory:\n" + "\n".join(items[:30])

    prompt = f"{request.prompt.strip()}{memory_context}"

    try:
        client = genai.Client(api_key=api_key)
        response = client.models.generate_content(
            model=MODEL,
            contents=prompt,
            config={"system_instruction": SYSTEM_PROMPT},
        )
        reply = (response.text or "").strip()
        if not reply:
            raise HTTPException(status_code=502, detail="Gemini returned an empty response")
        return ChatResponse(reply=reply)
    except HTTPException:
        raise
    except Exception as exc:
        # Keep provider internals out of the Android response.
        print(f"Gemini request failed: {type(exc).__name__}")
        raise HTTPException(status_code=502, detail="Gemini request failed") from exc
