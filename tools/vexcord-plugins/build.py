#!/usr/bin/env python3
"""Build reproducible, versioned plugin archives and a format-1 repository index."""
import json
import hashlib
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT=Path(__file__).resolve().parents[2]/"plugins"
BASE="https://raw.githubusercontent.com/ctrl-mietze/Vexcord/main/plugins/"
index={"format":1,"name":"Vexcord Plugins","description":"First-party Vexcord extensions · audio, playback and more.","plugins":{}}
prior=json.loads((ROOT/"index.json").read_text()) if (ROOT/"index.json").exists() else {"plugins":{}}
for directory in sorted(ROOT.iterdir()):
    mf=directory/"manifest.json"
    if not mf.is_file():continue
    m=json.loads(mf.read_bytes())
    assert m["format"]==1 and m["dist"]["script"]=="index.js"
    version=m["version"]
    artifact=ROOT/"pool"/(m["id"]+"@"+version+".zip")
    artifact.parent.mkdir(exist_ok=True)
    buf=BytesIO()
    with ZipFile(buf,"w",compression=ZIP_DEFLATED,compresslevel=9) as z:
        for name in ("manifest.json","index.js"):
            info=ZipInfo(name,date_time=(2026,1,1,0,0,0))
            info.compress_type=ZIP_DEFLATED
            info.external_attr=0o100644<<16
            z.writestr(info,(directory/name).read_bytes(),compress_type=ZIP_DEFLATED,compresslevel=9)
    data=buf.getvalue()
    if artifact.exists() and artifact.read_bytes()!=data:
        raise RuntimeError("Published artifact differs: bump version "+str(artifact))
    if not artifact.exists():artifact.write_bytes(data)
    index["plugins"][m["id"]]={k:m[k] for k in ("name","description","author","icon")}
    versions=dict(prior["plugins"].get(m["id"],{}).get("versions",{}))
    versions[version]={
        "url":BASE+"pool/"+artifact.name,"sha256":hashlib.sha256(data).hexdigest(),"size":len(data),"dependencies":m["dependencies"]}
    index["plugins"][m["id"]].update(channels={"latest":version},versions=versions)
    print(m["id"],version,len(data),hashlib.sha256(data).hexdigest())
(ROOT/"index.json").write_text(json.dumps(index,ensure_ascii=False,indent=2)+"\n")
