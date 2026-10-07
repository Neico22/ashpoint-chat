package uk.ashpoint.chat.mixin;
import uk.ashpoint.chat.ChatMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Per-recipient filtering leaves signatures and global chat hooks intact. */
@Mixin(ServerPlayer.class)
public abstract class ChatDeliveryMixin {
 @Inject(method="sendChatMessage",at=@At("HEAD"),cancellable=true)
 private void ashpoint$deliver(OutgoingChatMessage outgoing,boolean filter,ChatType.Bound type,CallbackInfo ci){
  if(ChatMod.INSTANCE==null||ChatMod.INSTANCE.store==null)return;
  ChatMod mod=ChatMod.INSTANCE;
  var generated=mod.generatedChat(type);
  PlayerChatMessage message;
  ServerPlayer sender;
  if(outgoing instanceof OutgoingChatMessage.Player player){message=player.message();sender=mod.server.getPlayerList().getPlayer(message.sender());}
  else if(outgoing instanceof OutgoingChatMessage.Disguised&&generated!=null){message=generated.message();sender=generated.sender();}
  else return;
  ServerPlayer recipient=(ServerPlayer)(Object)this;
  if(mod.ignored(recipient.getUUID(),generated!=null?generated.sender().getUUID():message.sender())){ci.cancel();return;}
  if(recipient.getChatVisibility()!=net.minecraft.world.entity.player.ChatVisiblity.FULL||message.filter(filter).isFullyFiltered())return;
  mod.mention(recipient,message);
  boolean mentioned=mod.store.pref(recipient.getUUID()).mentions&&ChatMod.containsMention(message.signedBody().content(),mod.name(recipient));
  if(mod.store.config.formatting){
   if(sender!=null){Component rendered=Component.literal(ChatMod.format(mod.store.config.publicFormat,"sender",mod.name(sender),"prefix",mod.prefix(sender),"message",message.decoratedContent().getString()));recipient.sendSystemMessage(mentioned?mod.personalized(recipient,message.withUnsignedContent(rendered)):rendered);ci.cancel();return;}
  }
  if(mentioned){OutgoingChatMessage personalized=generated!=null?new OutgoingChatMessage.Disguised(mod.personalized(recipient,message)):new OutgoingChatMessage.Player(message.withUnsignedContent(mod.personalized(recipient,message)));personalized.sendToPlayer(recipient,filter,type);ci.cancel();}
 }
}
