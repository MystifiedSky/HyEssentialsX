package xyz.thelegacyvoyage.hyessentialsx.managers.hologram.animation;

public enum EasingType {
   LINEAR,
   EASE_IN,
   EASE_OUT,
   EASE_IN_OUT,
   BOUNCE,
   ELASTIC;

   public double apply(double t) {
      double var10000;
      double t2;
      switch(this.ordinal()) {
      case 0:
         var10000 = t;
         break;
      case 1:
         var10000 = t * t;
         break;
      case 2:
         var10000 = 1.0D - (1.0D - t) * (1.0D - t);
         break;
      case 3:
         var10000 = t < 0.5D ? 2.0D * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 2.0D) / 2.0D;
         break;
      case 4:
         if (t < 0.36363636363636365D) {
            var10000 = 7.5625D * t * t;
         } else if (t < 0.7272727272727273D) {
            t2 = t - 0.5454545454545454D;
            var10000 = 7.5625D * t2 * t2 + 0.75D;
         } else if (t < 0.9090909090909091D) {
            t2 = t - 0.8181818181818182D;
            var10000 = 7.5625D * t2 * t2 + 0.9375D;
         } else {
            t2 = t - 0.9545454545454546D;
            var10000 = 7.5625D * t2 * t2 + 0.984375D;
         }
         break;
      case 5:
         if (t != 0.0D && t != 1.0D) {
            t2 = 0.3D;
            double s = t2 / 4.0D;
            var10000 = Math.pow(2.0D, -10.0D * t) * Math.sin((t - s) * 6.283185307179586D / t2) + 1.0D;
         } else {
            var10000 = t;
         }
         break;
      default:
         throw new MatchException((String)null, (Throwable)null);
      }

      return var10000;
   }

   public static EasingType fromString(String str) {
      if (str != null && !str.isEmpty()) {
         try {
            return valueOf(str.toUpperCase().replace("-", "_"));
         } catch (IllegalArgumentException var2) {
            return LINEAR;
         }
      } else {
         return LINEAR;
      }
   }

   // $FF: synthetic method
   private static EasingType[] $values() {
      return new EasingType[]{LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE, ELASTIC};
   }
}


