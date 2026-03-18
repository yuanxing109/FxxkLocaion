# Xposed - keep module class
-keep public class org.xiyu.fxxklocation.ModuleMain {
    *;
}

# Keep Xposed API stubs from being included in output
-dontwarn de.robv.android.xposed.**

# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void check*(...);
	public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}
-allowaccessmodification
