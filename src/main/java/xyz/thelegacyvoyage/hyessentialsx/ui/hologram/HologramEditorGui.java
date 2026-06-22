package xyz.thelegacyvoyage.hyessentialsx.ui.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.HologramService;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HologramEditorGui {
   private static final Map<UUID, HologramEditorGui.EditSession> activeSessions = new ConcurrentHashMap();

   public static void open(@Nonnull Object player, @Nonnull Hologram hologram, @Nonnull HologramService plugin) {
      UUID playerId = getPlayerId(player);
      if (playerId != null) {
         HologramEditorGui.EditSession session = new HologramEditorGui.EditSession(playerId, hologram.getId(), plugin);
         activeSessions.put(playerId, session);
         sendEditorUI(player, hologram, session);
      }
   }

   public static void close(@Nonnull Object player) {
      UUID playerId = getPlayerId(player);
      if (playerId != null) {
         HologramEditorGui.EditSession session = (HologramEditorGui.EditSession)activeSessions.remove(playerId);
         if (session != null) {
            session.saveChanges();
         }
      }

      sendCloseUI(player);
   }

   public static void handleEvent(@Nonnull Object player, @Nonnull String eventId, @Nullable String data) {
      UUID playerId = getPlayerId(player);
      if (playerId != null) {
         HologramEditorGui.EditSession session = (HologramEditorGui.EditSession)activeSessions.get(playerId);
         if (session != null) {
            Hologram hologram = session.getHologram();
            if (hologram == null) {
               close(player);
            } else {
               byte var7 = -1;
               switch(eventId.hashCode()) {
               case -1866256119:
                  if (eventId.equals("edit_line")) {
                     var7 = 1;
                  }
                  break;
               case -1236063022:
                  if (eventId.equals("add_line")) {
                     var7 = 0;
                  }
                  break;
               case -1122060792:
                  if (eventId.equals("delete_line")) {
                     var7 = 2;
                  }
                  break;
               case -97927545:
                  if (eventId.equals("toggle_visible")) {
                     var7 = 7;
                  }
                  break;
               case 3522941:
                  if (eventId.equals("save")) {
                     var7 = 5;
                  }
                  break;
               case 94756344:
                  if (eventId.equals("close")) {
                     var7 = 6;
                  }
                  break;
               case 1067998288:
                  if (eventId.equals("move_down")) {
                     var7 = 4;
                  }
                  break;
               case 1243568585:
                  if (eventId.equals("move_up")) {
                     var7 = 3;
                  }
               }

               switch(var7) {
               case 0:
                  handleAddLine(player, session, hologram, data);
                  break;
               case 1:
                  handleEditLine(player, session, hologram, data);
                  break;
               case 2:
                  handleDeleteLine(player, session, hologram, data);
                  break;
               case 3:
                  handleMoveUp(player, session, hologram, data);
                  break;
               case 4:
                  handleMoveDown(player, session, hologram, data);
                  break;
               case 5:
                  handleSave(player, session, hologram);
                  break;
               case 6:
                  close(player);
                  break;
               case 7:
                  handleToggleVisible(player, session, hologram);
               }

            }
         }
      }
   }

   private static void sendEditorUI(@Nonnull Object player, @Nonnull Hologram hologram, @Nonnull HologramEditorGui.EditSession session) {
      UICommandBuilder builder = new UICommandBuilder();
      StringBuilder uiDoc = new StringBuilder();
      uiDoc.append("<panel id=\"hologram-editor\" style=\"width:400;height:500;background:#1a1a2e\">");
      uiDoc.append("<text style=\"font-size:24;color:#00d4ff;margin:10\">Hologram Editor</text>");
      uiDoc.append("<text style=\"font-size:16;color:#ffffff;margin:5\">Editing: ").append(hologram.getName()).append("</text>");
      uiDoc.append("<panel style=\"height:2;background:#00d4ff;margin:10 0\" />");
      uiDoc.append("<scroll-panel id=\"lines-container\" style=\"flex:1;margin:10\">");
      List<String> lines = hologram.getLines();

      for(int i = 0; i < lines.size(); ++i) {
         String line = (String)lines.get(i);
         uiDoc.append("<panel style=\"flex-direction:row;margin:5;padding:5;background:#2a2a4e\">");
         uiDoc.append("<text style=\"width:30;color:#888\">").append(i + 1).append(".</text>");
         uiDoc.append("<text-input id=\"line-").append(i).append("\" style=\"flex:1;color:#fff\" value=\"").append(escapeXml(line)).append("\" />");
         if (i > 0) {
            uiDoc.append("<button onclick=\"move_up:").append(i).append("\" style=\"width:30;margin:0 2\">â†‘</button>");
         }

         if (i < lines.size() - 1) {
            uiDoc.append("<button onclick=\"move_down:").append(i).append("\" style=\"width:30;margin:0 2\">â†“</button>");
         }

         uiDoc.append("<button onclick=\"delete_line:").append(i).append("\" style=\"width:30;background:#ff4444\">Ã—</button>");
         uiDoc.append("</panel>");
      }

      uiDoc.append("</scroll-panel>");
      uiDoc.append("<panel style=\"flex-direction:row;margin:10\">");
      uiDoc.append("<text-input id=\"new-line\" style=\"flex:1\" placeholder=\"Enter new line text...\" />");
      uiDoc.append("<button onclick=\"add_line\" style=\"width:80;background:#44ff44\">Add Line</button>");
      uiDoc.append("</panel>");
      uiDoc.append("<panel style=\"flex-direction:row;margin:10;justify-content:space-between\">");
      uiDoc.append("<button onclick=\"toggle_visible\" style=\"width:100\">").append(hologram.isVisible() ? "Hide" : "Show").append("</button>");
      uiDoc.append("<button onclick=\"save\" style=\"width:100;background:#4488ff\">Save</button>");
      uiDoc.append("<button onclick=\"close\" style=\"width:100;background:#888888\">Close</button>");
      uiDoc.append("</panel>");
      uiDoc.append("</panel>");
      builder.appendInline("#root", uiDoc.toString());
   }

   private static void sendCloseUI(@Nonnull Object player) {
      UICommandBuilder builder = new UICommandBuilder();
      builder.remove("#hologram-editor");
   }

   private static void refreshUI(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session) {
      Hologram hologram = session.getHologram();
      if (hologram != null) {
         sendEditorUI(player, hologram, session);
      }

   }

   private static void handleAddLine(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session, @Nonnull Hologram hologram, @Nullable String text) {
      if (text == null || text.isEmpty()) {
         text = "New Line";
      }

      hologram.addLine(text);
      session.markDirty();
      refreshUI(player, session);
   }

   private static void handleEditLine(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session, @Nonnull Hologram hologram, @Nullable String data) {
      if (data != null) {
         int colonIndex = data.indexOf(58);
         if (colonIndex >= 0) {
            try {
               int lineIndex = Integer.parseInt(data.substring(0, colonIndex));
               String newText = data.substring(colonIndex + 1);
               if (lineIndex >= 0 && lineIndex < hologram.getLineCount()) {
                  hologram.setLine(lineIndex, newText);
                  session.markDirty();
               }
            } catch (NumberFormatException var7) {
            }

         }
      }
   }

   private static void handleDeleteLine(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session, @Nonnull Hologram hologram, @Nullable String data) {
      if (data != null) {
         try {
            int lineIndex = Integer.parseInt(data);
            if (lineIndex >= 0 && lineIndex < hologram.getLineCount()) {
               hologram.removeLine(lineIndex);
               session.markDirty();
               refreshUI(player, session);
            }
         } catch (NumberFormatException var5) {
         }

      }
   }

   private static void handleMoveUp(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session, @Nonnull Hologram hologram, @Nullable String data) {
      if (data != null) {
         try {
            int lineIndex = Integer.parseInt(data);
            if (lineIndex > 0 && lineIndex < hologram.getLineCount()) {
               List<String> lines = hologram.getLines();
               String line = (String)lines.get(lineIndex);
               lines.remove(lineIndex);
               lines.add(lineIndex - 1, line);
               hologram.setLines(lines);
               session.markDirty();
               refreshUI(player, session);
            }
         } catch (NumberFormatException var7) {
         }

      }
   }

   private static void handleMoveDown(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session, @Nonnull Hologram hologram, @Nullable String data) {
      if (data != null) {
         try {
            int lineIndex = Integer.parseInt(data);
            if (lineIndex >= 0 && lineIndex < hologram.getLineCount() - 1) {
               List<String> lines = hologram.getLines();
               String line = (String)lines.get(lineIndex);
               lines.remove(lineIndex);
               lines.add(lineIndex + 1, line);
               hologram.setLines(lines);
               session.markDirty();
               refreshUI(player, session);
            }
         } catch (NumberFormatException var7) {
         }

      }
   }

   private static void handleSave(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session, @Nonnull Hologram hologram) {
      session.saveChanges();
      sendMessage(player, "&aHologram saved successfully!");
   }

   private static void handleToggleVisible(@Nonnull Object player, @Nonnull HologramEditorGui.EditSession session, @Nonnull Hologram hologram) {
      hologram.setVisible(!hologram.isVisible());
      session.markDirty();
      session.saveChanges();
      refreshUI(player, session);
   }

   @Nullable
   private static UUID getPlayerId(@Nonnull Object player) {
      return UUID.randomUUID();
   }

   private static void sendMessage(@Nonnull Object player, @Nonnull String message) {
   }

   private static String escapeXml(String text) {
      return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
   }

   private static class EditSession {
      private final UUID playerId;
      private final UUID hologramId;
      private final HologramService plugin;
      private boolean dirty = false;

      public EditSession(@Nonnull UUID playerId, @Nonnull UUID hologramId, @Nonnull HologramService plugin) {
         this.playerId = playerId;
         this.hologramId = hologramId;
         this.plugin = plugin;
      }

      @Nullable
      public Hologram getHologram() {
         return this.plugin.getHologramManager().getHologram(this.hologramId);
      }

      public void markDirty() {
         this.dirty = true;
      }

      public void saveChanges() {
         if (this.dirty) {
            Hologram hologram = this.getHologram();
            if (hologram != null) {
               this.plugin.getHologramManager().updateHologram(hologram);
            }

            this.dirty = false;
         }

      }
   }
}


