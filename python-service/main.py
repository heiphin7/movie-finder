from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
import torch
import open_clip
from PIL import Image
from io import BytesIO

app = FastAPI()

# Загружаем модель OpenCLIP
device = "cuda" if torch.cuda.is_available() else "cpu"
model, _, preprocess = open_clip.create_model_and_transforms('ViT-B-32', pretrained='laion2b_s34b_b79k')
model = model.to(device)
model.eval()

@app.post("/embed")
async def get_embedding(image: UploadFile = File(...)):
    image_bytes = await image.read()
    image = Image.open(BytesIO(image_bytes)).convert("RGB")
    image_tensor = preprocess(image).unsqueeze(0).to(device)

    with torch.no_grad():
        image_features = model.encode_image(image_tensor)
        image_features = image_features / image_features.norm(dim=-1, keepdim=True)
    
    embedding = image_features[0].cpu().tolist()
    return JSONResponse(content={"embedding": embedding})
