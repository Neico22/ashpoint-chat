#!/usr/bin/env python3
"""Isolated dedicated-server tests. Never use a live server directory."""
import hashlib, os, pathlib, shutil, subprocess, sys, urllib.request, urllib.parse
project = pathlib.Path(__file__).resolve().parents[1]
root = project / 'build' / 'runtime-test-server'
root.mkdir(parents=True, exist_ok=True)
def download(url,path,digest):
    if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest()==digest: return
    with urllib.request.urlopen(url,timeout=90) as response: data=response.read()
    if hashlib.sha256(data).hexdigest()!=digest: raise RuntimeError('Dependency checksum mismatch: '+path.name)
    path.write_bytes(data)
launcher=root/'fabric-server-launch.jar'
download('https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/1.1.0/server/jar',launcher,'8c90a3ca48b3af42a50f25c7b561edb6ce1b404d6ff230bdf14257002bdecd7b')
mods=root/'mods';mods.mkdir(exist_ok=True)
for old in mods.glob('ashpoint-chat*.jar'): old.unlink()
bridge=os.environ.get('ASHPOINT_BRIDGE_JAR')
for old in mods.glob('ashpoint-bridge*.jar'):old.unlink()
if bridge:shutil.copy2(pathlib.Path(bridge),mods/pathlib.Path(bridge).name)
shutil.copy2(project/'build/game-inputs/server.jar',root/'server.jar')
for src,name in [('build/game-inputs/fabric-api.jar','fabric-api.jar'),('build/game-inputs/carpet.jar','carpet.jar'),('build/libs/ashpoint-chat-1.0.1.jar','ashpoint-chat-1.0.1.jar'),('build/libs/ashpoint-chat-test-harness-1.0.1.jar','ashpoint-chat-test-harness-1.0.1.jar')]:shutil.copy2(project/src,mods/name)
if '--no-lp' in sys.argv: (mods/'luckperms.jar').unlink(missing_ok=True)
else: download('https://cdn.modrinth.com/data/Vebnzrzj/versions/DzQPkkXY/LuckPerms-Fabric-5.5.85.jar',mods/'luckperms.jar','18598fcb95a83f6723d8bc68a905500ed5ecac92e9909ce1f25409cdebe2df49')
(root/'eula.txt').write_text('eula=true\n')
(root/'server.properties').write_text('online-mode=false\nenforce-secure-profile=true\nwhite-list=false\nserver-ip=127.0.0.1\nserver-port=25579\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\nnetwork-compression-threshold=-1\n')
java=str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java') if 'JAVA_HOME' in os.environ else 'java'
args=[java,'-Xmx1G','-Dfabric.installer.server.gameJar=server.jar']
proxy=urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
if proxy.hostname:
    args += [f'-Dhttps.proxyHost={proxy.hostname}',f'-Dhttps.proxyPort={proxy.port or 80}',f'-Dhttp.proxyHost={proxy.hostname}',f'-Dhttp.proxyPort={proxy.port or 80}']
    trust=pathlib.Path('/etc/ssl/certs/java/cacerts')
    if trust.exists():args += [f'-Djavax.net.ssl.trustStore={trust}']
if bridge:args += ['-Dashpoint.bridgeTests=true']
if '--restart' in sys.argv:args += ['-Dashpoint.restart=true']
args += ['-jar','fabric-server-launch.jar','nogui']
marker=root/'chat-test-result.txt';marker.unlink(missing_ok=True)
mode='restart' if '--restart' in sys.argv else 'no-lp' if '--no-lp' in sys.argv else 'full'
with (root/(mode+'-console.log')).open('w') as log:subprocess.run(args,cwd=root,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,timeout=240,check=True)
if not marker.exists() or not marker.read_text().startswith('PASS:'):raise SystemExit(marker.read_text() if marker.exists() else 'No pass marker; inspect console log')
shutil.copy2(marker,root/(mode+'-result.txt'))
print(marker.read_text())
