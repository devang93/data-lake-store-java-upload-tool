import Dependencies._

lazy val commonSettings = Seq(
  name := "blob-local-clientside-encrypt" ,
  version := "1.0.0",
  scalaVersion := "2.12.2",
  organization := "com.starbucks.bids"
)

lazy val blob_local_clientside_encrypt = (project in file ("."))
  .settings(commonSettings : _*)
  .settings(libraryDependencies ++= Seq(
    Libraries.azure_data_lake_store_sdk,
    Libraries.azure_keyvault_sdk,
    Libraries.azure_keyvault_extensions_sdk,
    Libraries.azure_storage_sdk,
    Libraries.azure_eventhubs_eph,
    Libraries.azure_eventhubs_sdk,
    Libraries.scallop
  ))