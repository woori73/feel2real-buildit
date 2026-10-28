"""PR914 resource-only JAR overlay. Neither Java classes nor media can be replaced."""
import hashlib
import shutil
import zipfile


def require(ok, reason):
    if not ok:
        raise RuntimeError(reason)


def sha_stream(stream):
    h = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        h.update(block)
    return h.hexdigest()


def entries(archive):
    names = archive.namelist()
    require(len(names) == len(set(names)), 'duplicate ZIP entries')
    require(all(not n.startswith('/') and '..' not in n.split('/') for n in names), 'unsafe ZIP path')
    return set(names)


def build_overlay(baseline, patch, output, approved):
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(patch) as delta:
        old_names, new_names = entries(old), entries(delta)
        require(new_names == set(approved), 'patch contains missing or unapproved resources')
        require(not any(n.upper().startswith('META-INF/') and n.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) for n in old_names), 'signed JAR needs separate release review')
        for name in new_names:
            require(delta.getinfo(name).file_size < 2 * 1024 * 1024, 'resource too large')
            with delta.open(name) as stream:
                require(sha_stream(stream) == approved[name], 'resource differs from approved PR914')
        with zipfile.ZipFile(output, 'x') as target:
            for info in old.infolist():
                source = delta if info.filename in new_names else old
                with source.open(info.filename) as src, target.open(info, 'w') as dst:
                    shutil.copyfileobj(src, dst, 1024 * 1024)
            for name in sorted(new_names - old_names):
                target.writestr(name, delta.read(name), compress_type=zipfile.ZIP_DEFLATED)
    # Verify every unmodified member, including Java, dependencies and DB migrations.
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(output) as result:
        require(entries(result) == entries(old) | set(approved), 'unexpected output entry')
        for name in result.namelist():
            with result.open(name) as stream:
                actual = sha_stream(stream)
            if name in approved:
                expected = approved[name]
            else:
                with old.open(name) as stream:
                    expected = sha_stream(stream)
            require(actual == expected, 'unapproved JAR content changed')
