
#Presigned URL에서 내려받아 → HEIC면 JPEG로 → 긴 변 1568px 이하 → JPEG 품질 90.

import io
import logging
from concurrent.futures import ThreadPoolExecutor

import requests
from django.conf import settings
from PIL import Image, ImageOps, UnidentifiedImageError
from pillow_heif import register_heif_opener

from .errors import image_fetch_failed

register_heif_opener()  # Pillow가 HEIC를 열 수 있게 함
log = logging.getLogger(__name__)


def to_jpeg(raw: bytes) -> bytes:
    """사진 바이트를 모델에 넣을 JPEG로 바꾼다."""
    try:
        img = Image.open(io.BytesIO(raw))
        img = ImageOps.exif_transpose(img)  # 휴대폰 사진 회전 방향 바로잡기
    except (UnidentifiedImageError, OSError) as exc:
        raise image_fetch_failed("이미지 파일이 아니거나 읽을 수 없는 형식이에요.") from exc

    if img.mode != "RGB":
        background = Image.new("RGB", img.size, "white")
        rgba = img.convert("RGBA")
        background.paste(rgba, mask=rgba.split()[-1])
        img = background

    side = settings.AI_IMAGE_MAX_SIDE
    img.thumbnail((side, side), Image.LANCZOS)
    out = io.BytesIO()
    img.save(out, format="JPEG", quality=90)
    return out.getvalue()


def _download(url: str) -> bytes:
    if not url.startswith(("https://", "http://")):
        raise image_fetch_failed("사진 주소 형식이 올바르지 않아요.")
    limit = settings.AI_IMAGE_MAX_BYTES
    try:
        with requests.get(url, stream=True, timeout=(3, settings.AI_IMAGE_TIMEOUT_SECONDS)) as resp:
            if resp.status_code != 200:
                raise image_fetch_failed(f"사진을 내려받지 못했어요 (HTTP {resp.status_code}). URL이 만료됐을 수 있어요.")
            data = bytearray()
            for chunk in resp.iter_content(64 * 1024):
                data.extend(chunk)
                if len(data) > limit:
                    raise image_fetch_failed("사진 한 장이 10MB를 넘어요.")
            return bytes(data)
    except requests.RequestException as exc:
        log.warning("image download failed: %s", type(exc).__name__)  # URL은 로그에 남기지 않음
        raise image_fetch_failed() from exc


def fetch_images(urls: list[str]) -> list[bytes]:
    """여러 장을 동시에 내려받아 JPEG로 바꾼다. 순서는 그대로 유지."""
    with ThreadPoolExecutor(max_workers=min(5, len(urls) or 1)) as pool:
        raws = list(pool.map(_download, urls))
    return [to_jpeg(raw) for raw in raws]
