package xyz.thelegacyvoyage.hyessentialsx.commands.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.ui.hologram.HologramEditorPage;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.GifManager;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import xyz.thelegacyvoyage.hyessentialsx.util.HologramPermissionUtil;
import xyz.thelegacyvoyage.hyessentialsx.util.Messages;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HologramCommand extends AbstractCommand {
   private static final String COLOR_GOLD = "#FFAA00";
   private static final String COLOR_YELLOW = "#FFFF55";
   private static final String COLOR_GRAY = "#AAAAAA";
   private static final String COLOR_WHITE = "#FFFFFF";
   private static final String COLOR_GREEN = "#55FF55";
   private static final String COLOR_RED = "#FF5555";
   private static final String COLOR_DARK_GRAY = "#555555";
   @Nonnull
   private final HologramService plugin;

   public HologramCommand(@Nonnull HologramService plugin) {
      super("hologram", "Manage holograms");
      this.plugin = plugin;
      this.addAliases(new String[]{"holo", "hg"});
      this.addSubCommand(new HologramCommand.CreateCommand(plugin));
      this.addSubCommand(new HologramCommand.DeleteCommand(plugin));
      this.addSubCommand(new HologramCommand.EditCommand(plugin));
      this.addSubCommand(new HologramCommand.ListCommand(plugin));
      this.addSubCommand(new HologramCommand.MoveToCommand(plugin));
      this.addSubCommand(new HologramCommand.MoveHereCommand(plugin));
      this.addSubCommand(new HologramCommand.AddLineCommand(plugin));
      this.addSubCommand(new HologramCommand.SetLineCommand(plugin));
      this.addSubCommand(new HologramCommand.RemoveLineCommand(plugin));
      this.addSubCommand(new HologramCommand.InfoCommand(plugin));
      this.addSubCommand(new HologramCommand.ReloadCommand(plugin));
      this.addSubCommand(new HologramCommand.CleanupCommand(plugin));
      this.addSubCommand(new HologramCommand.AnimListCommand(plugin));
      this.addSubCommand(new HologramCommand.ListImagesCommand(plugin));
   }

   protected boolean canGeneratePermission() {
      return false;
   }

   @Nullable
   public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
      if (!this.plugin.getConfigManager().isHologramsEnabled()) {
         Messages.sendKey(context, "hologram.disabled", Map.of());
         return CompletableFuture.completedFuture(null);
      }
      if (!this.hasAnyHologramPermission(context)) {
         Messages.noPerm(context, "/holo");
         return CompletableFuture.completedFuture(null);
      } else {
         Messages.sendKey(context, "hologram.help.header", Map.of());
         Messages.sendKey(context, "hologram.help.create", Map.of());
         Messages.sendKey(context, "hologram.help.delete", Map.of());
         Messages.sendKey(context, "hologram.help.edit", Map.of());
         Messages.sendKey(context, "hologram.help.list", Map.of());
         Messages.sendKey(context, "hologram.help.moveto", Map.of());
         Messages.sendKey(context, "hologram.help.movehere", Map.of());
         Messages.sendKey(context, "hologram.help.addline", Map.of());
         Messages.sendKey(context, "hologram.help.setline", Map.of());
         Messages.sendKey(context, "hologram.help.removeline", Map.of());
         Messages.sendKey(context, "hologram.help.info", Map.of());
         Messages.sendKey(context, "hologram.help.reload", Map.of());
         Messages.sendKey(context, "hologram.help.cleanup", Map.of());
         Messages.sendKey(context, "hologram.help.spacer", Map.of());
         Messages.sendKey(context, "hologram.help.anim.header", Map.of());
         Messages.sendKey(context, "hologram.help.anim.list", Map.of());
         Messages.sendKey(context, "hologram.help.anim.usage", Map.of());
         Messages.sendKey(context, "hologram.help.spacer", Map.of());
         Messages.sendKey(context, "hologram.help.formats.header", Map.of());
         Messages.sendKey(context, "hologram.help.formats.item", Map.of());
         Messages.sendKey(context, "hologram.help.formats.item_scale", Map.of());
         Messages.sendKey(context, "hologram.help.formats.image", Map.of());
         Messages.sendKey(context, "hologram.help.formats.image_scale", Map.of());
         Messages.sendKey(context, "hologram.help.formats.image_billboard", Map.of());
         Messages.sendKey(context, "hologram.help.formats.image_billboard_dist", Map.of());
         Messages.sendKey(context, "hologram.help.spacer", Map.of());
         Messages.sendKey(context, "hologram.help.tip", Map.of());
         return CompletableFuture.completedFuture(null);
      }
   }

   private boolean hasAnyHologramPermission(@Nonnull CommandContext context) {
      return HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_ADMIN)
              || HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_CREATE)
              || HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_DELETE)
              || HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_EDIT)
              || HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_LIST)
              || HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_MOVE)
              || HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_RELOAD)
              || HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_CLEANUP);
   }

   private static class CreateCommand extends AbstractCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;

      public CreateCommand(@Nonnull HologramService plugin) {
         super("create", "Create a new hologram at your location");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name for the hologram", ArgTypes.STRING);
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_CREATE)) {
            Messages.noPerm(context, "/holo create");
            return CompletableFuture.completedFuture(null);
         } else {
            String name = (String)context.get(this.nameArg);
            if (!context.isPlayer()) {
               Messages.errKey(context, "error.player_only", Map.of());
               return CompletableFuture.completedFuture(null);
            } else {
               Player player = (Player)context.senderAs(Player.class);
               int maxName = this.plugin.getConfigManager().getHologramMaxNameLength();
               if (name.length() > maxName) {
                  Messages.errKey(context, "hologram.name_too_long", Map.of("max", String.valueOf(maxName)));
                  return CompletableFuture.completedFuture(null);
               }
               if (this.plugin.getHologramManager().hologramExists(name)) {
                  Messages.errKey(context, "hologram.exists", Map.of("name", name));
                  return CompletableFuture.completedFuture(null);
               } else {
                  World world = player.getWorld();
                  if (world == null) {
                     Messages.err(context, "World not available.");
                     return CompletableFuture.completedFuture(null);
                  }
                  CompletableFuture<Void> future = new CompletableFuture<>();
                  world.execute(() -> {
                     try {
                        TransformComponent transform = player.getTransformComponent();
                        if (transform == null || transform.getPosition() == null) {
                           Messages.err(context, "Could not read your position.");
                           future.complete(null);
                           return;
                        }
                        Vector3d playerPos = transform.getPosition();
                        Vector3f rotation = transform.getRotation();
                        double yawRadians = Math.toRadians((double)rotation.getYaw());
                        double distance = 3.0D;
                        double offsetX = -Math.sin(yawRadians) * distance;
                        double offsetZ = Math.cos(yawRadians) * distance;
                        Vec3d position = new Vec3d(playerPos.getX() + offsetX, playerPos.getY() + 1.5D, playerPos.getZ() + offsetZ);
                        this.plugin.getHologramManager().createHologram(name, position, world.getWorldConfig().getUuid(), player.getUuid());
                        Messages.sendKey(context, "hologram.created", Map.of("name", name));
                        Messages.sendKey(context, "hologram.created.position", Map.of(
                                "x", String.format("%.1f", position.x()),
                                "y", String.format("%.1f", position.y()),
                                "z", String.format("%.1f", position.z())
                        ));
                        Messages.sendKey(context, "hologram.created.tip", Map.of("name", name));
                     } catch (Exception var16) {
                        Messages.errKey(context, "hologram.create_failed", Map.of("error", var16.getMessage()));
                     } finally {
                        future.complete(null);
                     }
                  });
                  return future;
               }
            }
         }
      }
   }

   private static class DeleteCommand extends AbstractCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;

      public DeleteCommand(@Nonnull HologramService plugin) {
         super("delete", "Delete a hologram");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram to delete", ArgTypes.STRING);
         this.addAliases(new String[]{"remove", "del"});
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_DELETE)) {
            Messages.noPerm(context, "/holo delete");
            return CompletableFuture.completedFuture(null);
         } else {
            String name = (String)context.get(this.nameArg);
            if (this.plugin.getHologramManager().deleteHologram(name)) {
               Messages.sendKey(context, "hologram.deleted", Map.of("name", name));
            } else {
               Messages.errKey(context, "hologram.not_found", Map.of("name", name));
            }

            return CompletableFuture.completedFuture(null);
         }
      }
   }

   private static class EditCommand extends AbstractPlayerCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;

      public EditCommand(@Nonnull HologramService plugin) {
         super("edit", "Open the hologram editor GUI");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram to edit", ArgTypes.STRING);
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_EDIT)) {
            Messages.noPerm(context, "/holo edit");
         } else {
            String name = (String)context.get(this.nameArg);
            Hologram hologram = this.plugin.getHologramManager().getHologram(name);
            if (hologram == null) {
               Messages.errKey(context, "hologram.not_found", Map.of("name", name));
            } else {
               Player player = (Player)store.getComponent(ref, Player.getComponentType());
               if (player == null) {
                  Messages.errKey(context, "hologram.player_missing", Map.of());
               } else {
                  HologramEditorPage editorPage = new HologramEditorPage(playerRef, this.plugin, hologram);
                  player.getPageManager().openCustomPage(ref, store, editorPage);
                  Messages.sendKey(context, "hologram.editor_opened", Map.of("name", name));
               }
            }
         }
      }
   }

   private static class ListCommand extends AbstractCommand {
      private final HologramService plugin;

      public ListCommand(@Nonnull HologramService plugin) {
         super("list", "List all holograms");
         this.plugin = plugin;
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_LIST)) {
            Messages.noPerm(context, "/holo list");
            return CompletableFuture.completedFuture(null);
         } else {
            Collection<Hologram> holograms = this.plugin.getHologramManager().getAllHolograms();
            if (holograms.isEmpty()) {
               Messages.sendKey(context, "hologram.none", Map.of());
               return CompletableFuture.completedFuture(null);
            } else {
               Messages.sendKey(context, "hologram.list.header", Map.of("count", String.valueOf(holograms.size())));
               Iterator var3 = holograms.iterator();

               while(var3.hasNext()) {
                  Hologram hologram = (Hologram)var3.next();
                  Vec3d pos = hologram.getPosition();
                  Messages.sendKey(context, "hologram.list.entry", Map.of(
                          "name", hologram.getName(),
                          "lines", String.valueOf(hologram.getLineCount()),
                          "x", String.format("%.1f", pos.x()),
                          "y", String.format("%.1f", pos.y()),
                          "z", String.format("%.1f", pos.z())
                  ));
               }

               return CompletableFuture.completedFuture(null);
            }
         }
      }
   }

   private static class MoveToCommand extends AbstractPlayerCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;

      public MoveToCommand(@Nonnull HologramService plugin) {
         super("moveto", "Teleport to a hologram");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram", ArgTypes.STRING);
         this.addAliases(new String[]{"tp", "teleport", "goto"});
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_MOVE)) {
            Messages.noPerm(context, "/holo moveto");
         } else {
            String name = (String)context.get(this.nameArg);
            Hologram hologram = this.plugin.getHologramManager().getHologram(name);
            if (hologram == null) {
               Messages.errKey(context, "hologram.not_found", Map.of("name", name));
            } else {
               Vec3d holoPos = hologram.getPosition();
               TransformComponent transformComponent = (TransformComponent)store.getComponent(ref, TransformComponent.getComponentType());
               if (transformComponent == null) {
                  Messages.errKey(context, "hologram.player_transform_missing", Map.of());
               } else {
                  Vector3f currentRotation = transformComponent.getRotation();
                  Vector3d targetPos = new Vector3d(holoPos.x(), holoPos.y(), holoPos.z());
                  Teleport teleport = Teleport.createForPlayer(targetPos, currentRotation);
                  store.addComponent(ref, Teleport.getComponentType(), teleport);
                  Messages.sendKey(context, "hologram.teleported", Map.of("name", name));
               }
            }
         }
      }
   }

   private static class MoveHereCommand extends AbstractCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;

      public MoveHereCommand(@Nonnull HologramService plugin) {
         super("movehere", "Move a hologram to your location");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram to move", ArgTypes.STRING);
         this.addAliases(new String[]{"move"});
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_MOVE)) {
            Messages.noPerm(context, "/holo movehere");
            return CompletableFuture.completedFuture(null);
         } else {
            String name = (String)context.get(this.nameArg);
            if (!context.isPlayer()) {
               Messages.errKey(context, "error.player_only", Map.of());
               return CompletableFuture.completedFuture(null);
            } else {
               Hologram hologram = this.plugin.getHologramManager().getHologram(name);
               if (hologram == null) {
                  Messages.errKey(context, "hologram.not_found", Map.of("name", name));
                  return CompletableFuture.completedFuture(null);
               } else {
                  Player player = (Player)context.senderAs(Player.class);
                  World world = player.getWorld();
                  if (world == null) {
                     Messages.err(context, "World not available.");
                     return CompletableFuture.completedFuture(null);
                  }
                  CompletableFuture<Void> future = new CompletableFuture<>();
                  world.execute(() -> {
                     TransformComponent transform = player.getTransformComponent();
                     if (transform == null || transform.getPosition() == null) {
                        Messages.err(context, "Could not read your position.");
                        future.complete(null);
                        return;
                     }
                     Vector3d playerPos = transform.getPosition();
                     Vec3d position = new Vec3d(playerPos.getX(), playerPos.getY() + 2.5D, playerPos.getZ());
                     this.plugin.getHologramManager().moveHologram(hologram, position);
                     Messages.sendKey(context, "hologram.moved", Map.of("name", name));
                     future.complete(null);
                  });
                  return future;
               }
            }
         }
      }
   }

   private static class AddLineCommand extends AbstractCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;
      private final RequiredArg<String> textArg;

      public AddLineCommand(@Nonnull HologramService plugin) {
         super("addline", "Add a line to a hologram");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram", ArgTypes.STRING);
         this.textArg = this.withRequiredArg("text", "Text to add", ArgTypes.STRING);
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_EDIT)) {
            Messages.noPerm(context, "/holo addline");
            return CompletableFuture.completedFuture(null);
         } else {
            String name = (String)context.get(this.nameArg);
            String text = (String)context.get(this.textArg);
            Hologram hologram = this.plugin.getHologramManager().getHologram(name);
            if (hologram == null) {
               Messages.errKey(context, "hologram.not_found", Map.of("name", name));
               return CompletableFuture.completedFuture(null);
            } else {
               int maxLines = this.plugin.getConfigManager().getHologramMaxLines();
               if (hologram.getLineCount() >= maxLines) {
                  Messages.errKey(context, "hologram.lines_max", Map.of("max", String.valueOf(maxLines)));
                  return CompletableFuture.completedFuture(null);
               }
               int maxLength = this.plugin.getConfigManager().getHologramMaxLineLength();
               if (text.length() > maxLength) {
                  Messages.errKey(context, "hologram.line_too_long", Map.of("max", String.valueOf(maxLength)));
                  return CompletableFuture.completedFuture(null);
               }
               hologram.addLine(text);
               this.plugin.getHologramManager().updateHologram(hologram);
               Messages.sendKey(context, "hologram.line_added", Map.of("name", name));
               return CompletableFuture.completedFuture(null);
            }
         }
      }
   }

   private static class SetLineCommand extends AbstractCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;
      private final RequiredArg<Integer> lineArg;
      private final RequiredArg<String> textArg;

      public SetLineCommand(@Nonnull HologramService plugin) {
         super("setline", "Set a specific line of a hologram");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram", ArgTypes.STRING);
         this.lineArg = this.withRequiredArg("line", "Line number (1-based)", ArgTypes.INTEGER);
         this.textArg = this.withRequiredArg("text", "New text for the line", ArgTypes.STRING);
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_EDIT)) {
            Messages.noPerm(context, "/holo setline");
            return CompletableFuture.completedFuture(null);
         } else {
            String name = (String)context.get(this.nameArg);
            int lineNum = (Integer)context.get(this.lineArg) - 1;
            String text = (String)context.get(this.textArg);
            if (lineNum < 0) {
               Messages.errKey(context, "hologram.line_invalid", Map.of());
               return CompletableFuture.completedFuture(null);
            } else {
               Hologram hologram = this.plugin.getHologramManager().getHologram(name);
               if (hologram == null) {
                  Messages.errKey(context, "hologram.not_found", Map.of("name", name));
                  return CompletableFuture.completedFuture(null);
               } else if (lineNum >= hologram.getLineCount()) {
                  Messages.errKey(context, "hologram.line_out_of_range", Map.of("max", String.valueOf(hologram.getLineCount())));
                  return CompletableFuture.completedFuture(null);
               } else {
                  int maxLength = this.plugin.getConfigManager().getHologramMaxLineLength();
                  if (text.length() > maxLength) {
                     Messages.errKey(context, "hologram.line_too_long", Map.of("max", String.valueOf(maxLength)));
                     return CompletableFuture.completedFuture(null);
                  }
                  hologram.setLine(lineNum, text);
                  this.plugin.getHologramManager().updateHologram(hologram);
                  Messages.sendKey(context, "hologram.line_updated", Map.of("name", name, "line", String.valueOf(lineNum + 1)));
                  return CompletableFuture.completedFuture(null);
               }
            }
         }
      }
   }

   private static class RemoveLineCommand extends AbstractCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;
      private final RequiredArg<Integer> lineArg;

      public RemoveLineCommand(@Nonnull HologramService plugin) {
         super("removeline", "Remove a line from a hologram");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram", ArgTypes.STRING);
         this.lineArg = this.withRequiredArg("line", "Line number to remove (1-based)", ArgTypes.INTEGER);
         this.addAliases(new String[]{"delline"});
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_EDIT)) {
            Messages.noPerm(context, "/holo removeline");
            return CompletableFuture.completedFuture(null);
         } else {
            String name = (String)context.get(this.nameArg);
            int lineNum = (Integer)context.get(this.lineArg) - 1;
            if (lineNum < 0) {
               Messages.errKey(context, "hologram.line_invalid", Map.of());
               return CompletableFuture.completedFuture(null);
            } else {
               Hologram hologram = this.plugin.getHologramManager().getHologram(name);
               if (hologram == null) {
                  Messages.errKey(context, "hologram.not_found", Map.of("name", name));
                  return CompletableFuture.completedFuture(null);
               } else if (lineNum >= hologram.getLineCount()) {
                  Messages.errKey(context, "hologram.line_out_of_range", Map.of("max", String.valueOf(hologram.getLineCount())));
                  return CompletableFuture.completedFuture(null);
               } else {
                  hologram.removeLine(lineNum);
                  this.plugin.getHologramManager().updateHologram(hologram);
                  Messages.sendKey(context, "hologram.line_removed", Map.of("name", name, "line", String.valueOf(lineNum + 1)));
                  return CompletableFuture.completedFuture(null);
               }
            }
         }
      }
   }

   private static class InfoCommand extends AbstractCommand {
      private final HologramService plugin;
      private final RequiredArg<String> nameArg;

      public InfoCommand(@Nonnull HologramService plugin) {
         super("info", "Show information about a hologram");
         this.plugin = plugin;
         this.nameArg = this.withRequiredArg("name", "Name of the hologram", ArgTypes.STRING);
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_LIST)) {
            Messages.noPerm(context, "/holo info");
            return CompletableFuture.completedFuture(null);
         } else {
            String name = (String)context.get(this.nameArg);
            Hologram hologram = this.plugin.getHologramManager().getHologram(name);
            if (hologram == null) {
               Messages.errKey(context, "hologram.not_found", Map.of("name", name));
               return CompletableFuture.completedFuture(null);
            } else {
               Vec3d pos = hologram.getPosition();
               Messages.sendKey(context, "hologram.info.header", Map.of("name", hologram.getName()));
               Messages.sendKey(context, "hologram.info.id", Map.of("id", hologram.getId().toString()));
               Messages.sendKey(context, "hologram.info.position", Map.of(
                       "x", String.format("%.2f", pos.x()),
                       "y", String.format("%.2f", pos.y()),
                       "z", String.format("%.2f", pos.z())
               ));
               Messages.sendKey(context, "hologram.info.world", Map.of("world", hologram.getWorldId().toString()));
               Messages.sendKey(context, "hologram.info.visible", Map.of("visible", String.valueOf(hologram.isVisible())));
               Messages.sendKey(context, "hologram.info.lines_header", Map.of("count", String.valueOf(hologram.getLineCount())));
               List<String> lines = hologram.getLines();

               for(int i = 0; i < lines.size(); ++i) {
                  Messages.sendKey(context, "hologram.info.line_entry", Map.of(
                          "line", String.valueOf(i + 1),
                          "text", lines.get(i)
                  ));
               }

               return CompletableFuture.completedFuture(null);
            }
         }
      }
   }

   private static class ReloadCommand extends AbstractCommand {
      private final HologramService plugin;

      public ReloadCommand(@Nonnull HologramService plugin) {
         super("reload", "Reload all holograms and configuration");
         this.plugin = plugin;
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_RELOAD)) {
            Messages.noPerm(context, "/holo reload");
            return CompletableFuture.completedFuture(null);
         } else {
            Messages.sendKey(context, "hologram.reload.start", Map.of());

            try {
               this.plugin.getHologramManager().reload();
               int hologramCount = this.plugin.getHologramManager().getAllHolograms().size();
               int imageCount = this.plugin.getHologramManager().getImageManager().getAvailableImages().size();
               Map<String, GifManager.GifData> gifData = this.plugin.getHologramManager().getGifManager().getAllGifData();
               int gifCount = gifData.size();
               this.plugin.getLogger().at(Level.INFO).log("HologramService reloaded successfully!");
               this.plugin.getLogger().at(Level.INFO).log("  • Holograms: " + hologramCount);
               this.plugin.getLogger().at(Level.INFO).log("  • Images: " + imageCount);
               this.plugin.getLogger().at(Level.INFO).log("  • GIFs: " + gifCount);
               Iterator var6 = gifData.entrySet().iterator();

               Entry entry;
               String gifName;
               int frameCount;
               while(var6.hasNext()) {
                  entry = (Entry)var6.next();
                  gifName = (String)entry.getKey();
                  frameCount = ((GifManager.GifData)entry.getValue()).frameCount;
                  this.plugin.getLogger().at(Level.INFO).log("    - " + gifName + ": " + frameCount + " frames");
               }

               Messages.sendKey(context, "hologram.reload.done", Map.of());
               Messages.sendKey(context, "hologram.reload.counts", Map.of(
                       "holograms", String.valueOf(hologramCount),
                       "images", String.valueOf(imageCount),
                       "gifs", String.valueOf(gifCount)
               ));
               var6 = gifData.entrySet().iterator();

               while(var6.hasNext()) {
                  entry = (Entry)var6.next();
                  gifName = (String)entry.getKey();
                  frameCount = ((GifManager.GifData)entry.getValue()).frameCount;
                  Messages.sendKey(context, "hologram.reload.gif_entry", Map.of(
                          "name", gifName,
                          "frames", String.valueOf(frameCount)
                  ));
               }
            } catch (Exception var10) {
               Messages.errKey(context, "hologram.reload.failed", Map.of("error", var10.getMessage()));
            }

            return CompletableFuture.completedFuture(null);
         }
      }
   }

   private static class CleanupCommand extends AbstractCommand {
      private final HologramService plugin;

      public CleanupCommand(@Nonnull HologramService plugin) {
         super("cleanup", "Remove orphaned hologram entities");
         this.plugin = plugin;
         this.addAliases(new String[]{"clean", "purge"});
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_CLEANUP)) {
            Messages.noPerm(context, "/holo cleanup");
            return CompletableFuture.completedFuture(null);
         } else {
            Messages.sendKey(context, "hologram.cleanup.start", Map.of());

            try {
               int removed = this.plugin.getHologramManager().cleanupOrphanedEntities();
               if (removed > 0) {
                  Messages.sendKey(context, "hologram.cleanup.removed", Map.of("count", String.valueOf(removed)));
               } else {
                  Messages.sendKey(context, "hologram.cleanup.none", Map.of());
               }
            } catch (Exception var3) {
               Messages.errKey(context, "hologram.cleanup.failed", Map.of("error", var3.getMessage()));
            }

            return CompletableFuture.completedFuture(null);
         }
      }
   }

   private static class AnimListCommand extends AbstractCommand {
      private final HologramService plugin;

      public AnimListCommand(@Nonnull HologramService plugin) {
         super("animlist", "List available animations");
         this.plugin = plugin;
         this.addAliases(new String[]{"animations", "anims"});
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         Messages.sendKey(context, "hologram.anim.header", Map.of());
         Messages.sendKey(context, "hologram.anim.floating", Map.of());
         Messages.sendKey(context, "hologram.anim.spinning", Map.of());
         Messages.sendKey(context, "hologram.anim.pulsing", Map.of());
         Messages.sendKey(context, "hologram.anim.bouncing", Map.of());
         Messages.sendKey(context, "hologram.anim.swaying", Map.of());
         Messages.sendKey(context, "hologram.anim.wobbling", Map.of());
         Messages.sendKey(context, "hologram.anim.shaking", Map.of());
         Messages.sendKey(context, "hologram.anim.orbiting", Map.of());
         Messages.sendKey(context, "hologram.anim.other", Map.of());
         Messages.sendKey(context, "hologram.help.spacer", Map.of());
         Messages.sendKey(context, "hologram.anim.usage", Map.of());
         Messages.sendKey(context, "hologram.anim.example1", Map.of());
         Messages.sendKey(context, "hologram.anim.example2", Map.of());
         return CompletableFuture.completedFuture(null);
      }
   }

   private static class ListImagesCommand extends AbstractCommand {
      private final HologramService plugin;

      public ListImagesCommand(@Nonnull HologramService plugin) {
         super("listimages", "List available hologram images");
         this.plugin = plugin;
      }

      protected boolean canGeneratePermission() {
         return false;
      }

      @Nullable
      public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
         if (!HologramPermissionUtil.hasPermission(context.sender(), HologramPermissionUtil.PERMISSION_LIST)) {
            Messages.noPerm(context, "/holo listimages");
            return CompletableFuture.completedFuture(null);
         }
         Set<String> images = this.plugin.getHologramManager().getImageManager().getAvailableImages();
         if (images.isEmpty()) {
            Messages.ok(context, "No images found. Add PNG/JPG files to the holograms/images folder, then /holo reload and restart.");
            return CompletableFuture.completedFuture(null);
         }
         List<String> sorted = new ArrayList<>(images);
         Collections.sort(sorted);
         Messages.ok(context, "Images (" + sorted.size() + "): " + String.join(", ", sorted));
         return CompletableFuture.completedFuture(null);
      }
   }
}



