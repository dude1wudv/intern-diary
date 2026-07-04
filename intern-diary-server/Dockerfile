FROM node:24-alpine
WORKDIR /app
RUN apk add --no-cache python3 py3-pip \
  && npm install -g @openai/codex
COPY pyproject.toml ./
COPY app ./app
RUN python3 -m pip install --break-system-packages --no-cache-dir .
EXPOSE 8088
CMD ["python3", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8088"]
