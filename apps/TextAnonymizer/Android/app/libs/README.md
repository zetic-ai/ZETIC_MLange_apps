# libs directory

If you have the ZeticMLange SDK AAR file, place it here and update `app/build.gradle.kts`:

```kotlin
dependencies {
    // ... other dependencies ...
    
    // Local AAR file
    implementation(files("libs/mlange.aar"))
    
    // OR if you have it as a flat directory dependency:
    // implementation(name: "mlange", ext: "aar")
}
```

Then add to `app/build.gradle.kts`:
```kotlin
android {
    // ... existing config ...
    
    repositories {
        flatDir {
            dirs("libs")
        }
    }
}
```

