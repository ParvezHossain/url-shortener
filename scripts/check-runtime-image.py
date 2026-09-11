#!/usr/bin/env python3
"""Check that a built application image packages only production frontend assets."""
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def check_image(image):
    """Inspect an image without starting the application or connecting to a database."""
    subprocess.run([
        "docker", "run", "--rm", "--entrypoint", "sh", image, "-ec",
        "if command -v node || command -v npm; then exit 1; fi; "
        "test ! -d /frontend; test ! -d /workspace; test ! -d /app/node_modules",
    ], check=True)
    container = subprocess.check_output(["docker", "create", image], text=True).strip()
    try:
        with tempfile.TemporaryDirectory(prefix="urlshortener-image-") as directory:
            jar = Path(directory) / "app.jar"
            subprocess.run(["docker", "cp", f"{container}:/app/app.jar", str(jar)], check=True)
            with zipfile.ZipFile(jar) as archive:
                names = archive.namelist()
                assets = [name for name in names if name.startswith("BOOT-INF/classes/static/")]
                assert "BOOT-INF/classes/static/index.html" in assets, "Missing frontend entry point"
                assert any(name.endswith(".js") for name in assets), "Missing JavaScript assets"
                assert any(name.endswith(".css") for name in assets), "Missing stylesheet assets"
                for name in names:
                    assert "/node_modules/" not in name, f"Development dependency in jar: {name}"
                for name in assets:
                    assert not name.endswith((".map", ".ts", ".tsx", ".jsx")), f"Source asset: {name}"
                    assert "/src/" not in name, f"Frontend source tree: {name}"
                    if name.endswith((".js", ".css", ".html")):
                        text = archive.read(name).decode("utf-8")
                        assert "sourceMappingURL=" not in text, f"Source map reference: {name}"
                        assert "/@vite/client" not in text, f"Development server reference: {name}"
            print(f"Runtime image passed: {len(assets)} production frontend entries, no Node or source maps")
    finally:
        subprocess.run(["docker", "rm", "-v", container], check=True, stdout=subprocess.DEVNULL)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 scripts/check-runtime-image.py IMAGE")
    check_image(sys.argv[1])
