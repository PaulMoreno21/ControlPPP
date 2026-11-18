FROM python:3.12-alpine
RUN apk add --no-cache openssl gnupg
COPY setup.py maintenance.py /tools/
ENV PYTHONDONTWRITEBYTECODE=1
