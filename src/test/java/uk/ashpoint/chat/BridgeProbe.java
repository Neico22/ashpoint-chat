package uk.ashpoint.chat;
import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/** Runs the unmodified production Bridge listener and formatter. Only the Discord
 * HTTP boundary is replaced, so tests never need a token or post to live channels. */
final class BridgeProbe {
 record Delivery(int guild,String channel,String username,String content,String avatar,boolean mentionsDisabled) {}
 final List<Delivery> deliveries=new java.util.concurrent.CopyOnWriteArrayList<>();
 final Object entry,bridge;
 static Object get(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 static void set(Object object,String name,Object value)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 static Object construct(Class<?> type,Object... args)throws Exception{for(var c:type.getDeclaredConstructors())if(c.getParameterCount()==args.length){c.setAccessible(true);return c.newInstance(args);}throw new IllegalArgumentException(type.getName());}
 BridgeProbe(MinecraftServer server)throws Exception{
  entry=FabricLoader.getInstance().getEntrypointContainers("main",net.fabricmc.api.ModInitializer.class).stream().filter(e->e.getProvider().getMetadata().getId().equals("ashpoint_bridge")).findFirst().orElseThrow().getEntrypoint();
  ClassLoader loader=entry.getClass().getClassLoader();
  Object config=construct(Class.forName("dev.ashpoint.bridge.BridgeConfig",true,loader));
  Class<?> guildClass=Class.forName("dev.ashpoint.bridge.BridgeConfig$GuildConfig",true,loader);
  List<Object> guilds=new ArrayList<>();
  for(int i=1;i<=2;i++){var guild=construct(guildClass);set(guild,"guildId",""+(100*i));set(guild,"chatChannelId",""+(100*i+1));set(guild,"staffChannelId",""+(100*i+2));set(guild,"neicosChannelId",""+(100*i+3));guilds.add(guild);}
  set(config,"guilds",guilds);
  bridge=construct(Class.forName("dev.ashpoint.bridge.DiscordBridge",true,loader),server,config,null);
  List<?> routes=(List<?>)get(bridge,"routes");
  for(int i=0;i<routes.size();i++){Object route=routes.get(i);set(route,"chatWebhook",hook(loader,i,"chat"));set(route,"staffWebhook",hook(loader,i,"console"));}
  set(bridge,"activated",true);set(entry,"discord",bridge);
 }
 Object hook(ClassLoader loader,int guild,String channel)throws Exception{
  Class<?> hook=Class.forName("dev.ashpoint.bridge.libs.jda.api.entities.Webhook",true,loader);
  Class<?> action=Class.forName("dev.ashpoint.bridge.libs.jda.api.requests.restaction.WebhookMessageCreateAction",true,loader);
  return Proxy.newProxyInstance(loader,new Class[]{hook},(proxy,method,args)->{
   if(method.getName().equals("sendMessage")){
    String content=args[0].toString();String[] name={null},avatar={null};boolean[] disabled={false};
    return Proxy.newProxyInstance(loader,new Class[]{action},(self,m,a)->{
     switch(m.getName()){
      case "setUsername" -> name[0]=(String)a[0];
      case "setAvatarUrl" -> avatar[0]=(String)a[0];
      case "setAllowedMentions" -> disabled[0]=((Collection<?>)a[0]).isEmpty();
      case "queue" -> {deliveries.add(new Delivery(guild,channel,name[0],content,avatar[0],disabled[0]));return null;}
      case "toString" -> {return "captured webhook action";}
     }
     if(m.getReturnType().isInstance(self))return self;
     return null;
    });
   }
   return null;
  });
 }
 long count(String content){return deliveries.stream().filter(d->d.channel.equals("chat")&&d.guild==0&&d.content.equals(content)).count();}
 boolean correct(String content,String name,UUID id){return deliveries.stream().filter(d->d.content.equals(content)).allMatch(d->d.username.equals(name)&&d.avatar.contains(id.toString())&&d.mentionsDisabled);}
 boolean eachOnce(String content){return deliveries.stream().filter(d->d.content.equals(content)).count()==4&&count(content)==1&&deliveries.stream().filter(d->d.content.equals(content)).map(d->d.guild+":"+d.channel).distinct().count()==4;}
 void close()throws Exception{set(entry,"discord",null);}
}
