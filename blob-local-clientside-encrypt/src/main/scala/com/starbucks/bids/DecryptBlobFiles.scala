package com.starbucks.bids

import java.io.File
import java.net.URI
import java.nio.file.Files

import com.microsoft.azure.keyvault.extensions.SymmetricKey
import com.microsoft.azure.storage.OperationContext
import com.microsoft.azure.storage.blob.{BlobEncryptionPolicy, BlobRequestOptions, CloudBlobContainer}

/**
  * Created by depatel on 1/5/18.
  */
object DecryptBlobFiles {

  def decryptBlobFilesToConsole(blobSasUri: String, encryptionKeyPath: String, blobStoreContainerName: String): Unit = {

    val cloudBlobContainer = new CloudBlobContainer(new URI(blobSasUri))

    val decryptKeyBytes = Files.readAllBytes(new File(encryptionKeyPath).toPath)
    val decryptKey = new SymmetricKey("vendor", decryptKeyBytes)

    cloudBlobContainer.listBlobs().forEach( blob => {
      val blobDecryptionPolicy = new BlobEncryptionPolicy(decryptKey, null)
      val blobRequestOptions = new BlobRequestOptions()
      blobRequestOptions.setConcurrentRequestCount(10)
      blobRequestOptions.setEncryptionPolicy(blobDecryptionPolicy)
      val operationContext = new OperationContext()
      val blobFix = "/vendorexportonetime/data/sb/green/outgoing/AmperityLyticsAndRedPointExport/ICTData/Header.txt"
      val blobName = blob.getUri.getPath.drop(1).split("\\/").filter(word => !(word.contains(blobStoreContainerName))).mkString("/")+blobFix
      println(blob.getUri.getPath.drop(1).split("\\/").filter(word => !(word.contains(blobStoreContainerName))).mkString("/"))
      println(blobName)
      val blobReference = cloudBlobContainer.getBlockBlobReference(blobName)
      println(s"PRINTING THE FILE : ${blobName}")
      println(blobReference.downloadText("UTF-8", null, blobRequestOptions, operationContext))
    })
  }
}
