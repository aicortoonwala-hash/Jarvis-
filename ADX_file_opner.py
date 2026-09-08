import os
import subprocess
import sys
import logging
import asyncio
from fuzzywuzzy import process
try:
    import pygetwindow as gw
except ImportError:
    gw = None
from langchain.tools import tool
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
async def focus_window(title_keyword: str) -> bool:
    if not gw: return False
    await asyncio.sleep(1.5)
    title_keyword = title_keyword.lower().strip()
    for window in gw.getAllWindows():
        if title_keyword in window.title.lower():
            try:
                if window.isMinimized: window.restore()
                window.activate()
            except Exception: pass
            return True
    return False
async def index_files(base_dirs):
    file_index=[]
    for base_dir in base_dirs:
        if not os.path.exists(base_dir): continue
        for root,_,files in os.walk(base_dir):
            for filename in files: file_index.append({"name":filename,"path":os.path.join(root,filename),"type":"file"})
    return file_index
async def search_file(query,index):
    choices=[item["name"] for item in index]
    if not choices: return None
    match=process.extractOne(query,choices)
    if not match: return None
    best_match,score=match
    if score>70:
        for item in index:
            if item["name"]==best_match: return item
    return None
async def open_file(item):
    try:
        path=item["path"]
        if os.name=="nt": os.startfile(path)
        else: subprocess.call(["open" if sys.platform=="darwin" else "xdg-open",path])
        await focus_window(item["name"])
        return f"File opened: {item['name']}"
    except Exception as e:
        logger.error("Failed to open file: %s",e); return f"Failed to open file: {e}"
async def handle_command(command,index):
    item=await search_file(command,index)
    return await open_file(item) if item else "File not found."
@tool
async def Play_file(name: str) -> str:
    """Search for and open a file by name from the D: drive."""
    return await handle_command(name.strip(), await index_files(["D:/"]))
