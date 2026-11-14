"""Generate offline Wordward assets. See ../../doc/WORDWARD_02_DATA.md."""
import argparse
import hashlib
import io
import json
from math import fsum, hypot, isfinite
from pathlib import Path
import re
import tarfile
import zipfile

from wordfreq import top_n_list

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
COUNT = 5000
DAYS = 31
TOKEN = re.compile(r"[a-z]+")
VECTOR_MEMBER = "glove.6B.100d.txt"


def tokens(name):
    return [
        word
        for line in (HERE / name).read_text(encoding="utf-8").splitlines()
        for word in line.partition("#")[0].split()
    ]


def digest(path):
    sha = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def checked_input(path, expected):
    actual = digest(path)
    if actual != expected:
        raise ValueError(f"Unrecognized source archive {path.name}: {actual}")
    return actual


def wordnet(path):
    lowercase = set()
    nouns = set()
    with tarfile.open(path, "r:gz") as archive:
        for pos in ("noun", "verb", "adj", "adv"):
            with archive.extractfile(f"WordNet-3.0/dict/data.{pos}") as stream:
                for line in io.TextIOWrapper(stream, encoding="ascii"):
                    if not line[0].isdigit():
                        continue
                    row = line.split("|", 1)[0].split()
                    word_count = int(row[3], 16)
                    words = row[4:4 + 2 * word_count:2]
                    for word in words:
                        if TOKEN.fullmatch(word):
                            lowercase.add(word)
                            if pos == "noun":
                                nouns.add(word)
        with archive.extractfile("WordNet-3.0/dict/noun.exc") as stream:
            exceptions = [
                line.split() for line in io.TextIOWrapper(stream, encoding="ascii")
                if line.strip()
            ]
    return lowercase, nouns, exceptions


def vectors(path, candidates):
    result = {}
    unusable = set()
    with zipfile.ZipFile(path) as archive, archive.open(VECTOR_MEMBER) as stream:
        for row in stream:
            word_bytes, _, values = row.partition(b" ")
            word = word_bytes.decode("utf-8", errors="strict")
            if word not in candidates:
                continue
            values = tuple(map(float, values.split()))
            length = hypot(*values)
            if len(values) != 100 or not isfinite(length) or length == 0 or word in result:
                unusable.add(word)
                continue
            result[word] = tuple(value / length for value in values)
    for word in unusable:
        result.pop(word, None)
    return result, unusable


def regular_plural(word):
    if word.endswith("y") and len(word) > 1 and word[-2] not in "aeiou":
        return word[:-1] + "ies"
    if word.endswith(("ch", "sh", "ss", "x", "zz")):
        return word + "es"
    if word.endswith(("f", "fe", "o", "s", "z")):
        return None
    return word + "s"


def aliases(vocab, lexicon, nouns, exceptions, exclude, frequent, reviewed):
    options = {}
    irregular_lemmas = {lemma for row in exceptions for lemma in row[1:]}
    for form, *lemmas in exceptions:
        options.setdefault(form, set()).update(lemmas)
    # Use the full noun lexicon to spot collisions, even with out-of-game lemmas.
    for lemma in nouns - irregular_lemmas:
        form = regular_plural(lemma)
        if form:
            options.setdefault(form, set()).add(lemma)
    result = {}
    for form, lemmas in options.items():
        if (not TOKEN.fullmatch(form) or form in lexicon or form in exclude
                or form not in frequent or len(lemmas) != 1):
            continue
        lemma = next(iter(lemmas))
        if form not in vocab and lemma in vocab:
            result[form] = lemma
    for form, lemma in reviewed.items():
        if form in vocab or lemma not in vocab or form in exclude:
            raise ValueError(f"Conflicting reviewed alias: {form} -> {lemma}")
        result[form] = lemma
    return result


def export(args):
    glove_hash = checked_input(args.glove, GLOVE_SHA256)
    wn_hash = checked_input(args.wordnet, WORDNET_SHA256)
    lexicon, nouns, exceptions = wordnet(args.wordnet)
    secrets = tokens("secrets.txt")
    extras = tokens("extra-words.txt")
    exclude = set(tokens("exclude-words.txt"))
    if len(secrets) != DAYS or len(set(secrets)) != DAYS or set(secrets) - nouns:
        raise ValueError("Expected 31 distinct WordNet nouns as secrets")
    inflections = {row[0] for row in exceptions if row[0] not in lexicon}
    reviewed = dict(line.split("\t") for line in
                    (HERE / "reviewed-aliases.tsv").read_text(encoding="utf-8").splitlines())
    eligible = lexicon - exclude - inflections - reviewed.keys()
    required = set(secrets) | set(extras) | set(reviewed.values())
    frequencies = top_n_list("en", 100_000, wordlist="large")
    pool = list(dict.fromkeys(secrets + extras + sorted(reviewed.values()) + frequencies[:40_000]))
    candidates = [
        word for word in pool
        if word in eligible and (len(word) >= 3 or word in {"go", "do", "be", "in", "on", "up"})
    ]
    chosen_vectors, unusable = vectors(args.glove, set(candidates))
    chosen = [word for word in candidates if word in chosen_vectors][:COUNT]
    vocab = set(chosen)
    missing_required = required - vocab
    if len(vocab) != COUNT or missing_required:
        raise ValueError(f"Only {len(vocab)} canonical words or missing required words: "
                         f"{', '.join(sorted(missing_required))}")
    forms = aliases(vocab, lexicon, nouns, exceptions, exclude, set(frequencies), reviewed)
    rows = []
    report = []
    for secret in secrets:
        reference = chosen_vectors[secret]

        def key(word):
            score = fsum(a * b for a, b in zip(reference, chosen_vectors[word]))
            return -round(score, 12), word

        ordered = [secret] + sorted(vocab - {secret}, key=key)
        rows.append(secret + "\t" + " ".join(ordered))
        report.append(secret + "\t" + " ".join(ordered[:50]))
    args.out.mkdir(parents=True, exist_ok=True)
    outputs = {
        "puzzles.tsv": rows,
        "aliases.tsv": [f"{form}\t{forms[form]}" for form in sorted(forms)],
    }
    for name, lines in outputs.items():
        (args.out / name).write_bytes(("\n".join(lines) + "\n").encode("utf-8"))
    if args.top:
        args.top.write_bytes(("\n".join(report) + "\n").encode("utf-8"))
    print(json.dumps({
        "dataset_version": "wordward-en-5000-31-v1",
        "canonical": len(vocab), "secrets": len(secrets), "aliases": len(forms),
        "candidates": len(candidates), "outside_vectors": len(set(candidates) - set(chosen_vectors)),
        "unusable_vectors": len(unusable), "glove_sha256": glove_hash, "wordnet_sha256": wn_hash,
        "output_sha256": {name: digest(args.out / name) for name in outputs},
    }, indent=2))


GLOVE_SHA256 = "6471382cdd837544bf3ac72497a38715e845897d265b2b424b4761832009c837"
WORDNET_SHA256 = "640db279c949a88f61f851dd54ebbb22d003f8b90b85267042ef85a3781d3a52"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--glove", type=Path, required=True)
    parser.add_argument("--wordnet", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=ROOT / "app/src/main/assets")
    parser.add_argument("--top", type=Path, help="Optional output with top 50 per secret")
    export(parser.parse_args())
