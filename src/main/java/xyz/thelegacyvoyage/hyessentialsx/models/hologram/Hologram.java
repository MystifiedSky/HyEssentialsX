package xyz.thelegacyvoyage.hyessentialsx.models.hologram;

import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Hologram {
   @Nonnull
   private final UUID id;
   @Nonnull
   private final String name;
   @Nonnull
   private Vec3d position;
   @Nonnull
   private final UUID worldId;
   @Nonnull
   private List<String> lines;
   @Nullable
   private UUID creatorId;
   private boolean visible;
   @Nonnull
   private final List<UUID> lineEntityIds;
   private double lineSpacing = 0.25D;
   @Nonnull
   private FacingDirection facingDirection;

   public Hologram(@Nonnull String name, @Nonnull Vec3d position, @Nonnull UUID worldId) {
      this.facingDirection = FacingDirection.NORTH;
      this.id = UUID.randomUUID();
      this.name = name;
      this.position = position;
      this.worldId = worldId;
      this.lines = new ArrayList();
      this.lineEntityIds = new ArrayList();
      this.visible = true;
   }

   public Hologram(@Nonnull UUID id, @Nonnull String name, @Nonnull Vec3d position, @Nonnull UUID worldId, @Nonnull List<String> lines) {
      this.facingDirection = FacingDirection.NORTH;
      this.id = id;
      this.name = name;
      this.position = position;
      this.worldId = worldId;
      this.lines = new ArrayList(lines);
      this.lineEntityIds = new ArrayList();
      this.visible = true;
   }

   @Nonnull
   public UUID getId() {
      return this.id;
   }

   @Nonnull
   public String getName() {
      return this.name;
   }

   @Nonnull
   public Vec3d getPosition() {
      return this.position;
   }

   public void setPosition(@Nonnull Vec3d position) {
      this.position = position;
   }

   @Nonnull
   public UUID getWorldId() {
      return this.worldId;
   }

   @Nonnull
   public List<String> getLines() {
      return new ArrayList(this.lines);
   }

   public void setLines(@Nonnull List<String> lines) {
      this.lines = new ArrayList(lines);
   }

   public void addLine(@Nonnull String line) {
      this.lines.add(line);
   }

   public void setLine(int index, @Nonnull String line) {
      if (index >= 0 && index < this.lines.size()) {
         this.lines.set(index, line);
      }

   }

   public void removeLine(int index) {
      if (index >= 0 && index < this.lines.size()) {
         this.lines.remove(index);
      }

   }

   public void insertLine(int index, @Nonnull String line) {
      if (index >= 0 && index <= this.lines.size()) {
         this.lines.add(index, line);
      }

   }

   public int getLineCount() {
      return this.lines.size();
   }

   @Nullable
   public UUID getCreatorId() {
      return this.creatorId;
   }

   public void setCreatorId(@Nullable UUID creatorId) {
      this.creatorId = creatorId;
   }

   public boolean isVisible() {
      return this.visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   @Nonnull
   public List<UUID> getLineEntityIds() {
      return this.lineEntityIds;
   }

   public double getLineSpacing() {
      return this.lineSpacing;
   }

   public void setLineSpacing(double lineSpacing) {
      this.lineSpacing = lineSpacing;
   }

   @Nonnull
   public FacingDirection getFacingDirection() {
      return this.facingDirection;
   }

   public void setFacingDirection(@Nonnull FacingDirection facingDirection) {
      this.facingDirection = facingDirection;
   }

   @Nonnull
   public Vec3d getLinePosition(int lineIndex) {
      double yOffset = (double)(-lineIndex) * this.lineSpacing;
      return new Vec3d(this.position.x(), this.position.y() + yOffset, this.position.z());
   }

   public void clearLineEntityIds() {
      this.lineEntityIds.clear();
   }

   public void addLineEntityId(@Nonnull UUID entityId) {
      this.lineEntityIds.add(entityId);
   }

   public String toString() {
      String var10000 = String.valueOf(this.id);
      return "Hologram{id=" + var10000 + ", name='" + this.name + "', position=" + String.valueOf(this.position) + ", lines=" + this.lines.size() + ", visible=" + this.visible + "}";
   }
}


