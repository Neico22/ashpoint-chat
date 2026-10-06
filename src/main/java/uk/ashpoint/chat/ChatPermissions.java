package uk.ashpoint.chat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.*;
/** Uses LuckPerms API when available; explicit denials are respected, including for operators. */
public final class ChatPermissions {
 public static boolean has(CommandSourceStack source,String node,boolean fallback) {
  if(source.getEntity() instanceof ServerPlayer player) {
   if(FabricLoader.getInstance().isModLoaded("luckperms"))try {
    Class<?> provider=Class.forName("net.luckperms.api.LuckPermsProvider");Object lp=provider.getMethod("get").invoke(null);
    Class<?> api=Class.forName("net.luckperms.api.LuckPerms");Object manager=api.getMethod("getUserManager").invoke(lp);
    Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",java.util.UUID.class).invoke(manager,player.getUUID());
    if(user!=null){Object data=Class.forName("net.luckperms.api.model.PermissionHolder").getMethod("getCachedData").invoke(user);
     Object perms=Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("getPermissionData").invoke(data);
     Object state=Class.forName("net.luckperms.api.cacheddata.CachedPermissionData").getMethod("checkPermission",String.class).invoke(perms,node);
     String value=state.toString();if(value.equals("TRUE"))return true;if(value.equals("FALSE"))return false;
    }
   }catch(ReflectiveOperationException e){return false;}
   return fallback||source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
  }
  return source.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
 }

}
