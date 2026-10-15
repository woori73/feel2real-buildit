FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY hwpx_cli.py hwpx_editor.py ./

ENTRYPOINT ["python", "hwpx_cli.py"]
