package xyz.thelegacyvoyage.hyessentialsx.ui.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.FacingDirection;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import xyz.thelegacyvoyage.hyessentialsx.util.HologramPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;

public class HologramEditorPage extends InteractiveCustomUIPage<HologramEditorEventData> {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final String hologramName;
   private int editingLineIndex = -1;

   public HologramEditorPage(@Nonnull PlayerRef playerRef, @Nonnull HologramService plugin, @Nonnull Hologram hologram) {
      super(playerRef, CustomPageLifetime.CanDismiss, HologramEditorEventData.CODEC);
      this.plugin = plugin;
      this.hologramName = hologram.getName();
   }

   public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
      Hologram hologram = this.plugin.getHologramManager().getHologram(this.hologramName);
      if (hologram == null) {
         this.closePage(ref, store);
      } else {
        commandBuilder.append("hyessentialsx/HologramEditorPage.ui");
         commandBuilder.set("#HologramName.Text", hologram.getName());
         Vec3d pos = hologram.getPosition();
         commandBuilder.set("#XInput.Value", String.format("%.1f", pos.x()));
         commandBuilder.set("#YInput.Value", String.format("%.1f", pos.y()));
         commandBuilder.set("#ZInput.Value", String.format("%.1f", pos.z()));
         this.buildLinesList(hologram, commandBuilder, eventBuilder);
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AddLineButton", (new EventData()).append("Action", "addLine").append("@NewText", "#NewLineInput.Value"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#UpdateLineButton", (new EventData()).append("Action", "updateLine").append("@NewText", "#NewLineInput.Value"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelEditButton", EventData.of("Action", "cancelEdit"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#MoveHereButton", EventData.of("Action", "moveHere"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteButton", EventData.of("Action", "delete"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ExitButton", EventData.of("Action", "exit"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#XUpButton", EventData.of("Action", "posXUp"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#XDownButton", EventData.of("Action", "posXDown"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#YUpButton", EventData.of("Action", "posYUp"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#YDownButton", EventData.of("Action", "posYDown"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ZUpButton", EventData.of("Action", "posZUp"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ZDownButton", EventData.of("Action", "posZDown"));
         eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#SetPositionButton", (new EventData()).append("Action", "setPosition").append("@X", "#XInput.Value").append("@Y", "#YInput.Value").append("@Z", "#ZInput.Value"));
         this.setupDirectionButtons(hologram, commandBuilder, eventBuilder);
      }
   }

   private void setupDirectionButtons(@Nonnull Hologram hologram, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
      boolean hasImageOrGifLines = hologram.getLines().stream().anyMatch((line) -> {
         return line.toLowerCase().startsWith("image:") || line.toLowerCase().startsWith("gif:");
      });
      commandBuilder.set("#DirectionSection.Visible", hasImageOrGifLines);
      if (hasImageOrGifLines) {
         FacingDirection currentDir = hologram.getFacingDirection();
         String currentDirShort = currentDir.getShortName().toLowerCase();
         commandBuilder.set("#CurrentDirection.Text", currentDirShort.toUpperCase());
         String[][] dirButtons = new String[][]{{"#DirN", "n"}, {"#DirNE", "ne"}, {"#DirE", "e"}, {"#DirSE", "se"}, {"#DirS", "s"}, {"#DirSW", "sw"}, {"#DirW", "w"}, {"#DirNW", "nw"}};
         String[][] var8 = dirButtons;
         int var9 = dirButtons.length;

         for(int var10 = 0; var10 < var9; ++var10) {
            String[] btn = var8[var10];
            String buttonId = btn[0];
            String dir = btn[1];
            if (dir.equals(currentDirShort)) {
               commandBuilder.set(buttonId + ".Background.Color", "#22AA22");
            } else {
               commandBuilder.set(buttonId + ".Background.Color", "#333333");
            }

            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, buttonId, EventData.of("Action", "setDirection").append("Dir", dir));
         }
      }

   }

   private void buildLinesList(@Nonnull Hologram hologram, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder) {
      commandBuilder.clear("#LinesList");
      List<String> lines = hologram.getLines();
      if (lines.isEmpty()) {
         commandBuilder.appendInline("#LinesList", "Label { Text: \"No lines yet.\"; Style: (Alignment: Center, TextColor: #6e7da1); }");
      } else {
         for(int i = 0; i < lines.size(); ++i) {
            String lineText = (String)lines.get(i);
            int lineNum = i + 1;
            String selector = "#LinesList[" + i + "]";
            commandBuilder.append("#LinesList", "hyessentialsx/HologramLineItem.ui");
            commandBuilder.set(selector + " #LineNumber.Text", lineNum + ".");
            commandBuilder.set(selector + " #LineText.Text", lineText);
            commandBuilder.set(selector + " #DeleteLabel.Text", "X");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector + " #Button", EventData.of("Action", "editLine").append("LineIndex", String.valueOf(i)), false);
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector + " #LineDeleteButton", EventData.of("Action", "removeLine").append("LineIndex", String.valueOf(i)), false);
         }
      }

   }

   private String escapeText(String text) {
      return text.replace(";", ",").replace("{", "(").replace("}", ")").replace("\"", "'");
   }

   public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull HologramEditorEventData data) {
      String action = data.getAction();
      if (action != null) {
         Hologram hologram = this.plugin.getHologramManager().getHologram(this.hologramName);
         if (hologram == null) {
            this.closePage(ref, store);
         } else {
            Player player = (Player)store.getComponent(ref, Player.getComponentType());
            if (player != null) {
               byte var8 = -1;
               switch(action.hashCode()) {
               case -1557842005:
                  if (action.equals("setPosition")) {
                     var8 = 14;
                  }
                  break;
               case -1335458389:
                  if (action.equals("delete")) {
                     var8 = 3;
                  }
                  break;
               case -1148820427:
                  if (action.equals("addLine")) {
                     var8 = 0;
                  }
                  break;
               case -982478273:
                  if (action.equals("posXUp")) {
                     var8 = 8;
                  }
                  break;
               case -982477312:
                  if (action.equals("posYUp")) {
                     var8 = 10;
                  }
                  break;
               case -982476351:
                  if (action.equals("posZUp")) {
                     var8 = 12;
                  }
                  break;
               case -296169379:
                  if (action.equals("updateLine")) {
                     var8 = 6;
                  }
                  break;
               case -104779935:
                  if (action.equals("moveHere")) {
                     var8 = 2;
                  }
                  break;
               case 3127582:
                  if (action.equals("exit")) {
                     var8 = 4;
                  }
                  break;
               case 576796989:
                  if (action.equals("setDirection")) {
                     var8 = 15;
                  }
                  break;
               case 730681158:
                  if (action.equals("posXDown")) {
                     var8 = 9;
                  }
                  break;
               case 731604679:
                  if (action.equals("posYDown")) {
                     var8 = 11;
                  }
                  break;
               case 732528200:
                  if (action.equals("posZDown")) {
                     var8 = 13;
                  }
                  break;
               case 1098332824:
                  if (action.equals("removeLine")) {
                     var8 = 1;
                  }
                  break;
               case 1601797406:
                  if (action.equals("editLine")) {
                     var8 = 5;
                  }
                  break;
               case 1888175012:
                  if (action.equals("cancelEdit")) {
                     var8 = 7;
                  }
               }

               String dir;
               Vec3d pos;
               Vec3d playerPos;
               int lineIndex;
               switch(var8) {
               case 0:
                  if (!HologramPermissionUtil.hasPermission(player, "HologramService.edit")) {
                     player.sendMessage(Message.raw("You don't have permission to edit holograms!").color("#FF5555"));
                     this.refreshUI(ref, store);
                     return;
                  }

                  dir = data.getNewLineText();
                  if (dir != null && !dir.trim().isEmpty()) {
                     hologram.addLine(dir.trim());
                     this.plugin.getHologramManager().updateHologram(hologram);
                     this.plugin.getHologramManager().saveHolograms();
                     this.editingLineIndex = -1;
                  }

                  this.refreshUI(ref, store);
                  break;
               case 1:
                  if (!HologramPermissionUtil.hasPermission(player, "HologramService.delete")) {
                     player.sendMessage(Message.raw("You don't have permission to delete hologram lines!").color("#FF5555"));
                     this.refreshUI(ref, store);
                     return;
                  }

                  lineIndex = data.getLineIndexInt();
                  if (lineIndex >= 0 && lineIndex < hologram.getLines().size()) {
                     hologram.removeLine(lineIndex);
                     this.plugin.getHologramManager().updateHologram(hologram);
                     this.plugin.getHologramManager().saveHolograms();
                     this.editingLineIndex = -1;
                  }

                  this.refreshUI(ref, store);
                  break;
               case 2:
                  if (!HologramPermissionUtil.hasPermission(player, "HologramService.move")) {
                     player.sendMessage(Message.raw("You don't have permission to move holograms!").color("#FF5555"));
                     this.refreshUI(ref, store);
                     return;
                  }

                  try {
                     Vector3d transformPos = player.getTransformComponent().getPosition();
                     playerPos = new Vec3d(transformPos.getX(), transformPos.getY(), transformPos.getZ());
                     this.plugin.getHologramManager().moveHologram(hologram, playerPos);
                     this.plugin.getHologramManager().saveHolograms();
                     this.refreshUI(ref, store);
                  } catch (Exception var17) {
                     this.refreshUI(ref, store);
                  }
                  break;
               case 3:
                  if (!HologramPermissionUtil.hasPermission(player, "HologramService.delete")) {
                     player.sendMessage(Message.raw("You don't have permission to delete holograms!").color("#FF5555"));
                     this.refreshUI(ref, store);
                     return;
                  }

                  this.plugin.getHologramManager().deleteHologram(this.hologramName);
                  this.closePage(ref, store);
                  break;
               case 4:
                  this.closePage(ref, store);
                  break;
               case 5:
                  lineIndex = data.getLineIndexInt();
                  if (lineIndex >= 0 && lineIndex < hologram.getLines().size()) {
                     this.editingLineIndex = lineIndex;
                     String lineText = (String)hologram.getLines().get(lineIndex);
                     this.enterEditMode(ref, store, lineIndex, lineText);
                  }
                  break;
               case 6:
                  if (!HologramPermissionUtil.hasPermission(player, "HologramService.edit")) {
                     player.sendMessage(Message.raw("You don't have permission to edit holograms!").color("#FF5555"));
                     this.refreshUI(ref, store);
                     return;
                  }

                  dir = data.getNewLineText();
                  if (this.editingLineIndex >= 0 && this.editingLineIndex < hologram.getLines().size() && dir != null && !dir.trim().isEmpty()) {
                     hologram.setLine(this.editingLineIndex, dir.trim());
                     this.plugin.getHologramManager().updateHologram(hologram);
                     this.plugin.getHologramManager().saveHolograms();
                     this.editingLineIndex = -1;
                  }

                  this.refreshUI(ref, store);
                  break;
               case 7:
                  this.editingLineIndex = -1;
                  this.refreshUI(ref, store);
                  break;
               case 8:
                  if (!this.checkMovePermission(player, ref, store)) {
                     return;
                  }

                  pos = hologram.getPosition();
                  playerPos = new Vec3d(pos.x() + 0.5D, pos.y(), pos.z());
                  this.plugin.getHologramManager().moveHologram(hologram, playerPos);
                  this.plugin.getHologramManager().saveHolograms();
                  this.refreshUI(ref, store);
                  break;
               case 9:
                  if (!this.checkMovePermission(player, ref, store)) {
                     return;
                  }

                  pos = hologram.getPosition();
                  playerPos = new Vec3d(pos.x() - 0.5D, pos.y(), pos.z());
                  this.plugin.getHologramManager().moveHologram(hologram, playerPos);
                  this.plugin.getHologramManager().saveHolograms();
                  this.refreshUI(ref, store);
                  break;
               case 10:
                  if (!this.checkMovePermission(player, ref, store)) {
                     return;
                  }

                  pos = hologram.getPosition();
                  playerPos = new Vec3d(pos.x(), pos.y() + 0.5D, pos.z());
                  this.plugin.getHologramManager().moveHologram(hologram, playerPos);
                  this.plugin.getHologramManager().saveHolograms();
                  this.refreshUI(ref, store);
                  break;
               case 11:
                  if (!this.checkMovePermission(player, ref, store)) {
                     return;
                  }

                  pos = hologram.getPosition();
                  playerPos = new Vec3d(pos.x(), pos.y() - 0.5D, pos.z());
                  this.plugin.getHologramManager().moveHologram(hologram, playerPos);
                  this.plugin.getHologramManager().saveHolograms();
                  this.refreshUI(ref, store);
                  break;
               case 12:
                  if (!this.checkMovePermission(player, ref, store)) {
                     return;
                  }

                  pos = hologram.getPosition();
                  playerPos = new Vec3d(pos.x(), pos.y(), pos.z() + 0.5D);
                  this.plugin.getHologramManager().moveHologram(hologram, playerPos);
                  this.plugin.getHologramManager().saveHolograms();
                  this.refreshUI(ref, store);
                  break;
               case 13:
                  if (!this.checkMovePermission(player, ref, store)) {
                     return;
                  }

                  pos = hologram.getPosition();
                  playerPos = new Vec3d(pos.x(), pos.y(), pos.z() - 0.5D);
                  this.plugin.getHologramManager().moveHologram(hologram, playerPos);
                  this.plugin.getHologramManager().saveHolograms();
                  this.refreshUI(ref, store);
                  break;
               case 14:
                  if (!this.checkMovePermission(player, ref, store)) {
                     return;
                  }

                  pos = hologram.getPosition();
                  double newX = data.getPosXDouble(pos.x());
                  double newY = data.getPosYDouble(pos.y());
                  double newZ = data.getPosZDouble(pos.z());
                  Vec3d newPos = new Vec3d(newX, newY, newZ);
                  this.plugin.getHologramManager().moveHologram(hologram, newPos);
                  this.plugin.getHologramManager().saveHolograms();
                  this.refreshUI(ref, store);
                  break;
               case 15:
                  if (!HologramPermissionUtil.hasPermission(player, "HologramService.edit")) {
                     player.sendMessage(Message.raw("You don't have permission to edit holograms!").color("#FF5555"));
                     this.refreshUI(ref, store);
                     return;
                  }

                  dir = data.getDirection();
                  if (dir != null) {
                     FacingDirection facing = FacingDirection.fromString(dir);
                     if (facing != null) {
                        hologram.setFacingDirection(facing);
                        this.plugin.getHologramManager().updateHologram(hologram);
                        this.plugin.getHologramManager().saveHolograms();
                        this.refreshUI(ref, store);
                     }
                  }
               }

            }
         }
      }
   }

   private void enterEditMode(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, int lineIndex, String lineText) {
      UICommandBuilder commandBuilder = new UICommandBuilder();
      commandBuilder.set("#NewLineInput.Value", lineText);
      commandBuilder.set("#EditModeLabel.Text", "Editing Line " + (lineIndex + 1) + ":");
      commandBuilder.set("#EditModeLabel.Visible", true);
      commandBuilder.set("#AddLineButton.Visible", false);
      commandBuilder.set("#UpdateLineButton.Visible", true);
      commandBuilder.set("#CancelEditButton.Visible", true);
      this.sendUpdate(commandBuilder, (UIEventBuilder)null, false);
   }

   private void refreshUI(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
      Hologram hologram = this.plugin.getHologramManager().getHologram(this.hologramName);
      if (hologram == null) {
         this.closePage(ref, store);
      } else {
         UICommandBuilder commandBuilder = new UICommandBuilder();
         UIEventBuilder eventBuilder = new UIEventBuilder();
         Vec3d pos = hologram.getPosition();
         commandBuilder.set("#XInput.Value", String.format("%.1f", pos.x()));
         commandBuilder.set("#YInput.Value", String.format("%.1f", pos.y()));
         commandBuilder.set("#ZInput.Value", String.format("%.1f", pos.z()));
         commandBuilder.set("#NewLineInput.Value", "");
         commandBuilder.set("#EditModeLabel.Visible", false);
         commandBuilder.set("#AddLineButton.Visible", true);
         commandBuilder.set("#UpdateLineButton.Visible", false);
         commandBuilder.set("#CancelEditButton.Visible", false);
         this.buildLinesList(hologram, commandBuilder, eventBuilder);
         this.setupDirectionButtons(hologram, commandBuilder, eventBuilder);
         this.sendUpdate(commandBuilder, eventBuilder, false);
      }
   }

   private boolean checkMovePermission(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
      if (!HologramPermissionUtil.hasPermission(player, "HologramService.move")) {
         player.sendMessage(Message.raw("You don't have permission to move holograms!").color("#FF5555"));
         this.refreshUI(ref, store);
         return false;
      } else {
         return true;
      }
   }

   private void closePage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
      Player player = (Player)store.getComponent(ref, Player.getComponentType());
      if (player != null) {
         player.getPageManager().setPage(ref, store, Page.None);
      }

   }

   public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
   }
}



