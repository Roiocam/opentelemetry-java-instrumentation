allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.9.0-alpha-SNAPSHOT"
  } else {
    version = "1.9.0-SNAPSHOT"
  }
}
