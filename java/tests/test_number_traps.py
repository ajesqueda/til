import shutil
import subprocess
from pathlib import Path

import pytest
from traps import number_traps as nt

JAVA_SRC = Path(__file__).parents[1] / "traps" / "NumberTraps.java"
has_jdk = shutil.which("javac") is not None and shutil.which("java") is not None


@pytest.fixture(scope="module")
def classes(tmp_path_factory):
    if not has_jdk:
        pytest.skip("no JDK on PATH")
    out = tmp_path_factory.mktemp("classes")
    subprocess.run(["javac", "-d", str(out), str(JAVA_SRC)], check=True)
    return out


def test_java_asserts_pass_with_ea(classes):
    run = subprocess.run(
        ["java", "-ea", "-cp", str(classes), "NumberTraps"], capture_output=True, text=True
    )
    assert run.returncode == 0, run.stderr
    assert "all number traps confirmed" in run.stdout


def test_java_refuses_to_run_without_ea(classes):
    run = subprocess.run(
        ["java", "-cp", str(classes), "NumberTraps"], capture_output=True, text=True
    )
    assert run.returncode != 0
    assert "java -ea" in run.stderr


def test_to_int32_matches_java_wraparound():
    assert nt.to_int32(nt.day_in_microseconds()) == 500_654_080
    assert nt.to_int32(3_000_000_000) == -1_294_967_296
    assert nt.to_int32(2**31 - 1) == 2**31 - 1
    assert nt.to_int32(2**31) == -(2**31)
    assert nt.to_int32(-1) == -1


def test_python_ints_do_not_overflow():
    assert nt.day_in_microseconds() == 86_400_000_000


@pytest.mark.parametrize(
    "ms, expected",
    [(99, "fast"), (100, "ok"), (499, "ok"), (500, "slow"), (1999, "slow"), (2000, "timeout")],
)
def test_bucket_boundaries(ms, expected):
    assert nt.bucket(ms) == expected


def test_python_folds_the_constant_too():
    assert "86400000000" in nt.bytecode(nt.folded_constant)


def test_missing_key_is_none_not_zero():
    counts: dict[str, int] = {}
    with pytest.raises(TypeError):
        counts.get("missing") + 1  # Python's version of unboxing null
    assert counts.get("missing", 0) == 0
