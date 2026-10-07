package uk.ashpoint.chat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import carpet.patches.*;
import java.util.*;
import java.nio.file.*;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

public final class ChatRuntimeTests implements ModInitializer {
 HeadlessClient realA,realB;long realChatBefore,realDmBefore;BridgeProbe bridge;
 int ticks,checks,phase;boolean done;ChatMod mod;ServerPlayer a,b,c;Capture ca,cb,cc;
 static final class Capture extends FakeClientConnection {
  List<Packet<?>> packets=new ArrayList<>();Capture(){super(PacketFlow.SERVERBOUND);}
  @Override public void send(Packet<?> p,io.netty.channel.ChannelFutureListener f,boolean flush){packets.add(p);}
  @Override public void send(Packet<?> p){packets.add(p);}
  @Override public void send(Packet<?> p,io.netty.channel.ChannelFutureListener f){packets.add(p);}
  long chat(){return packets.stream().filter(p->p instanceof ClientboundPlayerChatPacket||p instanceof ClientboundDisguisedChatPacket).count();}
  long system(){return packets.stream().filter(p->p instanceof ClientboundSystemChatPacket).count();}
  void clear(){packets.clear();}
 }
 void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;System.out.println("CHAT CHECK: "+label);}
 void command(MinecraftServer s,String str){s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),str);}
 Capture capture(ServerPlayer p)throws Exception{var capture=new Capture();p.connection=new NetHandlerPlayServerFake(mod.server,capture,p,CommonListenerCookie.createInitial(p.getGameProfile(),false));return capture;}
 void grant(ServerPlayer p,String node,boolean value){if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms")){var u=LuckPermsProvider.get().getUserManager().getUser(p.getUUID());if(u==null)u=LuckPermsProvider.get().getUserManager().loadUser(p.getUUID(),p.getGameProfile().name()).join();u.data().add(Node.builder("ashpoint.chat."+node).value(value).build());}}
 @Override public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{step(s);}catch(Throwable t){done=true;t.printStackTrace();try{Files.writeString(Path.of("chat-test-result.txt"),"FAIL: "+t);}catch(Exception ignored){}s.halt(false);}});}
 void step(MinecraftServer s)throws Exception{
  ticks++;mod=ChatMod.INSTANCE;
  if(ticks==5&&Boolean.getBoolean("ashpoint.restart")){
   UUID alice=net.minecraft.server.players.NameAndId.createOffline("ChatAlice").id(),bob=net.minecraft.server.players.NameAndId.createOffline("ChatBob").id(),staff=net.minecraft.server.players.NameAndId.createOffline("ChatStaff").id();
   check(mod.store.data.mutes.containsKey(alice),"mute survives actual server restart");check(mod.store.data.mutes.get(alice).reason.equals("restart-test"),"mute reason survives restart");check(mod.store.pref(bob).ignores.contains(alice),"ignore survives actual restart");check(mod.store.pref(staff).spy&&mod.store.pref(staff).staff,"SocialSpy and staff mode survive restart");check(!mod.store.pref(bob).messages&&!mod.store.pref(bob).mentions,"message and mention preferences survive restart");
   Files.writeString(Path.of("chat-test-result.txt"),"PASS: "+checks+" actual restart assertions");done=true;s.halt(false);return;
  }
  if(ticks==10){realA=new HeadlessClient(s,"WireAlice");realB=new HeadlessClient(s,"WireBob");}
  if(ticks==20){if(Boolean.getBoolean("ashpoint.bridgeTests"))bridge=new BridgeProbe(s);check(mod.store!=null,"configuration initialized");check(!mod.store.config.formatting,"formatting disabled by default");
   for(String n:List.of("clearchat","sudo","mute","unmute","mutelist","ignore","unignore","ignorelist","msg","tell","w","whisper","reply","r","msgtoggle","socialspy","sc","staffchat","slowchat","lockchat","ashpointchat"))check(s.getCommands().getDispatcher().getRoot().getChild(n)!=null,"registered /"+n);
   EntityPlayerMPFake.createFake("ChatAlice",s,new Vec3(0,100,0),0,0,s.overworld().dimension(),GameType.CREATIVE,false);EntityPlayerMPFake.createFake("ChatBob",s,new Vec3(2,100,0),0,0,s.overworld().dimension(),GameType.CREATIVE,false);EntityPlayerMPFake.createFake("ChatStaff",s,new Vec3(4,100,0),0,0,s.overworld().dimension(),GameType.CREATIVE,false);
  }
  if(ticks>=100&&phase==0){a=s.getPlayerList().getPlayerByName("ChatAlice");b=s.getPlayerList().getPlayerByName("ChatBob");c=s.getPlayerList().getPlayerByName("ChatStaff");if(a==null||b==null||c==null){if(ticks>1200)throw new AssertionError("Carpet players failed to join");return;}check(true,"three actual Carpet players joined");phase=1;ticks=100;ca=capture(a);cb=capture(b);cc=capture(c);
   mod.store.data.mutes.clear();mod.store.pref(b.getUUID()).ignores.clear();mod.store.pref(b.getUUID()).messages=true;mod.store.pref(c.getUUID()).staff=false;mod.store.pref(b.getUUID()).mentions=true;mod.store.data.locked=false;mod.store.data.slow=0;
   grant(c,"socialspy",true);grant(c,"staffchat",true);grant(c,"sudo",true);grant(c,"mute",true);grant(a,"sudo",false);grant(a,"mute.bypass",false);
   check(!mod.has(a,"sudo",false),"normal player denied sudo");check(mod.has(c,"socialspy",false)||!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("luckperms"),"LuckPerms staff node grants");
   s.getPlayerList().op(new net.minecraft.server.players.NameAndId(c.getGameProfile()));
   mod.store.config.antiSpam=false;
   command(s,"sudo ChatAlice command msg ChatBob first message");check(ca.system()>0&&cb.system()>0,"sudo command executes DM as target");check(mod.replies.get(b.getUUID()).equals(a.getUUID()),"reply target assigned");
   for(String alias:List.of("msg","tell","w","whisper")){ca.clear();cb.clear();s.getCommands().performPrefixedCommand(a.createCommandSourceStack(),alias+" ChatBob alias "+alias);check(ca.system()==1&&cb.system()==1,"alias delivers "+alias);}
   ca.clear();cb.clear();s.getCommands().performPrefixedCommand(b.createCommandSourceStack(),"reply actual reply");check(ca.system()==1&&cb.system()==1,"reply command sends to last conversation");ca.clear();cb.clear();s.getCommands().performPrefixedCommand(a.createCommandSourceStack(),"r short reply");check(ca.system()==1&&cb.system()==1,"r alias sends reply");
   boolean denied=false;try{s.getCommands().getDispatcher().execute("sudo ChatBob command gamemode spectator",a.createCommandSourceStack());}catch(Exception expected){denied=true;}check(denied,"normal player's sudo command denied by dispatcher");
   mod.store.pref(c.getUUID()).spy=true;ca.clear();cb.clear();cc.clear();mod.dm(a,b,"spy test");check(cc.system()==1,"SocialSpy receives successful DM");
   mod.store.pref(b.getUUID()).messages=false;boolean rejected=false;try{mod.dm(a,b,"blocked");}catch(Exception expected){rejected=true;}check(rejected,"message toggle rejects normal DM");mod.store.pref(b.getUUID()).messages=true;
   mod.store.pref(b.getUUID()).ignores.add(a.getUUID());cb.clear();ca.clear();command(s,"sudo ChatAlice chat hidden chat");check(cb.chat()==0&&ca.chat()==1,"ignore filters server-authored chat per recipient");if(bridge!=null)check(bridge.eachOnce("hidden chat"),"per-recipient ignore does not duplicate or suppress Bridge broadcast");rejected=false;try{mod.dm(a,b,"ignored");}catch(Exception e){rejected=true;}check(rejected,"ignored private message rejected");mod.store.pref(b.getUUID()).ignores.clear();
   ca.clear();cb.clear();command(s,"sudo ChatAlice chat real Carpet chat");check(ca.chat()==1&&cb.chat()==1,"Carpet sudo chat delivers exactly once");
   check(cb.packets.stream().noneMatch(p->p instanceof ClientboundPlayerChatPacket),"sudo never enters signed player packet validation");
   check(cb.packets.stream().anyMatch(p->p instanceof ClientboundDisguisedChatPacket chat&&chat.message().getString().equals("real Carpet chat")&&chat.chatType().name().getString().equals("ChatAlice")),"vanilla CHAT type has exact fake-player sender and text");
   if(bridge!=null)check(bridge.eachOnce("real Carpet chat")&&bridge.correct("real Carpet chat","ChatAlice",a.getUUID()),"production Bridge queues exact sender/text once per chat and console across two guilds");
   check(mod.generatedChat(ChatType.bind(ChatType.CHAT,a))==null,"scoped sudo identity cleared after broadcast");
   mod.store.config.formatting=true;ca.clear();cb.clear();command(s,"sudo ChatAlice chat formatted bot message");check(ca.chat()==0&&cb.chat()==0&&cb.system()==1,"custom formatting emits exactly one system delivery");if(bridge!=null)check(bridge.eachOnce("formatted bot message"),"custom formatting still forwards through Bridge once");mod.store.config.formatting=false;
   command(s,"sudo ChatAlice command gamemode spectator");check(a.gameMode.getGameModeForPlayer()==GameType.CREATIVE,"sudo does not grant target admin permissions");
   command(s,"mute ChatAlice 1s test");check(mod.muted(a),"temporary mute applies");cb.clear();command(s,"sudo ChatAlice chat blocked by mute");check(cb.chat()==0,"mute blocks sudo chat");if(bridge!=null)check(bridge.count("blocked by mute")==0,"mute cancels production Bridge forwarding");check(mod.dm(a,b,"muted DM")==0,"mute blocks private message");
  }
  if(ticks==140&&phase==1){check(!mod.muted(a),"temporary mute expires");command(s,"mute ChatAlice persistent reason");check(mod.store.data.mutes.get(a.getUUID()).expires==0,"permanent mute");command(s,"unmute ChatAlice");check(!mod.muted(a),"unmute restores chat");
   command(s,"lockchat Testing");check(!mod.allowPublic(a,"locked"),"chat lock blocks normal player");grant(c,"lockchat.bypass",true);check(mod.allowPublic(c,"bypass"),"chat lock bypass");command(s,"lockchat off");check(!mod.store.data.locked,"chat unlock");
   command(s,"slowchat 5");mod.spam.clear();check(mod.allowPublic(a,"one"),"slowchat first message allowed");check(!mod.allowPublic(a,"two"),"slowchat rate enforced");command(s,"slowchat off");
   mod.store.config.antiSpam=true;mod.spam.clear();check(mod.allowPublic(a,"some original message"),"anti-spam allows ordinary chat");check(!mod.allowPublic(a,"some original message"),"anti-spam fast repeat blocked");check(mod.spamReason(new ChatMod.Spam(),"AAAAAAAAAAAAAAAAAA",System.currentTimeMillis())!=null,"character repetition detected");check(mod.spamReason(new ChatMod.Spam(),"THIS IS ALL CAPS HERE",System.currentTimeMillis())!=null,"CAPS detected");check(ChatMod.similar("helloeverybody","helloeverybodx"),"near duplicate detection");
   mod.store.config.antiSpam=false;mod.store.pref(c.getUUID()).staff=true;ca.clear();cb.clear();cc.clear();check(!mod.allowPublic(c,"staff only"),"staff mode diverts public chat");check(cc.system()>0&&ca.system()==0&&cb.system()==0,"staff recipients only");mod.store.pref(c.getUUID()).staff=false;
   cb.clear();command(s,"sudo ChatAlice chat hello @ChatBob");check(cb.packets.stream().anyMatch(p->p instanceof ClientboundSoundPacket),"mention sends vanilla sound");mod.store.pref(b.getUUID()).mentions=false;cb.clear();command(s,"sudo ChatAlice chat hello @ChatBob");check(cb.packets.stream().noneMatch(p->p instanceof ClientboundSoundPacket),"mention toggle respected");
   ca.clear();command(s,"clearchat ChatAlice");check(ca.system()>=100,"individual clear sends blank lines");
   Path config=mod.store.configPath;String good=Files.readString(config);Files.writeString(config,"{ broken");check(mod.reload(s.createCommandSourceStack())==0,"malformed reload rejected");Files.writeString(config,good);check(mod.reload(s.createCommandSourceStack())==1,"valid config reload succeeds");
   check(Store.duration("2h")==7200000,"duration parser");try{Store.duration("0m");throw new AssertionError("bad duration accepted");}catch(IllegalArgumentException e){check(true,"invalid duration rejected");}
   check(s.getCommands().getDispatcher().execute("ashpointchat info",s.createCommandSourceStack())==1,"info command");
   mod.store.pref(b.getUUID()).ignores.add(a.getUUID());mod.store.pref(c.getUUID()).spy=true;mod.store.pref(c.getUUID()).staff=true;mod.store.pref(b.getUUID()).messages=false;command(s,"mute ChatAlice restart-test");mod.save();
   Store reloaded=new Store(config,mod.store.dataPath);check(reloaded.data.mutes.containsKey(a.getUUID()),"mute persisted to disk");check(reloaded.pref(b.getUUID()).ignores.contains(a.getUUID()),"ignore persisted to disk");check(reloaded.pref(c.getUUID()).spy&&reloaded.pref(c.getUUID()).staff,"staff preferences persisted");check(!reloaded.pref(b.getUUID()).messages&&!reloaded.pref(b.getUUID()).mentions,"player preferences persisted");
   phase=2;
  }
  if(ticks>=180&&phase==2){
   if(realA.failure!=null||realB.failure!=null)throw new AssertionError("Vanilla socket client failed",realA.failure!=null?realA.failure:realB.failure);
   if(!realA.playing||!realB.playing){if(ticks>1200)throw new AssertionError("Socket clients did not join");return;}
   check(s.getPlayerList().getPlayerByName("WireAlice")!=null&&s.getPlayerList().getPlayerByName("WireBob")!=null,"two vanilla protocol clients joined");
   mod.store.config.antiSpam=false;mod.store.pref(s.getPlayerList().getPlayerByName("WireBob").getUUID()).ignores.clear();realChatBefore=realB.chatCount();realDmBefore=realB.systemCount();realA.command("msg WireBob real private message");realA.chat("hello from a connected player");phase=3;ticks=180;
  }
  if(ticks==210&&phase==3){check(realB.systemCount()>realDmBefore,"real connected player DM received");check(realB.chatCount()==realChatBefore+1,"normal connected-player public chat exactly once");if(bridge!=null)check(bridge.eachOnce("hello from a connected player")&&bridge.correct("hello from a connected player","WireAlice",s.getPlayerList().getPlayerByName("WireAlice").getUUID()),"normal chat reaches production Bridge exactly once with original formatting");realChatBefore=realB.chatCount();command(s,"sudo WireAlice chat server generated chat");command(s,"unmute ChatAlice");command(s,"sudo ChatAlice chat hello from bot");phase=4;}
  if(ticks==230&&phase==4){check(realB.chatCount()==realChatBefore+2,"real-target and Carpet sudo each arrive exactly once at normal connected player");check(realB.received.stream().filter(p->p instanceof ClientboundDisguisedChatPacket chat&&chat.message().getString().equals("hello from bot")&&chat.chatType().name().getString().equals("ChatAlice")).count()==1,"Carpet sender name and text exactly once on real socket");if(bridge!=null)check(bridge.eachOnce("hello from bot")&&bridge.correct("hello from bot","ChatAlice",a.getUUID()),"hello from bot forwarded by production Bridge exactly once with correct fake identity");check(realB.received.stream().anyMatch(p->p instanceof ClientboundDisguisedChatPacket chat&&chat.message().getString().equals("server generated chat")&&chat.chatType().name().getString().equals("WireAlice")),"real target uses safe server-authored vanilla chat without signature");if(bridge!=null)check(bridge.eachOnce("server generated chat")&&bridge.correct("server generated chat","WireAlice",s.getPlayerList().getPlayerByName("WireAlice").getUUID()),"real-target sudo reaches Bridge exactly once");check(realA.failure==null&&realB.failure==null,"both protocol clients remain connected without validation disconnect");mod.store.pref(s.getPlayerList().getPlayerByName("WireBob").getUUID()).ignores.add(a.getUUID());realChatBefore=realB.chatCount();command(s,"sudo ChatAlice chat ignored by wire player");phase=5;
  }
  if(ticks==250&&phase==5){check(realB.chatCount()==realChatBefore,"ignore blocks sudo on normal connected recipient");if(bridge!=null)check(bridge.eachOnce("ignored by wire player"),"ignore retains exactly one Discord message per public channel");command(s,"mute ChatAlice restart-test");long before=bridge==null?0:bridge.deliveries.size();command(s,"sudo ChatAlice chat muted bot to bridge");if(bridge!=null)check(bridge.deliveries.size()==before,"muted Carpet message produces zero Discord submissions");mod.save();if(bridge!=null)bridge.close();realA.close();realB.close();
   Files.writeString(Path.of("chat-test-result.txt"),"PASS: "+checks+" assertions, three Carpet players and two vanilla protocol socket clients, dedicated server");done=true;s.halt(false);
  }
 }
}
