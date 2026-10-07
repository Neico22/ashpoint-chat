package uk.ashpoint.chat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundEvents;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.slf4j.*;
import static net.minecraft.commands.Commands.*;

public final class ChatMod implements ModInitializer {
 public static ChatMod INSTANCE; public Store store; public MinecraftServer server;
 static final Logger LOG=LoggerFactory.getLogger("AshPoint Chat");
 final Map<UUID,UUID> replies=new HashMap<>();final Map<UUID,Spam> spam=new HashMap<>();
 int ticks,sudoDepth; final Set<String> conflicts=new TreeSet<>();
 public record GeneratedChat(ServerPlayer sender,PlayerChatMessage message,ChatType.Bound type) {}
 private final ThreadLocal<GeneratedChat> generatedChat=new ThreadLocal<>();
 public GeneratedChat generatedChat(ChatType.Bound type){var current=generatedChat.get();return current!=null&&current.type()==type?current:null;}
 /** Vanilla server-authored chat, through Fabric's player-chat broadcast hooks.
  * Carpet has no client/private signing key; never invent a target's signed chain.
  * The bound CHAT type carries the display name; the actual player argument carries
  * identity to moderation and bridges. Scoped identity is only for recipient rules.
  */
 private void speak(ServerPlayer sender,String text){
  var message=PlayerChatMessage.system(text);var type=ChatType.bind(ChatType.CHAT,sender);
  var previous=generatedChat.get();generatedChat.set(new GeneratedChat(sender,message,type));
  try{server.getPlayerList().broadcastChatMessage(message,sender,type);}
  finally{if(previous==null)generatedChat.remove();else generatedChat.set(previous);}
 }
 static final class Spam {long last,violationStart;int violations;String previous="";long previousAt;}
 @Override public void onInitialize(){
  INSTANCE=this;
  CommandRegistrationCallback.EVENT.register((d,r,e)->{
   for(var node:commands()){String name=node.getLiteral();if(d.getRoot().getChild(name)!=null){conflicts.add(name);LOG.warn("Existing /{} detected; AshPoint installs its own branch. Use /ashpointchat commands for guaranteed routing.",name);}removeRoot(d,name);d.register(node);}
   var root=literal("ashpointchat").then(literal("reload").requires(s->has(s,"reload",false)).executes(c->reload(c.getSource()))).then(literal("info").requires(s->has(s,"info",true)).executes(c->{notice(c.getSource(),"AshPoint Chat 1.0.1 | mutes: "+store.data.mutes.size()+" | slow: "+store.data.slow+"s | locked: "+store.data.locked+" | detected aliases: "+conflicts);return 1;}));
   for(var node:commands())root.then(node);d.register(root);
  });
  ServerLifecycleEvents.SERVER_STARTED.register(s->installCommands(s));
  ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s,r,ok)->{if(ok)installCommands(s);});
  ServerLifecycleEvents.SERVER_STARTING.register(s->{server=s;try{store=new Store(FabricLoader.getInstance().getConfigDir().resolve("ashpoint-chat.json"),s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("ashpoint-chat-data.json"));}catch(Exception ex){throw new IllegalStateException("AshPoint Chat refuses to overwrite unreadable config/data",ex);}});
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->{var p=h.getPlayer();store.pref(p.getUUID()).name=p.getGameProfile().name();save();});
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{spam.remove(h.getPlayer().getUUID());replies.remove(h.getPlayer().getUUID());});
  ServerTickEvents.END_SERVER_TICK.register(s->{if(++ticks%20==0&&store.expire(System.currentTimeMillis()))save();});
  ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(store!=null)save();});
  ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message,p,type)->allowPublic(p,message.signedBody().content()));
 }
 void installCommands(MinecraftServer s){var d=s.getCommands().getDispatcher();for(var node:commands()){removeRoot(d,node.getLiteral());d.register(node);}for(var p:s.getPlayerList().getPlayers())s.getCommands().sendCommands(p);}
 static void removeRoot(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d,String name){try{for(String field:List.of("children","literals","arguments")){var f=com.mojang.brigadier.tree.CommandNode.class.getDeclaredField(field);f.setAccessible(true);((Map<?,?>)f.get(d.getRoot())).remove(name);}}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot safely install chat command /"+name,e);}}
 boolean has(CommandSourceStack s,String n,boolean normal){return ChatPermissions.has(s,"ashpoint.chat."+n,normal);}
 boolean has(ServerPlayer p,String n,boolean normal){return has(p.createCommandSourceStack(),n,normal);}
 void save(){try{store.save();}catch(Exception e){LOG.error("Failed to save AshPoint Chat data",e);throw new IllegalStateException("Could not persist AshPoint Chat data",e);}}
 public static String format(String pattern,String... values){String out=pattern;for(int i=0;i<values.length;i+=2)out=out.replace("{"+values[i]+"}",values[i+1]);return out;}
 static void notice(CommandSourceStack s,String msg){s.sendSuccess(()->Component.literal(msg),false);}
 static void notice(ServerPlayer p,String msg){p.sendSystemMessage(Component.literal(msg));}
 public String name(ServerPlayer p){return p.getGameProfile().name();}
 String name(UUID id){ServerPlayer p=server.getPlayerList().getPlayer(id);return p!=null?name(p):store.pref(id).name.isBlank()?id.toString():store.pref(id).name;}
 UUID identity(String input)throws CommandSyntaxException{
  ServerPlayer p=server.getPlayerList().getPlayerByName(input);if(p!=null)return p.getUUID();
  for(var e:store.data.players.entrySet())if(e.getValue().name.equalsIgnoreCase(input))return e.getKey();
  try{return UUID.fromString(input);}catch(IllegalArgumentException e){throw error("Unknown player. Use a previously seen name or UUID.");}
 }
 ServerPlayer online(String input)throws CommandSyntaxException{var p=server.getPlayerList().getPlayerByName(input);if(p==null)throw error("Player must be online.");return p;}
 static CommandSyntaxException error(String text){return new SimpleCommandExceptionType(Component.literal(text)).create();}
 ServerPlayer self(CommandSourceStack s)throws CommandSyntaxException{return s.getPlayerOrException();}
 String arg(CommandContext<CommandSourceStack> c,String key){return StringArgumentType.getString(c,key);}
 LiteralArgumentBuilder<CommandSourceStack> cmd(String name,String permission,boolean normal){return literal(name).requires(s->store!=null&&has(s,permission,normal));}
 List<LiteralArgumentBuilder<CommandSourceStack>> commands(){
  List<LiteralArgumentBuilder<CommandSourceStack>> a=new ArrayList<>();
  a.add(cmd("clearchat","clearchat",false).executes(c->clear(c.getSource(),null)).then(argument("player",StringArgumentType.word()).executes(c->clear(c.getSource(),online(arg(c,"player"))))));
  a.add(cmd("sudo","sudo",false).then(argument("player",StringArgumentType.word()).then(literal("command").then(argument("command",StringArgumentType.greedyString()).executes(c->sudo(c.getSource(),online(arg(c,"player")),arg(c,"command"),false)))).then(literal("chat").then(argument("message",StringArgumentType.greedyString()).executes(c->sudo(c.getSource(),online(arg(c,"player")),arg(c,"message"),true))))));
  a.add(cmd("mute","mute",false).then(argument("player",StringArgumentType.word()).executes(c->mute(c.getSource(),identity(arg(c,"player")),"")).then(argument("details",StringArgumentType.greedyString()).executes(c->mute(c.getSource(),identity(arg(c,"player")),arg(c,"details"))))));
  a.add(cmd("unmute","unmute",false).then(argument("player",StringArgumentType.word()).executes(c->{UUID id=identity(arg(c,"player"));store.data.mutes.remove(id);save();audit(c.getSource(),"unmute "+name(id));notice(c.getSource(),"Unmuted "+name(id));return 1;})));
  a.add(cmd("mutelist","mutelist",false).executes(c->{store.expire(System.currentTimeMillis());notice(c.getSource(),"Active mutes: "+store.data.mutes.size());store.data.mutes.forEach((id,m)->notice(c.getSource(),name(id)+": "+remaining(m.expires)+" — "+m.reason));return 1;}));
  for(String verb:List.of("ignore","unignore"))a.add(cmd(verb,"ignore",true).then(argument("player",StringArgumentType.word()).executes(c->{var p=self(c.getSource());UUID id=identity(arg(c,"player"));if(id.equals(p.getUUID()))throw error("You cannot ignore yourself.");var pref=store.pref(p.getUUID());if(verb.equals("unignore"))pref.ignores.remove(id);else pref.ignores.add(id);save();notice(p,verb.equals("ignore")?"Ignoring "+name(id):"No longer ignoring "+name(id));return 1;})));
  a.add(cmd("ignorelist","ignore",true).executes(c->{notice(c.getSource(),"Ignored: "+store.pref(self(c.getSource()).getUUID()).ignores.stream().map(this::name).sorted().toList());return 1;}));
  for(String alias:List.of("msg","tell","w","whisper"))a.add(cmd(alias,"msg",true).then(argument("player",StringArgumentType.word()).then(argument("message",StringArgumentType.greedyString()).executes(c->dm(self(c.getSource()),online(arg(c,"player")),arg(c,"message"))))));
  for(String alias:List.of("reply","r"))a.add(cmd(alias,"msg",true).then(argument("message",StringArgumentType.greedyString()).executes(c->{var p=self(c.getSource());UUID id=replies.get(p.getUUID());ServerPlayer to=id==null?null:server.getPlayerList().getPlayer(id);if(to==null)throw error("No online reply recipient.");return dm(p,to,arg(c,"message"));})));
  a.add(cmd("msgtoggle","msgtoggle",true).executes(c->{var p=self(c.getSource());var pref=store.pref(p.getUUID());pref.messages=!pref.messages;save();notice(p,"Private messages "+(pref.messages?"enabled":"disabled"));return 1;}));
  a.add(cmd("mentiontoggle","mentions",true).executes(c->{var p=self(c.getSource());var pref=store.pref(p.getUUID());pref.mentions=!pref.mentions;save();notice(p,"Mention notifications "+(pref.mentions?"enabled":"disabled"));return 1;}));
  a.add(cmd("socialspy","socialspy",false).executes(c->{var p=self(c.getSource());var pref=store.pref(p.getUUID());pref.spy=!pref.spy;save();notice(p,"SocialSpy "+(pref.spy?"enabled":"disabled"));return 1;}));
  for(String alias:List.of("staffchat","sc"))a.add(cmd(alias,"staffchat",false).executes(c->{var p=self(c.getSource());var pref=store.pref(p.getUUID());pref.staff=!pref.staff;save();notice(p,"Staff chat mode "+(pref.staff?"enabled":"disabled"));return 1;}).then(argument("message",StringArgumentType.greedyString()).executes(c->{staff(self(c.getSource()),arg(c,"message"));return 1;})));
  a.add(cmd("slowchat","slowchat",false).then(literal("off").executes(c->slow(c.getSource(),0))).then(argument("seconds",IntegerArgumentType.integer(0,3600)).executes(c->slow(c.getSource(),IntegerArgumentType.getInteger(c,"seconds")))));
  a.add(cmd("lockchat","lockchat",false).executes(c->lock(c.getSource(),true,"Chat is temporarily locked.")).then(literal("off").executes(c->lock(c.getSource(),false,""))).then(argument("reason",StringArgumentType.greedyString()).executes(c->lock(c.getSource(),true,arg(c,"reason")))));
  return a;
 }
 int reload(CommandSourceStack source){try{store.reload();notice(source,"AshPoint Chat configuration reloaded; player data retained.");return 1;}catch(Exception e){notice(source,"Reload rejected; existing configuration retained. "+e.getMessage());return 0;}}
 int clear(CommandSourceStack s,ServerPlayer only){for(var p:server.getPlayerList().getPlayers()){if(only!=null&&only!=p)continue;if(!has(p,"clearchat.bypass",false))for(int n=0;n<store.config.clearLines;n++)p.sendSystemMessage(Component.literal(" "));notice(p,format(store.config.clearNotice,"sender",s.getTextName()));}audit(s,"clearchat "+(only==null?"all":name(only)));return 1;}
 void audit(CommandSourceStack source,String action){LOG.info("{}: {}",source.getTextName(),action.replace('\n',' ').replace('\r',' '));}
 int sudo(CommandSourceStack s,ServerPlayer p,String input,boolean chat)throws CommandSyntaxException{
  if(input.isBlank()||input.length()>2048||input.chars().anyMatch(ch->ch<32||ch==127))throw error("Invalid command/message text.");
  if(sudoDepth>=4)throw error("Sudo recursion limit.");
  audit(s,"sudo "+name(p)+" "+(chat?"chat":"command "+input.split(" ",2)[0]));
  sudoDepth++;try{if(chat){if(input.length()>256)throw error("Chat message is too long.");speak(p,input);return 1;}
   String command=input.startsWith("/")?input.substring(1):input;if(command.isBlank())throw error("Empty command.");server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),command);return 1;
  }finally{sudoDepth--;}
 }
 int mute(CommandSourceStack s,UUID id,String details)throws CommandSyntaxException{
  String[] parts=details.trim().split("\\s+",2);long ms=Math.multiplyExact(store.config.defaultMuteSeconds,1000);String reason="No reason supplied";
  if(!details.isBlank()){if(parts[0].matches("[0-9].*")){try{ms=Store.duration(parts[0]);}catch(Exception e){throw error(e.getMessage());}if(parts.length>1)reason=parts[1];}else reason=details;}
  long expiry=ms==0?0:Math.addExact(System.currentTimeMillis(),ms);store.data.mutes.put(id,new Store.Mute(expiry,reason,s.getTextName()));save();audit(s,"mute "+name(id)+" ("+remaining(expiry)+")");notice(s,"Muted "+name(id)+" ("+remaining(expiry)+")");var p=server.getPlayerList().getPlayer(id);if(p!=null)notice(p,format(store.config.muted,"remaining",remaining(expiry),"reason",reason));return 1;
 }
 static String remaining(long expires){if(expires==0)return "permanent";long sec=Math.max(0,(expires-System.currentTimeMillis()+999)/1000);if(sec>=86400)return (sec/86400)+"d "+((sec%86400)/3600)+"h";if(sec>=3600)return sec/3600+"h "+sec%3600/60+"m";if(sec>=60)return sec/60+"m "+sec%60+"s";return sec+"s";}
 boolean muted(ServerPlayer p){Store.Mute m=store.data.mutes.get(p.getUUID());if(m!=null&&m.expires>0&&m.expires<=System.currentTimeMillis()){store.data.mutes.remove(p.getUUID());save();return false;}if(m==null||has(p,"mute.bypass",false))return false;notice(p,format(store.config.muted,"remaining",remaining(m.expires),"reason",m.reason));return true;}
 public boolean ignored(UUID recipient,UUID sender){return store.pref(recipient).ignores.contains(sender);}
 int dm(ServerPlayer from,ServerPlayer to,String message)throws CommandSyntaxException{
  if(!has(from,"msg",true))throw error("You cannot send private messages.");if(muted(from))return 0;if(from==to)throw error("You cannot message yourself.");
  if(ignored(to.getUUID(),from.getUUID())||ignored(from.getUUID(),to.getUUID()))throw error(store.config.ignored);
  if(!store.pref(to.getUUID()).messages&&!(store.config.staffMessageBypass&&has(from,"msg.bypass",false)))throw error(store.config.messagesOff);
  Component text=Component.literal(format(store.config.privateFormat,"sender",name(from),"target",name(to),"message",message));from.sendSystemMessage(text);to.sendSystemMessage(text);
  replies.put(from.getUUID(),to.getUUID());replies.put(to.getUUID(),from.getUUID());
  for(var p:server.getPlayerList().getPlayers())if(p!=from&&p!=to&&store.pref(p.getUUID()).spy&&has(p,"socialspy",false))notice(p,format(store.config.spyFormat,"sender",name(from),"target",name(to),"message",message));return 1;
 }
 void staff(ServerPlayer p,String message){for(var to:server.getPlayerList().getPlayers())if(has(to,"staffchat",false))notice(to,format(store.config.staffFormat,"sender",name(p),"message",message));}
 int slow(CommandSourceStack s,int seconds){store.data.slow=seconds;save();audit(s,"slowchat "+seconds);server.getPlayerList().broadcastSystemMessage(Component.literal("Slow chat: "+seconds+" seconds."),false);return 1;}
 int lock(CommandSourceStack s,boolean locked,String reason){store.data.locked=locked;store.data.lockReason=reason;save();audit(s,"lockchat "+locked);server.getPlayerList().broadcastSystemMessage(Component.literal(locked?"Chat locked: "+reason:"Chat unlocked."),false);return 1;}
 boolean allowPublic(ServerPlayer p,String message){
  if(muted(p))return false;
  if(store.pref(p.getUUID()).staff&&has(p,"staffchat",false)){staff(p,message);return false;}
  if(store.data.locked&&!has(p,"lockchat.bypass",false)){notice(p,format(store.config.locked,"reason",store.data.lockReason));return false;}
  long now=System.currentTimeMillis();Spam state=spam.computeIfAbsent(p.getUUID(),k->new Spam());
  long wait=store.data.slow*1000L-(now-state.last);if(wait>0&&!has(p,"slowchat.bypass",false)){notice(p,format(store.config.slow,"remaining",((wait+999)/1000)+"s"));return false;}
  String reason=spamReason(state,message,now);
  if(store.config.antiSpam&&!has(p,"antispam.bypass",false)&&reason!=null){
   if(now-state.violationStart>store.config.violationWindowSeconds*1000L){state.violations=0;state.violationStart=now;}state.violations++;
   if(store.config.warnSpam)notice(p,format(store.config.spam,"reason",reason));
   if(store.config.alertStaff)for(var to:server.getPlayerList().getPlayers())if(has(to,"antispam.alerts",false))notice(to,"[AntiSpam] "+name(p)+": "+reason);
   if(store.config.autoMute&&state.violations>=store.config.violationsToMute){store.data.mutes.put(p.getUUID(),new Store.Mute(now+store.config.autoMuteSeconds*1000L,"Repeated chat spam","AntiSpam"));save();return false;}
   if(store.config.cancelSpam)return false;
  }
  state.last=now;state.previous=normalize(message);state.previousAt=now;return true;
 }
 static String normalize(String input){return input.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
 String spamReason(Spam s,String input,long now){var c=store.config;
  if(s.last>0&&now-s.last<c.minIntervalMillis)return "Messages sent too quickly";
  String normalized=normalize(input);if(!normalized.isEmpty()&&!s.previous.isEmpty()&&now-s.previousAt<c.duplicateWindowSeconds*1000L&&(normalized.equals(s.previous)||similar(normalized,s.previous,c.nearDuplicateMinimumLength,c.nearDuplicateDistance)))return "Repeated message";
  int repeat=0,previous=-1,letters=0,caps=0;for(int cp:input.codePoints().toArray()){repeat=cp==previous?repeat+1:1;previous=cp;if(repeat>c.maxCharacterRepeat)return "Excessive character repetition";if(Character.isLetter(cp)){letters++;if(Character.isUpperCase(cp))caps++;}}
  if(letters>=c.capsMinimumLetters&&caps*100>=letters*c.capsPercent)return "Excessive CAPS";return null;
 }
 static boolean similar(String a,String b){return similar(a,b,8,2);}
 static boolean similar(String a,String b,int minimum,int distance){if(a.length()<minimum||b.length()<minimum||Math.abs(a.length()-b.length())>distance)return false;int[] prev=new int[b.length()+1];for(int j=0;j<prev.length;j++)prev[j]=j;for(int i=1;i<=a.length();i++){int[] next=new int[b.length()+1];next[0]=i;for(int j=1;j<=b.length();j++)next[j]=Math.min(Math.min(next[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));prev=next;}return prev[b.length()]<=distance;}
 public Component personalized(ServerPlayer p,PlayerChatMessage message){
  String raw=message.decoratedContent().getString();String target=name(p);Pattern pattern=Pattern.compile("(?<![A-Za-z0-9_])@"+Pattern.quote(target)+"(?![A-Za-z0-9_])");var matcher=pattern.matcher(raw);var out=Component.empty();int start=0;while(matcher.find()){out.append(Component.literal(raw.substring(start,matcher.start())));out.append(Component.literal(matcher.group()).withStyle(ChatFormatting.YELLOW));start=matcher.end();}out.append(Component.literal(raw.substring(start)));return out;
 }
 public String prefix(ServerPlayer p){if(!FabricLoader.getInstance().isModLoaded("luckperms"))return "";try{
  Object lp=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(lp);Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,p.getUUID());if(user==null)return "";Object data=Class.forName("net.luckperms.api.model.PermissionHolder").getMethod("getCachedData").invoke(user);Object meta=Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("getMetaData").invoke(data);Object prefix=Class.forName("net.luckperms.api.cacheddata.CachedMetaData").getMethod("getPrefix").invoke(meta);return prefix==null?"":prefix.toString();
 }catch(ReflectiveOperationException e){return "";}}
 public void mention(ServerPlayer recipient,PlayerChatMessage message){if(!store.pref(recipient.getUUID()).mentions||!store.config.mentionSound||!containsMention(message.signedBody().content(),name(recipient)))return;recipient.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(net.minecraft.resources.Identifier.parse(store.config.mentionSoundId))),net.minecraft.sounds.SoundSource.PLAYERS,recipient.getX(),recipient.getY(),recipient.getZ(),store.config.mentionVolume,store.config.mentionPitch,0));}
 public static boolean containsMention(String text,String name){return Pattern.compile("(?<![A-Za-z0-9_])@"+Pattern.quote(name)+"(?![A-Za-z0-9_])").matcher(text).find();}
}
