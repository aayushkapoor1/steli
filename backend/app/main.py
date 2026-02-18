from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import auth, users, spots, rankings

app = FastAPI(
    title="Steli API",
    description="Backend for Steli - share and rank study spots around campus",
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api/auth", tags=["auth"])
app.include_router(users.router, prefix="/api/users", tags=["users"])
app.include_router(spots.router, prefix="/api/spots", tags=["study-spots"])
app.include_router(rankings.router, prefix="/api/rankings", tags=["rankings"])


@app.get("/health")
def health():
    return {"status": "ok"}
