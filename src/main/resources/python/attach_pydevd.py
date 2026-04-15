from __future__ import annotations

import argparse
import sys
import socket
from pathlib import Path
from typing import Any

def process_command_line() -> dict[str, Any]:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("pid", type=int)
    parser.add_argument("pydevPath", type=Path)
    parser.add_argument("--port", type=int, default=5678)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--protocol", default="")
    parser.add_argument("--mcPort", type=int, required=True)

    args = parser.parse_args()
    setup = {
        "port": args.port,
        "pid": args.pid,
        "host": args.host,
        "protocol": args.protocol,
        "mcPort": args.mcPort,
        "pydevPath": args.pydevPath,
    }

    return setup


def send_command(port, message):
    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        client.connect(("localhost", port))
        client.sendall(('python("' + message.replace("\n", "\\n").replace(r'"', r"\"") + '")').encode())
        data = client.recv(1024)
        if data.strip().strip(b"\x00"):
            print(data)
    finally:
        client.close()


def build_python_code(setup: dict[str, Any]) -> str:
    pythonpath = setup["pydevPath"].as_posix()
    pythonpath2 = (setup["pydevPath"] / "pydevd_attach_to_process").as_posix()

    python_code = f"""import sys
sys.path.append("{pythonpath}")
sys.path.append("{pythonpath2}")
try:
    import pydevd
    pydevd.stoptrace()
    pydevd.connected = False
    pydevd.SetupHolder.setup = None
except Exception:
    pass
import attach_script
attach_script.attach(port={{port}}, host="{{host}}", protocol="{{protocol}}")
"""

    return python_code.format(**setup)


def main():
    setup = process_command_line()
    send_command(setup["mcPort"], build_python_code(setup))


if __name__ == "__main__":
    main()
