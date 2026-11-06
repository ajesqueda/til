import ipaddress
import random
import shutil
import subprocess
from pathlib import Path

import pytest

NET = Path(__file__).parents[1] / "net"
has_jdk = shutil.which("javac") is not None and shutil.which("java") is not None


@pytest.fixture(scope="module")
def classes(tmp_path_factory):
    if not has_jdk:
        pytest.skip("no JDK on PATH")
    out = tmp_path_factory.mktemp("net-classes")
    sources = [str(NET / "Encapsulation.java"), str(NET / "IpMath.java")]
    subprocess.run(["javac", "-d", str(out), *sources], check=True)
    return out


def java(classes, *args, ea=True):
    cmd = ["java", *(["-ea"] if ea else []), "-cp", str(classes), *args]
    return subprocess.run(cmd, capture_output=True, text=True)


@pytest.mark.parametrize("program, message", [
    ("Encapsulation", "all encapsulation checks passed"),
    ("IpMath", "all IP math checks passed"),
])
def test_self_checks_pass_with_ea(classes, program, message):
    run = java(classes, program)
    assert run.returncode == 0, run.stderr
    assert message in run.stdout


@pytest.mark.parametrize("program", ["Encapsulation", "IpMath"])
def test_refuses_to_run_without_ea(classes, program):
    run = java(classes, program, ea=False)
    assert run.returncode != 0
    assert "java -ea" in run.stderr


def test_overhead_table(classes):
    rows = {
        int(line.split()[0]): line.split()[1:]
        for line in java(classes, "Encapsulation").stdout.splitlines()
        if line.strip()[:1].isdigit()
    }
    assert rows[42] == ["100", "58.00%"]
    assert rows[1460] == ["1518", "3.82%"]
    for payload, (frame, _) in rows.items():
        assert int(frame) == payload + 58


def test_ip_conversions_match_python_ipaddress(classes):
    rnd = random.Random(651)
    ips = ["0.0.0.0", "255.255.255.255", "127.0.0.1"] + [
        str(ipaddress.IPv4Address(rnd.getrandbits(32))) for _ in range(300)
    ]
    to_long = java(classes, "IpMath", "toLong", *ips)
    assert to_long.returncode == 0, to_long.stderr
    numbers = to_long.stdout.split()
    assert [int(n) for n in numbers] == [int(ipaddress.IPv4Address(ip)) for ip in ips]

    back = java(classes, "IpMath", "toIp", *numbers)
    assert back.stdout.split() == ips


def test_private_ranges_match_rfc1918(classes):
    blocks = [ipaddress.ip_network(n) for n in ("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")]
    ips = ["172.15.255.255", "172.16.0.0", "172.31.255.255", "172.32.0.0",
           "192.167.255.255", "192.168.0.1", "9.255.255.255", "10.0.0.0", "11.0.0.0"]
    run = java(classes, "IpMath", "private", *ips)
    expected = [str(any(ipaddress.ip_address(ip) in b for b in blocks)).lower() for ip in ips]
    assert run.stdout.split() == expected


@pytest.mark.parametrize(
    "bad", ["256.0.0.1", "1.2.3", "1.2.3.4.5", "1..2.3", "x.1.2.3", "1.2.3.4."]
)
def test_malformed_addresses_are_rejected(classes, bad):
    run = java(classes, "IpMath", "toLong", bad)
    assert run.returncode != 0
    assert "IllegalArgumentException" in run.stderr
