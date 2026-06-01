FROM python:3.11-slim
WORKDIR /app
# Flush stdout immediately so the app's log messages show up in `docker logs`.
ENV PYTHONUNBUFFERED=1
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]