package xyz.thelegacyvoyage.hyessentialsx.managers.hologram;

import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation.AnimationData;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation.AnimationRegistry;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation.HologramAnimationManager;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.entitytool.EntityToolComponents;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.FacingDirection;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Hologram;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.HologramLineType;
import xyz.thelegacyvoyage.hyessentialsx.models.hologram.Vec3d;
import xyz.thelegacyvoyage.hyessentialsx.managers.hologram.placeholder.PlaceholderUpdateManager;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger.Api;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class HologramManager {
   @Nonnull
   private final HologramService plugin;
   @Nonnull
   private final Map<UUID, Hologram> holograms;
   @Nonnull
   private final Map<String, UUID> hologramsByName;
   @Nonnull
   private final Path dataFile;
   @Nonnull
   private final ImageManager imageManager;
   @Nonnull
   private final GifManager gifManager;
   @Nonnull
   private final BillboardManager billboardManager;
   @Nonnull
   private final HologramVisibilityManager visibilityManager;
   @Nonnull
   private final PlaceholderUpdateManager placeholderUpdateManager;
   private boolean hologramsSpawned = false;
   private volatile boolean imageRetryPending = false;
   private volatile long imageRetryStartTime = 0L;
   private final Set<UUID> spawningHolograms = ConcurrentHashMap.newKeySet();
   private static final double ITEM_SPACING_FACTOR = 1.5D;

   public HologramManager(@Nonnull HologramService plugin) {
      this.plugin = plugin;
      this.holograms = new ConcurrentHashMap();
      this.hologramsByName = new ConcurrentHashMap();
      this.dataFile = plugin.getDataDirectory().resolve("holograms.dat");
      this.imageManager = new ImageManager(plugin);
      this.gifManager = new GifManager(plugin, this.imageManager);
      this.billboardManager = new BillboardManager(plugin);
      this.visibilityManager = new HologramVisibilityManager(plugin, this);
      this.placeholderUpdateManager = new PlaceholderUpdateManager(plugin, this);
      this.imageManager.initialize();
      this.gifManager.initialize();
      this.billboardManager.start();
      this.gifManager.start();
      this.visibilityManager.start();
      this.placeholderUpdateManager.start();
   }

   @Nonnull
   public HologramVisibilityManager getVisibilityManager() {
      return this.visibilityManager;
   }

   @Nonnull
   public BillboardManager getBillboardManager() {
      return this.billboardManager;
   }

   @Nonnull
   public PlaceholderUpdateManager getPlaceholderUpdateManager() {
      return this.placeholderUpdateManager;
   }

   @Nonnull
   public ImageManager getImageManager() {
      return this.imageManager;
   }

   @Nonnull
   public GifManager getGifManager() {
      return this.gifManager;
   }

   @Nonnull
   public Hologram createHologram(@Nonnull String name, @Nonnull Vec3d position, @Nonnull UUID worldId, @Nullable UUID creatorId) {
      if (this.hologramsByName.containsKey(name.toLowerCase())) {
         throw new IllegalArgumentException("A hologram with name '" + name + "' already exists!");
      } else {
         Hologram hologram = new Hologram(name, position, worldId);
         hologram.setCreatorId(creatorId);
         hologram.setLineSpacing(this.plugin.getConfigManager().getHologramDefaultLineSpacing());
         hologram.addLine("New Hologram: " + name);
         hologram.addLine("Use /holo edit " + name);
         this.holograms.put(hologram.getId(), hologram);
         this.hologramsByName.put(name.toLowerCase(), hologram.getId());
         this.spawnHologram(hologram);
         this.plugin.getLogger().at(Level.INFO).log("Created hologram: " + name + " at " + String.valueOf(position));
         return hologram;
      }
   }

   public boolean deleteHologram(@Nonnull String name) {
      UUID id = (UUID)this.hologramsByName.remove(name.toLowerCase());
      if (id == null) {
         return false;
      } else {
         Hologram hologram = (Hologram)this.holograms.remove(id);
         if (hologram != null) {
            this.despawnHologram(hologram);
            this.plugin.getLogger().at(Level.INFO).log("Deleted hologram: " + name);
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean deleteHologram(@Nonnull UUID id) {
      Hologram hologram = (Hologram)this.holograms.remove(id);
      if (hologram != null) {
         this.hologramsByName.remove(hologram.getName().toLowerCase());
         this.despawnHologram(hologram);
         return true;
      } else {
         return false;
      }
   }

   @Nullable
   public Hologram getHologram(@Nonnull String name) {
      UUID id = (UUID)this.hologramsByName.get(name.toLowerCase());
      return id != null ? (Hologram)this.holograms.get(id) : null;
   }

   @Nullable
   public Hologram getHologram(@Nonnull UUID id) {
      return (Hologram)this.holograms.get(id);
   }

   @Nonnull
   public Collection<Hologram> getAllHolograms() {
      return Collections.unmodifiableCollection(this.holograms.values());
   }

   public int getHologramCount() {
      return this.holograms.size();
   }

   public boolean hologramExists(@Nonnull String name) {
      return this.hologramsByName.containsKey(name.toLowerCase());
   }

   public void updateHologram(@Nonnull Hologram hologram) {
      this.despawnHologramSync(hologram);
      this.spawnHologram(hologram);
   }

   public void respawnHologram(@Nonnull Hologram hologram) {
      this.despawnHologramSync(hologram);
      this.spawnHologram(hologram);
   }

   public void moveHologram(@Nonnull Hologram hologram, @Nonnull Vec3d newPosition) {
      hologram.setPosition(newPosition);
      this.moveHologramLive(hologram);
   }

   public void moveHologramLive(@Nonnull Hologram hologram) {
      List<UUID> entityIds = hologram.getLineEntityIds();
      if (entityIds.isEmpty()) {
         this.spawnHologram(hologram);
      } else {
         World world = this.findWorldByUuid(hologram.getWorldId());
         if (world != null) {
            Vec3d basePosition = hologram.getPosition();
            double lineSpacing = hologram.getLineSpacing();
            world.execute(() -> {
               Store<EntityStore> store = world.getEntityStore().getStore();

               for(int i = 0; i < entityIds.size(); ++i) {
                  UUID entityId = (UUID)entityIds.get(i);
                  Ref<EntityStore> entityRef = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
                  if (entityRef != null && entityRef.isValid()) {
                     TransformComponent transform = (TransformComponent)store.getComponent(entityRef, TransformComponent.getComponentType());
                     if (transform != null) {
                        double yOffset = (double)(-i) * lineSpacing;
                        transform.getPosition().assign(basePosition.x(), basePosition.y() + yOffset, basePosition.z());
                        transform.markChunkDirty(store);
                     }
                  }
               }

            });
         }
      }
   }

   private void spawnHologram(@Nonnull Hologram hologram) {
      if (hologram.isVisible()) {
         UUID holoId = hologram.getId();
         if (this.spawningHolograms.add(holoId)) {
            World world = this.findWorldByUuid(hologram.getWorldId());
            if (world == null) {
               Api var10000 = this.plugin.getLogger().at(Level.WARNING);
               String var10001 = hologram.getName();
               var10000.log("Cannot spawn hologram '" + var10001 + "' - world not found: " + String.valueOf(hologram.getWorldId()));
               this.spawningHolograms.remove(holoId);
            } else {
               world.execute(() -> {
                  try {
                     Api var10000;
                     String var10001;
                     try {
                        Vec3d pos = hologram.getPosition();
                        int chunkX = (int)Math.floor(pos.x()) >> 5;
                        int chunkZ = (int)Math.floor(pos.z()) >> 5;
                        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
                        Ref<?> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
                        if (chunkRef != null && chunkRef.isValid()) {
                           hologram.clearLineEntityIds();
                           List<String> lines = hologram.getLines();
                           double currentYOffset = 0.0D;

                           for(int i = 0; i < lines.size(); ++i) {
                              String line = (String)lines.get(i);
                              HologramLineType lineType = HologramLineType.fromLine(line);
                              double lineSpacing = hologram.getLineSpacing();
                              if (lineType != HologramLineType.ITEM && lineType != HologramLineType.IMAGE && lineType != HologramLineType.GIF) {
                                 if (i > 0) {
                                    currentYOffset -= lineSpacing;
                                 }
                              } else if (i > 0) {
                                 currentYOffset -= lineSpacing * 1.5D;
                              }

                              Vec3d linePos = new Vec3d(hologram.getPosition().x(), hologram.getPosition().y() + currentYOffset, hologram.getPosition().z());
                              UUID entityId = null;
                              switch(lineType) {
                              case ITEM:
                                 entityId = this.spawnItemLine(linePos, line, hologram.getWorldId());
                                 break;
                              case IMAGE:
                                 entityId = this.spawnImageLine(linePos, line, hologram);
                                 break;
                              case GIF:
                                 entityId = this.spawnGifLine(linePos, line, hologram);
                                 break;
                              case TEXT:
                              default:
                                 entityId = this.spawnHologramLine(linePos, line, hologram.getWorldId());
                              }

                              if (entityId != null) {
                                 hologram.addLineEntityId(entityId);
                                 this.plugin.getEntityToolSupport().getEntityTracker().registerEntity(entityId, hologram, i);
                                 if (lineType == HologramLineType.ITEM || lineType == HologramLineType.TEXT) {
                                    String animName = HologramLineType.extractAnimationName(line);
                                    if (animName != null) {
                                       float scale = 1.0F;
                                       float yawRadians = 0.0F;
                                       if (lineType == HologramLineType.ITEM) {
                                          HologramLineType.ItemLineData itemData = HologramLineType.parseItemLine(line);
                                          scale = itemData.scale;
                                          yawRadians = (float)Math.toRadians((double)itemData.yaw);
                                       }

                                       this.registerLineAnimation(entityId, hologram.getName(), i, animName, linePos, yawRadians, scale);
                                    }
                                 }
                              }
                           }

                           this.placeholderUpdateManager.registerHologram(hologram);
                           var10000 = this.plugin.getLogger().at(Level.FINE);
                           var10001 = hologram.getName();
                           var10000.log("Spawned hologram '" + var10001 + "' with " + hologram.getLineEntityIds().size() + " lines");
                           return;
                        }

                        this.plugin.getLogger().at(Level.FINE).log("Hologram '" + hologram.getName() + "' chunk not loaded at " + chunkX + ", " + chunkZ + " - will spawn when chunk loads");
                        this.spawningHolograms.remove(holoId);
                     } catch (Exception var27) {
                        var10000 = this.plugin.getLogger().at(Level.WARNING);
                        var10001 = hologram.getName();
                        var10000.log("Error spawning hologram '" + var10001 + "': " + var27.getMessage());
                        return;
                     }
                  } finally {
                     this.spawningHolograms.remove(hologram.getId());
                  }

               });
            }
         }
      }
   }

   @Nullable
   private UUID spawnHologramLine(@Nonnull Vec3d position, @Nonnull String text, @Nonnull UUID worldId) {
      try {
         World world = this.findWorldByUuid(worldId);
         if (world == null) {
            this.plugin.getLogger().at(Level.WARNING).log("Could not find world with UUID: " + String.valueOf(worldId));
            return null;
         } else {
            UUID entityUuid = UUID.randomUUID();
            Runnable spawnLogic = () -> {
               try {
                  int chunkX = (int)Math.floor(position.x()) >> 5;
                  int chunkZ = (int)Math.floor(position.z()) >> 5;
                  long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
                  Ref<?> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
                  if (chunkRef == null || !chunkRef.isValid()) {
                     this.plugin.getLogger().at(Level.WARNING).log("Cannot spawn hologram - chunk not loaded at " + chunkX + ", " + chunkZ + " for position " + String.valueOf(position));
                     return;
                  }

                  Store<EntityStore> store = world.getEntityStore().getStore();
                  Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                  Vector3d pos = new Vector3d(position.x(), position.y(), position.z());
                  Vector3f rotation = new Vector3f();
                  holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rotation));
                  ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
                  holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
                  if (projectileComponent.getProjectile() == null) {
                     projectileComponent.initialize();
                  }

                  holder.ensureComponent(Intangible.getComponentType());
                  holder.addComponent(Nameplate.getComponentType(), new Nameplate(this.formatText(text)));
                  holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(1.0F));
                  holder.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)store.getExternalData()).takeNextNetworkId()));
                  holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));
                  EntityToolComponents.addTextLineComponents(holder);
                  holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
                  Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
                  Api var10000;
                  if (ref != null) {
                      NetworkId netId = (NetworkId)store.getComponent(ref, NetworkId.getComponentType());
                     if (netId != null) {
                        this.plugin.getEntityToolSupport().getEntityTracker().registerNetworkId(netId.getId(), entityUuid);
                     }

                     var10000 = this.plugin.getLogger().at(Level.FINE);
                     String var10001 = String.format("%.1f, %.1f, %.1f", position.x(), position.y(), position.z());
                     var10000.log("Spawned hologram entity at " + var10001 + " with text: " + text);
                  } else {
                     var10000 = this.plugin.getLogger().at(Level.FINE);
                     Object[] var10002 = new Object[]{position.x(), position.y(), position.z()};
                     var10000.log("Entity spawn returned null at " + String.format("%.1f, %.1f, %.1f", var10002));
                  }
               } catch (Exception var17) {
                  this.plugin.getLogger().at(Level.FINE).log("Error spawning hologram line: " + var17.getMessage());
               }

            };
            if (world.isInThread()) {
               spawnLogic.run();
            } else {
               world.execute(spawnLogic);
            }

            return entityUuid;
         }
      } catch (Exception var7) {
         this.plugin.getLogger().at(Level.WARNING).log("Error in spawnHologramLine: " + var7.getMessage());
         return null;
      }
   }

   @Nullable
   private UUID spawnItemLine(@Nonnull Vec3d position, @Nonnull String line, @Nonnull UUID worldId) {
      try {
         World world = this.findWorldByUuid(worldId);
         if (world == null) {
            this.plugin.getLogger().at(Level.WARNING).log("Could not find world with UUID: " + String.valueOf(worldId));
            return null;
         } else {
            HologramLineType.ItemLineData itemData = HologramLineType.parseItemLine(line);
            UUID entityUuid = UUID.randomUUID();
            Runnable spawnLogic = () -> {
               try {
                  Store<EntityStore> store = world.getEntityStore().getStore();
                  Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                  Vector3d pos = new Vector3d(position.x(), position.y(), position.z());
                  Vector3f rotation = new Vector3f((float)Math.toRadians((double)itemData.pitch), (float)Math.toRadians((double)itemData.yaw), (float)Math.toRadians((double)itemData.roll));
                  holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rotation));
                  Item item = (Item)Item.getAssetMap().getAsset(itemData.itemId);
                  if (item == null) {
                     this.plugin.getLogger().at(Level.WARNING).log("Item not found: " + itemData.itemId);
                     ProjectileComponent projectileComponent = new ProjectileComponent("Projectile");
                     holder.putComponent(ProjectileComponent.getComponentType(), projectileComponent);
                     holder.ensureComponent(Intangible.getComponentType());
                     if (projectileComponent.getProjectile() == null) {
                        projectileComponent.initialize();
                     }

                     holder.addComponent(Nameplate.getComponentType(), new Nameplate("Item not found: " + itemData.itemId));
                  } else {
                     float finalScale = itemData.scale * item.getScale();
                     String modelName = item.getModel();
                     BlockType blockType = null;
                     if ((modelName == null || modelName.isEmpty()) && item.hasBlockType()) {
                        String blockId = item.getBlockId();
                        if (blockId != null) {
                           blockType = (BlockType)BlockType.getAssetMap().getAsset(blockId);
                           if (blockType != null) {
                              modelName = blockType.getCustomModel();
                              this.plugin.getLogger().at(Level.FINE).log("Found BlockType for " + itemData.itemId + " with blockId=" + blockId + ", customModel=" + modelName);
                           } else {
                              this.plugin.getLogger().at(Level.FINE).log("BlockType not found for blockId: " + blockId);
                           }
                        }
                     }

                     boolean modelSpawned = false;
                     if (modelName != null && !modelName.isEmpty()) {
                        ModelAsset asset = (ModelAsset)ModelAsset.getAssetMap().getAsset(modelName);
                        if (asset == null) {
                           String searchName = modelName;
                           if (modelName.contains("/")) {
                              searchName = modelName.substring(modelName.lastIndexOf("/") + 1);
                           }

                           if (searchName.endsWith(".blockymodel")) {
                              searchName = searchName.replace(".blockymodel", "");
                           }

                           asset = (ModelAsset)ModelAsset.getAssetMap().getAsset(searchName);
                           if (asset != null) {
                              modelName = searchName;
                           } else {
                              label106: {
                                 Iterator i$ = ModelAsset.getAssetMap().getAssetMap().values().iterator();

                                 ModelAsset a;
                                 String assetModelPath;
                                 do {
                                    do {
                                       if (!i$.hasNext()) {
                                          break label106;
                                       }

                                       a = (ModelAsset)i$.next();
                                       assetModelPath = a.getModel();
                                    } while(assetModelPath == null);
                                 } while(!assetModelPath.equalsIgnoreCase(modelName) && !assetModelPath.endsWith(modelName) && !modelName.endsWith(assetModelPath) && !a.getId().equalsIgnoreCase(searchName));

                                 modelName = a.getId();
                                 asset = a;
                              }
                           }
                        }

                        if (asset != null) {
                           ModelReference modelRef = new ModelReference(modelName, 1.0F, (Map)null, true);
                           holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(modelRef));
                           holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(finalScale));
                           holder.ensureComponent(Intangible.getComponentType());
                           modelSpawned = true;
                           this.plugin.getLogger().at(Level.INFO).log("Using PersistentModel for item: " + itemData.itemId + " -> " + modelName);
                        } else {
                           this.plugin.getLogger().at(Level.WARNING).log("Could not find ModelAsset for: " + modelName + " (item: " + itemData.itemId + ")");
                        }
                     }

                     if (!modelSpawned) {
                        this.plugin.getLogger().at(Level.INFO).log("Using ItemComponent fallback for item: " + itemData.itemId);
                        ItemStack itemStack = new ItemStack(itemData.itemId, 1);
                        itemStack.setOverrideDroppedItemAnimation(true);
                        holder.addComponent(ItemComponent.getComponentType(), new ItemComponent(itemStack));
                        holder.addComponent(PreventPickup.getComponentType(), PreventPickup.INSTANCE);
                        holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);
                        holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(finalScale));
                        holder.ensureComponent(Intangible.getComponentType());
                     }
                  }

                  holder.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)store.getExternalData()).takeNextNetworkId()));
                  holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));
                  EntityToolComponents.addItemLineComponents(holder);
                  holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
                  Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
                  if (ref != null) {
                     NetworkId netId = (NetworkId)store.getComponent(ref, NetworkId.getComponentType());
                     if (netId != null) {
                        this.plugin.getEntityToolSupport().getEntityTracker().registerNetworkId(netId.getId(), entityUuid);
                     }

                     Api var10000 = this.plugin.getLogger().at(Level.INFO);
                     String var10001 = String.format("%.1f, %.1f, %.1f", position.x(), position.y(), position.z());
                     var10000.log("Spawned item hologram at " + var10001 + " with item: " + itemData.itemId);
                  }
               } catch (Exception var19) {
                  this.plugin.getLogger().at(Level.WARNING).log("Error spawning item line: " + var19.getMessage());
                  var19.printStackTrace();
               }

            };
            if (world.isInThread()) {
               spawnLogic.run();
            } else {
               world.execute(spawnLogic);
            }

            return entityUuid;
         }
      } catch (Exception var8) {
         this.plugin.getLogger().at(Level.WARNING).log("Error in spawnItemLine: " + var8.getMessage());
         return null;
      }
   }

   @Nullable
   private UUID spawnImageLine(@Nonnull Vec3d position, @Nonnull String line, @Nonnull Hologram hologram) {
      HologramLineType.ImageLineData imageData = HologramLineType.parseImageLine(line);
      this.plugin.getLogger().at(Level.INFO).log("[DEBUG] Image line: " + line + " | doubleSided=" + imageData.doubleSided + " | billboard=" + imageData.billboard);
      Model imageModel = this.imageManager.createImageModel(imageData.imageName, imageData.scale, imageData.billboard, imageData.doubleSided);
      if (imageModel == null) {
         this.plugin.getLogger().at(Level.WARNING).log("Could not create image model for: " + imageData.imageName);
         return this.spawnHologramLine(position, "[Image: " + imageData.imageName + "]", hologram.getWorldId());
      } else {
         FacingDirection facing = imageData.facing;
         if (facing == FacingDirection.NORTH && !line.toLowerCase().contains(":n:") && !line.toLowerCase().endsWith(":n")) {
            facing = hologram.getFacingDirection();
         }

         UUID entityId = this.spawnImageWithModel(position, imageModel, imageData.scale, hologram.getWorldId(), imageData.billboard, imageData.trackingDistance, facing);
         if (entityId != null && imageData.hasAnimation()) {
            this.registerLineAnimation(entityId, hologram.getName(), hologram.getLines().indexOf(line), imageData.animationName, position, facing.getYaw(), imageData.scale);
         }

         return entityId;
      }
   }

   @Nullable
   private UUID spawnGifLine(@Nonnull Vec3d position, @Nonnull String line, @Nonnull Hologram hologram) {
      HologramLineType.GifLineData gifData = HologramLineType.parseGifLine(line);
      this.plugin.getLogger().at(Level.INFO).log("[DEBUG] GIF line: " + line + " | doubleSided=" + gifData.doubleSided + " | billboard=" + gifData.billboard);
      if (!this.gifManager.hasGif(gifData.gifName)) {
         this.plugin.getLogger().at(Level.WARNING).log("GIF not found: " + gifData.gifName + ". Place GIF in gifs/ folder and run /holo reload.");
         return this.spawnHologramLine(position, "[GIF: " + gifData.gifName + "]", hologram.getWorldId());
      } else {
         Model firstFrameModel = this.gifManager.createFirstFrameModel(gifData.gifName, gifData.scale, gifData.billboard, gifData.doubleSided);
         if (firstFrameModel == null) {
            this.plugin.getLogger().at(Level.WARNING).log("Could not create GIF frame model for: " + gifData.gifName);
            return this.spawnHologramLine(position, "[GIF: " + gifData.gifName + "]", hologram.getWorldId());
         } else {
            FacingDirection facing = gifData.facing;
            if (facing == FacingDirection.NORTH && !line.toLowerCase().contains(":n:") && !line.toLowerCase().endsWith(":n")) {
               facing = hologram.getFacingDirection();
            }

            UUID entityId = this.spawnImageWithModel(position, firstFrameModel, gifData.scale, hologram.getWorldId(), gifData.billboard, gifData.trackingDistance, facing);
            if (entityId != null) {
               this.gifManager.registerAnimation(entityId, hologram.getWorldId(), gifData.gifName, gifData.scale, gifData.speedMultiplier, gifData.billboard, gifData.doubleSided, facing);
               if (gifData.hasAnimation()) {
                  this.registerLineAnimation(entityId, hologram.getName(), hologram.getLines().indexOf(line), gifData.animationName, position, facing.getYaw(), gifData.scale);
               }

               String billboardInfo = gifData.billboard ? " [BILLBOARD]" : "";
               String dsInfo = gifData.doubleSided ? " [DOUBLESIDED]" : "";
               String animInfo = gifData.hasAnimation() ? " [ANIM: " + gifData.animationName + "]" : "";
               Api var10000 = this.plugin.getLogger().at(Level.INFO);
               String var10001 = gifData.gifName;
               var10000.log("Spawned GIF hologram: " + var10001 + " at " + String.format("%.1f, %.1f, %.1f", position.x(), position.y(), position.z()) + " (speed: " + gifData.speedMultiplier + "x)" + billboardInfo + dsInfo + animInfo);
            }

            return entityId;
         }
      }
   }

   @Nullable
   private UUID spawnImageWithModel(@Nonnull Vec3d position, @Nonnull Model model, float scale, @Nonnull UUID worldId, boolean billboard, float trackingDistance, @Nonnull FacingDirection facing) {
      try {
         World world = this.findWorldByUuid(worldId);
         if (world == null) {
            this.plugin.getLogger().at(Level.WARNING).log("Could not find world with UUID: " + String.valueOf(worldId));
            return null;
         } else {
            UUID entityUuid = UUID.randomUUID();
            float facingYaw = facing.getYaw();
            world.execute(() -> {
               try {
                  Store<EntityStore> store = world.getEntityStore().getStore();
                  Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
                  Vector3d pos = new Vector3d(position.x(), position.y(), position.z());
                  Vector3f rotation = new Vector3f(0.0F, facingYaw, 0.0F);
                  holder.addComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rotation));
                  holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(entityUuid));
                  holder.addComponent(ModelComponent.getComponentType(), new ModelComponent(model));
                  holder.addComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
                  holder.addComponent(EntityScaleComponent.getComponentType(), new EntityScaleComponent(scale));
                  EntityToolComponents.addImageLineComponents(holder, scale);
                  holder.addComponent(NetworkId.getComponentType(), new NetworkId(((EntityStore)store.getExternalData()).takeNextNetworkId()));
                  holder.ensureComponent(EntityModule.get().getVisibleComponentType());
                  holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
                  Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
                  if (ref != null) {
                     if (!billboard) {
                        store.ensureComponent(ref, Frozen.getComponentType());
                     }

                     NetworkId netId = (NetworkId)store.getComponent(ref, NetworkId.getComponentType());
                     if (netId != null) {
                        this.plugin.getEntityToolSupport().getEntityTracker().registerNetworkId(netId.getId(), entityUuid);
                     }

                     Api var10000;
                     String var10001;
                     if (billboard) {
                        this.billboardManager.registerBillboard(entityUuid, worldId, position, trackingDistance, facingYaw);
                        String distInfo = trackingDistance > 0.0F ? " (distance: " + trackingDistance + " blocks)" : " (using config distance)";
                        var10000 = this.plugin.getLogger().at(Level.FINE);
                        var10001 = String.format("%.1f, %.1f, %.1f", position.x(), position.y(), position.z());
                        var10000.log("Spawned BILLBOARD image hologram at " + var10001 + distInfo + ", facing: " + facing.getShortName().toUpperCase() + " using model: " + model.getModelAssetId());
                     } else {
                        var10000 = this.plugin.getLogger().at(Level.FINE);
                        var10001 = String.format("%.1f, %.1f, %.1f", position.x(), position.y(), position.z());
                        var10000.log("Spawned image hologram at " + var10001 + ", facing: " + facing.getShortName().toUpperCase() + " using model: " + model.getModelAssetId());
                     }
                  }
               } catch (Exception var19) {
                  this.plugin.getLogger().at(Level.WARNING).log("Error spawning image hologram: " + var19.getMessage());
                  var19.printStackTrace();
               }

            });
            return entityUuid;
         }
      } catch (Exception var14) {
         this.plugin.getLogger().at(Level.WARNING).log("Error in spawnImageWithModel: " + var14.getMessage());
         return null;
      }
   }

   private void registerLineAnimation(@Nonnull UUID entityUuid, @Nonnull String hologramName, int lineIndex, @Nonnull String animationName, @Nonnull Vec3d position, float yawRadians, float scale) {
      AnimationRegistry animRegistry = this.plugin.getAnimationRegistry();
      HologramAnimationManager animManager = this.plugin.getAnimationManager();
      AnimationData animation = animRegistry.getAnimation(animationName);
      if (animation == null) {
         this.plugin.getLogger().at(Level.WARNING).log("Unknown animation: " + animationName + ". Available animations: float, spin, pulse, bounce, sway (and variants like _slow, _fast)");
      } else {
         Vector3d basePos = new Vector3d(position.x(), position.y(), position.z());
         Vector3f baseRot = new Vector3f(0.0F, yawRadians, 0.0F);
         animManager.registerAnimation(entityUuid, hologramName, lineIndex, animation, basePos, baseRot, scale);
         this.plugin.getLogger().at(Level.INFO).log("Registered animation '" + animationName + "' for " + hologramName + " line " + lineIndex);
      }
   }

   @Nullable
   private World findWorldByUuid(@Nonnull UUID worldId) {
      Iterator var2 = Universe.get().getWorlds().values().iterator();

      World world;
      do {
         if (!var2.hasNext()) {
            return null;
         }

         world = (World)var2.next();
      } while(!world.getWorldConfig().getUuid().equals(worldId));

      return world;
   }

   private void despawnHologram(@Nonnull Hologram hologram) {
      World world = this.findWorldByUuid(hologram.getWorldId());
      if (world == null) {
         this.plugin.getEntityToolSupport().getEntityTracker().unregisterHologram(hologram);
         hologram.clearLineEntityIds();
      } else {
         List<UUID> entityIds = new ArrayList(hologram.getLineEntityIds());
         hologram.clearLineEntityIds();
         if (!entityIds.isEmpty()) {
            this.plugin.getEntityToolSupport().getEntityTracker().unregisterHologram(hologram);
            this.billboardManager.unregisterAll(new HashSet(entityIds));
            Iterator var4 = entityIds.iterator();

            while(var4.hasNext()) {
               UUID entityId = (UUID)var4.next();
               this.gifManager.unregisterAnimation(entityId);
            }

            HologramAnimationManager animManager = this.plugin.getAnimationManager();
            Iterator var7 = entityIds.iterator();

            while(var7.hasNext()) {
               UUID entityId = (UUID)var7.next();
               animManager.unregisterAnimation(entityId);
            }

            world.execute(() -> {
               Store<EntityStore> store = world.getEntityStore().getStore();
               Iterator i$ = entityIds.iterator();

               while(i$.hasNext()) {
                  UUID entityId = (UUID)i$.next();
                  this.plugin.getEntityToolSupport().getEntityTracker().unregisterEntity(entityId);
                  Ref<EntityStore> ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
                  if (ref != null && ref.isValid()) {
                     store.removeEntity(ref, RemoveReason.REMOVE);
                  }
               }

            });
         }
      }
   }

   public void removeAllHolograms() {
      this.plugin.getLogger().at(Level.INFO).log("Removing all hologram entities from world...");
      this.visibilityManager.stop();
      this.billboardManager.stop();
      this.gifManager.stop();
      int removedCount = 0;
      Iterator var2 = this.holograms.values().iterator();

      while(var2.hasNext()) {
         Hologram hologram = (Hologram)var2.next();

         try {
            this.despawnHologramSync(hologram);
            ++removedCount;
         } catch (Exception var5) {
            Api var10000 = this.plugin.getLogger().at(Level.WARNING);
            String var10001 = hologram.getName();
            var10000.log("Error despawning hologram '" + var10001 + "': " + var5.getMessage());
         }
      }

      this.holograms.clear();
      this.hologramsByName.clear();
      this.hologramsSpawned = false;
      this.plugin.getLogger().at(Level.INFO).log("Removed " + removedCount + " holograms from world");
   }

   private void despawnHologramSync(@Nonnull Hologram hologram) {
      World world = this.findWorldByUuid(hologram.getWorldId());
      if (world == null) {
         this.plugin.getLogger().at(Level.WARNING).log("World not found for hologram '" + hologram.getName() + "', clearing entity IDs");
         this.plugin.getEntityToolSupport().getEntityTracker().unregisterHologram(hologram);
         hologram.clearLineEntityIds();
      } else {
         List<UUID> entityIds = new ArrayList(hologram.getLineEntityIds());
         hologram.clearLineEntityIds();
         if (entityIds.isEmpty()) {
            this.plugin.getLogger().at(Level.FINE).log("No entities to despawn for hologram '" + hologram.getName() + "'");
         } else {
            this.plugin.getEntityToolSupport().getEntityTracker().unregisterHologram(hologram);
            Iterator var4 = entityIds.iterator();

            while(var4.hasNext()) {
               UUID entityId = (UUID)var4.next();
               this.plugin.getEntityToolSupport().getEntityTracker().unregisterEntity(entityId);
            }

            this.billboardManager.unregisterAll(new HashSet(entityIds));
            var4 = entityIds.iterator();

            while(var4.hasNext()) {
               UUID entityId = (UUID)var4.next();
               this.gifManager.unregisterAnimation(entityId);
            }

            HologramAnimationManager animManager = this.plugin.getAnimationManager();
            Iterator var9 = entityIds.iterator();

            while(var9.hasNext()) {
               UUID entityId = (UUID)var9.next();
               animManager.unregisterAnimation(entityId);
            }

            Api var10000 = this.plugin.getLogger().at(Level.FINE);
            int var10001 = entityIds.size();
            var10000.log("Despawning " + var10001 + " entities for hologram '" + hologram.getName() + "'");
            if (!Thread.currentThread().getName().contains("World") && !Thread.currentThread().getName().contains("world")) {
               try {
                  CountDownLatch latch = new CountDownLatch(1);
                  world.execute(() -> {
                     try {
                        this.despawnEntitiesDirect(world, entityIds, hologram.getName());
                     } finally {
                        latch.countDown();
                     }

                  });
                  boolean completed = latch.await(5L, TimeUnit.SECONDS);
                  if (!completed) {
                     this.plugin.getLogger().at(Level.WARNING).log("Timeout waiting for entity removal for hologram '" + hologram.getName() + "'");
                  }
               } catch (InterruptedException var7) {
                  Thread.currentThread().interrupt();
                  this.plugin.getLogger().at(Level.WARNING).log("Interrupted while despawning hologram '" + hologram.getName() + "'");
               }

            } else {
               this.despawnEntitiesDirect(world, entityIds, hologram.getName());
            }
         }
      }
   }

   private void despawnEntitiesDirect(@Nonnull World world, @Nonnull List<UUID> entityIds, @Nonnull String hologramName) {
      Store<EntityStore> store = world.getEntityStore().getStore();
      int removedCount = 0;
      Iterator var6 = entityIds.iterator();

      while(var6.hasNext()) {
         UUID entityId = (UUID)var6.next();

         try {
            Ref<EntityStore> ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
            if (ref != null && ref.isValid()) {
               store.removeEntity(ref, RemoveReason.REMOVE);
               ++removedCount;
            }
         } catch (Exception var9) {
            Api var10000 = this.plugin.getLogger().at(Level.WARNING);
            String var10001 = String.valueOf(entityId);
            var10000.log("Failed to remove entity " + var10001 + ": " + var9.getMessage());
         }
      }

      this.plugin.getLogger().at(Level.FINE).log("Successfully removed " + removedCount + "/" + entityIds.size() + " entities for hologram '" + hologramName + "'");
   }

   public void saveHolograms() {
      try {
         Files.createDirectories(this.plugin.getDataDirectory());
         ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(this.dataFile.toFile()));

         try {
            oos.writeInt(this.holograms.size());
            Iterator var2 = this.holograms.values().iterator();

            while(var2.hasNext()) {
               Hologram hologram = (Hologram)var2.next();
               this.writeHologram(oos, hologram);
            }
         } catch (Throwable var5) {
            try {
               oos.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }

            throw var5;
         }

         oos.close();
         this.plugin.getLogger().at(Level.INFO).log("Saved " + this.holograms.size() + " holograms");
      } catch (IOException var6) {
         this.plugin.getLogger().at(Level.SEVERE).log("Failed to save holograms: " + var6.getMessage());
      }

   }

   public void loadHolograms() {
      if (!Files.exists(this.dataFile, new LinkOption[0])) {
         this.plugin.getLogger().at(Level.INFO).log("No hologram data file found, starting fresh");
      } else {
         try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(this.dataFile.toFile()));

            try {
               int count = ois.readInt();

               for(int i = 0; i < count; ++i) {
                  Hologram hologram = this.readHologram(ois);
                  if (hologram != null) {
                     this.holograms.put(hologram.getId(), hologram);
                     this.hologramsByName.put(hologram.getName().toLowerCase(), hologram.getId());
                  }
               }

               this.plugin.getLogger().at(Level.INFO).log("Loaded " + this.holograms.size() + " holograms from file (spawning will occur when world is ready)");
            } catch (Throwable var6) {
               try {
                  ois.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }

               throw var6;
            }

            ois.close();
         } catch (ClassNotFoundException | IOException var7) {
            this.plugin.getLogger().at(Level.SEVERE).log("Failed to load holograms: " + var7.getMessage());
         }

      }
   }

   public void spawnAllHolograms() {
      if (!this.hologramsSpawned) {
         this.hologramsSpawned = true;
         int spawnedCount = 0;
         int imageHologramCount = 0;
         Iterator var3 = this.holograms.values().iterator();

         while(var3.hasNext()) {
            Hologram hologram = (Hologram)var3.next();

            try {
               boolean hasImageLines = hologram.getLines().stream().anyMatch((line) -> {
                  return line.toLowerCase().startsWith("image:");
               });
               if (hasImageLines) {
                  ++imageHologramCount;
               }

               this.spawnHologram(hologram);
               ++spawnedCount;
            } catch (Exception var6) {
               Api var10000 = this.plugin.getLogger().at(Level.WARNING);
               String var10001 = hologram.getName();
               var10000.log("Failed to spawn hologram '" + var10001 + "': " + var6.getMessage());
            }
         }

         this.plugin.getLogger().at(Level.INFO).log("Spawned " + spawnedCount + " holograms in world");
         if (imageHologramCount > 0) {
            this.scheduleImageHologramRetry();
         }

      }
   }

   private void scheduleImageHologramRetry() {
      Set<String> unloadedImagesAtSpawn = new HashSet();
      Iterator var2 = this.holograms.values().iterator();

      while(var2.hasNext()) {
         Hologram hologram = (Hologram)var2.next();
         Iterator var4 = hologram.getLines().iterator();

         while(var4.hasNext()) {
            String line = (String)var4.next();
            if (line.toLowerCase().startsWith("image:")) {
               String imageName = line.substring(6).split(":")[0].trim();
               if (!this.imageManager.isImageLoaded(imageName)) {
                  unloadedImagesAtSpawn.add(imageName.toLowerCase());
               }
            }
         }
      }

      if (unloadedImagesAtSpawn.isEmpty()) {
         this.plugin.getLogger().at(Level.FINE).log("All image assets loaded at spawn time, no retry needed");
      } else {
         this.imageRetryPending = true;
         long myStartTime = System.currentTimeMillis();
         this.imageRetryStartTime = myStartTime;
         Api var10000 = this.plugin.getLogger().at(Level.INFO);
         int var10001 = unloadedImagesAtSpawn.size();
         var10000.log("Scheduling image retry for " + var10001 + " unloaded images: " + String.valueOf(unloadedImagesAtSpawn));
         (new Thread(() -> {
            try {
               Thread.sleep(5000L);
               if (this.imageRetryStartTime != myStartTime) {
                  this.plugin.getLogger().at(Level.FINE).log("Image retry cancelled - newer reload in progress");
                  return;
               }

               this.imageRetryPending = false;
               boolean anyNowLoaded = false;
               Iterator i$ = unloadedImagesAtSpawn.iterator();

               while(i$.hasNext()) {
                  String imageName = (String)i$.next();
                  if (this.imageManager.hasImage(imageName) && this.imageManager.isImageLoaded(imageName)) {
                     anyNowLoaded = true;
                     this.plugin.getLogger().at(Level.INFO).log("Image '" + imageName + "' is now loaded");
                     break;
                  }
               }

               if (anyNowLoaded) {
                  this.plugin.getLogger().at(Level.INFO).log("Previously unloaded image assets now available, respawning affected holograms...");
                  i$ = this.holograms.values().iterator();

                  while(i$.hasNext()) {
                     Hologram hologram = (Hologram)i$.next();
                     Stream<String> imageNames = hologram.getLines().stream().filter((line) -> {
                        return line.toLowerCase().startsWith("image:");
                     }).map((line) -> {
                        return line.substring(6).split(":")[0].trim().toLowerCase();
                     });
                     Objects.requireNonNull(unloadedImagesAtSpawn);
                     boolean hasUnloadedImage = imageNames.anyMatch(unloadedImagesAtSpawn::contains);
                     if (hasUnloadedImage) {
                        try {
                           this.despawnHologramSync(hologram);
                           this.spawnHologram(hologram);
                        } catch (Exception var9) {
                           Api var11 = this.plugin.getLogger().at(Level.WARNING);
                           String hologramName = hologram.getName();
                           var11.log("Failed to respawn image hologram '" + hologramName + "': " + var9.getMessage());
                        }
                     }
                  }

                  this.plugin.getLogger().at(Level.INFO).log("Image hologram respawn complete");
               } else {
                  this.plugin.getLogger().at(Level.INFO).log("No previously unloaded images became available, skipping retry");
               }
            } catch (InterruptedException var10) {
               Thread.currentThread().interrupt();
            }

         }, "HologramService-ImageRetry")).start();
      }
   }

   public void reload() {
      this.plugin.getLogger().at(Level.INFO).log("Reloading HologramService...");
      this.saveHolograms();
      this.despawnAllHologramsBatched();
      this.holograms.clear();
      this.hologramsByName.clear();
      this.hologramsSpawned = false;
      this.gifManager.reload(true);
      this.loadHolograms();
      this.spawnAllHolograms();
      this.plugin.getLogger().at(Level.INFO).log("HologramService reload complete!");
   }

   private void despawnAllHologramsBatched() {
      Map<UUID, List<UUID>> entitiesByWorld = new HashMap();
      Set<UUID> allEntityIds = new HashSet();

      Hologram hologram;
      for(Iterator var3 = this.holograms.values().iterator(); var3.hasNext(); hologram.clearLineEntityIds()) {
         hologram = (Hologram)var3.next();
         List<UUID> entityIds = hologram.getLineEntityIds();
         if (!entityIds.isEmpty()) {
            ((List)entitiesByWorld.computeIfAbsent(hologram.getWorldId(), (k) -> {
               return new ArrayList();
            })).addAll(entityIds);
            allEntityIds.addAll(entityIds);
         }
      }

      if (!allEntityIds.isEmpty()) {
         this.billboardManager.unregisterAll(allEntityIds);
         Api var10000 = this.plugin.getLogger().at(Level.INFO);
         int var10001 = allEntityIds.size();
         var10000.log("Batch despawning " + var10001 + " entities across " + entitiesByWorld.size() + " world(s)");
         CountDownLatch latch = new CountDownLatch(entitiesByWorld.size());
         Iterator var11 = entitiesByWorld.entrySet().iterator();

         while(var11.hasNext()) {
            Entry<UUID, List<UUID>> entry = (Entry)var11.next();
            UUID worldId = (UUID)entry.getKey();
            List<UUID> entityIds = (List)entry.getValue();
            World world = this.findWorldByUuid(worldId);
            if (world == null) {
               latch.countDown();
            } else {
               world.execute(() -> {
                  try {
                     this.despawnEntitiesDirect(world, entityIds, "batch-reload");
                  } finally {
                     latch.countDown();
                  }

               });
            }
         }

         try {
            boolean completed = latch.await(5L, TimeUnit.SECONDS);
            if (!completed) {
               this.plugin.getLogger().at(Level.WARNING).log("Timeout waiting for batch entity removal");
            }
         } catch (InterruptedException var9) {
            Thread.currentThread().interrupt();
         }

      }
   }

   public boolean areHologramsSpawned() {
      return this.hologramsSpawned;
   }

   public boolean verifyHologramEntities() {
      if (this.hologramsSpawned && !this.holograms.isEmpty()) {
         Iterator var1 = this.holograms.values().iterator();

         label47:
         while(true) {
            List entityIds;
            World world;
            do {
               if (!var1.hasNext()) {
                  return true;
               }

               Hologram hologram = (Hologram)var1.next();
               entityIds = hologram.getLineEntityIds();
               if (entityIds.isEmpty() && !hologram.getLines().isEmpty()) {
                  return false;
               }

               world = this.findWorldByUuid(hologram.getWorldId());
            } while(world == null);

            try {
               Store<EntityStore> store = world.getEntityStore().getStore();
               Iterator var6 = entityIds.iterator();

               UUID entityId;
               Ref ref;
               do {
                  if (!var6.hasNext()) {
                     continue label47;
                  }

                  entityId = (UUID)var6.next();
                  ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
               } while(ref != null && ref.isValid());

               this.plugin.getLogger().at(Level.INFO).log("Hologram entity " + String.valueOf(entityId) + " no longer valid, needs respawn");
               return false;
            } catch (Exception var9) {
               this.plugin.getLogger().at(Level.FINE).log("Error verifying hologram entities: " + var9.getMessage());
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public void ensureHologramsVisible() {
      if (!this.hologramsSpawned) {
         this.spawnAllHolograms();
      } else {
         if (!this.verifyHologramEntities()) {
            this.plugin.getLogger().at(Level.INFO).log("Some hologram entities are missing, respawning...");
            Iterator var1 = this.holograms.values().iterator();

            Hologram hologram;
            while(var1.hasNext()) {
               hologram = (Hologram)var1.next();

               try {
                  this.despawnHologramSync(hologram);
               } catch (Exception var5) {
               }
            }

            var1 = this.holograms.values().iterator();

            while(var1.hasNext()) {
               hologram = (Hologram)var1.next();

               try {
                  this.spawnHologram(hologram);
               } catch (Exception var4) {
                  Api var10000 = this.plugin.getLogger().at(Level.WARNING);
                  String var10001 = hologram.getName();
                  var10000.log("Failed to respawn hologram '" + var10001 + "': " + var4.getMessage());
               }
            }

            this.plugin.getLogger().at(Level.INFO).log("Respawned " + this.holograms.size() + " holograms");
         } else {
            this.plugin.getLogger().at(Level.FINE).log("All hologram entities verified as valid");
         }

      }
   }

   public void ensureHologramsVisibleInWorld(@Nonnull UUID worldId) {
      if (!this.hologramsSpawned) {
         this.spawnAllHolograms();
      } else {
         World world = this.findWorldByUuid(worldId);
         if (world != null) {
            int checked = 0;
            int respawned = 0;
            Iterator var5 = this.holograms.values().iterator();

            while(true) {
               Hologram hologram;
               do {
                  if (!var5.hasNext()) {
                     if (respawned > 0) {
                        this.plugin.getLogger().at(Level.INFO).log("Respawned " + respawned + "/" + checked + " holograms in world");
                     }

                     return;
                  }

                  hologram = (Hologram)var5.next();
               } while(!hologram.getWorldId().equals(worldId));

               ++checked;
               List<UUID> entityIds = hologram.getLineEntityIds();
               boolean needsRespawn = false;
               if (entityIds.isEmpty() && !hologram.getLines().isEmpty() && hologram.isVisible()) {
                  needsRespawn = true;
               } else {
                  try {
                     label69: {
                        Store<EntityStore> store = world.getEntityStore().getStore();
                        Iterator var10 = entityIds.iterator();

                        Ref ref;
                        do {
                           if (!var10.hasNext()) {
                              break label69;
                           }

                           UUID entityId = (UUID)var10.next();
                           ref = ((EntityStore)store.getExternalData()).getRefFromUUID(entityId);
                        } while(ref != null && ref.isValid());

                        needsRespawn = true;
                     }
                  } catch (Exception var14) {
                     needsRespawn = true;
                  }
               }

               if (needsRespawn) {
                  try {
                     this.despawnHologramSync(hologram);
                     this.spawnHologram(hologram);
                     ++respawned;
                     this.plugin.getLogger().at(Level.FINE).log("Respawned hologram '" + hologram.getName() + "' in world");
                  } catch (Exception var13) {
                     Api var10000 = this.plugin.getLogger().at(Level.WARNING);
                     String var10001 = hologram.getName();
                     var10000.log("Failed to respawn hologram '" + var10001 + "': " + var13.getMessage());
                  }
               }
            }
         }
      }
   }

   public int cleanupOrphanedEntities() {
      Set<UUID> trackedEntityIds = new HashSet();
      Iterator var2 = this.holograms.values().iterator();

      while(var2.hasNext()) {
         Hologram hologram = (Hologram)var2.next();
         trackedEntityIds.addAll(hologram.getLineEntityIds());
      }

      AtomicInteger removedCount = new AtomicInteger(0);
      CountDownLatch latch = new CountDownLatch(Universe.get().getWorlds().size());
      Iterator var4 = Universe.get().getWorlds().values().iterator();

      while(var4.hasNext()) {
         World world = (World)var4.next();

         try {
            world.execute(() -> {
               try {
                  Store<EntityStore> store = world.getEntityStore().getStore();
                  store.forEachEntityParallel((index, archetypeChunk, commandBuffer) -> {
                     try {
                        boolean hasNameplate = archetypeChunk.getArchetype().contains(Nameplate.getComponentType());
                        boolean hasProjectile = archetypeChunk.getArchetype().contains(ProjectileComponent.getComponentType());
                        boolean hasPersistentModel = archetypeChunk.getArchetype().contains(PersistentModel.getComponentType());
                        boolean hasPropComponent = archetypeChunk.getArchetype().contains(PropComponent.getComponentType());
                        boolean hasIntangible = archetypeChunk.getArchetype().contains(Intangible.getComponentType());
                        if (archetypeChunk.getArchetype().contains(UUIDComponent.getComponentType())) {
                           UUIDComponent uuidComponent = (UUIDComponent)archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
                           if (uuidComponent != null) {
                              UUID entityUuid = uuidComponent.getUuid();
                              boolean looksLikeTextHologram = hasNameplate && hasProjectile;
                              boolean looksLikeImageHologram = hasPersistentModel && (hasIntangible || hasPropComponent);
                              boolean looksLikeHologram = looksLikeTextHologram || looksLikeImageHologram;
                              boolean hasHologramSignature = hasPropComponent && hasIntangible;
                              if ((looksLikeHologram || hasHologramSignature) && !trackedEntityIds.contains(entityUuid)) {
                                 Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
                                 commandBuffer.removeEntity(entityRef, RemoveReason.REMOVE);
                                 removedCount.incrementAndGet();
                              }
                           }
                        }
                     } catch (Exception var17) {
                     }

                  });
               } finally {
                  latch.countDown();
               }

            });
         } catch (Exception var8) {
            latch.countDown();
            this.plugin.getLogger().at(Level.WARNING).log("Error cleaning up entities in world: " + var8.getMessage());
         }
      }

      try {
         latch.await(5L, TimeUnit.SECONDS);
      } catch (InterruptedException var7) {
         Thread.currentThread().interrupt();
      }

      int count = removedCount.get();
      if (count > 0) {
         this.plugin.getLogger().at(Level.INFO).log("Cleaned up " + count + " orphaned hologram entities");
      }

      return count;
   }

   private void writeHologram(@Nonnull ObjectOutputStream oos, @Nonnull Hologram hologram) throws IOException {
      oos.writeObject(hologram.getId());
      oos.writeUTF(hologram.getName());
      oos.writeDouble(hologram.getPosition().x());
      oos.writeDouble(hologram.getPosition().y());
      oos.writeDouble(hologram.getPosition().z());
      oos.writeObject(hologram.getWorldId());
      oos.writeObject(hologram.getCreatorId());
      oos.writeObject(new ArrayList(hologram.getLines()));
      oos.writeDouble(hologram.getLineSpacing());
      oos.writeBoolean(hologram.isVisible());
      oos.writeUTF(hologram.getFacingDirection().name());
      oos.writeBoolean(true);
   }

   @Nullable
   private Hologram readHologram(@Nonnull ObjectInputStream ois) throws IOException, ClassNotFoundException {
      UUID id = (UUID)ois.readObject();
      String name = ois.readUTF();
      double x = ois.readDouble();
      double y = ois.readDouble();
      double z = ois.readDouble();
      UUID worldId = (UUID)ois.readObject();
      UUID creatorId = (UUID)ois.readObject();
      List<String> lines = (List)ois.readObject();
      double lineSpacing = ois.readDouble();
      boolean visible = ois.readBoolean();
      FacingDirection facingDirection = FacingDirection.NORTH;

      try {
         String dirName = ois.readUTF();
         facingDirection = FacingDirection.valueOf(dirName);
         ois.readBoolean();
      } catch (Exception var18) {
      }

      Hologram hologram = new Hologram(id, name, new Vec3d(x, y, z), worldId, lines);
      hologram.setCreatorId(creatorId);
      hologram.setLineSpacing(lineSpacing);
      hologram.setVisible(visible);
      hologram.setFacingDirection(facingDirection);
      return hologram;
   }

    @Nonnull
    public String formatText(@Nonnull String text) {
      return this.plugin.getPlaceholderIntegration().processPlaceholders(text);
    }

    @Nonnull
    public String formatText(@Nonnull String text, @Nullable UUID playerUuid) {
      return this.plugin.getPlaceholderIntegration().processPlaceholders(text, playerUuid);
    }

    @Nonnull
    public String formatTextWithPlayer(@Nonnull String text, @Nullable UUID playerUuid, @Nullable String playerName) {
      return this.plugin.getPlaceholderIntegration().processPlaceholders(text, playerUuid, playerName);
    }

    @Nonnull
    public String formatTextWithFullContext(@Nonnull String text, @Nullable Player player, @Nullable UUID playerUuid, @Nullable String playerName, @Nullable String worldName, int x, int y, int z) {
      return this.plugin.getPlaceholderIntegration().processPlaceholdersWithFullContext(text, player, playerUuid, playerName, worldName, x, y, z);
    }
}


