package xyz.thelegacyvoyage.hyessentialsx.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.modules.collision.WorldUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldBlockUtil {
    private WorldBlockUtil() {}

    public static boolean isClearBlock(@Nonnull World world, int x, int y, int z) {
        BlockType type = getBlockType(world, x, y, z);
        return type != null && type.getMaterial() != BlockMaterial.Solid
                && WorldUtil.getFluidIdAtPosition(world.getChunkStore(), x, y, z) == 0;
    }

    /** Reads a resident section only; unavailable terrain must not count as safe air. */
    @Nullable
    public static BlockType getBlockType(@Nonnull World world, int x, int y, int z) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) return null;
        BlockSection section = chunks.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        return section == null ? null : BlockType.getAssetMap().getAsset(section.get(x, y, z));
    }
}
