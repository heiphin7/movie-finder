from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import Dict
import torch
import httpx
import open_clip
import faiss
from PIL import Image
from io import BytesIO
import numpy as np
import os
import json

app = FastAPI()

# === Модель OpenCLIP ===
device = "cuda" if torch.cuda.is_available() else "cpu"
model, _, preprocess = open_clip.create_model_and_transforms('ViT-B-32', pretrained='laion2b_s34b_b79k')
model = model.to(device)
model.eval()

# === FAISS Index ===
dimension = 512  # Размерность ViT-B-32 эмбеддингов
faiss_index = faiss.IndexFlatL2(dimension)  # Используем простой L2 индекс
metadata_store = []  # Сопоставление индекса -> метадата

INDEX_PATH = "index.faiss"        # Файл с индексом
META_PATH = "metadata.json"       # Файл с метаданными

# Загрузка сохранённых данных
if os.path.exists(INDEX_PATH):
    faiss_index = faiss.read_index(INDEX_PATH)
    print("✅ FAISS индекс загружен")

if os.path.exists(META_PATH):
    with open(META_PATH, "r", encoding="utf-8") as f:
        metadata_store = json.load(f)
    print("✅ Metadata загружена")

# === Генерация эмбеддинга из картинки ===
def extract_embedding(image_bytes: bytes):
    image = Image.open(BytesIO(image_bytes)).convert("RGB")
    image_tensor = preprocess(image).unsqueeze(0).to(device)

    with torch.no_grad():
        image_features = model.encode_image(image_tensor)
        image_features = image_features / image_features.norm(dim=-1, keepdim=True)

    return image_features[0].cpu().numpy()


# === /embed: просто получить эмбеддинг ===
@app.post("/embed")
async def get_embedding(image: UploadFile = File(...)):
    image_bytes = await image.read()
    embedding = extract_embedding(image_bytes)
    return JSONResponse(content={"embedding": embedding.tolist()})


# === /upload: загрузка эмбеддингов с метаданными ===
class UploadPayload(BaseModel):
    data: Dict[str, Dict[str, int]]  # путь -> {название -> таймкод}


@app.post("/upload")
async def upload_vectors(data: Dict[str, Dict[str, int]]):
    for path, meta in data.items():
        try:
            with open(path, "rb") as f:
                image_bytes = f.read()
            embedding = extract_embedding(image_bytes)
            faiss_index.add(np.array([embedding]))
            for title, timecode in meta.items():
                metadata_store.append({"title": title, "timecode": timecode})
        except Exception as e:
            print(f"❌ Ошибка при обработке {path}: {e}")
            continue

        
    # Сохраняем индекс и метаданные после загрузки
    faiss.write_index(faiss_index, INDEX_PATH)

    with open(META_PATH, "w", encoding="utf-8") as f:
        json.dump(metadata_store, f, ensure_ascii=False, indent=2)

    # === 🔁 Уведомляем Java-сервис об удалении файлов ===
    try:
        async with httpx.AsyncClient() as client:
            java_url = "http://localhost:5720/files/delete"  # Измени на нужный host:port
            await client.post(java_url, json=data, timeout=10)
            print("✅ Уведомление об удалении файлов отправлено в Java-сервис")
    except Exception as e:
        print(f"⚠️ Не удалось отправить запрос на удаление: {e}")

    return {"status": "uploaded", "count": len(metadata_store)}



# === /find: поиск по картинке ===
@app.post("/find")
async def find_image(image: UploadFile = File(...)):
    image_bytes = await image.read()
    embedding = extract_embedding(image_bytes)

    if faiss_index.ntotal == 0:
        return JSONResponse(content={"result": None})

    D, I = faiss_index.search(np.array([embedding]), k=1)
    index = I[0][0]
    distance = D[0][0]

    if index < 0 or distance > 0.5:  # Порог
        return JSONResponse(content={"result": None})

    meta = metadata_store[index]
    return JSONResponse(content={"result": meta})
