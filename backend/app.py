import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

app = FastAPI(title="JARVIS AI Backend", version="0.4.0")

MODEL = os.getenv("GEMINI_MODEL", "gemini-3.7-flash")
SYSTEM_PROMPT = os.getenv(
    "JARVIS_SYSTEM_PROMPT",
    "You are JARVIS, a highly capable Android voice assistant. "
    "Answer in the user's language (Hindi/Hinglish if they use Hindi/Hinglish). "
    "Be concise when speaking, but give the useful answer directly. "
    "You have access to Google Search. Use it whenever the question needs current, live, niche, factual, price, news, sports, product, person, place, or otherwise up-to-date information. "
    "Never say information is unavailable merely because it is current; search the web first. "
    "For stock/index prices, search for the latest available Indian market price and clearly say if the market is closed or the quote is delayed. "
    "For requests such as play/lagao/chalao a cartoon, song, video, or topic, understand the user's intent and give a useful action-oriented answer. "
    "Do not claim to have physically changed something on the phone unless the Android app actually performed that action.",
)


class ChatRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=12000)
    memories: dict[str, str] = Field(default_factory=dict)


class ChatResponse(BaseModel):
    reply: str


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL, "gemini_configured": bool(os.getenv("GEMINI_API_KEY")), "web_search": True}


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
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,
                tools=[types.Tool(google_search=types.GoogleSearch())],
            ),
        )
        reply = (response.text or "").strip()
        if not reply:
            raise HTTPException(status_code=502, detail="Gemini returned an empty response")
        return ChatResponse(reply=reply)
    except HTTPException:
        raise
    except Exception as exc:
        print(f"Gemini request failed: {type(exc).__name__}")
        raise HTTPException(status_code=502, detail="Gemini request failed") from exc
