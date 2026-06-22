package xyz.thelegacyvoyage.hyessentialsx.util.hologram;

import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class ByteArrayCommonAsset extends CommonAsset {
   private final byte[] data;

   public ByteArrayCommonAsset(@Nonnull String name, @Nonnull byte[] bytes) {
      super(name, bytes);
      this.data = bytes;
   }

   public ByteArrayCommonAsset(@Nonnull String name, @Nonnull String hash, @Nonnull byte[] bytes) {
      super(name, hash, bytes);
      this.data = bytes;
   }

   @Nonnull
   protected CompletableFuture<byte[]> getBlob0() {
      return CompletableFuture.completedFuture(this.data);
   }

   @Nonnull
   public String toString() {
      String var10000 = this.getName();
      return "ByteArrayCommonAsset{name='" + var10000 + "', hash='" + this.getHash() + "', size=" + this.data.length + "}";
   }
}


