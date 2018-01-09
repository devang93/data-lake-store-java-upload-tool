package com.starbucks.bids

import java.io.File
import java.net.URI
import java.nio.file.Files

import com.microsoft.azure.keyvault.extensions.SymmetricKey
import com.microsoft.azure.storage.OperationContext
import com.microsoft.azure.storage.blob.{BlobEncryptionPolicy, BlobRequestOptions, CloudBlobContainer, CloudBlobDirectory}

import scala.collection.mutable.ListBuffer

/**
  * Created by depatel on 1/5/18.
  */
object DecryptBlobFiles {

  // Test code to download and print just one file.
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

  def decryptBlobContentsRecursively(blobSasUri: String, encryptionKeyPath: String, blobStoreContainerName: String) ={
    val cloudBlobContainer = new CloudBlobContainer(new URI(blobSasUri))
    val decryptKeyBytes = Files.readAllBytes(new File(encryptionKeyPath).toPath)
    val decryptKey = new SymmetricKey("vendor", decryptKeyBytes)

    val blobNames = getBlobNamesRecursively(cloudBlobContainer, blobStoreContainerName)
    println(blobNames)
    blobNames.foreach(blob => {
      val blobEncryptionPolicy = new BlobEncryptionPolicy(decryptKey, null)
      val blobRequestOptions = new BlobRequestOptions()
      blobRequestOptions.setConcurrentRequestCount(100)
      blobRequestOptions.setEncryptionPolicy(blobEncryptionPolicy)
      val operationContext = new OperationContext()
      println(s"Download and decrypt file: ${blob}")
      println(cloudBlobContainer.getBlockBlobReference(blob).downloadText("UTF-8", null, blobRequestOptions, operationContext))
    })
  }


  private def getBlobNamesRecursively(cloudBlobContainer: CloudBlobContainer, blobStoreContainerName: String) ={
    var blobNames = ListBuffer[String]()

    cloudBlobContainer.listBlobs().forEach(blobItem => {
      println(s"For ${blobItem.getUri.getPath}")
      val name = blobItem.getUri.getPath.drop(1).split("\\/").filter(word => !(word.equals(blobStoreContainerName))).mkString("/")
      if(blobItem.isInstanceOf[CloudBlobDirectory]){
        val blobName = getBlobNamesFromDirectory(cloudBlobContainer.getDirectoryReference(name), blobStoreContainerName )
        blobNames = blobNames ++ blobName
      } else {
        val blobName = blobItem.getUri.getPath.drop(1).split("\\/").filter(word => !(word.equals(blobStoreContainerName))).mkString("/")
        blobNames += blobName
      }
    })
    blobNames.toList
  }

  private def getBlobNamesFromDirectory(cloudBlobDirectory: CloudBlobDirectory, blobStoreContainerName: String): List[String] = {
    var blobNames = ListBuffer[String]()

    cloudBlobDirectory.listBlobs().forEach(blobItem => {
      val name = blobItem.getUri.getPath.drop(1).split("\\/").filter(word => !(word.equals(blobStoreContainerName))).mkString("/")
      if(blobItem.isInstanceOf[CloudBlobDirectory]){
        val blobName = getBlobNamesFromDirectory(cloudBlobDirectory.getContainer.getDirectoryReference(name), blobStoreContainerName )
        blobNames = blobNames ++ blobName
      } else {
        val blobName = blobItem.getUri.getPath.drop(1).split("\\/").filter(word => !(word.equals(blobStoreContainerName))).mkString("/")
        blobNames += blobName
      }
    })
    blobNames.toList
  }
}
