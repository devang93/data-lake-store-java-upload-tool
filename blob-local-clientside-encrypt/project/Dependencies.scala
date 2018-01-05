import sbt._

object Dependencies {

  object Libraries {
    val scala_logging = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
    val logback_classic = "ch.qos.logback" % "logback-classic" % "1.2.3"
    val scallop = "org.rogach" %% "scallop" % "3.0.3"
    val joda_time = "joda-time" % "joda-time" % "2.9.9"
    val azure_data_lake_store_sdk = "com.microsoft.azure" % "azure-data-lake-store-sdk" % "2.1.5"
    val azure_storage_sdk = "com.microsoft.azure" % "azure-storage" % "5.3.1"
    val azure_keyvault_sdk = "com.microsoft.azure" % "azure-keyvault" % "0.9.7"
    val azure_keyvault_extensions_sdk = "com.microsoft.azure" % "azure-keyvault-extensions" % "0.9.7"
    val azure_eventhubs_sdk: ModuleID = "com.microsoft.azure" % "azure-eventhubs" % "0.14.0"
    val azure_eventhubs_eph: ModuleID = "com.microsoft.azure" % "azure-eventhubs-eph" % "0.14.0"
  }


}