dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.255-stable")
    compileOnly(rootProject)
}

tasks.jar {
    archiveBaseName.set("neon-testkit")
}
