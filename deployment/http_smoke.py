#!/usr/bin/env python3
"""Check gateway routing with synthetic accounts. Never print auth material."""
import argparse
import json
import secrets
import urllib.error
import urllib.parse
import urllib.request


def smoke(base_url, create_test_data=False, origin_override=None):
    base_url = base_url.rstrip("/")
    origin = urllib.parse.urlsplit(base_url)
    origin = f"{origin.scheme}://{origin.netloc}"
    if origin_override:
        origin = origin_override.rstrip("/")

    def request(method, path, payload=None, token=None, expected=(200,)):
        headers = {"Origin": origin}
        if payload is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = "Bearer " + token
        req = urllib.request.Request(
            base_url + path,
            data=json.dumps(payload).encode() if payload is not None else None,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        if status not in expected:
            raise RuntimeError(f"{method} {path}: HTTP {status}; expected {expected}")
        return json.loads(body) if body and body[:1] in (b"{", b"[") else None

    request("GET", "/workspace/projects", expected=(401, 403))
    request("GET", "/account/internal/v1/users/1", expected=(404,))
    print("PASS: gateway protects project and internal routes")
    if not create_test_data:
        return

    username = "codegen-smoke-" + secrets.token_hex(6) + "@example.invalid"
    password = secrets.token_urlsafe(24)
    account = request("POST", "/account/auth/signup", {
        "username": username, "name": "CodeGen smoke", "password": password
    }, expected=(200, 201))
    token = account["token"]
    login = request("POST", "/account/auth/login", {"username": username, "password": password})
    token = login["token"]
    print("PASS: signup and login through gateway with browser Origin")
    project = request("POST", "/workspace/projects", {"name": "Communication smoke"}, token, (200, 201))
    project_id = project["id"]
    print("PASS: workspace creates project after authenticated account plan lookup")
    try:
        projects = request("GET", "/workspace/projects", token=token)
        assert any(p["id"] == project_id for p in projects), "Created project missing"
        request("GET", f"/workspace/projects/{project_id}", token=token)
        files = request("GET", f"/workspace/projects/{project_id}/files", token=token)
        assert isinstance(files["files"], list), "File tree contract mismatch"
        history = request("GET", f"/intelligence/chat/projects/{project_id}", token=token)
        assert isinstance(history, list), "Chat history contract mismatch"
        request("GET", "/account/internal/v1/users/1", token=token, expected=(404,))
        print("PASS: project/file/history routes and authenticated internal-route rejection")
    finally:
        request("DELETE", f"/workspace/projects/{project_id}", token=token, expected=(200, 204))
        print("PASS: smoke project removed (synthetic account retained)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base_url", help="Frontend or gateway URL, including scheme")
    parser.add_argument("--create-test-data", action="store_true", help="Create a synthetic account/project; delete the project afterward")
    parser.add_argument("--origin", help="Public browser origin when testing through a private port-forward")
    args = parser.parse_args()
    try:
        smoke(args.base_url, args.create_test_data, args.origin)
    except Exception as error:
        # Error types/statuses help diagnosis without dumping response bodies,
        # user credentials, or bearer headers.
        if isinstance(error, (RuntimeError, AssertionError)):
            print("FAIL:", error)
        else:
            print("FAIL:", type(error).__name__)
        raise SystemExit(1)
