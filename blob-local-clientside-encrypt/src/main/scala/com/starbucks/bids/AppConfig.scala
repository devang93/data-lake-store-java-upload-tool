package com.starbucks.bids

import org.rogach.scallop.ScallopConf
import org.rogach.scallop.exceptions.Help

/**
  * Created by depatel on 1/2/18.
  */
class AppConfig(arguments: Seq[String]) extends ScallopConf(arguments) {

  banner(
    s"""
       |Utility to copy files from one Azure Blob Container to another with client-side encryption.
       |Example:
       |java -jar <jar-file-path>
       | --spnClientId xx
       | --spnClientKey xx
       | --keyVaultResourceUri xx
       | --blobStoreAccountName xx
       | --blobStoreAccountKey xx
       | --blobStoreDestContainerName xx
       | --blobStoreSASUri xx
       | --encryptionKeyPath xx
     """.stripMargin
  )

  val spnClientId = opt[String](
    descr = "SPN Client Id for the Azure Active Directory application",
    required = true,
    name = "spnClientId",
    noshort = true
  )
  val spnClientKey = opt[String](
    descr = "SPN Client Key for the Azure Active Directory application",
    noshort = true,
    required = true,
    name = "spnClientKey"
  )
  val blobStoreSASUri = opt[String](
    descr = "Azure Blob store destination container uri",
    noshort = true,
    required = true,
    name = "blobStoreSASUri"
  )
  val keyVaultResourceId = opt[String](
    descr = "Azure Key Vault key ID uri",
    noshort = true,
    required = true,
    name = "keyVaultResourceUri"
  )
  val blobStoreAccountName = opt[String](
    descr = "Azure Blob Storage Account name",
    noshort = true,
    required = true,
    name = "blobStoreAccountName"
  )
  val blobStoreAccountKey = opt[String](
    descr = "Key to access Azure Blob Storage Account",
    noshort = true,
    required = true,
    name = "blobStoreAccountKey"
  )
  val blobStoreSourceContainerName = opt[String](
    descr = "Azure Blob Storage Source Container name having files to encrypt",
    noshort = true,
    required = true,
    name = "blobStoreSourceContainerName"
  )
  val blobDirectory = opt[String](
    descr = "Azure blob Storage Source Container Directory Name",
    noshort = true,
    required = true,
    name = "blobDirectory"
  )
  val blobStoreDestContainerName = opt[String](
    descr = "Azure Blob Storage Destination Container name to upload encrypted files to",
    noshort = true,
    required = true,
    name = "blobStoreDestContainerName"
  )
  val encryptionKeyPath = opt[String](
    descr = "File path to encryption key stored on locally",
    noshort = true,
    required = true,
    name = "encryptionKeyPath"
  )

  override def onError(e: Throwable): Unit = e match {
    case Help("") => printHelp()
  }

  verify()
}
