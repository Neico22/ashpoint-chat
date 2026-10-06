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
  if(ChatMod.INSTANCE==null||ChatMod.INSTANCE.store==null||!(outgoing instanceof OutgoingChatMessage.Player player))return;
  ServerPlayer recipient=(ServerPlayer)(Object)this;
  if(ChatMod.INSTANCE.ignored(recipient.getUUID(),player.message().sender())){ci.cancel();return;}
  if(recipient.getChatVisibility()!=net.minecraft.world.entity.player.ChatVisiblity.FULL||player.message().filter(filter).isFullyFiltered())return;
  ChatMod mod=ChatMod.INSTANCE; mod.mention(recipient,player.message());
  boolean mentioned=mod.store.pref(recipient.getUUID()).mentions&&ChatMod.containsMention(player.message().signedBody().content(),mod.name(recipient));
  if(mod.store.config.formatting){
   ServerPlayer sender=mod.server.getPlayerList().getPlayer(player.message().sender());
   if(sender!=null){Component rendered=Component.literal(ChatMod.format(mod.store.config.publicFormat,"sender",mod.name(sender),"prefix",mod.prefix(sender),"message",player.message().decoratedContent().getString()));recipient.sendSystemMessage(mentioned?mod.personalized(recipient,player.message().withUnsignedContent(rendered)):rendered);ci.cancel();return;}
  }
  if(mentioned){new OutgoingChatMessage.Player(player.message().withUnsignedContent(mod.personalized(recipient,player.message()))).sendToPlayer(recipient,filter,type);ci.cancel();}
 }
}
