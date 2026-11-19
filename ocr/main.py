import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from recognition import UnreadableDocument, create_engine, recognize_document
from registration import extract_front, extract_back
from stay_document import extract_stay_document


MAX_FILE_SIZE = 5 * 1024 * 1024


@asynccontextmanager
async def lifespan(app):
    app.state.engine = create_engine()
    app.state.lock = asyncio.Lock()
    yield
    del app.state.engine


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


def failure(status):
    return JSONResponse({"code": "OCR_FAILED"}, status_code=status, headers={"Cache-Control": "no-store"})


@app.get("/health")
async def health():
    return {"status": "UP"}


@app.post("/recognize")
async def recognize(request: Request):
    return await recognize_request(request, recognize_document)


@app.post("/registration/front")
async def registration_front(request: Request):
    return await recognize_request(request, lambda engine, data, kind: recognize_document(engine, data, kind, extract_front), True)


@app.post("/registration/back")
async def registration_back(request: Request):
    return await recognize_request(request, lambda engine, data, kind: recognize_document(engine, data, kind, extract_back), True)


@app.post("/registration/stay-document")
async def registration_stay_document(request: Request):
    return await recognize_request(request, lambda engine, data, kind: recognize_document(
        engine, data, kind, extract_stay_document, max_pages=3), pdf_only=True)


async def recognize_request(request: Request, recognizer, image_only=False, pdf_only=False):
    content_type = request.headers.get("content-type", "").split(";")[0]
    allowed = {"image/png", "image/jpeg"} if image_only else {"image/png", "image/jpeg", "application/pdf"}
    if pdf_only:
        allowed = {"application/pdf"}
    if content_type not in allowed:
        return failure(415)
    try:
        await asyncio.wait_for(request.app.state.lock.acquire(), timeout=1)
    except TimeoutError:
        return failure(503)
    data = bytearray()
    try:
        async with asyncio.timeout(10):
            async for chunk in request.stream():
                if len(data) + len(chunk) > MAX_FILE_SIZE:
                    return failure(413)
                data.extend(chunk)
        if not data:
            return failure(422)
        result = await run_in_threadpool(recognizer, request.app.state.engine, bytes(data), content_type)
        return JSONResponse(result, headers={"Cache-Control": "no-store"})
    except UnreadableDocument:
        return failure(422)
    except Exception:
        return failure(503)
    finally:
        data[:] = b"\0" * len(data)
        request.app.state.lock.release()
