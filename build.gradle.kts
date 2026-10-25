plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

allprojects {
    layout.buildDirectory.set(file("C:/Users/navaneeth kn/.faceapp_build/${project.name}"))
}
