plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.bouncycastle)
    testImplementation(libs.kotlin.test)
}