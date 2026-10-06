package uk.ashpoint.chat;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.*;
import java.util.*;

/** Validated snapshots; force temp file to disk before atomic replacement. */
public final class Store {
 static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
 public static final class Pref {
  public String name=""; public Set<UUID> ignores=new HashSet<>();
  public boolean spy=false, messages=true, staff=false, mentions=true;
 }
 public static final class Mute { public long expires; public String reason,actor; public Mute(long e,String r,String a){expires=e;reason=r;actor=a;} }
 public static final class Data {
  public Map<UUID,Pref> players=new HashMap<>(); public Map<UUID,Mute> mutes=new HashMap<>();
  public int slow=0; public boolean locked=false; public String lockReason="Chat is temporarily locked.";
 }
 public static final class Config {
  public boolean formatting=false,staffMessageBypass=true,mentionSound=true;
  public String mentionSoundId="minecraft:block.note_block.pling";public float mentionVolume=0.6f,mentionPitch=1.0f;public int nearDuplicateDistance=2,nearDuplicateMinimumLength=8;
  public String publicFormat="{prefix}{sender} » {message}", privateFormat="[DM] {sender} → {target}: {message}",spyFormat="[SocialSpy] {sender} → {target}: {message}",staffFormat="[STAFF] {sender}: {message}";
  public String muted="You are muted ({remaining}). Reason: {reason}",locked="Chat is locked: {reason}",slow="Wait {remaining} before chatting.",spam="Message blocked: {reason}", clearNotice="Chat cleared by {sender}.",ignored="This player is not accepting your messages.",messagesOff="This player has private messages disabled.";
  public boolean antiSpam=true,cancelSpam=true,warnSpam=true,alertStaff=true,autoMute=false;
  public int minIntervalMillis=900,duplicateWindowSeconds=15,maxCharacterRepeat=12,capsMinimumLetters=12,capsPercent=85,violationsToMute=5,violationWindowSeconds=60,autoMuteSeconds=300,clearLines=100;
  public long defaultMuteSeconds=0;
 }
 final Path configPath,dataPath; public Config config; public Data data;
 public Store(Path configPath,Path dataPath)throws IOException{this.configPath=configPath;this.dataPath=dataPath;reload();if(!Files.exists(dataPath)){data=new Data();save();}else{data=read(dataPath,Data.class);validateData(data);}}
 static <T>T read(Path path,Class<T> type)throws IOException{try{T v=JSON.fromJson(Files.readString(path),type);if(v==null)throw new IllegalArgumentException("Empty JSON");return v;}catch(RuntimeException e){throw new IOException("Invalid "+path.getFileName(),e);}}
 public void reload()throws IOException{Config next=Files.exists(configPath)?read(configPath,Config.class):new Config();validate(next);if(!Files.exists(configPath))write(configPath,next);config=next;}
 static void validate(Config c)throws IOException {
  if(c.nearDuplicateDistance<0||c.nearDuplicateDistance>10||c.nearDuplicateMinimumLength<1||c.nearDuplicateMinimumLength>256||!Float.isFinite(c.mentionVolume)||c.mentionVolume<0||c.mentionVolume>2||!Float.isFinite(c.mentionPitch)||c.mentionPitch<0.5f||c.mentionPitch>2)throw new IOException("Invalid mention or duplicate settings");
  try{if(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(net.minecraft.resources.Identifier.parse(c.mentionSoundId))==null)throw new IllegalArgumentException("Unknown sound");}catch(Exception e){throw new IOException("Invalid mentionSoundId",e);}
  if(c.minIntervalMillis<0||c.minIntervalMillis>60000||c.duplicateWindowSeconds<0||c.duplicateWindowSeconds>86400||c.maxCharacterRepeat<2||c.maxCharacterRepeat>1000||c.capsMinimumLetters<1||c.capsPercent<1||c.capsPercent>100||c.violationsToMute<1||c.violationWindowSeconds<1||c.autoMuteSeconds<1||c.clearLines<1||c.clearLines>1000||c.defaultMuteSeconds<0||c.defaultMuteSeconds>315360000)throw new IOException("Config threshold out of range");
  for(String s:List.of(c.publicFormat,c.privateFormat,c.spyFormat,c.staffFormat,c.muted,c.locked,c.slow,c.spam,c.clearNotice,c.ignored,c.messagesOff))if(s.length()>4096)throw new IOException("Config message too long");
 }
 static void validateData(Data d)throws IOException{if(d.players==null||d.mutes==null||d.slow<0||d.slow>3600||d.lockReason==null)throw new IOException("Invalid persisted data");for(var p:d.players.values())if(p==null||p.ignores==null||p.name==null)throw new IOException("Invalid player data");for(var m:d.mutes.values())if(m==null||m.expires<0||m.reason==null||m.actor==null)throw new IOException("Invalid mute data");}
 public Pref pref(UUID id){return data.players.computeIfAbsent(id,k->new Pref());}
 public void save()throws IOException{write(dataPath,data);}
 static void write(Path path,Object value)throws IOException{
  Files.createDirectories(path.toAbsolutePath().getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp");
  byte[] bytes=JSON.toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  try(FileChannel ch=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){var b=java.nio.ByteBuffer.wrap(bytes);while(b.hasRemaining())ch.write(b);ch.force(true);}
  try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
 }
 public boolean expire(long now){return data.mutes.entrySet().removeIf(e->e.getValue().expires>0&&e.getValue().expires<=now);}
 public static long duration(String input){if(!input.matches("[1-9][0-9]{0,8}[smhdw]"))throw new IllegalArgumentException("Use a duration such as 10m, 2h, 7d or 1w");long n=Long.parseLong(input.substring(0,input.length()-1));long mul=switch(input.charAt(input.length()-1)){case 's'->1;case 'm'->60;case 'h'->3600;case 'd'->86400;case 'w'->604800;default->throw new IllegalArgumentException();};long ms=Math.multiplyExact(n,Math.multiplyExact(mul,1000));if(ms>315360000000L)throw new IllegalArgumentException("Maximum mute duration is 10 years");return ms;}
}
