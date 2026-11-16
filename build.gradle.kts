plugins { kotlin("jvm") version "2.0.21"; application }
repositories { mavenCentral() }
dependencies { implementation("org.bouncycastle:bcprov-jdk18on:1.78.1") }
application { mainClass.set("com.tony.explorador.TestMainKt") }
kotlin { jvmToolchain(17) }
