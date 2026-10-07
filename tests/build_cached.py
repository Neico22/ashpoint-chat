#!/usr/bin/env python3
"""Offline Java 25 build from Gradle's checksum-pinned, prepared classpath.
Useful for integration CI with network-disabled Java toolchain downloads.
"""
import os,pathlib,subprocess,zipfile,re,hashlib,shutil
root=pathlib.Path(__file__).resolve().parents[1]
java=pathlib.Path(os.environ['JAVA_HOME'])/'bin'
version=re.search(r"version = '([^']+)'",(root/'build.gradle').read_text()).group(1)
# Validate and reconstruct classpath from the exact pinned Gradle inputs.
rows=re.findall(r"\['([^']+\.jar)','[^']+','([a-f0-9]{64})'\]",(root/'build.gradle').read_text())
for name,digest in rows:
 p=root/'build/game-inputs'/name
 if hashlib.sha256(p.read_bytes()).hexdigest()!=digest:raise RuntimeError('Unverified input: '+name)
 if name in ['server.jar','fabric-api.jar']:
  with zipfile.ZipFile(p) as bundle:
   for member in bundle.namelist():
    if member.endswith('.jar'):(root/'build/game-classpath'/member.replace('/','_')).write_bytes(bundle.read(member))
 else:shutil.copy2(p,root/'build/game-classpath'/name)
cp=os.pathsep.join(str(p) for p in sorted((root/'build/game-classpath').glob('*.jar')))
main=root/'build/classes/java/main';test=root/'build/classes/java/test'
for out in [main,test]:
 out.mkdir(parents=True,exist_ok=True)
 for old in out.rglob('*.class'):old.unlink()
subprocess.run([str(java/'javac'),'--release','25','-proc:none','-cp',cp,'-d',str(main),*[str(p) for p in sorted((root/'src/main/java').rglob('*.java'))]],check=True)
subprocess.run([str(java/'javac'),'--release','25','-proc:none','-cp',cp+os.pathsep+str(main),'-d',str(test),*[str(p) for p in sorted((root/'src/test/java').rglob('*.java'))]],check=True)
for name,classes,res in [('ashpoint-chat',main,'main'),('ashpoint-chat-test-harness',test,'test')]:
 path=root/'build/libs'/f'{name}-{version}.jar';path.parent.mkdir(parents=True,exist_ok=True)
 entries={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
 for p in (root/f'src/{res}/resources').rglob('*'):
  if p.is_file():entries[p.relative_to(root/f'src/{res}/resources').as_posix()]=p.read_bytes().replace(b'${version}',version.encode())
 entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\n\r\n'
 if res=='main':entries['LICENSE']=(root/'LICENSE').read_bytes()
 with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
  for name,data in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
 print(path)
