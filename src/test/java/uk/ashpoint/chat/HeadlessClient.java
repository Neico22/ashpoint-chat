package uk.ashpoint.chat;
import java.net.*;import java.io.*;import java.util.*;import java.util.concurrent.*;
import io.netty.buffer.*;
import net.minecraft.network.*;import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.handshake.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;

/** Vanilla protocol socket client; no client mod and no injected server player. */
final class HeadlessClient implements AutoCloseable {
 final String name;final MinecraftServer server;final Socket socket=new Socket();
 volatile boolean playing;volatile Throwable failure;
 ProtocolInfo<?> outgoing=HandshakeProtocols.SERVERBOUND,incoming=LoginProtocols.CLIENTBOUND;
 final List<Packet<?>> received=new CopyOnWriteArrayList<>();
 HeadlessClient(MinecraftServer s,String name){this.server=s;this.name=name;Thread.ofPlatform().daemon().start(()->{try{socket.connect(new InetSocketAddress("127.0.0.1",25579));socket.setSoTimeout(60000);
  send(new ClientIntentionPacket(net.minecraft.SharedConstants.getCurrentVersion().protocolVersion(),"localhost",25579,ClientIntent.LOGIN));outgoing=LoginProtocols.SERVERBOUND;send(new ServerboundHelloPacket(name,UUID.randomUUID()));
  while(!socket.isClosed()){int len=readVarInt(socket.getInputStream());byte[] data=socket.getInputStream().readNBytes(len);if(data.length!=len)throw new EOFException();ByteBuf buf=Unpooled.wrappedBuffer(data);Packet<?> packet=(Packet<?>)incoming.codec().decode(buf);buf.release();received.add(packet);handle(packet);}
 }catch(Throwable e){if(!socket.isClosed()){failure=e;e.printStackTrace();}}});}
 @SuppressWarnings({"rawtypes","unchecked"}) synchronized void send(Packet<?> p)throws IOException{ByteBuf buf=Unpooled.buffer();try{((net.minecraft.network.codec.StreamCodec)outgoing.codec()).encode(buf,p);writeVarInt(socket.getOutputStream(),buf.readableBytes());byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);socket.getOutputStream().write(bytes);socket.getOutputStream().flush();}finally{buf.release();}}
 void handle(Packet<?> p)throws IOException{
  System.out.println("WIRE "+name+" received "+p.type());
  if(p instanceof ClientboundCustomQueryPacket query)send(new ServerboundCustomQueryAnswerPacket(query.transactionId(),null));
  if(p instanceof net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket cookie)send(new net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket(cookie.key(),null));
  if(p instanceof ClientboundPingPacket ping)send(new ServerboundPongPacket(ping.getId()));
  if(p instanceof ClientboundLoginCompressionPacket)throw new IOException("Test server must disable compression");
  if(p instanceof ClientboundLoginFinishedPacket){send(ServerboundLoginAcknowledgedPacket.INSTANCE);outgoing=ConfigurationProtocols.SERVERBOUND;incoming=ConfigurationProtocols.CLIENTBOUND;send(new ServerboundClientInformationPacket(net.minecraft.server.level.ClientInformation.createDefault()));}
  if(p instanceof ClientboundSelectKnownPacks)send(new ServerboundSelectKnownPacks(List.of()));
  if(p instanceof ClientboundFinishConfigurationPacket){send(ServerboundFinishConfigurationPacket.INSTANCE);outgoing=GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess()),new GameProtocols.Context(){public boolean hasInfiniteMaterials(){return false;}public boolean canUseCommandBlocks(){return false;}});incoming=GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess()));}
  if(p instanceof ClientboundLoginPacket){playing=true;send(new ServerboundPlayerLoadedPacket());}
  if(p instanceof ClientboundPlayerPositionPacket pos){var change=pos.change();var xyz=change.position();send(new ServerboundAcceptTeleportationPacket(pos.id(),xyz.x,xyz.y,xyz.z,change.yRot(),change.xRot()));}
  if(p instanceof ClientboundKeepAlivePacket alive)send(new ServerboundKeepAlivePacket(alive.getId()));
  if(p instanceof ClientboundLoginDisconnectPacket||p instanceof ClientboundDisconnectPacket)throw new IOException("Disconnected: "+p);
 }
 void command(String command)throws IOException{send(new ServerboundChatCommandPacket(command));}
 void chat(String message)throws IOException{send(new ServerboundChatPacket(message,java.time.Instant.now(),0,Optional.empty(),new net.minecraft.network.chat.LastSeenMessages.Update(0,new BitSet(),net.minecraft.network.chat.LastSeenMessages.Update.IGNORE_CHECKSUM)));}
 long chatCount(){return received.stream().filter(p->p instanceof ClientboundPlayerChatPacket||p instanceof ClientboundDisguisedChatPacket).count();}
 long systemCount(){return received.stream().filter(p->p instanceof ClientboundSystemChatPacket).count();}
 static int readVarInt(InputStream in)throws IOException{int result=0;for(int i=0;i<5;i++){int b=in.read();if(b<0)throw new EOFException();result|=(b&127)<<(i*7);if((b&128)==0)return result;}throw new IOException("Malformed VarInt");}
 static void writeVarInt(OutputStream out,int value)throws IOException{while((value&~127)!=0){out.write((value&127)|128);value>>>=7;}out.write(value);}
 public void close()throws IOException{socket.close();}
}
