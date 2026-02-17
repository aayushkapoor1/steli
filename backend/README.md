# Steli Backend (Python)

FastAPI backend for the Steli study spots app.

## Setup

```bash
cd backend
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## Run

```bash
uvicorn app.main:app --reload
```

API docs: http://127.0.0.1:8000/docs
