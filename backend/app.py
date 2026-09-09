import json
import os

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

app = FastAPI(title="JARVIS AI Backend", version="0.6.0")
MODEL = os.getenv("GEMINI_MODEL", "gemini-3.7-flash")

BASE_SYSTEM_PROMPT = os.getenv(
    "JARVIS_SYSTEM_PROMPT",
    "You are JARVIS, an advanced Android voice assistant. "
    "Speak naturally like a calm, intelligent human assistant, not like a chatbot. "
    "Answer in the user's language; use Hindi/Hinglish when the user does. "
    "Be direct and conversational because your answer will be spoken aloud. "
    "You have Google Search. Use it for current, live, niche, price, news, sports, product, person, place, weather, market, or otherwise time-sensitive facts. "
    "Never call current information unavailable before searching. For Indian stocks/indexes, search for the latest available quote and clearly state when the market is closed or a quote is delayed. "
    "Use memory only when relevant. Do not invent personal facts. "
    "The Android device can execute a limited set of safe phone actions. Never claim an action happened unless you return that action for the Android app to execute. "
    "For explicit commands to call or send an SMS, create the corresponding action. For dangerous, irreversible, financial, security, or privacy-sensitive actions, do not invent an execution capability. "
)

ACTION_RULES = """
You are also an action planner. Return ONLY valid JSON, with no markdown and no extra text:
{"reply":"short natural spoken reply","actions":[{"type":"...","target":"...","text":"...","value":"..."}]}

Allowed action types:
- open_app: target is YouTube, WhatsApp, Chrome, Maps, Gmail, Phone, or Camera
- open_url: target is a complete https URL
- search_web: target is the web search query
- search_youtube: target is the YouTube query
- play_youtube: target is the video/song/cartoon/topic query
- maps: target is a place/address
- camera: empty fields
- settings: target is wifi, bluetooth, sound, display, battery, app, or empty
- flashlight: value is on/off
- volume: value is up/down/mute/unmute
- call: target is a contact name or phone number
- sms: target is a contact name or phone number, text is the exact message
- alarm: target is 24-hour HH:MM time, value is optional label
- timer: target is optional spoken label, value is duration in seconds
- remember: target is memory key, text is memory value

Rules:
1. If the user asks a question, give the answer in reply. If an action is also needed, include it in actions.
2. For “Nifty price batao” or similar current market questions, SEARCH first and answer with the latest result; do not output a phone action unless requested.
3. For “Motu Patlu cartoon lagao”, “Arijit Singh ka gaana chalao”, etc., output play_youtube with the cleaned query.
4. For “YouTube par X search karo”, output search_youtube.
5. For “Google par X search karo” or “search X”, output search_web.
6. For “YouTube kholo”, “WhatsApp kholo”, etc., output open_app.
7. For “call papa”, output call target papa. For “papa ko SMS bhejo: ...”, output sms.
8. For explicit flashlight, volume, alarm, timer, maps, camera, or settings requests, output the matching action.
9. Never output an action type outside this allowlist.
10. Do not output duplicate actions. Keep actions minimal.
"""

class ChatRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=12000)
    memories: dict[str, str] = Field(default_factory=dict)

class ChatResponse(BaseModel):
    reply: str

class AgentResponse(BaseModel):
    reply: str
    actions: list[dict[str, str]] = Field(default_factory=list)


def memory_context(memories: dict[str, str]) -> str:
    if not memories:
        return ""
    return "\nRelevant user memory:\n" + "\n".join(f"{k}: {v}" for k, v in memories.items())[:6000]


def gemini(prompt: str, system_instruction: str):
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise HTTPException(status_code=503, detail="GEMINI_API_KEY is not configured")
    try:
        client = genai.Client(api_key=api_key)
        return client.models.generate_content(
            model=MODEL,
            contents=prompt,
            config=types.GenerateContentConfig(
                system_instruction=system_instruction,
                tools=[types.Tool(google_search=types.GoogleSearch())],
            ),
        )
    except HTTPException:
        raise
    except Exception as exc:
        print(f"Gemini request failed: {type(exc).__name__}")
        raise HTTPException(status_code=502, detail="Gemini request failed") from exc


def clean_json(text: str) -> dict:
    raw = (text or "").strip()
    if raw.startswith("```"):
        raw = raw.strip("`")
        if raw.startswith("json"):
            raw = raw[4:].strip()
    start, end = raw.find("{"), raw.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("No JSON object")
    return json.loads(raw[start:end + 1])


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL, "gemini_configured": bool(os.getenv("GEMINI_API_KEY")), "web_search": True, "agent": True}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    response = gemini(request.prompt.strip() + memory_context(request.memories), BASE_SYSTEM_PROMPT)
    reply = (response.text or "").strip()
    if not reply:
        raise HTTPException(status_code=502, detail="Gemini returned an empty response")
    return ChatResponse(reply=reply)


@app.post("/agent", response_model=AgentResponse)
def agent(request: ChatRequest):
    prompt = request.prompt.strip() + memory_context(request.memories)
    response = gemini(prompt, BASE_SYSTEM_PROMPT + "\n" + ACTION_RULES)
    try:
        data = clean_json(response.text or "")
    except Exception:
        fallback = gemini(prompt, BASE_SYSTEM_PROMPT)
        return AgentResponse(reply=(fallback.text or "I couldn't complete that.").strip(), actions=[])

    reply = str(data.get("reply", "")).strip() or "Done."
    raw_actions = data.get("actions", [])
    allowed = {"open_app", "open_url", "search_web", "search_youtube", "play_youtube", "maps", "camera", "settings", "flashlight", "volume", "call", "sms", "alarm", "timer", "remember"}
    actions = []
    if isinstance(raw_actions, list):
        for item in raw_actions:
            if not isinstance(item, dict):
                continue
            typ = str(item.get("type", "")).strip().lower()
            if typ not in allowed:
                continue
            actions.append({
                "type": typ,
                "target": str(item.get("target", "")).strip(),
                "text": str(item.get("text", "")).strip(),
                "value": str(item.get("value", "")).strip(),
            })
    return AgentResponse(reply=reply, actions=actions[:4])
